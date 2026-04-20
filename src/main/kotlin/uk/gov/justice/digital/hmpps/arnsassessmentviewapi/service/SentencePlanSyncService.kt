package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.AapApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.CoordinatorApiClient
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.config.AppProperties
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository.SentencePlanRepository

@Service
class SentencePlanSyncService(
  private val aapApiClient: AapApiClient,
  private val coordinatorApiClient: CoordinatorApiClient,
  private val sentencePlanRepository: SentencePlanRepository,
  private val appProperties: AppProperties,
) {
  fun sync() {
    log.info("Sentence plan sync triggered (since-hours={})", appProperties.sync.sinceHours)
    // TODO: mapping + persistence logic
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
