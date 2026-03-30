package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.controller.response

import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.ActorType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.CriminogenicNeed
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.FreeTextType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.IdentifierType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanAgreementEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.StepEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.StepStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SentencePlanResponse(
  val id: UUID,
  val identifiers: List<IdentifierResponse>,
  val oasysPks: List<String>,
  val goals: List<GoalResponse>,
  val agreements: List<PlanAgreementResponse>,
  val createdAt: Instant,
  val updatedAt: Instant,
) {
  companion object {
    fun from(entity: SentencePlanEntity) = SentencePlanResponse(
      id = entity.id,
      identifiers = entity.identifiers.map { IdentifierResponse(it.type, it.value) },
      oasysPks = entity.oasysPks.map { it.oasysAssessmentPk },
      goals = entity.goals.map { GoalResponse.from(it) },
      agreements = entity.agreements.map { PlanAgreementResponse.from(it) },
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt,
    )
  }
}

data class IdentifierResponse(
  val type: IdentifierType,
  val value: String,
)

data class GoalResponse(
  val id: UUID,
  val title: String,
  val areaOfNeed: CriminogenicNeed,
  val targetDate: LocalDate?,
  val status: GoalStatus,
  val statusDate: Instant?,
  val relatedAreasOfNeed: List<CriminogenicNeed>,
  val steps: List<StepResponse>,
  val freeTexts: List<FreeTextResponse>,
  val createdByUserId: String,
  val createdAt: Instant,
  val updatedAt: Instant,
) {
  companion object {
    fun from(entity: GoalEntity) = GoalResponse(
      id = entity.id,
      title = entity.title,
      areaOfNeed = entity.areaOfNeed,
      targetDate = entity.targetDate,
      status = entity.status,
      statusDate = entity.statusDate,
      relatedAreasOfNeed = entity.relatedAreasOfNeed.map { it.criminogenicNeed },
      steps = entity.steps.map { StepResponse.from(it) },
      freeTexts = entity.freeTexts.map { FreeTextResponse(it.id, it.type, it.textLength, it.createdByUserId, it.createdAt) },
      createdByUserId = entity.createdByUserId,
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt,
    )
  }
}

data class StepResponse(
  val id: UUID,
  val description: String,
  val actor: ActorType,
  val status: StepStatus,
  val statusDate: Instant?,
  val createdByUserId: String,
  val createdAt: Instant,
) {
  companion object {
    fun from(entity: StepEntity) = StepResponse(
      id = entity.id,
      description = entity.description,
      actor = entity.actor,
      status = entity.status,
      statusDate = entity.statusDate,
      createdByUserId = entity.createdByUserId,
      createdAt = entity.createdAt,
    )
  }
}

data class FreeTextResponse(
  val id: UUID,
  val type: FreeTextType,
  val textLength: Int,
  val createdByUserId: String,
  val createdAt: Instant,
)

data class PlanAgreementResponse(
  val id: UUID,
  val status: PlanStatus,
  val statusDate: Instant?,
  val freeTexts: List<FreeTextResponse>,
  val createdByUserId: String,
  val createdAt: Instant,
) {
  companion object {
    fun from(entity: PlanAgreementEntity) = PlanAgreementResponse(
      id = entity.id,
      status = entity.status,
      statusDate = entity.statusDate,
      freeTexts = entity.freeTexts.map { FreeTextResponse(it.id, it.type, it.textLength, it.createdByUserId, it.createdAt) },
      createdByUserId = entity.createdByUserId,
      createdAt = entity.createdAt,
    )
  }
}
