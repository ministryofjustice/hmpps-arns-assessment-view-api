package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.client

import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.CoordinatorApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.CoordinatorApiExtension
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.CoordinatorApiExtension.Companion.coordinatorApi
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import java.util.UUID

@ExtendWith(HmppsAuthApiExtension::class, CoordinatorApiExtension::class)
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
class CoordinatorApiClientTest {

  @Autowired
  private lateinit var coordinatorApiClient: CoordinatorApiClient

  @BeforeEach
  fun setUp() {
    hmppsAuth.stubGrantToken()
    coordinatorApi.resetAll()
  }

  @Test
  fun `returns empty map when given empty list`() {
    val result = coordinatorApiClient.getOasysPksForEntities(emptyList())

    assertThat(result).isEmpty()
    coordinatorApi.verify(0, postRequestedFor(urlEqualTo("/entity/associations")))
  }

  @Test
  fun `sends entity UUIDs and returns mapped OASys PKs`() {
    val entityUuid1 = UUID.fromString("11111111-1111-1111-1111-111111111111")
    val entityUuid2 = UUID.fromString("22222222-2222-2222-2222-222222222222")

    coordinatorApi.stubEntityAssociations(
      """
      {
        "11111111-1111-1111-1111-111111111111": ["100", "200"],
        "22222222-2222-2222-2222-222222222222": ["300"]
      }
      """,
    )

    val result = coordinatorApiClient.getOasysPksForEntities(listOf(entityUuid1, entityUuid2))

    assertThat(result).hasSize(2)
    assertThat(result[entityUuid1]).containsExactly("100", "200")
    assertThat(result[entityUuid2]).containsExactly("300")

    coordinatorApi.verify(
      postRequestedFor(urlEqualTo("/entity/associations"))
        .withRequestBody(
          equalToJson(
            """["11111111-1111-1111-1111-111111111111","22222222-2222-2222-2222-222222222222"]""",
          ),
        ),
    )
  }

  @Test
  fun `returns empty map when downstream returns null`() {
    coordinatorApi.stubEntityAssociations("{}")

    val result = coordinatorApiClient.getOasysPksForEntities(listOf(UUID.randomUUID()))

    assertThat(result).isEmpty()
  }

  @Test
  fun `throws on server error`() {
    coordinatorApi.stubEntityAssociations("""{"error": "Internal Server Error"}""", status = 500)

    assertThrows<WebClientResponseException.InternalServerError> {
      coordinatorApiClient.getOasysPksForEntities(listOf(UUID.randomUUID()))
    }
  }
}
