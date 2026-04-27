package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.AssessmentVersionQueryResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.Collection
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.CollectionItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.EntityAssociationDetails
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.MultiValue
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.SingleValue
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.Value
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.ActorType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.CriminogenicNeed
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.FreeTextEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.FreeTextType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalNoteType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalRelatedAreaOfNeedEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanAgreementEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanIdentifierEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.StepEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.StepStatus
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.IdentifierType as AapIdentifierType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.IdentifierType as EntityIdentifierType

@Component
class SentencePlanMapper {

  fun toEntity(
    source: AssessmentVersionQueryResult,
    association: EntityAssociationDetails,
    existing: SentencePlanEntity?,
    creators: Map<UUID, UUID>,
  ): SentencePlanEntity {
    val plan = existing ?: SentencePlanEntity(
      id = source.assessmentUuid,
      createdAt = source.createdAt.toInstant(),
      updatedAt = source.updatedAt.toInstant(),
      lastSyncedAt = Instant.now(),
      oasysPk = association.oasysAssessmentPk.toIntOrNull(),
      version = association.baseVersion.toInt(),
      regionCode = association.regionPrisonCode,
      deleted = association.deleted,
    )

    if (existing != null) {
      existing.deleted = association.deleted
      existing.lastSyncedAt = Instant.now()
      existing.identifiers.clear()
      existing.agreements.clear()
      existing.goals.clear()
    }

    plan.identifiers.addAll(mapIdentifiers(source, plan))
    plan.agreements.addAll(mapAgreements(source.collections, plan, creators))
    plan.goals.addAll(mapGoals(source.collections, plan, creators))

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

  private fun mapAgreements(collections: List<Collection>, plan: SentencePlanEntity, creators: Map<UUID, UUID>): List<PlanAgreementEntity> {
    val collection = collections.firstOrNull { it.name == COLLECTION_PLAN_AGREEMENTS } ?: return emptyList()
    return collection.items.mapNotNull { item -> mapAgreement(item, plan, creators) }
  }

  private fun mapAgreement(item: CollectionItem, plan: SentencePlanEntity, creators: Map<UUID, UUID>): PlanAgreementEntity? {
    val statusKey = item.properties.asString("status") ?: run {
      log.warn("Agreement {} missing 'status' property; skipping", item.uuid)
      return null
    }
    val status = PLAN_STATUS_BY_KEY[statusKey] ?: run {
      log.warn("Agreement {} has unknown status '{}'; skipping", item.uuid, statusKey)
      return null
    }
    val createdAt = item.createdAt.toInstant()
    val createdBy = creators[item.uuid] ?: error("No timeline creator found for agreement ${item.uuid}")
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

  private fun mapGoals(collections: List<Collection>, plan: SentencePlanEntity, creators: Map<UUID, UUID>): List<GoalEntity> {
    val collection = collections.firstOrNull { it.name == COLLECTION_GOALS } ?: return emptyList()
    return collection.items.mapIndexedNotNull { index, item -> mapGoal(item, index, plan, creators) }
  }

  private fun mapGoal(item: CollectionItem, order: Int, plan: SentencePlanEntity, creators: Map<UUID, UUID>): GoalEntity? {
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
      ?.mapNotNullTo(goal.steps) { step -> mapStep(step, goal) }

    item.collections.firstOrNull { it.name == COLLECTION_NOTES }
      ?.items
      ?.mapNotNullTo(goal.freeTexts) { note -> mapGoalNote(note, goal, creators) }

    return goal
  }

  private fun mapStep(item: CollectionItem, goal: GoalEntity): StepEntity? {
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
    return StepEntity(
      id = item.uuid,
      goal = goal,
      description = description,
      actor = actor,
      status = status,
      statusDate = item.properties.asString("status_date")?.toInstantOrNull(),
      createdAt = item.createdAt.toInstant(),
    )
  }

  private fun mapGoalNote(item: CollectionItem, goal: GoalEntity, creators: Map<UUID, UUID>): FreeTextEntity? {
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
    val createdBy = creators[item.uuid] ?: error("No timeline creator found for goal note ${item.uuid}")
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

  private fun Map<String, Value>.asString(key: String): String? = when (val v = this[key]) {
    is SingleValue -> v.value
    else -> null
  }

  private fun Map<String, Value>.asStringList(key: String): List<String> = when (val v = this[key]) {
    is MultiValue -> v.values
    is SingleValue -> listOf(v.value)
    null -> emptyList()
  }

  private fun String.toInstantOrNull(): Instant? = runCatching {
    Instant.parse(this)
  }.recoverCatching {
    LocalDateTime.parse(this).toInstant()
  }.getOrNull()

  private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

  private fun LocalDateTime.toInstant(): Instant = this.atZone(ZoneId.systemDefault()).toInstant()

  private fun sha256Hex(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray())
    .joinToString("") { "%02x".format(it) }

  private companion object {
    private val log = LoggerFactory.getLogger(SentencePlanMapper::class.java)

    private const val COLLECTION_GOALS = "GOALS"
    private const val COLLECTION_PLAN_AGREEMENTS = "PLAN_AGREEMENTS"
    private const val COLLECTION_STEPS = "STEPS"
    private const val COLLECTION_NOTES = "NOTES"

    private val AREA_OF_NEED_BY_SLUG: Map<String, CriminogenicNeed> = mapOf(
      "accommodation" to CriminogenicNeed.ACCOMMODATION,
      "employment-and-education" to CriminogenicNeed.EMPLOYMENT_AND_EDUCATION,
      "finances" to CriminogenicNeed.FINANCES,
      "drug-use" to CriminogenicNeed.DRUG_USE,
      "alcohol-use" to CriminogenicNeed.ALCOHOL_USE,
      "health-and-wellbeing" to CriminogenicNeed.HEALTH_AND_WELLBEING,
      "personal-relationships-and-community" to CriminogenicNeed.PERSONAL_RELATIONSHIPS_AND_COMMUNITY,
      "thinking-behaviours-and-attitudes" to CriminogenicNeed.THINKING_BEHAVIOURS_AND_ATTITUDES,
    )

    private val ACTOR_BY_KEY: Map<String, ActorType> = mapOf(
      "person_on_probation" to ActorType.PERSON_ON_PROBATION,
      "probation_practitioner" to ActorType.PROBATION_PRACTITIONER,
      "programme_staff" to ActorType.PROGRAMME_STAFF,
      "partnership_agency" to ActorType.PARTNERSHIP_AGENCY,
      "crs_provider" to ActorType.CRS_PROVIDER,
      "prison_offender_manager" to ActorType.PRISON_OFFENDER_MANAGER,
      "someone_else" to ActorType.SOMEONE_ELSE,
    )

    private val GOAL_STATUS_BY_KEY: Map<String, GoalStatus> = mapOf(
      "ACTIVE" to GoalStatus.ACTIVE,
      "FUTURE" to GoalStatus.FUTURE,
      "ACHIEVED" to GoalStatus.ACHIEVED,
      "REMOVED" to GoalStatus.REMOVED,
    )

    private val STEP_STATUS_BY_KEY: Map<String, StepStatus> = mapOf(
      "NOT_STARTED" to StepStatus.NOT_STARTED,
      "IN_PROGRESS" to StepStatus.IN_PROGRESS,
      "COMPLETED" to StepStatus.COMPLETED,
      "CANNOT_BE_DONE_YET" to StepStatus.CANNOT_BE_DONE_YET,
      "NO_LONGER_NEEDED" to StepStatus.NO_LONGER_NEEDED,
    )

    private val PLAN_STATUS_BY_KEY: Map<String, PlanStatus> = mapOf(
      "DRAFT" to PlanStatus.DRAFT,
      "AGREED" to PlanStatus.AGREED,
      "DO_NOT_AGREE" to PlanStatus.DO_NOT_AGREE,
      "COULD_NOT_ANSWER" to PlanStatus.COULD_NOT_ANSWER,
      "UPDATED_AGREED" to PlanStatus.UPDATED_AGREED,
      "UPDATED_DO_NOT_AGREE" to PlanStatus.UPDATED_DO_NOT_AGREE,
    )

    private val GOAL_NOTE_TYPE_BY_KEY: Map<String, GoalNoteType> = mapOf(
      "ACHIEVED" to GoalNoteType.ACHIEVED,
      "REMOVED" to GoalNoteType.REMOVED,
      "READDED" to GoalNoteType.READDED,
      "PROGRESS" to GoalNoteType.PROGRESS,
    )

    private val IDENTIFIER_TYPE_BY_AAP: Map<AapIdentifierType, EntityIdentifierType> = mapOf(
      AapIdentifierType.CRN to EntityIdentifierType.CRN,
      AapIdentifierType.NOMIS_ID to EntityIdentifierType.NOMIS,
    )

    private val AGREEMENT_FREE_TEXT_ANSWERS: List<Pair<String, FreeTextType>> = listOf(
      "details_no" to FreeTextType.AGREEMENT_DETAILS,
      "details_could_not_answer" to FreeTextType.AGREEMENT_DETAILS,
      "notes" to FreeTextType.AGREEMENT_NOTES,
    )
  }
}
