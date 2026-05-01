package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.controller.response.SentencePlanResponse
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.IdentifierType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository.SentencePlanRepository

@Service
class SentencePlanService(
  private val sentencePlanRepository: SentencePlanRepository,
) {
  fun getSentencePlans(crn: String): List<SentencePlanResponse> {
    log.info("Fetching sentence plans for CRN {}", crn)
    val plans = sentencePlanRepository.findByIdentifier(IdentifierType.CRN, crn)
    if (plans.isEmpty()) {
      log.info("No sentence plans found for CRN {}", crn)
      throw SentencePlanNotFoundException(crn)
    }
    log.info("Found {} sentence plan(s) for CRN {}", plans.size, crn)
    return plans.map { SentencePlanResponse.from(it) }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

class SentencePlanNotFoundException(crn: String) : RuntimeException("No sentence plans found for CRN $crn")
