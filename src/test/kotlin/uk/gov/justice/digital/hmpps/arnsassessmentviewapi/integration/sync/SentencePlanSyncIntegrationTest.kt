package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.sync

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.CriminogenicNeed
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.AapApiExtension
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.AapApiExtension.Companion.aapApi
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.CoordinatorApiExtension
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.CoordinatorApiExtension.Companion.coordinatorApi
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository.SentencePlanRepository
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service.SentencePlanSyncService
import java.util.UUID

@ExtendWith(AapApiExtension::class, CoordinatorApiExtension::class)
@TestPropertySource(
  properties = [
    "app.services.coordinator-api.base-url=http://localhost:8091",
    "app.services.aap-api.base-url=http://localhost:8092",
    "app.client.id=test-client",
    "app.client.secret=test-secret",
  ],
)
class SentencePlanSyncIntegrationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var sentencePlanSyncService: SentencePlanSyncService

  @Autowired
  private lateinit var repository: SentencePlanRepository

  @BeforeEach
  fun setUp() {
    repository.deleteAllInBatch()
    hmppsAuth.stubGrantToken()
    // Default to "no soft-deletes since" so tests focused on the modified-since path don't have to stub it.
    aapApi.stubSoftDeletedSinceQuery("""{"queries":[{"result":{"assessments":[]}}]}""")
  }

  // Smoke test: prove Spring beans, JPA cascade, wiremock contract, and JSON deserialisation
  // all work together end-to-end.
  @Test
  fun `sync round-trips a single assessment with goal+step+note+agreement through the DB`() {
    // GIVEN AAP, coordinator, and timeline mocks loaded with a fully populated single-plan fixture
    aapApi.stubModifiedSinceQuery(loadFixture("aap-query-1.json"))
    aapApi.stubTimelineQuery(loadFixture("aap-timeline-1.json"))
    coordinatorApi.stubEntityAssociations(loadFixture("coordinator-1.json"))

    // WHEN sync runs
    sentencePlanSyncService.sync()

    // THEN the plan is persisted with its identifier, goal+step+note, and agreement reachable via JPA
    val plan = repository.findById(PLAN_ID).orElseThrow()
    assertThat(plan.identifiers).hasSize(1)
    assertThat(plan.identifiers.single().value).isEqualTo("X000001")
    assertThat(plan.goals).hasSize(1)
    val goal = plan.goals.single()
    assertThat(goal.steps).hasSize(1)
    assertThat(goal.freeTexts).hasSize(1)
    assertThat(plan.agreements).hasSize(1)

    // AND authorship resolves per-item: createdBy from CollectionItemAddedEvent rows,
    // goal updatedBy from the GOAL_UPDATED custom timeline row.
    assertThat(goal.createdByUserId).isEqualTo(GOAL_CREATOR)
    assertThat(goal.updatedByUserId).isEqualTo(GOAL_UPDATER)
    val step = goal.steps.single()
    assertThat(step.createdByUserId).isEqualTo(STEP_CREATOR)
    assertThat(goal.freeTexts.single().createdByUserId).isEqualTo(NOTE_CREATOR)
    assertThat(plan.agreements.single().createdByUserId).isEqualTo(AGREEMENT_CREATOR)
  }

  // Locks two behaviours mocks cannot prove:
  // 1. clear() + saveAndFlush() avoids the unique-constraint trip on (sentence_plan_id, type, value)
  //    when re-syncing the same identifier value, and orphan removal cleans up replaced goals/agreements/notes.
  // 2. Fields (oasysPk, version, regionCode) refresh on every sync the assessment UUID
  //    is the only invariant per assessment.
  @Test
  fun `re-syncing the same plan replaces nested children and refreshes fields`() {
    // GIVEN the plan has been synced once with one set of coordinator details
    aapApi.stubModifiedSinceQuery(loadFixture("aap-query-1.json"))
    aapApi.stubTimelineQuery(loadFixture("aap-timeline-1.json"))
    coordinatorApi.stubEntityAssociations(loadFixture("coordinator-1.json"))
    sentencePlanSyncService.sync()

    // WHEN AAP emits a rewrite, same plan UUID, same CRN, but different child UUIDs/content,
    // and coordinator now reports a different oasysPk, baseVersion, and regionPrisonCode
    aapApi.stubModifiedSinceQuery(loadFixture("aap-query-1-rewrite.json"))
    aapApi.stubTimelineQuery(loadFixture("aap-timeline-1-rewrite.json"))
    coordinatorApi.stubEntityAssociations(loadFixture("coordinator-1-rewrite.json"))
    sentencePlanSyncService.sync()

    // THEN the parent plan is intact, fields reflect the rewrite values, and children
    // have been swapped out for the rewrite payload. The identifier value being identical across both
    // syncs also proves clear() + saveAndFlush() is preventing a unique constraint violation
    val plan = repository.findById(PLAN_ID).orElseThrow()
    assertThat(plan.oasysPk).isEqualTo("1000099")
    assertThat(plan.version).isEqualTo(5)
    assertThat(plan.regionCode).isEqualTo("MDI")
    assertThat(plan.identifiers.single().value).isEqualTo("X000001")
    assertThat(plan.goals).hasSize(1)
    val goal = plan.goals.single()
    assertThat(goal.id).isEqualTo(REWRITE_GOAL_ID)
    assertThat(goal.areaOfNeed).isEqualTo(CriminogenicNeed.EMPLOYMENT_AND_EDUCATION)
    assertThat(goal.status).isEqualTo(GoalStatus.FUTURE)
    assertThat(goal.steps).isEmpty()
    assertThat(goal.freeTexts).isEmpty()
    assertThat(plan.agreements).hasSize(1)
    val agreement = plan.agreements.single()
    assertThat(agreement.id).isEqualTo(REWRITE_AGREEMENT_ID)
    assertThat(agreement.status).isEqualTo(PlanStatus.AGREED)
  }

  @Test
  fun `soft-delete pass flips deleted=true on plans returned by GetAssessmentsSoftDeletedSinceQuery`() {
    // GIVEN one plan synced live
    aapApi.stubModifiedSinceQuery(loadFixture("aap-query-1.json"))
    aapApi.stubTimelineQuery(loadFixture("aap-timeline-1.json"))
    coordinatorApi.stubEntityAssociations(loadFixture("coordinator-1.json"))
    sentencePlanSyncService.sync()
    assertThat(repository.findById(PLAN_ID).orElseThrow().deleted).isFalse

    // WHEN AAP subsequently reports the plan as soft-deleted on a follow-up sync
    aapApi.stubModifiedSinceQuery("""{"queries":[{"result":{"assessments":[],"nextCursor":null}}]}""")
    aapApi.stubSoftDeletedSinceQuery("""{"queries":[{"result":{"assessments":["$PLAN_ID"]}}]}""")
    sentencePlanSyncService.sync()

    // THEN the local row is flagged deleted.
    assertThat(repository.findById(PLAN_ID).orElseThrow().deleted).isTrue
  }

  private fun loadFixture(name: String): String = javaClass.classLoader.getResourceAsStream("fixtures/sync/$name")!!
    .bufferedReader().use { it.readText() }

  private companion object {
    private val PLAN_ID = UUID.fromString("00000001-1111-1111-1111-000000000001")
    private val REWRITE_GOAL_ID = UUID.fromString("00000001-aaaa-aaaa-aaaa-000000000099")
    private val REWRITE_AGREEMENT_ID = UUID.fromString("00000001-dddd-dddd-dddd-000000000099")
    private val GOAL_CREATOR = UUID.fromString("99999999-9999-9999-9999-000000000001")
    private val STEP_CREATOR = UUID.fromString("99999999-9999-9999-9999-000000000002")
    private val NOTE_CREATOR = UUID.fromString("99999999-9999-9999-9999-000000000003")
    private val AGREEMENT_CREATOR = UUID.fromString("99999999-9999-9999-9999-000000000004")
    private val GOAL_UPDATER = UUID.fromString("99999999-9999-9999-9999-000000000099")
  }
}
