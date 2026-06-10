package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.messaging

import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.CoordinatorEventListener
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.Duration

@ActiveProfiles("coordinator-queue")
@TestPropertySource(properties = ["app.coordinator-event-listener.enabled=true"])
class CoordinatorEventListenerTest : IntegrationTestBase() {

  @MockitoSpyBean
  private lateinit var coordinatorEventListener: CoordinatorEventListener

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  @Test
  fun `coordinator queue listener consumes published messages`() {
    val queue = hmppsQueueService.findByQueueId("coordinator")
      ?: error("coordinator queue not configured")
    val body = """
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

    queue.sqsClient.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(queue.queueUrl)
        .messageBody(body)
        .build(),
    ).get()

    await().atMost(Duration.ofSeconds(10)).untilAsserted {
      verify(coordinatorEventListener).onMessage(body)
    }
  }
}
