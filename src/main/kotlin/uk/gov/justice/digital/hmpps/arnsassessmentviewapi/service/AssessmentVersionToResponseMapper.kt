package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.AssessmentVersionQueryResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.Collection
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.CollectionItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.IdentifierType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.controller.response.GoalResponse
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.controller.response.SentencePlanResponse
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.controller.response.StepResponse
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.CriminogenicNeed
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanStatus

// Shares the lookup tables and extraction helpers in SentencePlanMappings.kt with
// SentencePlanMapper, but keeps its own per-collection traversal (mapGoal/mapStep/…).
// Because the two mappers consume the same AAP shape but emit different objects: JPA entities for
// the sync path, plain response DTOs here.
@Component
class AssessmentVersionToResponseMapper {

  fun toResponse(source: AssessmentVersionQueryResult): SentencePlanResponse = SentencePlanResponse(
    crn = source.identifiers[IdentifierType.CRN],
    nomis = source.identifiers[IdentifierType.NOMIS_ID],
    planStatus = latestPlanStatus(source.collections),
    goals = mapGoals(source.collections),
  )

  private fun latestPlanStatus(collections: List<Collection>): PlanStatus? {
    val agreements = collections.firstOrNull { it.name == COLLECTION_PLAN_AGREEMENTS } ?: return null
    val latest = agreements.items.maxByOrNull { it.createdAt } ?: return null
    val statusKey = latest.properties.asString("status") ?: return null
    return PLAN_STATUS_BY_KEY[statusKey]
  }

  private fun mapGoals(collections: List<Collection>): List<GoalResponse> {
    val goals = collections.firstOrNull { it.name == COLLECTION_GOALS } ?: return emptyList()
    return goals.items.mapNotNull(::mapGoal)
  }

  private fun mapGoal(item: CollectionItem): GoalResponse? {
    val title = item.answers.asString("title") ?: run {
      log.warn("Goal {} missing 'title'; skipping", item.uuid)
      return null
    }
    val areaSlug = item.answers.asString("area_of_need") ?: run {
      log.warn("Goal {} missing 'area_of_need'; skipping", item.uuid)
      return null
    }
    val areaOfNeed = AREA_OF_NEED_BY_SLUG[areaSlug] ?: run {
      log.warn("Goal {} has unknown area_of_need '{}'; skipping", item.uuid, areaSlug)
      return null
    }
    val statusKey = item.properties.asString("status") ?: run {
      log.warn("Goal {} missing 'status' property; skipping", item.uuid)
      return null
    }
    val status = GOAL_STATUS_BY_KEY[statusKey] ?: run {
      log.warn("Goal {} has unknown status '{}'; skipping", item.uuid, statusKey)
      return null
    }

    val relatedAreasOfNeed: List<CriminogenicNeed> = item.answers.asStringList("related_areas_of_need")
      .mapNotNull { slug ->
        AREA_OF_NEED_BY_SLUG[slug] ?: run {
          log.warn("Goal {} has unknown related area '{}'; skipping related entry", item.uuid, slug)
          null
        }
      }

    val steps = item.collections.firstOrNull { it.name == COLLECTION_STEPS }
      ?.items
      ?.mapNotNull { step -> mapStep(step) }
      .orEmpty()

    return GoalResponse(
      goalTitle = title,
      areaOfNeed = areaOfNeed,
      relatedAreasOfNeed = relatedAreasOfNeed,
      targetDate = item.answers.asString("target_date")?.toLocalDateOrNull(),
      goalStatus = status,
      steps = steps,
    )
  }

  private fun mapStep(item: CollectionItem): StepResponse? {
    val description = item.answers.asString("description") ?: run {
      log.warn("Step {} missing 'description'; skipping", item.uuid)
      return null
    }
    val actorKey = item.answers.asString("actor") ?: run {
      log.warn("Step {} missing 'actor'; skipping", item.uuid)
      return null
    }
    val actor = ACTOR_BY_KEY[actorKey] ?: run {
      log.warn("Step {} has unknown actor '{}'; skipping", item.uuid, actorKey)
      return null
    }
    val statusKey = item.answers.asString("status") ?: run {
      log.warn("Step {} missing 'status'; skipping", item.uuid)
      return null
    }
    val status = STEP_STATUS_BY_KEY[statusKey] ?: run {
      log.warn("Step {} has unknown status '{}'; skipping", item.uuid, statusKey)
      return null
    }
    return StepResponse(
      description = description,
      status = status,
      actor = actor,
      statusDate = item.properties.asString("status_date")?.toInstantOrNull(),
    )
  }

  private companion object {
    private val log = LoggerFactory.getLogger(AssessmentVersionToResponseMapper::class.java)
  }
}
