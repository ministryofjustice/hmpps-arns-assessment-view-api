package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class CoordinatorApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val coordinatorApi = CoordinatorApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    coordinatorApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    coordinatorApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    coordinatorApi.stop()
  }
}

class CoordinatorApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8091
  }

  fun stubEntityAssociations(responseBody: String, status: Int = 200) {
    stubFor(
      post(urlEqualTo("/entity/associations"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)
            .withStatus(status),
        ),
    )
  }
}
