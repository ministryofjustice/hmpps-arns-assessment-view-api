package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.controller.response

import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.ActorType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.CriminogenicNeed
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.IdentifierType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.StepEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.StepStatus
import java.time.Instant
import java.time.LocalDate

data class SentencePlanResponse(
  val crn: String?,
  val nomis: String?,
  val planStatus: PlanStatus?,
  val goals: List<GoalResponse>,
) {
  companion object {
    fun from(entity: SentencePlanEntity) = SentencePlanResponse(
      crn = entity.identifiers.firstOrNull { it.type == IdentifierType.CRN }?.value,
      nomis = entity.identifiers.firstOrNull { it.type == IdentifierType.NOMIS }?.value,
      planStatus = entity.agreements.maxByOrNull { it.createdAt }?.status,
      goals = entity.goals.sortedBy { it.goalOrder }.map { GoalResponse.from(it) },
    )
  }
}

data class GoalResponse(
  val titleLength: Int,
  val titleHash: String,
  val areaOfNeed: CriminogenicNeed,
  val relatedAreasOfNeed: List<CriminogenicNeed>,
  val targetDate: LocalDate?,
  val goalStatus: GoalStatus,
  val steps: List<StepResponse>,
) {
  companion object {
    fun from(entity: GoalEntity) = GoalResponse(
      titleLength = entity.titleLength,
      titleHash = entity.titleHash,
      areaOfNeed = entity.areaOfNeed,
      relatedAreasOfNeed = entity.relatedAreasOfNeed.map { it.criminogenicNeed },
      targetDate = entity.targetDate,
      goalStatus = entity.status,
      steps = entity.steps.map { StepResponse.from(it) },
    )
  }
}

data class StepResponse(
  val description: String,
  val status: StepStatus,
  val actor: ActorType,
  val statusDate: Instant?,
) {
  companion object {
    fun from(entity: StepEntity) = StepResponse(
      description = entity.description,
      status = entity.status,
      actor = entity.actor,
      statusDate = entity.statusDate,
    )
  }
}
