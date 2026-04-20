package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.AapApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.AapApiExtension
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth

@ExtendWith(HmppsAuthApiExtension::class, AapApiExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(
  properties = [
    "app.services.coordinator-api.base-url=http://localhost:8091",
    "app.services.aap-api.base-url=http://localhost:8092",
    "app.client.id=test-client",
    "app.client.secret=test-secret",
  ],
)
class AapApiClientTest {

  @Autowired
  private lateinit var aapApiClient: AapApiClient

  @Test
  fun `client bean is wired with OAuth2 WebClient`() {
    hmppsAuth.stubGrantToken()
    assertThat(aapApiClient).isNotNull
  }
}
