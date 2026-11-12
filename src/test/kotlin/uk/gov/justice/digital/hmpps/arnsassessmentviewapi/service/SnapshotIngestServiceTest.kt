package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.AapApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.assessment
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.AssociationPayload
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.CoordinatorEvent
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.DeleteFlagUpdatePayload
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.EventType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.OasysEvent
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.VersionPayload
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository.SentencePlanRepository
import java.time.LocalDateTime
import java.util.Optional
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

  @ParameterizedTest
  @ValueSource(ints = [0, 1])
  fun `equal timestamps only attempt association correction`(affectedRows: Int) {
    val event = versionEvent()
    val payload = event.message as VersionPayload
    val incomingAsOf = payload.incrementedAt.toInstant()
    whenever(sentencePlanRepository.findByIdAndVersion(event.entityUuid, payload.version))
      .thenReturn(Optional.of(snapshot(event)))
    whenever(
      sentencePlanRepository.updateSnapshotAssociation(
        event.entityUuid,
        payload.version,
        incomingAsOf,
        payload.association.oasysAssessmentPk,
        payload.association.regionPrisonCode,
      ),
    ).thenReturn(affectedRows)

    service.ingestVersion(event, payload)

    verify(sentencePlanRepository).findByIdAndVersion(event.entityUuid, payload.version)
    verify(sentencePlanRepository).updateSnapshotAssociation(
      event.entityUuid,
      payload.version,
      incomingAsOf,
      payload.association.oasysAssessmentPk,
      payload.association.regionPrisonCode,
    )
    verifyNoMoreInteractions(sentencePlanRepository)
    verifyNoInteractions(aapApiClient, timelineAuthorshipFetcher, mapper, transactionTemplate)
  }

  @Test
  fun `older events remain skipped without association or content changes`() {
    val event = versionEvent()
    val payload = event.message as VersionPayload
    val existing = snapshot(event).apply { updatedAt = updatedAt.plusSeconds(1) }
    whenever(sentencePlanRepository.findByIdAndVersion(event.entityUuid, payload.version)).thenReturn(Optional.of(existing))

    service.ingestVersion(event, payload)

    verify(sentencePlanRepository).findByIdAndVersion(event.entityUuid, payload.version)
    verifyNoMoreInteractions(sentencePlanRepository)
    verifyNoInteractions(aapApiClient, timelineAuthorshipFetcher, mapper, transactionTemplate)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `newer events and missing snapshots ingest assessment content`(hasExisting: Boolean) {
    val event = versionEvent()
    val payload = event.message as VersionPayload
    val existing = if (hasExisting) snapshot(event).apply { updatedAt = updatedAt.minusSeconds(1) } else null
    val source = assessment(event.entityUuid)
    val authorship = emptyMap<UUID, ItemAuthorship>()
    val mapped = snapshot(event)
    whenever(sentencePlanRepository.findByIdAndVersion(event.entityUuid, payload.version)).thenReturn(Optional.ofNullable(existing))
    whenever(aapApiClient.queryAt(event.entityUuid, payload.incrementedAt)).thenReturn(source)
    whenever(timelineAuthorshipFetcher.fetchIfNeeded(source)).thenReturn(authorship)
    whenever(mapper.toEntity(source, payload, existing, authorship)).thenReturn(mapped)
    whenever(transactionTemplate.execute<Any?>(any())).thenAnswer { invocation ->
      @Suppress("UNCHECKED_CAST")
      (invocation.arguments[0] as TransactionCallback<Any?>).doInTransaction(mock<TransactionStatus>())
    }

    service.ingestVersion(event, payload)

    verify(aapApiClient).queryAt(event.entityUuid, payload.incrementedAt)
    verify(timelineAuthorshipFetcher).fetchIfNeeded(source)
    verify(mapper).toEntity(source, payload, existing, authorship)
    verify(sentencePlanRepository, times(2)).findByIdAndVersion(event.entityUuid, payload.version)
    if (existing != null) verify(sentencePlanRepository).saveAndFlush(existing)
    verify(sentencePlanRepository).save(mapped)
    verifyNoMoreInteractions(sentencePlanRepository)
  }

  @Test
  fun `association correction database failures propagate for retry`() {
    val event = versionEvent()
    val payload = event.message as VersionPayload
    val failure = DataAccessResourceFailureException("database unavailable")
    whenever(sentencePlanRepository.findByIdAndVersion(event.entityUuid, payload.version)).thenReturn(Optional.of(snapshot(event)))
    whenever(
      sentencePlanRepository.updateSnapshotAssociation(
        event.entityUuid,
        payload.version,
        payload.incrementedAt.toInstant(),
        payload.association.oasysAssessmentPk,
        payload.association.regionPrisonCode,
      ),
    ).thenThrow(failure)

    assertThat(assertThrows<DataAccessResourceFailureException> { service.ingestVersion(event, payload) }).isSameAs(failure)
    verifyNoInteractions(aapApiClient, timelineAuthorshipFetcher, mapper, transactionTemplate)
  }

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

  private fun versionEvent(): CoordinatorEvent {
    val incrementedAt = LocalDateTime.parse("2026-06-12T14:00:00.123")
    return CoordinatorEvent(
      eventType = EventType.OASYS_VERSION_EVENT,
      entityType = "AAP_PLAN",
      entityUuid = UUID.randomUUID(),
      occurredAt = incrementedAt,
      message = VersionPayload(
        version = 100L,
        oasysEvent = OasysEvent.CREATED,
        incrementedAt = incrementedAt,
        deleted = false,
        association = AssociationPayload(oasysAssessmentPk = "corrected-pk", regionPrisonCode = null, baseVersion = 100L),
      ),
    )
  }

  private fun snapshot(event: CoordinatorEvent): SentencePlanEntity {
    val payload = event.message as VersionPayload
    return SentencePlanEntity(
      id = event.entityUuid,
      createdAt = payload.incrementedAt.toInstant(),
      updatedAt = payload.incrementedAt.toInstant(),
      version = payload.version,
      oasysPk = "old-pk",
      regionCode = "OLD",
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
