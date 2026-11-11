package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.dto.TokenDto
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.FailedTestLogger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class IntegrationTestBase {

  protected lateinit var webTestClient: WebTestClient
  protected lateinit var authTestClient: WebTestClient
  protected lateinit var aapTestClient: WebTestClient
  protected lateinit var coordinatorTestClient: WebTestClient

  @BeforeAll
  fun setup() {
    val authBaseUrl = System.getenv("AUTH_BASE_URL")
      ?: "https://sign-in-dev.hmpps.service.justice.gov.uk"
    val apiBaseUrl = System.getenv("ARNS_ASSESSMENT_VIEW_BASE_URL")
    val aapBaseUrl = System.getenv("AAP_TEST_URL")
    val coordinatorBaseUrl = System.getenv("COORDINATOR_TEST_URL")

    authTestClient = WebTestClient.bindToServer()
      .baseUrl(authBaseUrl)
      .build()

    val clientId = System.getenv("ARNS_ASSESSMENT_VIEW_CLIENT_ID") ?: "local-development-client-id"
    val clientSecret = System.getenv("ARNS_ASSESSMENT_VIEW_CLIENT_SECRET") ?: "default_secret"

    val token = fetchAccessToken(clientId, clientSecret)

    webTestClient = WebTestClient.bindToServer()
      .baseUrl(apiBaseUrl)
      .defaultHeader("Authorization", "Bearer $token")
      .entityExchangeResultConsumer { result ->
        FailedTestLogger.record(result)
      }
      .build()

    val aapClientId = System.getenv("AAP_CLIENT_ID") ?: "local-development-client-id"
    val aapClientSecret = System.getenv("AAP_CLIENT_SECRET") ?: "default_secret"

    val aapToken = fetchAccessToken(aapClientId, aapClientSecret)

    aapTestClient = WebTestClient.bindToServer()
      .baseUrl(aapBaseUrl)
      .defaultHeader("Authorization", "Bearer $aapToken")
      .entityExchangeResultConsumer { result ->
        FailedTestLogger.record(result)
      }
      .build()

    coordinatorTestClient = WebTestClient.bindToServer()
      .baseUrl(coordinatorBaseUrl)
      .defaultHeader("Authorization", "Bearer $aapToken")
      .entityExchangeResultConsumer { result ->
        FailedTestLogger.record(result)
      }
      .build()
  }

  private fun fetchAccessToken(clientId: String, clientSecret: String): String {
    val body = authTestClient.post()
      .uri("/auth/oauth/token")
      .headers { it.setBasicAuth(clientId, clientSecret) }
      .contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .body(BodyInserters.fromFormData("grant_type", "client_credentials"))
      .exchange()
      .expectStatus()
      .isOk
      .expectBody<TokenDto>()
      .returnResult()
      .responseBody

    return body?.access_token ?: throw IllegalStateException("Failed to retrieve OAuth access token")
  }
}
