package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.healthWebClient
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @param:Value("\${hmpps-auth.url}") val hmppsAuthBaseUri: String,
  @param:Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @param:Value("\${api.timeout:20s}") val timeout: Duration,
) {
  @Bean
  fun hmppsAuthHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(hmppsAuthBaseUri, healthTimeout)

  // The hmpps-kotlin autoconfigure only provides this bean for servlet web apps.
  // The sync profile sets web-application-type=none, so we must provide it explicitly.
  @Bean
  @Profile("sync")
  fun authorizedClientManager(clientRegistrationRepository: ClientRegistrationRepository): OAuth2AuthorizedClientManager {
    val authorizedClientService = InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository)
    return AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientService).apply {
      setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build())
    }
  }

  @Bean
  @Profile("sync")
  fun coordinatorApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
    @Value("\${app.services.coordinator-api.base-url}") coordinatorApiBaseUrl: String,
  ): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "coordinator-api", url = coordinatorApiBaseUrl, timeout)

  @Bean
  @Profile("sync")
  fun aapApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
    @Value("\${app.services.aap-api.base-url}") aapApiBaseUrl: String,
  ): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "aap-api", url = aapApiBaseUrl, timeout)
}
