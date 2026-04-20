@file:Suppress("ktlint:standard:filename")

package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.health

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.health.HealthPingCheck

// HMPPS Auth health ping is required if your service calls HMPPS Auth to get a token to call other services
@Component("hmppsAuth")
class HmppsAuthHealthPing(hmppsAuthHealthWebClient: WebClient) : HealthPingCheck(hmppsAuthHealthWebClient)
