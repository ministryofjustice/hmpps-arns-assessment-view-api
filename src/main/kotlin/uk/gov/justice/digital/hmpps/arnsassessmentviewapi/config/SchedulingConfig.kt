package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service.SentencePlanSyncService
import java.time.Duration

@Configuration
class SchedulingConfig(
  private val appProperties: AppProperties,
  private val sentencePlanSyncService: SentencePlanSyncService,
) : SchedulingConfigurer {
  override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
    if (!appProperties.sync.enabled) {
      log.info("Sentence plan sync scheduler is DISABLED (app.sync.enabled=false)")
      return
    }
    val interval = Duration.ofMinutes(appProperties.sync.intervalMinutes)
    log.info("Sentence plan sync scheduler is ENABLED (interval={})", interval)
    taskRegistrar.addFixedDelayTask(sentencePlanSyncService::sync, interval)
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
