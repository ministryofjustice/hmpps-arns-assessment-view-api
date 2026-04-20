package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.ActorType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.CriminogenicNeed
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.FreeTextEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.FreeTextType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalRelatedAreaOfNeedEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.IdentifierType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanAgreementEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanIdentifierEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.StepEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.StepStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository.SentencePlanRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class SentencePlanControllerTest : IntegrationTestBase() {

  @Autowired
  private lateinit var sentencePlanRepository: SentencePlanRepository

  @BeforeEach
  fun setUp() {
    sentencePlanRepository.deleteAll()
  }

  private fun givenPlanWithIdentifier(type: IdentifierType, value: String): SentencePlanEntity {
    val plan = SentencePlanEntity(
      id = UUID.randomUUID(),
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
      version = 1,
    )
    plan.identifiers.add(
      SentencePlanIdentifierEntity(
        id = UUID.randomUUID(),
        sentencePlan = plan,
        type = type,
        value = value,
      ),
    )
    return sentencePlanRepository.save(plan)
  }

  private fun givenFullyPopulatedPlan(
    crn: String,
    planId: UUID = UUID.randomUUID(),
    goalId: UUID = UUID.randomUUID(),
    stepId: UUID = UUID.randomUUID(),
    agreementId: UUID = UUID.randomUUID(),
    goalFreeTextId: UUID = UUID.randomUUID(),
    agreementFreeTextId: UUID = UUID.randomUUID(),
    timestamp: Instant = Instant.parse("2025-06-01T12:00:00Z"),
  ): SentencePlanEntity {
    val plan = SentencePlanEntity(id = planId, createdAt = timestamp, updatedAt = timestamp, lastSyncedAt = timestamp, oasysPk = 1234567, version = 1, regionCode = "LDN")

    plan.identifiers.add(SentencePlanIdentifierEntity(id = UUID.randomUUID(), sentencePlan = plan, type = IdentifierType.CRN, value = crn))

    val goal = GoalEntity(
      id = goalId, sentencePlan = plan, title = "Find stable accommodation", areaOfNeed = CriminogenicNeed.ACCOMMODATION,
      targetDate = LocalDate.of(2025, 12, 31), status = GoalStatus.ACTIVE, statusDate = timestamp,
      createdByUserId = "test-user", createdAt = timestamp, updatedAt = timestamp, goalOrder = 0,
    )
    goal.relatedAreasOfNeed.add(GoalRelatedAreaOfNeedEntity(goal = goal, criminogenicNeed = CriminogenicNeed.FINANCES))
    goal.steps.add(StepEntity(id = stepId, goal = goal, description = "Contact housing provider", actor = ActorType.PROBATION_PRACTITIONER, status = StepStatus.NOT_STARTED, statusDate = timestamp, createdByUserId = "test-user", createdAt = timestamp))
    goal.freeTexts.add(FreeTextEntity(id = goalFreeTextId, type = FreeTextType.GOAL_NOTE, textLength = 42, goal = goal, createdByUserId = "test-user", createdAt = timestamp))
    plan.goals.add(goal)

    val agreement = PlanAgreementEntity(id = agreementId, sentencePlan = plan, status = PlanStatus.AGREED, statusDate = timestamp, createdByUserId = "test-user", createdAt = timestamp)
    agreement.freeTexts.add(FreeTextEntity(id = agreementFreeTextId, type = FreeTextType.AGREEMENT_NOTES, textLength = 15, planAgreement = agreement, createdByUserId = "test-user", createdAt = timestamp))
    plan.agreements.add(agreement)

    return sentencePlanRepository.save(plan)
  }

  @Nested
  inner class Security {
    @Test
    fun `returns 401 when no auth token provided`() {
      webTestClient.get()
        .uri("/sentence-plan/CRN/X123456")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `returns 403 when user lacks required role`() {
      webTestClient.get()
        .uri("/sentence-plan/CRN/X123456")
        .headers(setAuthorisation(roles = listOf("ROLE_OTHER")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `returns 200 when user has ROLE_ASSESSMENT_VIEW`() {
      givenPlanWithIdentifier(IdentifierType.CRN, "X123456")

      webTestClient.get()
        .uri("/sentence-plan/CRN/X123456")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW")))
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  inner class GetSentencePlans {
    @Test
    fun `returns 404 when no plans found`() {
      webTestClient.get()
        .uri("/sentence-plan/CRN/UNKNOWN")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW")))
        .exchange()
        .expectStatus().isNotFound
        .expectBody()
        .jsonPath("$.status").isEqualTo(404)
        .jsonPath("$.userMessage").isEqualTo("No sentence plans found for CRN/UNKNOWN")
    }

    @Test
    fun `returns 400 for invalid identifier type`() {
      webTestClient.get()
        .uri("/sentence-plan/INVALID/X123456")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.status").isEqualTo(400)
        .jsonPath("$.userMessage").isEqualTo("Invalid parameter: identifierType")
    }

    @Test
    fun `returns 404 for unknown path`() {
      webTestClient.get()
        .uri("/sentence-plan")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW")))
        .exchange()
        .expectStatus().isNotFound
        .expectBody()
        .jsonPath("$.status").isEqualTo(404)
    }

    @Test
    fun `returns sentence plan with all nested data`() {
      val planId = UUID.randomUUID()
      val goalId = UUID.randomUUID()
      val stepId = UUID.randomUUID()
      val agreementId = UUID.randomUUID()
      val goalFreeTextId = UUID.randomUUID()
      val agreementFreeTextId = UUID.randomUUID()

      givenFullyPopulatedPlan(
        crn = "X123456",
        planId = planId,
        goalId = goalId,
        stepId = stepId,
        agreementId = agreementId,
        goalFreeTextId = goalFreeTextId,
        agreementFreeTextId = agreementFreeTextId,
      )

      webTestClient.get()
        .uri("/sentence-plan/CRN/X123456")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW")))
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          """
          [{
            "id": "$planId",
            "createdAt": "2025-06-01T12:00:00Z",
            "updatedAt": "2025-06-01T12:00:00Z",
            "identifiers": [{ "type": "CRN", "value": "X123456" }],
            "oasysPk": 1234567,
            "version": 1,
            "regionCode": "LDN",
            "goals": [{
              "id": "$goalId",
              "title": "Find stable accommodation",
              "areaOfNeed": "ACCOMMODATION",
              "targetDate": "2025-12-31",
              "status": "ACTIVE",
              "createdByUserId": "test-user",
              "relatedAreasOfNeed": ["FINANCES"],
              "steps": [{
                "id": "$stepId",
                "description": "Contact housing provider",
                "actor": "PROBATION_PRACTITIONER",
                "status": "NOT_STARTED"
              }],
              "freeTexts": [{
                "id": "$goalFreeTextId",
                "type": "GOAL_NOTE",
                "textLength": 42
              }]
            }],
            "agreements": [{
              "id": "$agreementId",
              "status": "AGREED",
              "createdByUserId": "test-user",
              "freeTexts": [{
                "id": "$agreementFreeTextId",
                "type": "AGREEMENT_NOTES",
                "textLength": 15
              }]
            }]
          }]
          """,
        )
    }

    @Test
    fun `returns multiple plans for same CRN`() {
      givenPlanWithIdentifier(IdentifierType.CRN, "X999999")
      givenPlanWithIdentifier(IdentifierType.CRN, "X999999")

      webTestClient.get()
        .uri("/sentence-plan/CRN/X999999")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
    }

    @Test
    fun `can look up by NOMIS identifier`() {
      givenPlanWithIdentifier(IdentifierType.NOMIS, "A1234BC")

      webTestClient.get()
        .uri("/sentence-plan/NOMIS/A1234BC")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW")))
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          """
          [{ "identifiers": [{ "type": "NOMIS", "value": "A1234BC" }] }]
          """,
        )
    }
  }
}
