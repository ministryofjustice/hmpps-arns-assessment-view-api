package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.AapApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.CoordinatorEvent
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.DeleteFlagUpdatePayload
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.VersionPayload
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository.SentencePlanRepository

@Service
class SnapshotIngestService(
  private val aapApiClient: AapApiClient,
  private val sentencePlanRepository: SentencePlanRepository,
) {

  fun ingestVersion(envelope: CoordinatorEvent, payload: VersionPayload) {
    // TODO: impl
    log.info(
      "ingestVersion (no-op): entityUuid={} version={} oasysEvent={} incrementedAt={} deleted={}",
      envelope.entityUuid,
      payload.version,
      payload.oasysEvent,
      payload.incrementedAt,
      payload.deleted,
    )
  }

  fun applyDeleteFlagUpdate(envelope: CoordinatorEvent, payload: DeleteFlagUpdatePayload) {
    // TODO: impl
    log.info(
      "applyDeleteFlagUpdate (no-op): entityUuid={} deleted={} versionFrom={} versionTo={} ",
      envelope.entityUuid,
      payload.deleted,
      payload.versionFrom,
      payload.versionTo,
    )
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
