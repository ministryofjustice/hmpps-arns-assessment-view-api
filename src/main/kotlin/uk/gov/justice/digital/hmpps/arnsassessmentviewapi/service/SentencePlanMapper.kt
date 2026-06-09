package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.AssessmentVersionQueryResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.Collection
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.CollectionItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.EntityAssociationDetails
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.FreeTextEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.FreeTextType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalNoteType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalRelatedAreaOfNeedEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanAgreementEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanIdentifierEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.StepEntity
import java.time.Instant
import java.util.UUID

@Component
class SentencePlanMapper {

  fun toEntity(
    source: AssessmentVersionQueryResult,
    association: EntityAssociationDetails,
    existing: SentencePlanEntity?,
    authorship: Map<UUID, ItemAuthorship>,
  ): SentencePlanEntity {
    val plan = existing ?: SentencePlanEntity(
      id = source.assessmentUuid,
      createdAt = source.createdAt.toInstant(),
      updatedAt = source.updatedAt.toInstant(),
      lastSyncedAt = Instant.now(),
      oasysPk = association.oasysAssessmentPk,
      // The sync owns the mutable current state row, lifecycle event snapshots are written elsewhere.
      version = SentencePlanEntity.CURRENT_VERSION,
      regionCode = association.regionPrisonCode,
    )

    if (existing != null) {
      // The assessment UUID is the only invariant per assessment. Every other field
      // refreshes on every sync so view-api mirrors AAP+coordinator's current state.
      // `deleted` is deliberately NOT touched here, it is owned by the soft-delete pass.
      existing.createdAt = source.createdAt.toInstant()
      existing.updatedAt = source.updatedAt.toInstant()
      existing.lastSyncedAt = Instant.now()
      existing.oasysPk = association.oasysAssessmentPk
      existing.version = SentencePlanEntity.CURRENT_VERSION
      existing.regionCode = association.regionPrisonCode
      existing.identifiers.clear()
      existing.agreements.clear()
      existing.goals.clear()
    }

    plan.identifiers.addAll(mapIdentifiers(source, plan))
    plan.agreements.addAll(mapAgreements(source.collections, plan, authorship))
    plan.goals.addAll(mapGoals(source.collections, plan, authorship))

    return plan
  }

  private fun mapIdentifiers(source: AssessmentVersionQueryResult, plan: SentencePlanEntity): List<SentencePlanIdentifierEntity> = source.identifiers.mapNotNull { (aapType, value) ->
    val entityType = IDENTIFIER_TYPE_BY_AAP[aapType]
    if (entityType == null) {
      log.debug("Dropping unsupported AAP identifier {} for plan {}", aapType, plan.id)
      return@mapNotNull null
    }
    SentencePlanIdentifierEntity(
      id = UUID.randomUUID(),
      sentencePlan = plan,
      type = entityType,
      value = value,
    )
  }

  private fun mapAgreements(collections: List<Collection>, plan: SentencePlanEntity, authorship: Map<UUID, ItemAuthorship>): List<PlanAgreementEntity> {
    val collection = collections.firstOrNull { it.name == COLLECTION_PLAN_AGREEMENTS } ?: return emptyList()
    return collection.items.mapNotNull { item -> mapAgreement(item, plan, authorship) }
  }

  private fun mapAgreement(item: CollectionItem, plan: SentencePlanEntity, authorship: Map<UUID, ItemAuthorship>): PlanAgreementEntity? {
    val statusKey = item.properties.asString("status") ?: run {
      log.warn("Agreement {} missing 'status' property; skipping", item.uuid)
      return null
    }
    val status = PLAN_STATUS_BY_KEY[statusKey] ?: run {
      log.warn("Agreement {} has unknown status '{}'; skipping", item.uuid, statusKey)
      return null
    }
    val createdAt = item.createdAt.toInstant()
    val createdBy = authorship[item.uuid]?.createdBy ?: error("No timeline authorship found for agreement ${item.uuid}")
    val agreement = PlanAgreementEntity(
      id = item.uuid,
      sentencePlan = plan,
      status = status,
      statusDate = item.properties.asString("status_date")?.toInstantOrNull(),
      createdByUserId = createdBy,
      createdAt = createdAt,
    )

    AGREEMENT_FREE_TEXT_ANSWERS.forEach { (answerKey, type) ->
      val text = item.answers.asString(answerKey) ?: return@forEach
      agreement.freeTexts.add(
        FreeTextEntity(
          id = UUID.randomUUID(),
          type = type,
          textLength = text.length,
          textHash = sha256Hex(text),
          planAgreement = agreement,
          createdByUserId = createdBy,
          createdAt = createdAt,
        ),
      )
    }

    return agreement
  }

  private fun mapGoals(collections: List<Collection>, plan: SentencePlanEntity, authorship: Map<UUID, ItemAuthorship>): List<GoalEntity> {
    val collection = collections.firstOrNull { it.name == COLLECTION_GOALS } ?: return emptyList()
    return collection.items.mapIndexedNotNull { index, item -> mapGoal(item, index, plan, authorship) }
  }

  private fun mapGoal(item: CollectionItem, order: Int, plan: SentencePlanEntity, authorship: Map<UUID, ItemAuthorship>): GoalEntity? {
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

    val goalAuthorship = authorship[item.uuid] ?: error("No timeline authorship found for goal ${item.uuid}")
    val goal = GoalEntity(
      id = item.uuid,
      sentencePlan = plan,
      titleLength = title.length,
      titleHash = sha256Hex(title),
      areaOfNeed = areaOfNeed,
      targetDate = item.answers.asString("target_date")?.toLocalDateOrNull(),
      status = status,
      statusDate = item.properties.asString("status_date")?.toInstantOrNull(),
      createdAt = item.createdAt.toInstant(),
      updatedAt = item.updatedAt.toInstant(),
      goalOrder = order,
      createdByUserId = goalAuthorship.createdBy,
      updatedByUserId = goalAuthorship.updatedBy,
    )

    item.answers.asStringList("related_areas_of_need").forEach { slug ->
      val related = AREA_OF_NEED_BY_SLUG[slug] ?: run {
        log.warn("Goal {} has unknown related area '{}'; skipping related entry", item.uuid, slug)
        return@forEach
      }
      goal.relatedAreasOfNeed.add(
        GoalRelatedAreaOfNeedEntity(goal = goal, criminogenicNeed = related),
      )
    }

    item.collections.firstOrNull { it.name == COLLECTION_STEPS }
      ?.items
      ?.mapNotNullTo(goal.steps) { step -> mapStep(step, goal, authorship) }

    item.collections.firstOrNull { it.name == COLLECTION_NOTES }
      ?.items
      ?.mapNotNullTo(goal.freeTexts) { note -> mapGoalNote(note, goal, authorship) }

    return goal
  }

  private fun mapStep(item: CollectionItem, goal: GoalEntity, authorship: Map<UUID, ItemAuthorship>): StepEntity? {
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
    val createdBy = authorship[item.uuid]?.createdBy ?: error("No timeline authorship found for step ${item.uuid}")
    return StepEntity(
      id = item.uuid,
      goal = goal,
      description = description,
      actor = actor,
      status = status,
      statusDate = item.properties.asString("status_date")?.toInstantOrNull(),
      createdAt = item.createdAt.toInstant(),
      createdByUserId = createdBy,
    )
  }

  private fun mapGoalNote(item: CollectionItem, goal: GoalEntity, authorship: Map<UUID, ItemAuthorship>): FreeTextEntity? {
    val text = item.answers.asString("note") ?: run {
      log.warn("Goal note {} missing 'note'; skipping", item.uuid)
      return null
    }
    val createdAt = item.properties.asString("created_at")?.toInstantOrNull() ?: run {
      log.warn("Goal note {} missing 'created_at' property; skipping", item.uuid)
      return null
    }
    // aap-ui omits 'type' for progress notes; legacy migrator emits explicit "PROGRESS". Both map to PROGRESS.
    val noteTypeKey = item.properties.asString("type")
    val noteType = if (noteTypeKey == null) {
      GoalNoteType.PROGRESS
    } else {
      GOAL_NOTE_TYPE_BY_KEY[noteTypeKey] ?: run {
        log.warn("Goal note {} has unknown type '{}'; skipping", item.uuid, noteTypeKey)
        return null
      }
    }
    val createdBy = authorship[item.uuid]?.createdBy ?: error("No timeline authorship found for goal note ${item.uuid}")
    return FreeTextEntity(
      id = item.uuid,
      type = FreeTextType.GOAL_NOTE,
      textLength = text.length,
      textHash = sha256Hex(text),
      goal = goal,
      createdByUserId = createdBy,
      createdAt = createdAt,
      goalNoteType = noteType,
    )
  }

  private companion object {
    private val log = LoggerFactory.getLogger(SentencePlanMapper::class.java)
  }
}
