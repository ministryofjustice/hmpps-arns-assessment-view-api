package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.AapApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.IdentifierType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.UserDetails
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.AapApiExtension
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.AapApiExtension.Companion.aapApi
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import java.time.LocalDateTime
import java.util.UUID

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

  private val testUser = UserDetails(id = "test-user", name = "test-user")

  @BeforeEach
  fun setUp() {
    hmppsAuth.stubGrantToken()
  }

  @Test
  fun `client bean is wired with OAuth2 WebClient`() {
    assertThat(aapApiClient).isNotNull
  }

  @Test
  fun `queryAssessmentByCrn deserialises a single result`() {
    aapApi.stubAssessmentVersionQuery(
      """
      {"queries":[{"result":{
        "assessmentUuid":"00000001-1111-1111-1111-000000000001",
        "aggregateUuid":"00000001-2222-2222-2222-000000000002",
        "assessmentType":"SENTENCE_PLAN","formVersion":"v1.0",
        "createdAt":"2025-01-01T10:00:00","updatedAt":"2025-01-01T10:00:00",
        "answers":{},"properties":{},"collections":[],
        "collaborators":[],
        "identifiers":{"CRN":"X123456"},
        "flags":[]
      }}]}
      """.trimIndent(),
    )

    val result = aapApiClient.queryAssessmentByCrn("X123456", "SENTENCE_PLAN", testUser)

    assertThat(result).isNotNull
    assertThat(result!!.identifiers[IdentifierType.CRN]).isEqualTo("X123456")
  }

  @Test
  fun `queryAssessmentByCrn returns null on 404`() {
    aapApi.stubAssessmentVersionQuery("""{"error":"not found"}""", status = 404)

    val result = aapApiClient.queryAssessmentByCrn("UNKNOWN", "SENTENCE_PLAN", testUser)

    assertThat(result).isNull()
  }

  @Test
  fun `queryAt deserialises a single result`() {
    aapApi.stubAssessmentVersionQuery(
      """
      {"queries":[{"result":{
        "assessmentUuid":"00000001-1111-1111-1111-000000000001",
        "aggregateUuid":"00000001-2222-2222-2222-000000000002",
        "assessmentType":"SENTENCE_PLAN","formVersion":"v1.0",
        "createdAt":"2025-01-01T10:00:00","updatedAt":"2025-01-01T10:00:00",
        "answers":{},"properties":{},"collections":[],
        "collaborators":[],
        "identifiers":{"CRN":"X123456"},
        "flags":[]
      }}]}
      """.trimIndent(),
    )

    val result = aapApiClient.queryAt(
      UUID.fromString("00000001-1111-1111-1111-000000000001"),
      LocalDateTime.parse("2026-06-10T14:23:20.123"),
    )

    assertThat(result).isNotNull
    assertThat(result!!.assessmentUuid).isEqualTo(UUID.fromString("00000001-1111-1111-1111-000000000001"))
  }

  @Test
  fun `queryAt returns null on 404`() {
    aapApi.stubAssessmentVersionQuery("""{"error":"not found"}""", status = 404)

    val result = aapApiClient.queryAt(UUID.randomUUID(), LocalDateTime.now())

    assertThat(result).isNull()
  }

  @ParameterizedTest
  @MethodSource("sourceIdentities")
  fun `source identity deserialises on timeline actors collaborators and assigned users`(
    metadata: String,
    sourceUserId: String?,
    authSource: String?,
  ) {
    val actorId = if (sourceUserId == null) UUID(0, 0) else UUID.fromString("99999999-9999-9999-9999-000000000001")
    val actorName = if (sourceUserId == null) "UNKNOWN" else "Source Actor"
    val planId = UUID.fromString("00000001-1111-1111-1111-000000000001")
    val user = """{"id":"$actorId","name":"$actorName"$metadata}"""
    aapApi.stubTimelineQuery(
      """
      {"queries":[{"result":{
        "timeline":[{
          "uuid":"00000000-0000-0000-0000-000000000001",
          "timestamp":"2026-01-01T10:00:00",
          "user":$user,
          "assessment":"$planId",
          "event":"CollectionItemAddedEvent"
        }],
        "pageInfo":{"pageNumber":0,"totalPages":1}
      }}]}
      """.trimIndent(),
    )
    val assessmentFixture = javaClass.classLoader.getResourceAsStream("fixtures/sync/aap-query-1.json")!!
      .bufferedReader().use { it.readText() }
    aapApi.stubModifiedSinceQuery(
      assessmentFixture.replace("\"collaborators\": []", "\"collaborators\": [$user]")
        .replace("\"assignedUser\": null", "\"assignedUser\": $user"),
    )

    val actor = aapApiClient.queryTimeline(planId).timeline.single().user
    val assessment = aapApiClient.queryModifiedSince("SENTENCE_PLAN", LocalDateTime.parse("2026-01-01T00:00:00"))
      .assessments.single()

    assertThat(actor.id).isEqualTo(actorId)
    assertThat(actor.name).isEqualTo(actorName)
    assertThat(actor.userId).isEqualTo(sourceUserId)
    assertThat(actor.authSource).isEqualTo(authSource)
    listOf(assessment.collaborators.single(), requireNotNull(assessment.assignedUser)).forEach {
      assertThat(it.id).isEqualTo(actorId)
      assertThat(it.name).isEqualTo(actorName)
      assertThat(it.userId).isEqualTo(sourceUserId)
      assertThat(it.authSource).isEqualTo(authSource)
    }
  }

  private companion object {
    @JvmStatic
    fun sourceIdentities(): List<Arguments> = listOf(
      Arguments.of("", null, null),
      Arguments.of(""","userId":null,"authSource":null""", null, null),
      Arguments.of(""","userId":"legacy.user","authSource":"NOT_SPECIFIED"""", "legacy.user", "NOT_SPECIFIED"),
      Arguments.of(""","userId":"probation.user","authSource":"HMPPS_AUTH"""", "probation.user", "HMPPS_AUTH"),
      Arguments.of(""","userId":"12345","authSource":"OASYS"""", "12345", "OASYS"),
      Arguments.of(""","userId":"future:user/42","authSource":"FUTURE_IDENTITY_PROVIDER"""", "future:user/42", "FUTURE_IDENTITY_PROVIDER"),
    )
  }
}
