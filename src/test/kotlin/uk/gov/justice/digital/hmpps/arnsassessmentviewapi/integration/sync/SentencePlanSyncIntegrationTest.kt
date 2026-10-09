package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.sync

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.CriminogenicNeed
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanEntity
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

  @Autowired
  private lateinit var transactionTemplate: TransactionTemplate

  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

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

    // Reload after the sync transaction to prove the UUID/name pairs survive the database round trip.
    transactionTemplate.executeWithoutResult {
      val plan = repository.findByIdAndVersion(PLAN_ID, SentencePlanEntity.CURRENT_VERSION).orElseThrow()
      assertThat(plan.identifiers).hasSize(1)
      assertThat(plan.identifiers.single().value).isEqualTo("X000001")
      assertThat(plan.goals).hasSize(1)
      val goal = plan.goals.single()
      assertThat(goal.steps).hasSize(1)
      assertThat(goal.freeTexts).hasSize(1)
      assertThat(plan.agreements).hasSize(1)

      // Each child uses its own creation timeline event; goal updater remains independent.
      assertThat(goal.createdByUserId).isEqualTo(GOAL_CREATOR)
      assertThat(goal.createdByUserName).isEqualTo("Goal Creator")
      assertThat(goal.updatedByUserId).isEqualTo(GOAL_UPDATER)
      val step = goal.steps.single()
      assertThat(step.createdByUserId).isEqualTo(STEP_CREATOR)
      assertThat(step.createdByUserName).isEqualTo("Step Creator")
      val note = goal.freeTexts.single()
      assertThat(note.createdByUserId).isEqualTo(NOTE_CREATOR)
      assertThat(note.createdByUserName).isEqualTo("Note Creator")
      val agreement = plan.agreements.single()
      assertThat(agreement.createdByUserId).isEqualTo(AGREEMENT_CREATOR)
      assertThat(agreement.createdByUserName).isEqualTo("Agreement Creator")
      assertThat(agreement.freeTexts.single().createdByUserId).isEqualTo(AGREEMENT_CREATOR)
      assertThat(agreement.freeTexts.single().createdByUserName).isEqualTo("Agreement Creator")

      assertThat(viewName("goal_vw", GOAL_ID)).isEqualTo("Goal Creator")
      assertThat(viewName("step_vw", STEP_ID)).isEqualTo("Step Creator")
      assertThat(viewName("free_text_vw", NOTE_ID)).isEqualTo("Note Creator")
      assertThat(viewName("plan_agreement_vw", AGREEMENT_ID)).isEqualTo("Agreement Creator")

      jdbcTemplate.update("UPDATE goal SET created_by_user_name = NULL WHERE id = ?", GOAL_ID)
      assertThat(viewName("goal_vw", GOAL_ID)).isNull()
    }
  }

  // Locks two behaviours mocks cannot prove:
  // 1. clear() + saveAndFlush() avoids the unique-constraint trip on (sentence_plan_id, type, value)
  //    when re-syncing the same identifier value, and orphan removal cleans up replaced goals/agreements/notes.
  // 2. Fields (oasysPk, regionCode) refresh on every sync the assessment UUID
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
    val plan = repository.findByIdAndVersion(PLAN_ID, SentencePlanEntity.CURRENT_VERSION).orElseThrow()
    assertThat(plan.oasysPk).isEqualTo("1000099")
    assertThat(plan.version).isEqualTo(SentencePlanEntity.CURRENT_VERSION)
    assertThat(plan.regionCode).isEqualTo("MDI")
    assertThat(plan.identifiers.single().value).isEqualTo("X000001")
    assertThat(plan.goals).hasSize(1)
    val goal = plan.goals.single()
    assertThat(goal.id).isEqualTo(REWRITE_GOAL_ID)
    assertThat(goal.areaOfNeed).isEqualTo(CriminogenicNeed.EMPLOYMENT_AND_EDUCATION)
    assertThat(goal.status).isEqualTo(GoalStatus.FUTURE)
    assertThat(goal.steps).isEmpty()
    assertThat(goal.freeTexts).isEmpty()
    assertThat(goal.createdByUserId).isEqualTo(GOAL_CREATOR)
    assertThat(goal.createdByUserName).isEqualTo("Test Creator")
    assertThat(plan.agreements).hasSize(1)
    val agreement = plan.agreements.single()
    assertThat(agreement.id).isEqualTo(REWRITE_AGREEMENT_ID)
    assertThat(agreement.status).isEqualTo(PlanStatus.AGREED)
    assertThat(agreement.createdByUserId).isEqualTo(GOAL_CREATOR)
    assertThat(agreement.createdByUserName).isEqualTo("Test Creator")
    assertThat(agreement.freeTexts.single().createdByUserName).isEqualTo("Test Creator")
  }

  @Test
  fun `soft-delete pass flips deleted=true on plans returned by GetAssessmentsSoftDeletedSinceQuery`() {
    // GIVEN one plan synced live
    aapApi.stubModifiedSinceQuery(loadFixture("aap-query-1.json"))
    aapApi.stubTimelineQuery(loadFixture("aap-timeline-1.json"))
    coordinatorApi.stubEntityAssociations(loadFixture("coordinator-1.json"))
    sentencePlanSyncService.sync()
    assertThat(repository.findByIdAndVersion(PLAN_ID, SentencePlanEntity.CURRENT_VERSION).orElseThrow().deleted).isFalse

    // WHEN AAP subsequently reports the plan as soft-deleted on a follow-up sync
    aapApi.stubModifiedSinceQuery("""{"queries":[{"result":{"assessments":[],"nextCursor":null}}]}""")
    aapApi.stubSoftDeletedSinceQuery("""{"queries":[{"result":{"assessments":["$PLAN_ID"]}}]}""")
    sentencePlanSyncService.sync()

    // THEN the local row is flagged deleted.
    assertThat(repository.findByIdAndVersion(PLAN_ID, SentencePlanEntity.CURRENT_VERSION).orElseThrow().deleted).isTrue
  }

  private fun loadFixture(name: String): String = javaClass.classLoader.getResourceAsStream("fixtures/sync/$name")!!
    .bufferedReader().use { it.readText() }

  private fun viewName(view: String, id: UUID): String? = jdbcTemplate.queryForObject(
    """SELECT created_by_user_name FROM "assessment-view".$view WHERE id = ?""",
    String::class.java,
    id,
  )

  private companion object {
    private val PLAN_ID = UUID.fromString("00000001-1111-1111-1111-000000000001")
    private val REWRITE_GOAL_ID = UUID.fromString("00000001-aaaa-aaaa-aaaa-000000000099")
    private val REWRITE_AGREEMENT_ID = UUID.fromString("00000001-dddd-dddd-dddd-000000000099")
    private val GOAL_ID = UUID.fromString("00000001-aaaa-aaaa-aaaa-000000000001")
    private val STEP_ID = UUID.fromString("00000001-bbbb-bbbb-bbbb-000000000001")
    private val NOTE_ID = UUID.fromString("00000001-cccc-cccc-cccc-000000000001")
    private val AGREEMENT_ID = UUID.fromString("00000001-dddd-dddd-dddd-000000000001")
    private val GOAL_CREATOR = UUID.fromString("99999999-9999-9999-9999-000000000001")
    private val STEP_CREATOR = UUID.fromString("99999999-9999-9999-9999-000000000002")
    private val NOTE_CREATOR = UUID.fromString("99999999-9999-9999-9999-000000000003")
    private val AGREEMENT_CREATOR = UUID.fromString("99999999-9999-9999-9999-000000000004")
    private val GOAL_UPDATER = UUID.fromString("99999999-9999-9999-9999-000000000099")
  }
}
