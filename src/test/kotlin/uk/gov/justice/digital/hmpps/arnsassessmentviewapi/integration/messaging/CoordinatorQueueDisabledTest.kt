package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.messaging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging.CoordinatorEventListener

/**
 * Asserts the app boots without the `coordinator-queue` profile
 */
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
