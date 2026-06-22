package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.AapApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.CoordinatorEvent
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.DeleteFlagUpdatePayload
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.EventType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository.SentencePlanRepository
import java.time.LocalDateTime
import java.util.UUID

class SnapshotIngestServiceTest {

  private val aapApiClient: AapApiClient = mock()
  private val sentencePlanRepository: SentencePlanRepository = mock()
  private val mapper: SentencePlanMapper = mock()
  private val timelineAuthorshipFetcher: TimelineAuthorshipFetcher = mock()
  private val transactionTemplate: TransactionTemplate = mock()

  private val service = SnapshotIngestService(
    aapApiClient,
    sentencePlanRepository,
    mapper,
    timelineAuthorshipFetcher,
    transactionTemplate,
  )

  @Test
  fun `applyDeleteFlagUpdate forwards bounded range to the repository`() {
    val entityUuid = UUID.randomUUID()
    val event = deleteEvent(entityUuid, deleted = true, versionFrom = 100L, versionTo = 400L)

    service.applyDeleteFlagUpdate(event, event.message as DeleteFlagUpdatePayload)

    verify(sentencePlanRepository).markDeletedForRange(
      id = eq(entityUuid),
      deleted = eq(true),
      versionFrom = eq(100L),
      versionTo = eq(400L),
    )
  }

  @Test
  fun `applyDeleteFlagUpdate forwards open-ended range (versionTo=null) to the repository`() {
    val entityUuid = UUID.randomUUID()
    val event = deleteEvent(entityUuid, deleted = true, versionFrom = 400L, versionTo = null)

    service.applyDeleteFlagUpdate(event, event.message as DeleteFlagUpdatePayload)

    verify(sentencePlanRepository).markDeletedForRange(
      id = eq(entityUuid),
      deleted = eq(true),
      versionFrom = eq(400L),
      versionTo = eq(null),
    )
  }

  @Test
  fun `applyDeleteFlagUpdate forwards undelete (deleted=false)`() {
    val entityUuid = UUID.randomUUID()
    val event = deleteEvent(entityUuid, deleted = false, versionFrom = 100L, versionTo = 400L)

    service.applyDeleteFlagUpdate(event, event.message as DeleteFlagUpdatePayload)

    verify(sentencePlanRepository).markDeletedForRange(
      id = eq(entityUuid),
      deleted = eq(false),
      versionFrom = eq(100L),
      versionTo = eq(400L),
    )
  }

  private fun deleteEvent(
    entityUuid: UUID,
    deleted: Boolean,
    versionFrom: Long,
    versionTo: Long?,
  ): CoordinatorEvent = CoordinatorEvent(
    eventType = EventType.OASYS_DELETE_FLAG_UPDATE_EVENT,
    entityType = "AAP_PLAN",
    entityUuid = entityUuid,
    occurredAt = LocalDateTime.parse("2026-06-12T14:00:00"),
    message = DeleteFlagUpdatePayload(deleted = deleted, versionFrom = versionFrom, versionTo = versionTo),
  )
}
