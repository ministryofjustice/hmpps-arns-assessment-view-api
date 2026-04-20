package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
  val services: Services,
  val sync: Sync,
) {
  data class Services(
    val hmppsAuth: Endpoint,
    val coordinatorApi: Endpoint,
    val aapApi: Endpoint,
  ) {
    data class Endpoint(val baseUrl: String)
  }

  data class Sync(
    val enabled: Boolean,
    val intervalMinutes: Long,
    val sinceHours: Int,
  )
}
