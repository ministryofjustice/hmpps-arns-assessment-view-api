package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.EntityAssociationDetails
import java.util.UUID

@Component
class CoordinatorApiClient(
  private val coordinatorApiWebClient: WebClient,
) {
  fun getLatestAssociationDetails(entityUuids: List<UUID>): Map<UUID, EntityAssociationDetails> {
    if (entityUuids.isEmpty()) return emptyMap()
    log.info("Fetching association details for {} entities", entityUuids.size)
    return coordinatorApiWebClient.post()
      .uri("/entity/associations")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(entityUuids)
      .retrieve()
      .bodyToMono<Map<UUID, EntityAssociationDetails>>()
      .block() ?: emptyMap()
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
