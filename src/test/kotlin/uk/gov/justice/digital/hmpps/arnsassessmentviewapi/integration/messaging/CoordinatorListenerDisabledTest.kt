package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.messaging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.CoordinatorEventListener
import uk.gov.justice.hmpps.sqs.HmppsQueueService

/**
 * Queue configured (profile active) but listener turned off: the SQS queue is still
 * registered, yet no listener bean is created.
 */
@ActiveProfiles("coordinator-queue")
@TestPropertySource(properties = ["app.coordinator-event-listener.enabled=false"])
class CoordinatorListenerDisabledTest : IntegrationTestBase() {

  @Autowired
  private lateinit var applicationContext: ApplicationContext

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  @Test
  fun `queue is registered but the listener bean is not created when disabled`() {
    assertThat(hmppsQueueService.findByQueueId("coordinator")).isNotNull
    assertThat(applicationContext.getBeansOfType(CoordinatorEventListener::class.java)).isEmpty()
  }
}