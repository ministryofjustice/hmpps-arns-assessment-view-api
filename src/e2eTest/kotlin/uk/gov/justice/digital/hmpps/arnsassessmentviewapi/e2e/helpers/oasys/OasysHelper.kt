package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.oasys

import OasysVersionedEntityResponse
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

object OasysHelper {
  fun create(
    webTestClient: WebTestClient,
    crn: String,
    oasysPk: String,
  ): OasysVersionedEntityResponse? {
    val oasysCreateRequest = OasysCreateRequest(
      oasysAssessmentPk = oasysPk,
      subjectDetails = SubjectDetails(crn = crn),
      planType = PlanType.INITIAL,
      assessmentType = AssessmentType.SAN_SP,
      newPeriodOfSupervision = "N",
      regionPrisonCode = "MDI",
      userDetails = OasysUserDetails(id = "test-user", name = "Test User"),
      previousOasysSpPk = null,
      previousOasysSanPk = null,
      sanAssessmentType = SanAssessmentType.COMMUNITY,
      oasysData = null,
    )

    val createResponse = webTestClient.post().uri("/oasys/create")
      .bodyValue(oasysCreateRequest)
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isCreated
      .expectBody<OasysVersionedEntityResponse>()
      .returnResult().responseBody

    return createResponse
  }
}
