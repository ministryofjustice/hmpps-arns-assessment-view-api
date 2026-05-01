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
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalNoteType
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
import java.security.MessageDigest
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

  private fun sha256Hex(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

  private fun givenPlanWithIdentifiers(vararg identifiers: Pair<IdentifierType, String>): SentencePlanEntity {
    val plan = SentencePlanEntity(
      id = UUID.randomUUID(),
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
      version = 1,
    )
    identifiers.forEach { (type, value) ->
      plan.identifiers.add(
        SentencePlanIdentifierEntity(id = UUID.randomUUID(), sentencePlan = plan, type = type, value = value),
      )
    }
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
    val plan = SentencePlanEntity(id = planId, createdAt = timestamp, updatedAt = timestamp, lastSyncedAt = timestamp, oasysPk = "1234567", version = 1, regionCode = "LDN")

    plan.identifiers.add(SentencePlanIdentifierEntity(id = UUID.randomUUID(), sentencePlan = plan, type = IdentifierType.CRN, value = crn))
    plan.identifiers.add(SentencePlanIdentifierEntity(id = UUID.randomUUID(), sentencePlan = plan, type = IdentifierType.NOMIS, value = "A7779DY"))

    val goalTitle = "Find stable accommodation"
    val noteCreator = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val agreementCreator = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val goalCreator = UUID.fromString("00000000-0000-0000-0000-000000000003")
    val goalUpdater = UUID.fromString("00000000-0000-0000-0000-000000000004")
    val stepCreator = UUID.fromString("00000000-0000-0000-0000-000000000005")
    val goal = GoalEntity(
      id = goalId, sentencePlan = plan,
      titleLength = goalTitle.length,
      titleHash = sha256Hex(goalTitle),
      areaOfNeed = CriminogenicNeed.ACCOMMODATION,
      targetDate = LocalDate.of(2025, 12, 31), status = GoalStatus.ACTIVE, statusDate = timestamp,
      createdAt = timestamp, updatedAt = timestamp, goalOrder = 0,
      createdByUserId = goalCreator, updatedByUserId = goalUpdater,
    )
    goal.relatedAreasOfNeed.add(GoalRelatedAreaOfNeedEntity(goal = goal, criminogenicNeed = CriminogenicNeed.FINANCES))
    goal.steps.add(StepEntity(id = stepId, goal = goal, description = "Contact housing provider", actor = ActorType.PROBATION_PRACTITIONER, status = StepStatus.NOT_STARTED, statusDate = timestamp, createdAt = timestamp, createdByUserId = stepCreator))
    goal.freeTexts.add(FreeTextEntity(id = goalFreeTextId, type = FreeTextType.GOAL_NOTE, textLength = 42, textHash = sha256Hex("the redacted goal note text content"), goal = goal, createdByUserId = noteCreator, createdAt = timestamp, goalNoteType = GoalNoteType.PROGRESS))
    plan.goals.add(goal)

    val agreement = PlanAgreementEntity(id = agreementId, sentencePlan = plan, status = PlanStatus.AGREED, statusDate = timestamp, createdByUserId = agreementCreator, createdAt = timestamp)
    agreement.freeTexts.add(FreeTextEntity(id = agreementFreeTextId, type = FreeTextType.AGREEMENT_NOTES, textLength = 15, textHash = sha256Hex("agreement notes"), planAgreement = agreement, createdByUserId = agreementCreator, createdAt = timestamp))
    plan.agreements.add(agreement)

    return sentencePlanRepository.save(plan)
  }

  @Nested
  inner class Security {
    @Test
    fun `returns 401 when no auth token provided`() {
      webTestClient.get()
        .uri("/sentence-plan/X123456")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `returns 403 when user lacks required role`() {
      webTestClient.get()
        .uri("/sentence-plan/X123456")
        .headers(setAuthorisation(roles = listOf("ROLE_OTHER")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `returns 200 when user has ROLE_ASSESSMENT_VIEW_DELIUS`() {
      givenPlanWithIdentifiers(IdentifierType.CRN to "X123456")

      webTestClient.get()
        .uri("/sentence-plan/X123456")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW_DELIUS")))
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  inner class GetSentencePlans {
    @Test
    fun `returns 404 when no plans found`() {
      webTestClient.get()
        .uri("/sentence-plan/UNKNOWN")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW_DELIUS")))
        .exchange()
        .expectStatus().isNotFound
        .expectBody()
        .jsonPath("$.status").isEqualTo(404)
        .jsonPath("$.userMessage").isEqualTo("No sentence plans found for CRN UNKNOWN")
    }

    @Test
    fun `returns 404 for unknown path`() {
      webTestClient.get()
        .uri("/sentence-plan")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW_DELIUS")))
        .exchange()
        .expectStatus().isNotFound
        .expectBody()
        .jsonPath("$.status").isEqualTo(404)
    }

    @Test
    fun `returns sentence plan in spec-aligned shape`() {
      givenFullyPopulatedPlan(crn = "X123456")

      val titleHash = sha256Hex("Find stable accommodation")
      webTestClient.get()
        .uri("/sentence-plan/X123456")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW_DELIUS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .json(
          """
          [{
            "crn": "X123456",
            "nomis": "A7779DY",
            "planStatus": "AGREED",
            "goals": [{
              "titleLength": 25,
              "titleHash": "$titleHash",
              "areaOfNeed": "ACCOMMODATION",
              "relatedAreasOfNeed": ["FINANCES"],
              "targetDate": "2025-12-31",
              "goalStatus": "ACTIVE",
              "steps": [{
                "description": "Contact housing provider",
                "status": "NOT_STARTED",
                "actor": "PROBATION_PRACTITIONER",
                "statusDate": "2025-06-01T12:00:00Z"
              }]
            }]
          }]
          """,
          true,
        )
    }

    @Test
    fun `nomis and planStatus serialise as null when not populated`() {
      givenPlanWithIdentifiers(IdentifierType.CRN to "X777777")

      webTestClient.get()
        .uri("/sentence-plan/X777777")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW_DELIUS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .json(
          """
          [{
            "crn": "X777777",
            "nomis": null,
            "planStatus": null,
            "goals": []
          }]
          """,
          true,
        )
    }

    @Test
    fun `returns multiple plans for same CRN`() {
      givenPlanWithIdentifiers(IdentifierType.CRN to "X999999")
      givenPlanWithIdentifiers(IdentifierType.CRN to "X999999")

      webTestClient.get()
        .uri("/sentence-plan/X999999")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW_DELIUS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
    }
  }
}
