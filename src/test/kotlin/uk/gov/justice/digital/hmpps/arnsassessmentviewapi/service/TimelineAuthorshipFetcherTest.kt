package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.AapApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.PageInfo
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.TimelineItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.TimelineQueryResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.TimelineUser
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.agreementItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.agreementsCollection
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.assessment
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.emptyTimelinePage
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.goalItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.goalsCollection
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.noteItem
import java.time.LocalDateTime
import java.util.UUID

class TimelineAuthorshipFetcherTest {

  private val aapApiClient: AapApiClient = mock()
  private val fetcher = TimelineAuthorshipFetcher(aapApiClient)

  @Test
  fun `does not query timeline when the assessment has no agreements and no goal notes`() {
    val uuid = UUID.randomUUID()

    val result = fetcher.fetchIfNeeded(assessment(uuid))

    assertThat(result).isEmpty()
    verify(aapApiClient, never()).queryTimeline(any(), anyOrNull(), anyOrNull(), any(), any())
  }

  @Test
  fun `queries timeline when the assessment has at least one agreement`() {
    val uuid = UUID.randomUUID()
    val source = assessment(uuid, collections = listOf(agreementsCollection(listOf(agreementItem()))))
    whenever(aapApiClient.queryTimeline(any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(emptyTimelinePage())

    fetcher.fetchIfNeeded(source)

    verify(aapApiClient, times(2)).queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), any(), any())
  }

  @Test
  fun `queries timeline when the assessment has at least one goal note`() {
    val uuid = UUID.randomUUID()
    val source = assessment(
      uuid,
      collections = listOf(goalsCollection(listOf(goalItem(notes = listOf(noteItem()))))),
    )
    whenever(aapApiClient.queryTimeline(any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(emptyTimelinePage())

    fetcher.fetchIfNeeded(source)

    verify(aapApiClient, times(2)).queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), any(), any())
  }

  @Test
  fun `stops paginating timeline early when a page is empty`() {
    val uuid = UUID.randomUUID()
    val source = assessment(uuid, collections = listOf(agreementsCollection(listOf(agreementItem()))))
    whenever(aapApiClient.queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), eq(0), any()))
      .thenReturn(timelinePageWithItems(uuid, items = emptyMap(), pageNumber = 0, totalPages = 10))

    fetcher.fetchIfNeeded(source)

    verify(aapApiClient, times(2)).queryTimeline(any(), anyOrNull(), anyOrNull(), any(), any())
  }

  @Test
  fun `accumulates creator entries across multiple timeline pages`() {
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
    whenever(aapApiClient.queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), eq(0), any()))
      .thenReturn(timelinePageWithItems(uuid, items = mapOf(agreementUuid to (agreementCreator to "Agreement creator")), pageNumber = 0, totalPages = 2))
    whenever(aapApiClient.queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), eq(1), any()))
      .thenReturn(timelinePageWithItems(uuid, items = mapOf(noteUuid to (noteCreator to "Note creator")), pageNumber = 1, totalPages = 2))

    val result = fetcher.fetchIfNeeded(source)

    assertThat(result).containsExactlyInAnyOrderEntriesOf(
      mapOf(
        agreementUuid to ItemAuthorship(agreementCreator, null, "Agreement creator"),
        noteUuid to ItemAuthorship(noteCreator, null, "Note creator"),
      ),
    )
  }

  @Test
  fun `creator name and UUID come from the creation entry while latest goal updater remains independent`() {
    val uuid = UUID.randomUUID()
    val goalUuid = UUID.randomUUID()
    val creator = UUID.randomUUID()
    val earlierUpdater = UUID.randomUUID()
    val latestUpdater = UUID.randomUUID()
    val source = assessment(uuid, collections = listOf(goalsCollection(listOf(goalItem(uuid = goalUuid)))))
    val creation = timelineItem(uuid, goalUuid, creator, name = "Creator")
    val updates = listOf(
      updateTimelineItem(uuid, goalUuid, earlierUpdater, "2026-01-01T10:00:00", "Earlier updater"),
      updateTimelineItem(uuid, goalUuid, latestUpdater, "2026-01-02T10:00:00", "Latest updater"),
    )
    whenever(aapApiClient.queryTimeline(eq(uuid), eq(setOf("CollectionItemAddedEvent")), anyOrNull(), eq(0), any()))
      .thenReturn(TimelineQueryResult(listOf(creation), PageInfo(0, 1)))
    whenever(
      aapApiClient.queryTimeline(
        eq(uuid),
        anyOrNull(),
        eq(setOf("GOAL_UPDATED", "GOAL_ACHIEVED", "GOAL_REMOVED", "GOAL_READDED")),
        eq(0),
        any(),
      ),
    ).thenReturn(TimelineQueryResult(updates, PageInfo(0, 1)))

    val authorship = fetcher.fetchIfNeeded(source).getValue(goalUuid)

    assertThat(authorship).isEqualTo(ItemAuthorship(creator, latestUpdater, "Creator"))
  }

  @Test
  fun `blank creator name is preserved with its UUID`() {
    val uuid = UUID.randomUUID()
    val itemUuid = UUID.randomUUID()
    val creator = UUID.randomUUID()
    val source = assessment(uuid, collections = listOf(agreementsCollection(listOf(agreementItem(uuid = itemUuid)))))
    whenever(aapApiClient.queryTimeline(any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(
      TimelineQueryResult(listOf(timelineItem(uuid, itemUuid, creator, name = "")), PageInfo(0, 1)),
    )

    val authorship = fetcher.fetchIfNeeded(source).getValue(itemUuid)

    assertThat(authorship.createdBy).isEqualTo(creator)
    assertThat(authorship.createdByName).isEmpty()
  }

  @Test
  fun `silently skips a timeline item that has no collectionItemUuid in its data`() {
    val uuid = UUID.randomUUID()
    val agreementUuid = UUID.randomUUID()
    val agreementCreator = UUID.randomUUID()
    val source = assessment(uuid, collections = listOf(agreementsCollection(listOf(agreementItem(uuid = agreementUuid)))))
    whenever(aapApiClient.queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), any(), any())).thenReturn(
      TimelineQueryResult(
        timeline = listOf(
          timelineItem(uuid, agreementUuid, agreementCreator),
          timelineItem(uuid, dataOverride = mapOf("someOtherKey" to "value")),
        ),
        pageInfo = PageInfo(0, 1),
      ),
    )

    val result = fetcher.fetchIfNeeded(source)

    assertThat(result).containsExactlyEntriesOf(mapOf(agreementUuid to ItemAuthorship(agreementCreator, null, "User")))
  }

  @Test
  fun `silently skips a timeline item whose collectionItemUuid is not a String`() {
    val uuid = UUID.randomUUID()
    val agreementUuid = UUID.randomUUID()
    val agreementCreator = UUID.randomUUID()
    val source = assessment(uuid, collections = listOf(agreementsCollection(listOf(agreementItem(uuid = agreementUuid)))))
    whenever(aapApiClient.queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), any(), any())).thenReturn(
      TimelineQueryResult(
        timeline = listOf(
          timelineItem(uuid, agreementUuid, agreementCreator),
          timelineItem(uuid, dataOverride = mapOf("collectionItemUuid" to 12345)),
        ),
        pageInfo = PageInfo(0, 1),
      ),
    )

    val result = fetcher.fetchIfNeeded(source)

    assertThat(result).containsExactlyEntriesOf(mapOf(agreementUuid to ItemAuthorship(agreementCreator, null, "User")))
  }

  @Test
  fun `throws when collectionItemUuid is a malformed UUID string`() {
    val uuid = UUID.randomUUID()
    val source = assessment(uuid, collections = listOf(agreementsCollection(listOf(agreementItem()))))
    whenever(aapApiClient.queryTimeline(eq(uuid), anyOrNull(), anyOrNull(), any(), any())).thenReturn(
      TimelineQueryResult(
        timeline = listOf(timelineItem(uuid, dataOverride = mapOf("collectionItemUuid" to "not-a-uuid"))),
        pageInfo = PageInfo(0, 1),
      ),
    )

    assertThrows<IllegalArgumentException> { fetcher.fetchIfNeeded(source) }
  }

  private fun timelinePageWithItems(
    assessmentUuid: UUID,
    items: Map<UUID, Pair<UUID, String>>,
    pageNumber: Int = 0,
    totalPages: Int = 1,
  ): TimelineQueryResult = TimelineQueryResult(
    timeline = items.map { (itemUuid, creator) -> timelineItem(assessmentUuid, itemUuid, creator.first, name = creator.second) },
    pageInfo = PageInfo(pageNumber = pageNumber, totalPages = totalPages),
  )

  private fun timelineItem(
    assessmentUuid: UUID,
    itemUuid: UUID = UUID.randomUUID(),
    userUuid: UUID = UUID.randomUUID(),
    dataOverride: Map<String, Any>? = null,
    name: String = "User",
  ): TimelineItem = TimelineItem(
    uuid = UUID.randomUUID(),
    timestamp = LocalDateTime.now(),
    user = TimelineUser(id = userUuid, name = name),
    assessment = assessmentUuid,
    event = "CollectionItemAddedEvent",
    data = dataOverride ?: mapOf("collectionItemUuid" to itemUuid.toString()),
  )

  private fun updateTimelineItem(
    assessmentUuid: UUID,
    goalUuid: UUID,
    userUuid: UUID,
    timestamp: String,
    name: String,
  ): TimelineItem = TimelineItem(
    uuid = UUID.randomUUID(),
    timestamp = LocalDateTime.parse(timestamp),
    user = TimelineUser(id = userUuid, name = name),
    assessment = assessmentUuid,
    event = "CollectionItemPropertiesUpdatedEvent",
    customType = "GOAL_UPDATED",
    customData = mapOf("goalUuid" to goalUuid.toString()),
  )
}
