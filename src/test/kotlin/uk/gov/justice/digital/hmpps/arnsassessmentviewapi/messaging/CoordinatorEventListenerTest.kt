package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service.SnapshotIngestService

class CoordinatorEventListenerTest {

  private val snapshotIngestService: SnapshotIngestService = mock()
  private val objectMapper: ObjectMapper = ObjectMapper()
    .registerModule(KotlinModule.Builder().build())
    .registerModule(JavaTimeModule())

  private val listener = CoordinatorEventListener(objectMapper, snapshotIngestService)

  private val versionEvent = """
    {
      "eventType":"OASYS_VERSION_EVENT",
      "entityType":"AAP_PLAN",
      "entityUuid":"00000001-1111-1111-1111-000000000001",
      "occurredAt":"2026-06-10T14:23:20.123",
      "message":{
        "version":1781000513192,
        "oasysEvent":"CREATED",
        "incrementedAt":"2026-06-10T14:23:20.123",
        "deleted":false,
        "association":{
          "oasysAssessmentPk":"2185046",
          "regionPrisonCode":"DRH",
          "baseVersion":1781000513192
        }
      }
    }
  """.trimIndent()

  @Test
  fun `dispatches VersionPayload to ingestVersion`() {
    listener.onMessage(versionEvent)

    verify(snapshotIngestService).ingestVersion(any(), any())
    verify(snapshotIngestService, never()).applyDeleteFlagUpdate(any(), any())
  }

  @Test
  fun `dispatches DeleteFlagUpdatePayload to applyDeleteFlagUpdate`() {
    val deleteEvent = """
      {
        "eventType":"OASYS_DELETE_FLAG_UPDATE_EVENT",
        "entityType":"AAP_PLAN",
        "entityUuid":"00000001-1111-1111-1111-000000000001",
        "occurredAt":"2026-06-10T14:23:20.123",
        "message":{
          "deleted":true,
          "versionFrom":1781000513192,
          "versionTo":null
        }
      }
    """.trimIndent()

    listener.onMessage(deleteEvent)

    verify(snapshotIngestService).applyDeleteFlagUpdate(any(), any())
    verify(snapshotIngestService, never()).ingestVersion(any(), any())
  }

  @Test
  fun `unknown eventType is skipped without invoking the ingest service`() {
    val unknown = versionEvent.replace("OASYS_VERSION_EVENT", "OASYS_UNKNOWN_EVENT")

    listener.onMessage(unknown)

    verify(snapshotIngestService, never()).ingestVersion(any(), any())
    verify(snapshotIngestService, never()).applyDeleteFlagUpdate(any(), any())
  }

  @Test
  fun `malformed JSON rethrows so SQS retries and DLQs`() {
    assertThrows<JsonProcessingException> {
      listener.onMessage("not valid json")
    }

    verify(snapshotIngestService, never()).ingestVersion(any(), any())
    verify(snapshotIngestService, never()).applyDeleteFlagUpdate(any(), any())
  }
}
