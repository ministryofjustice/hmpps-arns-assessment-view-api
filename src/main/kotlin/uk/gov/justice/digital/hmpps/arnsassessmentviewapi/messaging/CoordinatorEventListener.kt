package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Consumes events from the coordinator SQS queue. Enabled by
 * `app.coordinator-event-listener.enabled` so it stays opt-in per environment.
 */
@Component
@Profile("coordinator-queue")
@ConditionalOnProperty("app.coordinator-event-listener.enabled", havingValue = "true")
class CoordinatorEventListener {

  @SqsListener("coordinator", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(rawMessage: String) {
    log.info("Received message from coordinator queue: {}", rawMessage)
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
