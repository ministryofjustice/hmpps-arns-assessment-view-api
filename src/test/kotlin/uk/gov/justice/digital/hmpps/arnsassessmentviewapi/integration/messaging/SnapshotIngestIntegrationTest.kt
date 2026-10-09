package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.AapApiExtension
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.AapApiExtension.Companion.aapApi
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.CoordinatorApiExtension
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.CoordinatorApiExtension.Companion.coordinatorApi
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.AssociationPayload
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.CoordinatorEvent
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.EventType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.OasysEvent
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.VersionPayload
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository.SentencePlanRepository
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service.SentencePlanSyncService
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service.SnapshotIngestService
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(AapApiExtension::class, CoordinatorApiExtension::class)
@TestPropertySource(
  properties = [
    "app.services.aap-api.base-url=http://localhost:8092",
    "app.services.coordinator-api.base-url=http://localhost:8091",
    "app.client.id=test-client",
    "app.client.secret=test-secret",
    // application-test.yml turns this on suite-wide, which silently loads collections on detached
    // entities. Production does not, so leaving it on here hides the exact failure below.
    "spring.jpa.properties.hibernate.enable_lazy_load_no_trans=false",
  ],
)
class SnapshotIngestIntegrationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var snapshotIngestService: SnapshotIngestService

  @Autowired
  private lateinit var sentencePlanSyncService: SentencePlanSyncService

  @Autowired
  private lateinit var repository: SentencePlanRepository

  @Autowired
  private lateinit var transactionTemplate: TransactionTemplate

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @BeforeEach
  fun setUp() {
    repository.deleteAllInBatch()
    hmppsAuth.stubGrantToken()
  }

  // ingestVersion reads the existing row outside the transaction that mutates it, so the entity
  // arrives detached. Clearing its lazy collections threw LazyInitializationException and sent
  // every update-shaped event to the DLQ, while CREATED events (existing == null) passed.
  @Test
  fun `re-ingesting an existing version replaces nested children instead of failing on detached collections`() {
    // GIVEN the snapshot has been ingested once
    stubAap("aap-query-1.json", "aap-timeline-1.json")
    snapshotIngestService.ingestVersion(event(FIRST_SEEN), payload(FIRST_SEEN))

    transactionTemplate.execute {
      val plan = repository.findByIdAndVersion(PLAN_ID, VERSION).orElseThrow()
      assertThat(plan.identifiers.single().value).isEqualTo("X000001")
      assertThat(plan.goals).hasSize(1)
      assertThat(plan.goals.single().createdByUserId).isEqualTo(GOAL_CREATOR)
      assertThat(plan.goals.single().createdByUserName).isEqualTo("Goal Creator")
      assertThat(plan.goals.single().steps.single().createdByUserId).isEqualTo(STEP_CREATOR)
      assertThat(plan.goals.single().steps.single().createdByUserName).isEqualTo("Step Creator")
      assertThat(plan.goals.single().freeTexts.single().createdByUserId).isEqualTo(NOTE_CREATOR)
      assertThat(plan.goals.single().freeTexts.single().createdByUserName).isEqualTo("Note Creator")
      assertThat(plan.agreements.single().createdByUserId).isEqualTo(AGREEMENT_CREATOR)
      assertThat(plan.agreements.single().createdByUserName).isEqualTo("Agreement Creator")
      assertThat(plan.agreements.single().freeTexts.single().createdByUserId).isEqualTo(AGREEMENT_CREATOR)
    }

    // WHEN a later event rewrites the same (id, version) with different children
    stubAap("aap-query-1-rewrite.json", "aap-timeline-1-rewrite.json")
    snapshotIngestService.ingestVersion(event(REWRITTEN), payload(REWRITTEN))

    // THEN the children are swapped rather than duplicated, and the identical identifier value
    // re-inserting proves clear() + flush() still runs against a managed entity.
    transactionTemplate.execute {
      val plan = repository.findByIdAndVersion(PLAN_ID, VERSION).orElseThrow()
      assertThat(plan.identifiers.single().value).isEqualTo("X000001")
      assertThat(plan.goals.single().id).isEqualTo(REWRITE_GOAL_ID)
      assertThat(plan.goals.single().createdByUserId).isEqualTo(GOAL_CREATOR)
      assertThat(plan.goals.single().createdByUserName).isEqualTo("Test Creator")
      assertThat(plan.agreements.single().id).isEqualTo(REWRITE_AGREEMENT_ID)
      assertThat(plan.agreements.single().createdByUserId).isEqualTo(GOAL_CREATOR)
      assertThat(plan.agreements.single().createdByUserName).isEqualTo("Test Creator")
      assertThat(plan.agreements.single().freeTexts.single().createdByUserId).isEqualTo(GOAL_CREATOR)
      assertThat(plan.agreements.single().freeTexts.single().createdByUserName).isEqualTo("Test Creator")
    }
  }

  @Test
  fun `current-state sync does not change creator names on an immutable snapshot`() {
    stubAap("aap-query-1.json", "aap-timeline-1.json")
    snapshotIngestService.ingestVersion(event(FIRST_SEEN), payload(FIRST_SEEN))

    aapApi.stubModifiedSinceQuery(loadFixture("aap-query-1-rewrite.json"))
    aapApi.stubTimelineQuery(loadFixture("aap-timeline-1-rewrite.json").replace("Test Creator", "Current Creator"))
    coordinatorApi.stubEntityAssociations(loadFixture("coordinator-1-rewrite.json"))
    aapApi.stubSoftDeletedSinceQuery("""{"queries":[{"result":{"assessments":[]}}]}""")
    sentencePlanSyncService.sync()

    transactionTemplate.execute {
      val snapshot = repository.findByIdAndVersion(PLAN_ID, VERSION).orElseThrow()
      val current = repository.findByIdAndVersion(PLAN_ID, -1L).orElseThrow()
      assertThat(snapshot.goals.single().createdByUserName).isEqualTo("Goal Creator")
      assertThat(snapshot.goals.single().steps.single().createdByUserName).isEqualTo("Step Creator")
      assertThat(snapshot.goals.single().freeTexts.single().createdByUserName).isEqualTo("Note Creator")
      assertThat(snapshot.agreements.single().createdByUserName).isEqualTo("Agreement Creator")
      assertThat(current.goals.single().createdByUserName).isEqualTo("Current Creator")
      assertThat(current.agreements.single().createdByUserName).isEqualTo("Current Creator")
      assertThat(current.agreements.single().freeTexts.single().createdByUserName).isEqualTo("Current Creator")
    }
  }

  private fun stubAap(query: String, timeline: String) {
    aapApi.stubAssessmentVersionQuery(versionQueryResponse(query))
    aapApi.stubTimelineQuery(loadFixture(timeline))
  }

  // Elements of the modified-since fixture's `assessments` array are AssessmentVersionQueryResult,
  // the same shape AssessmentVersionQuery returns singly — re-wrap rather than duplicate fixtures.
  private fun versionQueryResponse(name: String): String {
    val assessment = objectMapper.readTree(loadFixture(name)).at("/queries/0/result/assessments/0")
    return """{"queries":[{"result":$assessment}]}"""
  }

  private fun loadFixture(name: String): String = javaClass.classLoader.getResourceAsStream("fixtures/sync/$name")!!
    .bufferedReader().use { it.readText() }

  private fun event(incrementedAt: LocalDateTime) = CoordinatorEvent(
    eventType = EventType.OASYS_VERSION_EVENT,
    entityType = "AAP_PLAN",
    entityUuid = PLAN_ID,
    occurredAt = incrementedAt,
    message = payload(incrementedAt),
  )

  private fun payload(incrementedAt: LocalDateTime) = VersionPayload(
    version = VERSION,
    oasysEvent = OasysEvent.CREATED,
    incrementedAt = incrementedAt,
    deleted = false,
    association = AssociationPayload(oasysAssessmentPk = "2185046", regionPrisonCode = "DRH", baseVersion = VERSION),
  )

  private companion object {
    private const val VERSION = 1781000513192L
    private val PLAN_ID = UUID.fromString("00000001-1111-1111-1111-000000000001")
    private val REWRITE_GOAL_ID = UUID.fromString("00000001-aaaa-aaaa-aaaa-000000000099")
    private val REWRITE_AGREEMENT_ID = UUID.fromString("00000001-dddd-dddd-dddd-000000000099")
    private val GOAL_CREATOR = UUID.fromString("99999999-9999-9999-9999-000000000001")
    private val STEP_CREATOR = UUID.fromString("99999999-9999-9999-9999-000000000002")
    private val NOTE_CREATOR = UUID.fromString("99999999-9999-9999-9999-000000000003")
    private val AGREEMENT_CREATOR = UUID.fromString("99999999-9999-9999-9999-000000000004")
    private val FIRST_SEEN = LocalDateTime.parse("2026-06-10T14:23:20.123")
    private val REWRITTEN = LocalDateTime.parse("2026-06-11T09:00:00.000")
  }
}
