package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.controller.response

import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.ActorType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.CriminogenicNeed
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.StepStatus
import java.time.Instant
import java.time.LocalDate

data class SentencePlanResponse(
  val crn: String?,
  val nomis: String?,
  val planStatus: PlanStatus?,
  val goals: List<GoalResponse>,
)

data class GoalResponse(
  val goalTitle: String,
  val areaOfNeed: CriminogenicNeed,
  val relatedAreasOfNeed: List<CriminogenicNeed>,
  val targetDate: LocalDate?,
  val goalStatus: GoalStatus,
  val steps: List<StepResponse>,
)

data class StepResponse(
  val description: String,
  val status: StepStatus,
  val actor: ActorType,
  val statusDate: Instant?,
)
