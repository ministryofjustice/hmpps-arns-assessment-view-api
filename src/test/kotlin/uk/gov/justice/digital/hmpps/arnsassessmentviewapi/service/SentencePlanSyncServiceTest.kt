package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.AapApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.CoordinatorApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.AssessmentVersionQueryResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.EntityAssociationDetails
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.PageInfo
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.TimelineItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.TimelineQueryResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.TimelineUser
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SyncStateEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.agreementItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.agreementsCollection
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.assessment
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.association
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.emptyTimelinePage
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.goalItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.goalsCollection
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.noteItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.page
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository.SentencePlanRepository
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository.SyncStateRepository
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import java.util.UUID

private const val SYNC_STATE_KEY = "sentence_plan"
private const val ASSESSMENT_TYPE = "SENTENCE_PLAN"

class SentencePlanSyncServiceTest {

  private val aapApiClient: AapApiClient = mock()
  private val coordinatorApiClient: CoordinatorApiClient = mock()
  private val repository: SentencePlanRepository = mock()
  private val syncStateRepository: SyncStateRepository = mock()
  private val mapper: SentencePlanMapper = mock()
  private val transactionTemplate: TransactionTemplate = mock()

  private val service = SentencePlanSyncService(
    aapApiClient,
    coordinatorApiClient,
    repository,
    syncStateRepository,
    mapper,
    transactionTemplate,
  )

  @BeforeEach
  fun defaultStubs() {
    whenever(transactionTemplate.execute<Any?>(any())).thenAnswer { invocation ->
      @Suppress("UNCHECKED_CAST")
      (invocation.arguments[0] as TransactionCallback<Any?>).doInTransaction(mock<TransactionStatus>())
    }
    whenever(syncStateRepository.findById(SYNC_STATE_KEY)).thenReturn(Optional.empty())
    whenever(syncStateRepository.save(any<SyncStateEntity>())).doAnswer { it.arguments[0] as SyncStateEntity }
    whenever(aapApiClient.queryTimeline(any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(emptyTimelinePage())
    whenever(mapper.toEntity(any(), any(), anyOrNull(), any())).thenAnswer {
      val source = it.arguments[0] as AssessmentVersionQueryResult
      stubEntity(source.assessmentUuid)
    }
    whenever(repository.save(any<SentencePlanEntity>())).doAnswer { it.arguments[0] as SentencePlanEntity }
    whenever(repository.saveAndFlush(any<SentencePlanEntity>())).doAnswer { it.arguments[0] as SentencePlanEntity }
    whenever(repository.findById(any<UUID>())).thenReturn(Optional.empty())
  }

  // ----------------------------------------------------------------------------------------------
  @Nested
  inner class Watermark {

    @Test
    fun `uses EPOCH when no prior sync recorded`() {
      // GIVEN no sync state row exists
      whenever(syncStateRepository.findById(SYNC_STATE_KEY)).thenReturn(Optional.empty())
      stubEmptyPage()

      // WHEN the service syncs
      service.sync()

      // THEN AAP is queried with the EPOCH watermark
      verify(aapApiClient).queryModifiedSince(
        eq(ASSESSMENT_TYPE),
        check { assertThat(it).isEqualTo(LocalDateTime.of(1970, 1, 1, 0, 0)) },
        anyOrNull(),
        any(),
      )
    }

    @Test
    fun `uses previous sync start minus 1 minute buffer when present`() {
      // GIVEN a previously recorded sync start time
      val previousStart = Instant.parse("2026-04-20T10:00:00Z")
      whenever(syncStateRepository.findById(SYNC_STATE_KEY))
        .thenReturn(Optional.of(SyncStateEntity(id = SYNC_STATE_KEY, lastSyncStartedAt = previousStart)))
      stubEmptyPage()

      // WHEN the service syncs
      service.sync()

      // THEN AAP is queried with the previous start minus the 1 minute lookback buffer
      val expected = previousStart.minus(Duration.ofMinutes(1)).atZone(ZoneId.systemDefault()).toLocalDateTime()
      verify(aapApiClient).queryModifiedSince(eq(ASSESSMENT_TYPE), check { assertThat(it).isEqualTo(expected) }, anyOrNull(), any())
    }

    @Test
    fun `persists sync start time when sync completes normally`() {
      // GIVEN no assessments to process
      stubEmptyPage()

      // WHEN the service syncs
      val before = Instant.now()
      service.sync()
      val after = Instant.now()

      // THEN a sync_state row is saved with a fresh start time, falling between before/after
      verify(syncStateRepository).save(
        check<SyncStateEntity> {
          assertThat(it.id).isEqualTo(SYNC_STATE_KEY)
          assertThat(it.lastSyncStartedAt).isBetween(before, after)
        },
      )
    }

    @Test
    fun `updates the existing sync state row rather than creating a new one`() {
      // GIVEN a pre-existing sync state row
      val existing = SyncStateEntity(id = SYNC_STATE_KEY, lastSyncStartedAt = Instant.parse("2026-04-20T10:00:00Z"))
      whenever(syncStateRepository.findById(SYNC_STATE_KEY)).thenReturn(Optional.of(existing))
      stubEmptyPage()

      // WHEN the service syncs
      service.sync()

      // THEN the same instance is mutated and saved
      verify(syncStateRepository).save(check<SyncStateEntity> { assertThat(it).isSameAs(existing) })
    }
  }

  // ----------------------------------------------------------------------------------------------
  @Nested
  inner class Pagination {

    @Test
    fun `empty page does not call coordinator and persists no sentence plans`() {
      // GIVEN AAP returns no assessments
      stubEmptyPage()

      // WHEN the service syncs
      service.sync()

      // THEN coordinator and repository are untouched apart from the watermark write
      verify(coordinatorApiClient, never()).getLatestAssociationDetails(any())
      verify(repository, never()).save(any<SentencePlanEntity>())
    }

    @Test
    fun `single page processes every assessment`() {
      // GIVEN AAP returns a single page with two assessments
      val first = UUID.randomUUID()
      val second = UUID.randomUUID()
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any()))
        .thenReturn(page(listOf(assessment(first), assessment(second))))
      stubAssociationsFor(first, second)

      // WHEN the service syncs
      service.sync()

      // THEN both assessments produce a save
      verify(repository, times(2)).save(any<SentencePlanEntity>())
    }

    @Test
    fun `multi-page loop walks the cursor until nextCursor is null`() {
      // GIVEN page 1 with cursor=firstUuid and page 2 with cursor=null
      val first = UUID.randomUUID()
      val second = UUID.randomUUID()
      whenever(aapApiClient.queryModifiedSince(any(), any(), eq<UUID?>(null), any()))
        .thenReturn(page(listOf(assessment(first)), nextCursor = first))
      whenever(aapApiClient.queryModifiedSince(any(), any(), eq<UUID?>(first), any()))
        .thenReturn(page(listOf(assessment(second)), nextCursor = null))
      stubAssociationsFor(first, second)

      // WHEN the service syncs
      service.sync()

      // THEN AAP is called twice (cursor walks) and both assessments are persisted
      verify(aapApiClient, times(2)).queryModifiedSince(any(), any(), anyOrNull(), any())
      verify(repository, times(2)).save(any<SentencePlanEntity>())
    }
  }

  // ----------------------------------------------------------------------------------------------
  @Nested
  inner class ErrorHandling {

    @Test
    fun `lookback guard halts sync without advancing watermark`() {
      // GIVEN AAP throws the lookback guard 4xx with the well known marker
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any()))
        .doThrow(
          WebClientResponseException.create(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            HttpHeaders.EMPTY,
            """{"userMessage":"The 'since' parameter cannot be older than 1 day(s)"}""".toByteArray(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8,
          ),
        )

      // WHEN the service syncs
      service.sync()

      // THEN no work proceeds and the watermark is not advanced
      verify(coordinatorApiClient, never()).getLatestAssociationDetails(any())
      verify(repository, never()).save(any<SentencePlanEntity>())
      verify(syncStateRepository, never()).save(any<SyncStateEntity>())
    }

    @Test
    fun `4xx without lookback marker is re-thrown`() {
      // GIVEN AAP returns a 400 with an unrelated message
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any()))
        .doThrow(
          WebClientResponseException.create(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            HttpHeaders.EMPTY,
            """{"userMessage":"something else broke"}""".toByteArray(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8,
          ),
        )

      // WHEN/THEN the exception bubbles out, only the lookback marker triggers swallow
      assertThrows<WebClientResponseException> { service.sync() }
    }

    @Test
    fun `5xx server error is re-thrown`() {
      // GIVEN AAP returns 500
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any()))
        .doThrow(
          WebClientResponseException.create(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            HttpHeaders.EMPTY,
            ByteArray(0),
            StandardCharsets.UTF_8,
          ),
        )

      // WHEN/THEN sync fails fast on server errors so the next scheduled run can retry
      assertThrows<WebClientResponseException> { service.sync() }
    }

    @Test
    fun `assessment without coordinator association is skipped`() {
      // GIVEN two assessments but only one has a coordinator association
      val present = UUID.randomUUID()
      val missing = UUID.randomUUID()
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any()))
        .thenReturn(page(listOf(assessment(present), assessment(missing))))
      whenever(coordinatorApiClient.getLatestAssociationDetails(any()))
        .thenReturn(mapOf(present to defaultAssociation()))

      // WHEN the service syncs
      service.sync()

      // THEN only the assessment with a coordinator record is upserted
      val captor = argumentCaptor<SentencePlanEntity>()
      verify(repository).save(captor.capture())
      assertThat(captor.firstValue.id).isEqualTo(present)
      verify(repository, times(1)).save(any<SentencePlanEntity>())
    }

    @Test
    fun `failure on one assessment does not halt the rest of the batch`() {
      // GIVEN two assessments where the first throws on findById (poison)
      val poison = UUID.randomUUID()
      val good = UUID.randomUUID()
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any()))
        .thenReturn(page(listOf(assessment(poison), assessment(good))))
      stubAssociationsFor(poison, good)
      whenever(repository.findById(poison)).thenThrow(RuntimeException("boom"))

      // WHEN the service syncs
      service.sync()

      // THEN the good assessment still upserts, the poison one is counted but skipped
      verify(repository).save(check<SentencePlanEntity> { assertThat(it.id).isEqualTo(good) })
    }
  }

  // ----------------------------------------------------------------------------------------------
  @Nested
  inner class TransactionBoundary {

    @Test
    fun `existing entity is cleared and flushed before the mapper produces the new payload`() {
      // GIVEN an existing entity is loaded for the assessment
      val uuid = UUID.randomUUID()
      val existing = stubEntity(uuid)
      // pre-populate with dummy children so the test sees the clear() effect
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any()))
        .thenReturn(page(listOf(assessment(uuid))))
      stubAssociationsFor(uuid)
      whenever(repository.findById(uuid)).thenReturn(Optional.of(existing))

      // WHEN the service syncs
      service.sync()

      // THEN saveAndFlush of the cleared existing entity precedes mapper invocation, which precedes the final save
      val ordered = inOrder(repository, mapper)
      ordered.verify(repository).findById(uuid)
      ordered.verify(repository).saveAndFlush(check<SentencePlanEntity> { assertThat(it).isSameAs(existing) })
      ordered.verify(mapper).toEntity(any(), any(), eq(existing), any())
      ordered.verify(repository).save(any<SentencePlanEntity>())
    }

    @Test
    fun `no saveAndFlush is performed on the insert path (existing is null)`() {
      // GIVEN findById returns empty
      val uuid = UUID.randomUUID()
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any()))
        .thenReturn(page(listOf(assessment(uuid))))
      stubAssociationsFor(uuid)
      whenever(repository.findById(uuid)).thenReturn(Optional.empty())

      // WHEN the service syncs
      service.sync()

      // THEN the clear flush is skipped , saveAndFlush has nothing to do for inserts
      verify(repository, never()).saveAndFlush(any<SentencePlanEntity>())
      verify(repository).save(any<SentencePlanEntity>())
    }

    @Test
    fun `transactionTemplate execute is invoked once per assessment`() {
      // GIVEN three assessments
      val uuids = (1..3).map { UUID.randomUUID() }
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any()))
        .thenReturn(page(uuids.map { assessment(it) }))
      stubAssociationsFor(*uuids.toTypedArray())

      // WHEN the service syncs
      service.sync()

      // THEN transactionTemplate.execute fires three times , one transaction per assessment
      verify(transactionTemplate, times(3)).execute<Any?>(any())
    }
  }

  // ----------------------------------------------------------------------------------------------
  @Nested
  inner class TimelineFetching {

    @Test
    fun `does not query timeline when the assessment has no agreements and no goal notes`() {
      // GIVEN an assessment with neither agreements nor goal notes
      val uuid = UUID.randomUUID()
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any()))
        .thenReturn(page(listOf(assessment(uuid))))
      stubAssociationsFor(uuid)

      // WHEN the service syncs
      service.sync()

      // THEN we don't call for the timeline call when there's no authorship to attribute
      verify(aapApiClient, never()).queryTimeline(any(), anyOrNull(), anyOrNull(), any(), any())
    }

    @Test
    fun `queries timeline when the assessment has at least one agreement`() {
      // GIVEN an assessment with one agreement
      val uuid = UUID.randomUUID()
      val source = assessment(uuid, collections = listOf(agreementsCollection(listOf(agreementItem()))))
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any())).thenReturn(page(listOf(source)))
      stubAssociationsFor(uuid)

      // WHEN the service syncs
      service.sync()

      // THEN the timeline is queried twice once for createdBy (ADD events) and once for goal updatedBy (custom GOAL_* rows)
      verify(aapApiClient, times(2)).queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), any(), any())
    }

    @Test
    fun `queries timeline when the assessment has at least one goal note`() {
      // GIVEN an assessment with a goal containing notes
      val uuid = UUID.randomUUID()
      val source = assessment(
        uuid,
        collections = listOf(goalsCollection(listOf(goalItem(notes = listOf(noteItem()))))),
      )
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any())).thenReturn(page(listOf(source)))
      stubAssociationsFor(uuid)

      // WHEN the service syncs
      service.sync()

      // THEN the timeline is queried twice (createdBy + goal updatedBy harvesters)
      verify(aapApiClient, times(2)).queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), any(), any())
    }

    @Test
    fun `stops paginating timeline early when a page is empty`() {
      // GIVEN a totalPages=10 hint but the first page is empty
      val uuid = UUID.randomUUID()
      val source = assessment(uuid, collections = listOf(agreementsCollection(listOf(agreementItem()))))
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any())).thenReturn(page(listOf(source)))
      stubAssociationsFor(uuid)
      whenever(aapApiClient.queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), eq(0), any()))
        .thenReturn(timelinePageWithItems(uuid, items = emptyMap(), pageNumber = 0, totalPages = 10))

      // WHEN the service syncs
      service.sync()

      // THEN only the first page is fetched per harvester (createdBy + goal updatedBy = 2 calls total)
      verify(aapApiClient, times(2)).queryTimeline(any(), anyOrNull(), anyOrNull(), any(), any())
    }

    @Test
    fun `accumulates creator entries across multiple timeline pages`() {
      // GIVEN an assessment whose timeline spans two pages, each contributing one creator entry
      val uuid = UUID.randomUUID()
      val agreementUuid = UUID.randomUUID()
      val noteUuid = UUID.randomUUID()
      val agreementCreator = UUID.randomUUID()
      val noteCreator = UUID.randomUUID()
      val source = assessment(
        uuid,
        collections = listOf(
          agreementsCollection(listOf(agreementItem(uuid = agreementUuid))),
          goalsCollection(listOf(goalItem(notes = listOf(noteItem(uuid = noteUuid))))),
        ),
      )
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any())).thenReturn(page(listOf(source)))
      stubAssociationsFor(uuid)
      whenever(aapApiClient.queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), eq(0), any()))
        .thenReturn(timelinePageWithItems(uuid, items = mapOf(agreementUuid to agreementCreator), pageNumber = 0, totalPages = 2))
      whenever(aapApiClient.queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), eq(1), any()))
        .thenReturn(timelinePageWithItems(uuid, items = mapOf(noteUuid to noteCreator), pageNumber = 1, totalPages = 2))

      // WHEN the service syncs
      service.sync()

      // THEN the mapper receives a creator map containing entries from BOTH timeline pages
      verify(mapper).toEntity(
        any(),
        any(),
        anyOrNull(),
        check<Map<UUID, ItemAuthorship>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              agreementUuid to ItemAuthorship(agreementCreator, null),
              noteUuid to ItemAuthorship(noteCreator, null),
            ),
          )
        },
      )
    }

    @Test
    fun `silently skips a timeline item that has no collectionItemUuid in its data`() {
      // GIVEN a timeline page with one valid item and one whose data map lacks the collectionItemUuid key
      val uuid = UUID.randomUUID()
      val agreementUuid = UUID.randomUUID()
      val agreementCreator = UUID.randomUUID()
      val source = assessment(uuid, collections = listOf(agreementsCollection(listOf(agreementItem(uuid = agreementUuid)))))
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any())).thenReturn(page(listOf(source)))
      stubAssociationsFor(uuid)
      whenever(aapApiClient.queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), any(), any())).thenReturn(
        TimelineQueryResult(
          timeline = listOf(
            timelineItem(uuid, agreementUuid, agreementCreator),
            timelineItem(uuid, dataOverride = mapOf("someOtherKey" to "value")),
          ),
          pageInfo = PageInfo(0, 1),
        ),
      )

      // WHEN the service syncs
      service.sync()

      // THEN only the valid creator reaches the mapper, the malformed entry is dropped silently
      verify(mapper).toEntity(
        any(),
        any(),
        anyOrNull(),
        check<Map<UUID, ItemAuthorship>> {
          assertThat(it).containsExactlyEntriesOf(mapOf(agreementUuid to ItemAuthorship(agreementCreator, null)))
        },
      )
    }

    @Test
    fun `silently skips a timeline item whose collectionItemUuid is not a String`() {
      // GIVEN a timeline page with one valid item and one whose collectionItemUuid is the wrong shape (Int)
      val uuid = UUID.randomUUID()
      val agreementUuid = UUID.randomUUID()
      val agreementCreator = UUID.randomUUID()
      val source = assessment(uuid, collections = listOf(agreementsCollection(listOf(agreementItem(uuid = agreementUuid)))))
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any())).thenReturn(page(listOf(source)))
      stubAssociationsFor(uuid)
      whenever(aapApiClient.queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), any(), any())).thenReturn(
        TimelineQueryResult(
          timeline = listOf(
            timelineItem(uuid, agreementUuid, agreementCreator),
            timelineItem(uuid, dataOverride = mapOf("collectionItemUuid" to 12345)),
          ),
          pageInfo = PageInfo(0, 1),
        ),
      )

      // WHEN the service syncs
      service.sync()

      // THEN the wrong entry is silently dropped
      verify(mapper).toEntity(
        any(),
        any(),
        anyOrNull(),
        check<Map<UUID, ItemAuthorship>> {
          assertThat(it).containsExactlyEntriesOf(mapOf(agreementUuid to ItemAuthorship(agreementCreator, null)))
        },
      )
    }

    @Test
    fun `fails the assessment when collectionItemUuid is a malformed UUID string`() {
      // GIVEN a timeline whose item carries a non-UUID string in collectionItemUuid
      val uuid = UUID.randomUUID()
      val agreementUuid = UUID.randomUUID()
      val source = assessment(uuid, collections = listOf(agreementsCollection(listOf(agreementItem(uuid = agreementUuid)))))
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any())).thenReturn(page(listOf(source)))
      stubAssociationsFor(uuid)
      whenever(aapApiClient.queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), any(), any())).thenReturn(
        TimelineQueryResult(
          timeline = listOf(timelineItem(uuid, dataOverride = mapOf("collectionItemUuid" to "not-a-uuid"))),
          pageInfo = PageInfo(0, 1),
        ),
      )

      // WHEN the service syncs (UUID.fromString throws, runCatching catches it)
      service.sync()

      // THEN no save happens , the malformed UUID poisons this assessment without halting the batch
      verify(repository, never()).save(any<SentencePlanEntity>())
      verify(mapper, never()).toEntity(any(), any(), anyOrNull(), any())
    }
  }

  // ----------------------------------------------------------------------------------------------
  @Nested
  inner class MapperHandoff {

    @Test
    fun `passes the source assessment, association, and creator map to the mapper`() {
      // GIVEN one assessment with an agreement (so timeline is consulted)
      val uuid = UUID.randomUUID()
      val agreementUuid = UUID.randomUUID()
      val agreementCreator = UUID.randomUUID()
      val source = assessment(uuid, collections = listOf(agreementsCollection(listOf(agreementItem(uuid = agreementUuid)))))
      val assoc = defaultAssociation()
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any())).thenReturn(page(listOf(source)))
      whenever(coordinatorApiClient.getLatestAssociationDetails(any())).thenReturn(mapOf(uuid to assoc))
      whenever(aapApiClient.queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), any(), any()))
        .thenReturn(timelinePageWithItems(uuid, items = mapOf(agreementUuid to agreementCreator)))

      // WHEN the service syncs
      service.sync()

      // THEN the mapper receives the same source, association, and the creator map keyed by item uuid
      verify(mapper).toEntity(
        check<AssessmentVersionQueryResult> { assertThat(it.assessmentUuid).isEqualTo(uuid) },
        check<EntityAssociationDetails> { assertThat(it).isSameAs(assoc) },
        anyOrNull(),
        check<Map<UUID, ItemAuthorship>> {
          assertThat(it).containsExactlyEntriesOf(mapOf(agreementUuid to ItemAuthorship(agreementCreator, null)))
        },
      )
    }

    @Test
    fun `passes the existing entity from findById on the update path`() {
      // GIVEN findById returns an existing entity
      val uuid = UUID.randomUUID()
      val existing = stubEntity(uuid)
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any())).thenReturn(page(listOf(assessment(uuid))))
      stubAssociationsFor(uuid)
      whenever(repository.findById(uuid)).thenReturn(Optional.of(existing))

      // WHEN the service syncs
      service.sync()

      // THEN the mapper receives that same instance as 'existing'
      val captor = argumentCaptor<SentencePlanEntity>()
      verify(mapper).toEntity(any(), any(), captor.capture(), any())
      assertThat(captor.firstValue).isSameAs(existing)
    }

    @Test
    fun `passes null existing on the insert path`() {
      // GIVEN findById returns empty
      val uuid = UUID.randomUUID()
      whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any())).thenReturn(page(listOf(assessment(uuid))))
      stubAssociationsFor(uuid)
      whenever(repository.findById(uuid)).thenReturn(Optional.empty())

      // WHEN the service syncs
      service.sync()

      // THEN the mapper is told there's no existing entity
      val captor = argumentCaptor<SentencePlanEntity?>()
      verify(mapper).toEntity(any(), any(), captor.capture(), any())
      assertThat(captor.firstValue).isNull()
    }
  }

  // --- helpers ---

  private fun stubEmptyPage() {
    whenever(aapApiClient.queryModifiedSince(any(), any(), anyOrNull(), any())).thenReturn(page())
  }

  private fun stubAssociationsFor(vararg uuids: UUID) {
    whenever(coordinatorApiClient.getLatestAssociationDetails(any()))
      .thenReturn(uuids.associateWith { defaultAssociation() })
  }

  private fun defaultAssociation(): EntityAssociationDetails = association(oasysPk = "1", regionCode = null, baseVersion = 1)

  private fun stubEntity(id: UUID): SentencePlanEntity = SentencePlanEntity(
    id = id,
    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    lastSyncedAt = Instant.parse("2026-01-01T00:00:00Z"),
    oasysPk = "1",
    version = 1,
  )

  private fun timelinePageWithItems(
    assessmentUuid: UUID,
    items: Map<UUID, UUID>,
    pageNumber: Int = 0,
    totalPages: Int = 1,
  ): TimelineQueryResult = TimelineQueryResult(
    timeline = items.map { (itemUuid, userUuid) -> timelineItem(assessmentUuid, itemUuid, userUuid) },
    pageInfo = PageInfo(pageNumber = pageNumber, totalPages = totalPages),
  )

  private fun timelineItem(
    assessmentUuid: UUID,
    itemUuid: UUID = UUID.randomUUID(),
    userUuid: UUID = UUID.randomUUID(),
    dataOverride: Map<String, Any>? = null,
  ): TimelineItem = TimelineItem(
    uuid = UUID.randomUUID(),
    timestamp = LocalDateTime.now(),
    user = TimelineUser(id = userUuid, name = "User"),
    assessment = assessmentUuid,
    event = "CollectionItemAddedEvent",
    data = dataOverride ?: mapOf("collectionItemUuid" to itemUuid.toString()),
  )
}
