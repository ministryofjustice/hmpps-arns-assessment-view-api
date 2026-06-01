package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.AapApiExtension
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.AapApiExtension.Companion.aapApi
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth

@ExtendWith(AapApiExtension::class)
@TestPropertySource(
  properties = [
    "app.services.coordinator-api.base-url=http://localhost:8091",
    "app.services.aap-api.base-url=http://localhost:8092",
    "app.client.id=test-client",
    "app.client.secret=test-secret",
  ],
)
class SentencePlanControllerTest : IntegrationTestBase() {

  @BeforeEach
  fun setUp() {
    hmppsAuth.stubGrantToken()
  }

  private fun stubAap404() {
    aapApi.stubAssessmentVersionQuery("""{"error":"not found"}""", status = 404)
  }

  @Nested
  inner class Security {
    @Test
    fun `returns 401 when no auth token provided`() {
      webTestClient.get()
        .uri("/sentence-plan/X123456")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `returns 403 when user lacks required role`() {
      webTestClient.get()
        .uri("/sentence-plan/X123456")
        .headers(setAuthorisation(roles = listOf("ROLE_OTHER")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `returns 200 when user has ROLE_ASSESSMENT_VIEW_DELIUS`() {
      aapApi.stubAssessmentVersionQuery(minimalAssessmentResponse(crn = "X123456"))

      webTestClient.get()
        .uri("/sentence-plan/X123456")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW_DELIUS")))
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  inner class GetSentencePlan {
    @Test
    fun `returns 404 when AAP has no assessment for the CRN`() {
      stubAap404()

      webTestClient.get()
        .uri("/sentence-plan/UNKNOWN")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW_DELIUS")))
        .exchange()
        .expectStatus().isNotFound
        .expectBody()
        .jsonPath("$.status").isEqualTo(404)
        .jsonPath("$.userMessage").isEqualTo("No sentence plan found for the given CRN")
    }

    @Test
    fun `returns 404 for unknown path`() {
      webTestClient.get()
        .uri("/sentence-plan")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW_DELIUS")))
        .exchange()
        .expectStatus().isNotFound
        .expectBody()
        .jsonPath("$.status").isEqualTo(404)
    }

    @Test
    fun `returns sentence plan in spec-aligned shape`() {
      aapApi.stubAssessmentVersionQuery(fullyPopulatedAssessmentResponse(crn = "X123456"))

      webTestClient.get()
        .uri("/sentence-plan/X123456")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW_DELIUS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .json(
          """
          {
            "crn": "X123456",
            "nomis": "A7779DY",
            "planStatus": "AGREED",
            "goals": [{
              "goalTitle": "Find stable accommodation",
              "areaOfNeed": "ACCOMMODATION",
              "relatedAreasOfNeed": ["FINANCES"],
              "targetDate": "2025-12-31",
              "goalStatus": "ACTIVE",
              "steps": [{
                "description": "Contact housing provider",
                "status": "NOT_STARTED",
                "actor": "PROBATION_PRACTITIONER",
                "statusDate": "2025-06-01T12:00:00Z"
              }]
            }]
          }
          """,
          true,
        )
    }

    @Test
    fun `nomis and planStatus serialise as null when not populated`() {
      aapApi.stubAssessmentVersionQuery(minimalAssessmentResponse(crn = "X777777"))

      webTestClient.get()
        .uri("/sentence-plan/X777777")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW_DELIUS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .json(
          """
          {
            "crn": "X777777",
            "nomis": null,
            "planStatus": null,
            "goals": []
          }
          """,
          true,
        )
    }

    @Test
    fun `latest agreement determines planStatus`() {
      aapApi.stubAssessmentVersionQuery(
        """
        {"queries":[{"result":{
          "assessmentUuid":"00000001-1111-1111-1111-000000000001",
          "aggregateUuid":"00000001-2222-2222-2222-000000000002",
          "assessmentType":"SENTENCE_PLAN","formVersion":"v1.0",
          "createdAt":"2025-01-01T10:00:00","updatedAt":"2025-01-01T10:00:00",
          "answers":{},"properties":{},
          "collections":[{
            "uuid":"00000001-eeee-eeee-eeee-000000000004",
            "createdAt":"2025-01-01T10:00:00","updatedAt":"2025-01-01T10:00:00",
            "name":"PLAN_AGREEMENTS",
            "items":[
              {"uuid":"00000001-dddd-dddd-dddd-000000000001",
               "createdAt":"2025-01-01T10:00:00","updatedAt":"2025-01-01T10:00:00",
               "answers":{},
               "properties":{"status":{"type":"Single","value":"DRAFT"}},
               "collections":[]},
              {"uuid":"00000001-dddd-dddd-dddd-000000000002",
               "createdAt":"2025-06-01T10:00:00","updatedAt":"2025-06-01T10:00:00",
               "answers":{},
               "properties":{"status":{"type":"Single","value":"AGREED"}},
               "collections":[]}
            ]
          }],
          "collaborators":[],
          "identifiers":{"CRN":"X333333"},
          "flags":[]
        }}]}
        """.trimIndent(),
      )

      webTestClient.get()
        .uri("/sentence-plan/X333333")
        .headers(setAuthorisation(roles = listOf("ROLE_ASSESSMENT_VIEW_DELIUS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.planStatus").isEqualTo("AGREED")
    }
  }

  private fun minimalAssessmentResponse(crn: String): String = """
    {"queries":[{"result":{
      "assessmentUuid":"00000001-1111-1111-1111-000000000001",
      "aggregateUuid":"00000001-2222-2222-2222-000000000002",
      "assessmentType":"SENTENCE_PLAN","formVersion":"v1.0",
      "createdAt":"2025-01-01T10:00:00","updatedAt":"2025-01-01T10:00:00",
      "answers":{},"properties":{},"collections":[],
      "collaborators":[],
      "identifiers":{"CRN":"$crn"},
      "flags":[]
    }}]}
  """.trimIndent()

  private fun fullyPopulatedAssessmentResponse(crn: String): String = """
    {"queries":[{"result":{
      "assessmentUuid":"00000001-1111-1111-1111-000000000001",
      "aggregateUuid":"00000001-2222-2222-2222-000000000002",
      "assessmentType":"SENTENCE_PLAN","formVersion":"v1.0",
      "createdAt":"2025-06-01T12:00:00","updatedAt":"2025-06-01T12:00:00",
      "answers":{},"properties":{},
      "collections":[
        {
          "uuid":"00000001-eeee-eeee-eeee-000000000001",
          "createdAt":"2025-06-01T12:00:00","updatedAt":"2025-06-01T12:00:00",
          "name":"GOALS",
          "items":[{
            "uuid":"00000001-aaaa-aaaa-aaaa-000000000001",
            "createdAt":"2025-06-01T12:00:00","updatedAt":"2025-06-01T12:00:00",
            "answers":{
              "title":{"type":"Single","value":"Find stable accommodation"},
              "area_of_need":{"type":"Single","value":"accommodation"},
              "related_areas_of_need":{"type":"Multi","values":["finances"]},
              "target_date":{"type":"Single","value":"2025-12-31"}
            },
            "properties":{
              "status":{"type":"Single","value":"ACTIVE"},
              "status_date":{"type":"Single","value":"2025-06-01T12:00:00Z"}
            },
            "collections":[{
              "uuid":"00000001-eeee-eeee-eeee-000000000002",
              "createdAt":"2025-06-01T12:00:00","updatedAt":"2025-06-01T12:00:00",
              "name":"STEPS",
              "items":[{
                "uuid":"00000001-bbbb-bbbb-bbbb-000000000001",
                "createdAt":"2025-06-01T12:00:00","updatedAt":"2025-06-01T12:00:00",
                "answers":{
                  "description":{"type":"Single","value":"Contact housing provider"},
                  "actor":{"type":"Single","value":"probation_practitioner"},
                  "status":{"type":"Single","value":"NOT_STARTED"}
                },
                "properties":{
                  "status_date":{"type":"Single","value":"2025-06-01T12:00:00Z"}
                },
                "collections":[]
              }]
            }]
          }]
        },
        {
          "uuid":"00000001-eeee-eeee-eeee-000000000004",
          "createdAt":"2025-06-01T12:00:00","updatedAt":"2025-06-01T12:00:00",
          "name":"PLAN_AGREEMENTS",
          "items":[{
            "uuid":"00000001-dddd-dddd-dddd-000000000001",
            "createdAt":"2025-06-01T12:00:00","updatedAt":"2025-06-01T12:00:00",
            "answers":{},
            "properties":{"status":{"type":"Single","value":"AGREED"}},
            "collections":[]
          }]
        }
      ],
      "collaborators":[],
      "identifiers":{"CRN":"$crn","NOMIS_ID":"A7779DY"},
      "flags":[]
    }}]}
  """.trimIndent()
}
