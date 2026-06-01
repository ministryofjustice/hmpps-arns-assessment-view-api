package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.AapApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.UserDetails
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.controller.response.SentencePlanResponse

@Service
class SentencePlanService(
  private val aapApiClient: AapApiClient,
  private val mapper: AssessmentVersionToResponseMapper,
) {
  fun getSentencePlan(crn: String, authentication: Authentication): SentencePlanResponse {
    log.info("Fetching sentence plan from AAP by CRN")
    val user = UserDetails(id = authentication.name, name = authentication.name)
    val assessment = aapApiClient.queryAssessmentByCrn(crn, SENTENCE_PLAN_ASSESSMENT_TYPE, user)
      ?: throw SentencePlanNotFoundException()
    return mapper.toResponse(assessment)
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

class SentencePlanNotFoundException : RuntimeException("No sentence plan found for the given CRN")
