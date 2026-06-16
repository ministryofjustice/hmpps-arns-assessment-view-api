package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.AapApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.AssessmentVersionQueryResult
import java.time.LocalDateTime
import java.util.UUID

/**
 * Pulls per item authorship from AAP's timeline. Shared by the sync and the SQS
 * snapshot ingest paths.
 */
@Component
class TimelineAuthorshipFetcher(
  private val aapApiClient: AapApiClient,
) {

  fun fetchIfNeeded(assessment: AssessmentVersionQueryResult): Map<UUID, ItemAuthorship> {
    if (!needsTimelineAuthorship(assessment)) return emptyMap()
    val createdBy = harvestCreatedBy(assessment.assessmentUuid)
    val goalUpdatedBy = harvestGoalUpdatedBy(assessment.assessmentUuid)
    return createdBy.mapValues { (itemUuid, creator) ->
      ItemAuthorship(createdBy = creator, updatedBy = goalUpdatedBy[itemUuid])
    }
  }

  // Any non empty goals/agreements collection triggers the fetch so empty plans skip the AAP call.
  private fun needsTimelineAuthorship(assessment: AssessmentVersionQueryResult): Boolean = assessment.collections.any { collection ->
    (collection.name == COLLECTION_PLAN_AGREEMENTS || collection.name == COLLECTION_GOALS) && collection.items.isNotEmpty()
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

  private companion object {
    private const val PAGE_SIZE = 50
    private const val ADD_EVENT_TYPE = "CollectionItemAddedEvent"
    private val GOAL_UPDATE_CUSTOM_TYPES = setOf("GOAL_UPDATED", "GOAL_ACHIEVED", "GOAL_REMOVED", "GOAL_READDED")
    private const val TIMELINE_DATA_ITEM_UUID = "collectionItemUuid"
    private const val CUSTOM_DATA_GOAL_UUID = "goalUuid"
    private const val COLLECTION_GOALS = "GOALS"
    private const val COLLECTION_PLAN_AGREEMENTS = "PLAN_AGREEMENTS"
  }
}
