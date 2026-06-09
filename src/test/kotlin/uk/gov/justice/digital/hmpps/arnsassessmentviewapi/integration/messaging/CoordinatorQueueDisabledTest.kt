package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.messaging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.CoordinatorEventListener

/**
 * Asserts the application boots when no coordinator queue name is configured.
 *
 * The queue name secret (`HMPPS_SQS_QUEUES_COORDINATOR_QUEUE_NAME`) doubles as the
 * activating value for the `coordinator-queue` profile. When it is empty, the profile
 * stays inactive, so neither the SQS queue config nor the listener bean are loaded.
 *
 * The deployed/dev environment normally sets the secret, so we force it empty here to
 * reproduce the "secret not wired" case. Reaching `@Test` at all proves the context
 * started without it.
 */
@TestPropertySource(properties = ["HMPPS_SQS_QUEUES_COORDINATOR_QUEUE_NAME="])
class CoordinatorQueueDisabledTest : IntegrationTestBase() {

  @Autowired
  private lateinit var applicationContext: ApplicationContext

  @Test
  fun `application starts without a coordinator queue configured`() {
    assertThat(applicationContext.environment.activeProfiles).doesNotContain("coordinator-queue")
  }

  @Test
  fun `coordinator event listener bean is not registered when the queue is not configured`() {
    assertThat(applicationContext.getBeansOfType(CoordinatorEventListener::class.java)).isEmpty()
  }
}
