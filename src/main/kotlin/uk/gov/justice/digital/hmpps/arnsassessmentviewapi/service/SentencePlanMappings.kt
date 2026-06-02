package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.MultiValue
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.SingleValue
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.Value
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.ActorType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.CriminogenicNeed
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.FreeTextType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalNoteType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.StepStatus
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.IdentifierType as AapIdentifierType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.IdentifierType as EntityIdentifierType

const val SENTENCE_PLAN_ASSESSMENT_TYPE = "SENTENCE_PLAN"

internal const val COLLECTION_GOALS = "GOALS"
internal const val COLLECTION_PLAN_AGREEMENTS = "PLAN_AGREEMENTS"
internal const val COLLECTION_STEPS = "STEPS"
internal const val COLLECTION_NOTES = "NOTES"

internal val AREA_OF_NEED_BY_SLUG: Map<String, CriminogenicNeed> = mapOf(
  "accommodation" to CriminogenicNeed.ACCOMMODATION,
  "employment-and-education" to CriminogenicNeed.EMPLOYMENT_AND_EDUCATION,
  "finances" to CriminogenicNeed.FINANCES,
  "drug-use" to CriminogenicNeed.DRUG_USE,
  "alcohol-use" to CriminogenicNeed.ALCOHOL_USE,
  "health-and-wellbeing" to CriminogenicNeed.HEALTH_AND_WELLBEING,
  "personal-relationships-and-community" to CriminogenicNeed.PERSONAL_RELATIONSHIPS_AND_COMMUNITY,
  "thinking-behaviours-and-attitudes" to CriminogenicNeed.THINKING_BEHAVIOURS_AND_ATTITUDES,
)

internal val ACTOR_BY_KEY: Map<String, ActorType> = mapOf(
  "person_on_probation" to ActorType.PERSON_ON_PROBATION,
  "probation_practitioner" to ActorType.PROBATION_PRACTITIONER,
  "programme_staff" to ActorType.PROGRAMME_STAFF,
  "partnership_agency" to ActorType.PARTNERSHIP_AGENCY,
  "crs_provider" to ActorType.CRS_PROVIDER,
  "prison_offender_manager" to ActorType.PRISON_OFFENDER_MANAGER,
  "someone_else" to ActorType.SOMEONE_ELSE,
)

internal val GOAL_STATUS_BY_KEY: Map<String, GoalStatus> = mapOf(
  "ACTIVE" to GoalStatus.ACTIVE,
  "FUTURE" to GoalStatus.FUTURE,
  "ACHIEVED" to GoalStatus.ACHIEVED,
  "REMOVED" to GoalStatus.REMOVED,
)

internal val STEP_STATUS_BY_KEY: Map<String, StepStatus> = mapOf(
  "NOT_STARTED" to StepStatus.NOT_STARTED,
  "IN_PROGRESS" to StepStatus.IN_PROGRESS,
  "COMPLETED" to StepStatus.COMPLETED,
  "CANNOT_BE_DONE_YET" to StepStatus.CANNOT_BE_DONE_YET,
  "NO_LONGER_NEEDED" to StepStatus.NO_LONGER_NEEDED,
)

internal val PLAN_STATUS_BY_KEY: Map<String, PlanStatus> = mapOf(
  "DRAFT" to PlanStatus.DRAFT,
  "AGREED" to PlanStatus.AGREED,
  "DO_NOT_AGREE" to PlanStatus.DO_NOT_AGREE,
  "COULD_NOT_ANSWER" to PlanStatus.COULD_NOT_ANSWER,
  "UPDATED_AGREED" to PlanStatus.UPDATED_AGREED,
  "UPDATED_DO_NOT_AGREE" to PlanStatus.UPDATED_DO_NOT_AGREE,
)

internal val GOAL_NOTE_TYPE_BY_KEY: Map<String, GoalNoteType> = mapOf(
  "ACHIEVED" to GoalNoteType.ACHIEVED,
  "REMOVED" to GoalNoteType.REMOVED,
  "READDED" to GoalNoteType.READDED,
  "PROGRESS" to GoalNoteType.PROGRESS,
)

internal val IDENTIFIER_TYPE_BY_AAP: Map<AapIdentifierType, EntityIdentifierType> = mapOf(
  AapIdentifierType.CRN to EntityIdentifierType.CRN,
  AapIdentifierType.NOMIS_ID to EntityIdentifierType.NOMIS,
)

internal val AGREEMENT_FREE_TEXT_ANSWERS: List<Pair<String, FreeTextType>> = listOf(
  "details_no" to FreeTextType.AGREEMENT_DETAILS,
  "details_could_not_answer" to FreeTextType.AGREEMENT_DETAILS,
  "notes" to FreeTextType.AGREEMENT_NOTES,
)

private val log = LoggerFactory.getLogger("uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service.SentencePlanMappings")

internal fun Map<String, Value>.asString(key: String): String? = when (val v = this[key]) {
  is SingleValue -> v.value
  else -> null
}

internal fun Map<String, Value>.asStringList(key: String): List<String> = when (val v = this[key]) {
  is MultiValue -> v.values
  is SingleValue -> listOf(v.value)
  null -> emptyList()
}

internal fun String.toInstantOrNull(): Instant? = runCatching {
  Instant.parse(this)
}.recoverCatching {
  LocalDateTime.parse(this).toInstant()
}.getOrNull()

internal fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(substringBefore('T')) }
  .onFailure { log.warn("Could not parse '{}' as LocalDate", this) }
  .getOrNull()

internal fun LocalDateTime.toInstant(): Instant = this.atZone(ZoneId.systemDefault()).toInstant()

internal fun sha256Hex(text: String): String = MessageDigest.getInstance("SHA-256")
  .digest(text.toByteArray())
  .joinToString("") { "%02x".format(it) }
