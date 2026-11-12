package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.controller.response.SentencePlanResponse
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.aap.AapHelper
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.oasys.OasysHelper
import java.util.UUID
import kotlin.random.Random

@DisplayName("Sentence Plan API Tests")
class SentencePlanApiTest : IntegrationTestBase() {
  private lateinit var sentencePlanId: UUID

  val crn = Random.nextInt(0, 100000).toString().padStart(5, '0')
  val oasysPk = Random.nextInt(0, 1_000_000_000).toString()

  @BeforeAll
  fun beforeAll() {
    val createOasysResponse = OasysHelper.create(coordinatorTestClient, crn, oasysPk)

    assertThat(createOasysResponse?.sentencePlanId).isNotNull

    sentencePlanId = createOasysResponse?.sentencePlanId!!

    AapHelper.createGoal(aapTestClient, sentencePlanId, crn)!!
  }

  @Test
  fun `get sentence plan by crn`() {
    val planResponse = webTestClient.get().uri("/sentence-plan/$crn")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody<SentencePlanResponse>()
      .returnResult().responseBody

    assertThat(planResponse?.crn).isEqualTo(crn)
    assertThat(planResponse?.goals[0]?.areaOfNeed.toString()).isEqualTo("ACCOMMODATION")
  }
}
