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
  fun getSentencePlans(identifierType: IdentifierType, identifierValue: String): List<SentencePlanResponse> {
    log.info("Fetching sentence plans for {}/{}", identifierType, identifierValue)
    val plans = sentencePlanRepository.findByIdentifier(identifierType, identifierValue)
    if (plans.isEmpty()) {
      log.info("No sentence plans found for {}/{}", identifierType, identifierValue)
      throw SentencePlanNotFoundException(identifierType, identifierValue)
    }
    log.info("Found {} sentence plan(s) for {}/{}", plans.size, identifierType, identifierValue)
    return plans.map { SentencePlanResponse.from(it) }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

class SentencePlanNotFoundException(
  identifierType: IdentifierType,
  identifierValue: String,
) : RuntimeException("No sentence plans found for $identifierType/$identifierValue")
