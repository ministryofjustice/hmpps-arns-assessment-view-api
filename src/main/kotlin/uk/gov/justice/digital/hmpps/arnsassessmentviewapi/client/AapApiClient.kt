package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
@Profile("sync")
class AapApiClient(
  @Qualifier("aapApiWebClient") private val webClient: WebClient,
)
