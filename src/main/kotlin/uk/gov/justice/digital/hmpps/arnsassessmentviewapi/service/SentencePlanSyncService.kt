package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.AapApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.CoordinatorApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.AssessmentVersionQueryResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.EntityAssociationDetails
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.GetAssessmentsModifiedSinceResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SyncStateEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository.SentencePlanRepository
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository.SyncStateRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@Service
class SentencePlanSyncService(
  private val aapApiClient: AapApiClient,
  private val coordinatorApiClient: CoordinatorApiClient,
  private val sentencePlanRepository: SentencePlanRepository,
  private val syncStateRepository: SyncStateRepository,
  private val mapper: SentencePlanMapper,
  private val transactionTemplate: TransactionTemplate,
) {
  private enum class UpsertOutcome { INSERTED, UPDATED }

  fun sync() {
    val syncStartTime = Instant.now()
    val since = computeSince()
    log.info("Sentence plan sync starting (since={}, syncStart={})", since, syncStartTime)

    var inserted = 0
    var updated = 0
    var skipped = 0
    var failed = 0
    var processed = 0
    var deletedFlagged = 0

    var cursor: UUID? = null
    while (true) {
      val page = fetchPage(since, cursor) ?: return

      var pageInserted = 0
      var pageUpdated = 0
      var pageFailed = 0
      val pageSkipped = mutableListOf<UUID>()

      if (page.assessments.isNotEmpty()) {
        val details = coordinatorApiClient.getLatestAssociationDetails(page.assessments.map { it.assessmentUuid })
        page.assessments.forEach { assessment ->
          processed++
          val association = details[assessment.assessmentUuid]
          if (association == null) {
            skipped++
            pageSkipped.add(assessment.assessmentUuid)
            return@forEach
          }
          runCatching { upsert(assessment, association) }
            .onSuccess { outcome ->
              when (outcome) {
                UpsertOutcome.INSERTED -> {
                  inserted++
                  pageInserted++
                }
                UpsertOutcome.UPDATED -> {
                  updated++
                  pageUpdated++
                }
              }
            }
            .onFailure {
              failed++
              pageFailed++
              log.warn("Upsert failed for assessment {}", assessment.assessmentUuid, it)
            }
        }

        log.info(
          "Processed {} assessments ({} in this page) — page: inserted={} updated={} skipped={} failed={} | running: inserted={} updated={} skipped={} failed={}",
          processed,
          page.assessments.size,
          pageInserted,
          pageUpdated,
          pageSkipped.size,
          pageFailed,
          inserted,
          updated,
          skipped,
          failed,
        )
        if (pageSkipped.isNotEmpty()) {
          log.warn("Skipped (no coordinator record): {}", pageSkipped.joinToString())
        }
      }

      cursor = page.nextCursor ?: break
    }

    deletedFlagged = applySoftDeletes(since)

    persistSyncStartTime(syncStartTime)

    log.info(
      "Sync complete: inserted={} updated={} skipped={} failed={} processed={} deleted={} (since={}, syncStart={})",
      inserted,
      updated,
      skipped,
      failed,
      processed,
      deletedFlagged,
      since,
      syncStartTime,
    )
  }

  // AAP doesn't include deleted assessments in the modified-since stream, so we ask separately.
  // Returns count of rows newly flagged deleted.
  private fun applySoftDeletes(since: LocalDateTime): Int {
    val deletedUuids = try {
      aapApiClient.querySoftDeletedSince(ASSESSMENT_TYPE, since)
    } catch (ex: WebClientResponseException) {
      if (ex.isLookbackGuardTripped()) {
        log.error("AAP rejected soft-delete query: lookback guard tripped ({}).", ex.responseBodyAsString.ifBlank { ex.message })
        return 0
      }
      throw ex
    }
    if (deletedUuids.isEmpty()) return 0

    return transactionTemplate.execute {
      var flagged = 0
      deletedUuids.forEach { uuid ->
        val plan = sentencePlanRepository.findById(uuid).orElse(null) ?: return@forEach
        if (!plan.deleted) {
          plan.deleted = true
          sentencePlanRepository.save(plan)
          flagged++
        }
      }
      flagged
    } ?: 0
  }

  // Watermark = previous sync's start time, so the window covers everything updated since the previous sync began
  private fun computeSince(): LocalDateTime = syncStateRepository.findById(SYNC_STATE_KEY)
    .orElse(null)
    ?.lastSyncStartedAt
    ?.minus(WATERMARK_BUFFER)
    ?.atZone(ZoneId.systemDefault())
    ?.toLocalDateTime()
    ?: EPOCH

  private fun persistSyncStartTime(syncStartTime: Instant) {
    val state = syncStateRepository.findById(SYNC_STATE_KEY)
      .orElseGet { SyncStateEntity(id = SYNC_STATE_KEY) }
    state.lastSyncStartedAt = syncStartTime
    syncStateRepository.save(state)
  }

  private fun fetchPage(since: LocalDateTime, after: UUID?): GetAssessmentsModifiedSinceResult? = try {
    aapApiClient.queryModifiedSince(ASSESSMENT_TYPE, since, after, PAGE_SIZE)
  } catch (ex: WebClientResponseException) {
    if (ex.isLookbackGuardTripped()) {
      log.error(
        "AAP rejected sync: lookback guard tripped ({}).",
        ex.responseBodyAsString.ifBlank { ex.message },
      )
      null
    } else {
      throw ex
    }
  }

  // Timeline fetch runs outside the DB transaction — the outbound AAP call can be slow and
  // we don't want a Hikari connection held open for it.
  private fun upsert(assessment: AssessmentVersionQueryResult, association: EntityAssociationDetails): UpsertOutcome {
    // Timeline fetch runs outside the DB transaction, we don't want the connection held open for it.
    val authorship = if (needsTimelineAuthorship(assessment)) {
      fetchTimelineAuthorship(assessment.assessmentUuid)
    } else {
      emptyMap()
    }

    // TransactionTemplate (not @Transactional) avoids the self-invocation proxy trap.
    val outcome: UpsertOutcome? = transactionTemplate.execute {
      val existing = sentencePlanRepository.findById(assessment.assessmentUuid).orElse(null)
      if (existing != null) {
        existing.identifiers.clear()
        existing.agreements.clear()
        existing.goals.clear()

        // Flushing after clear() keeps the table's unique constraint from being violated when re-adding the same (type, value) pair.
        sentencePlanRepository.saveAndFlush(existing)
      }
      val entity = mapper.toEntity(assessment, association, existing, authorship)
      sentencePlanRepository.save(entity)
      if (existing == null) UpsertOutcome.INSERTED else UpsertOutcome.UPDATED
    }
    return outcome ?: error("Upsert transaction returned null for ${assessment.assessmentUuid}")
  }

  // Goals (and their child steps) need authorship attribution alongside agreements and notes,
  // so any non-empty agreements/goals collection triggers the timeline fetch. Empty plans skip it.
  private fun needsTimelineAuthorship(assessment: AssessmentVersionQueryResult): Boolean = assessment.collections.any { collection ->
    (collection.name == COLLECTION_PLAN_AGREEMENTS || collection.name == COLLECTION_GOALS) && collection.items.isNotEmpty()
  }

  // Two timeline queries: standard ADD events for createdBy on every item, custom GOAL_* rows for
  // goal updatedBy. Only goals carry updatedBy aap-ui doesn't track per-step update authorship.
  private fun fetchTimelineAuthorship(assessmentUuid: UUID): Map<UUID, ItemAuthorship> {
    val createdBy = harvestCreatedBy(assessmentUuid)
    val goalUpdatedBy = harvestGoalUpdatedBy(assessmentUuid)
    return createdBy.mapValues { (itemUuid, creator) ->
      ItemAuthorship(createdBy = creator, updatedBy = goalUpdatedBy[itemUuid])
    }
  }

  private fun harvestCreatedBy(assessmentUuid: UUID): Map<UUID, UUID> {
    val createdBy = mutableMapOf<UUID, UUID>()
    var pageNumber = 0
    while (true) {
      val page = aapApiClient.queryTimeline(
        assessmentUuid = assessmentUuid,
        includeEventTypes = setOf(ADD_EVENT_TYPE),
        pageNumber = pageNumber,
        pageSize = PAGE_SIZE,
      )
      page.timeline.forEach { item ->
        val itemUuid = (item.data[TIMELINE_DATA_ITEM_UUID] as? String)?.let(UUID::fromString) ?: return@forEach
        createdBy[itemUuid] = item.user.id
      }
      if (page.timeline.isEmpty() || pageNumber >= page.pageInfo.totalPages - 1) break
      pageNumber++
    }
    return createdBy
  }

  private fun harvestGoalUpdatedBy(assessmentUuid: UUID): Map<UUID, UUID> {
    data class Latest(val timestamp: LocalDateTime, val user: UUID)
    val latest = mutableMapOf<UUID, Latest>()
    var pageNumber = 0
    while (true) {
      val page = aapApiClient.queryTimeline(
        assessmentUuid = assessmentUuid,
        includeCustomTypes = GOAL_UPDATE_CUSTOM_TYPES,
        pageNumber = pageNumber,
        pageSize = PAGE_SIZE,
      )
      page.timeline.forEach { item ->
        val customData = item.customData ?: return@forEach
        val goalUuid = (customData[CUSTOM_DATA_GOAL_UUID] as? String)?.let(UUID::fromString) ?: return@forEach
        // Compare by timestamp
        val current = latest[goalUuid]
        if (current == null || item.timestamp.isAfter(current.timestamp)) {
          latest[goalUuid] = Latest(item.timestamp, item.user.id)
        }
      }
      if (page.timeline.isEmpty() || pageNumber >= page.pageInfo.totalPages - 1) break
      pageNumber++
    }
    return latest.mapValues { (_, l) -> l.user }
  }

  private fun WebClientResponseException.isLookbackGuardTripped(): Boolean = statusCode.is4xxClientError && responseBodyAsString.contains(LOOKBACK_GUARD_MARKER)

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    private const val ASSESSMENT_TYPE = "SENTENCE_PLAN"
    private const val PAGE_SIZE = 50
    private const val ADD_EVENT_TYPE = "CollectionItemAddedEvent"
    private val GOAL_UPDATE_CUSTOM_TYPES = setOf("GOAL_UPDATED", "GOAL_ACHIEVED", "GOAL_REMOVED", "GOAL_READDED")
    private const val TIMELINE_DATA_ITEM_UUID = "collectionItemUuid"
    private const val CUSTOM_DATA_GOAL_UUID = "goalUuid"
    private const val COLLECTION_GOALS = "GOALS"
    private const val COLLECTION_PLAN_AGREEMENTS = "PLAN_AGREEMENTS"
    private const val LOOKBACK_GUARD_MARKER = "cannot be older than"
    private const val SYNC_STATE_KEY = "sentence_plan"

    private val WATERMARK_BUFFER: Duration = Duration.ofMinutes(1)
    private val EPOCH: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)
  }
}

data class ItemAuthorship(val createdBy: UUID, val updatedBy: UUID?)
