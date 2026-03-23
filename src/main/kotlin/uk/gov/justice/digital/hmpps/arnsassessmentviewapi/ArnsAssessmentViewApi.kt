package uk.gov.justice.digital.hmpps.arnsassessmentviewapi

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

@SpringBootApplication
class ArnsAssessmentViewApi {
  private val log = LoggerFactory.getLogger(this::class.java)

  @Bean
  fun logStartupMode(environment: Environment) = ApplicationRunner {
    val profiles = environment.activeProfiles.toSet()
    val mode = when {
      "sync" in profiles -> "sync"
      "api" in profiles -> "api"
      else -> "unknown"
    }
    log.info("Starting in $mode mode (active profiles: ${profiles.joinToString()})")
  }
}

fun main(args: Array<String>) {
  runApplication<ArnsAssessmentViewApi>(*args)
}
