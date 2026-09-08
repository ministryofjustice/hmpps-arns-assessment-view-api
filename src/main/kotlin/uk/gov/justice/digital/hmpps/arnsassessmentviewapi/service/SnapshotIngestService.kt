package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.AapApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.CoordinatorEvent
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.DeleteFlagUpdatePayload
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.VersionPayload
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository.SentencePlanRepository

@Service
class SnapshotIngestService(
  private val aapApiClient: AapApiClient,
  private val sentencePlanRepository: SentencePlanRepository,
  private val mapper: SentencePlanMapper,
  private val timelineAuthorshipFetcher: TimelineAuthorshipFetcher,
  private val transactionTemplate: TransactionTemplate,
) {

  fun ingestVersion(envelope: CoordinatorEvent, payload: VersionPayload) {
    val incomingAsOf = payload.incrementedAt.toInstant()
    val existing = sentencePlanRepository.findByIdAndVersion(envelope.entityUuid, payload.version).orElse(null)

    if (existing != null && existing.updatedAt >= incomingAsOf) {
      log.info(
        "Snapshot at {}@{} is at-or-newer than incoming event (stored={}, incoming={}); skipping",
        envelope.entityUuid,
        payload.version,
        existing.updatedAt,
        incomingAsOf,
      )
      return
    }

    val assessment = aapApiClient.queryAt(envelope.entityUuid, payload.incrementedAt) ?: run {
      log.warn("AAP returned no assessment for {} @ {}; skipping", envelope.entityUuid, payload.incrementedAt)
      return
    }

    val authorship = timelineAuthorshipFetcher.fetchIfNeeded(assessment)

    transactionTemplate.execute {
      // Re-read inside the transaction: `existing` was loaded by a session that has since closed,
      // so clearing its lazy collections would throw. Mirrors SentencePlanSyncService.upsert.
      val managed = sentencePlanRepository.findByIdAndVersion(envelope.entityUuid, payload.version).orElse(null)
      if (managed != null) {
        managed.identifiers.clear()
        managed.agreements.clear()
        managed.goals.clear()

        // Flushing after clear() keeps the unique constraint on (sentence_plan_id, type, value) from tripping.
        sentencePlanRepository.saveAndFlush(managed)
      }
      val entity = mapper.toEntity(assessment, payload, managed, authorship)
      sentencePlanRepository.save(entity)
    }

    log.info(
      "{} snapshot for {}@{} (oasysEvent={})",
      if (existing == null) "Inserted" else "Replaced",
      envelope.entityUuid,
      payload.version,
      payload.oasysEvent,
    )
  }

  fun applyDeleteFlagUpdate(envelope: CoordinatorEvent, payload: DeleteFlagUpdatePayload) {
    val updated = sentencePlanRepository.markDeletedForRange(
      id = envelope.entityUuid,
      deleted = payload.deleted,
      versionFrom = payload.versionFrom,
      versionTo = payload.versionTo,
    )
    log.info(
      "Set deleted={} on {} rows for entity {} (versionFrom={} versionTo={})",
      payload.deleted,
      updated,
      envelope.entityUuid,
      payload.versionFrom,
      payload.versionTo,
    )
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
