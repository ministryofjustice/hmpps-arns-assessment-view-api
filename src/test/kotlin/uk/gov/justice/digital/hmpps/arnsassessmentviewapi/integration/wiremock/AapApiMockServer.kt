package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class AapApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val aapApi = AapApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    aapApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    aapApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    aapApi.stop()
  }
}

class AapApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8092
  }

  fun stubQuery(responseBody: String, status: Int = 200) {
    stubFor(
      post(urlEqualTo("/query"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)
            .withStatus(status),
        ),
    )
  }
}
