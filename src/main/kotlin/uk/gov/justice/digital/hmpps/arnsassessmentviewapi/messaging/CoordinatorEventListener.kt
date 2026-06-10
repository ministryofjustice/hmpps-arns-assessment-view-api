package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service.SnapshotIngestService

/**
 * Consumes events from the coordinator SQS queue. Enabled by
 * `app.coordinator-event-listener.enabled` so it stays opt-in per environment.
 */
@Component
@Profile("coordinator-queue")
@ConditionalOnProperty("app.coordinator-event-listener.enabled", havingValue = "true")
class CoordinatorEventListener(
  private val objectMapper: ObjectMapper,
  private val snapshotIngestService: SnapshotIngestService,
) {

  @SqsListener("coordinator", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(rawMessage: String) {
    val event = try {
      objectMapper.readValue(rawMessage, CoordinatorEvent::class.java)
    } catch (ex: InvalidTypeIdException) {
      log.warn("Unknown coordinator eventType, skipping message: {}", ex.message)
      return
    } catch (ex: JsonProcessingException) {
      // Rethrow so SQS retries (maxReceiveCount = 3) and the message ends up on the DLQ
      log.error("Failed to parse coordinator message", ex)
      throw ex
    }

    when (val payload = event.message) {
      is VersionPayload -> snapshotIngestService.ingestVersion(event, payload)
      is DeleteFlagUpdatePayload -> snapshotIngestService.applyDeleteFlagUpdate(event, payload)
    }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
