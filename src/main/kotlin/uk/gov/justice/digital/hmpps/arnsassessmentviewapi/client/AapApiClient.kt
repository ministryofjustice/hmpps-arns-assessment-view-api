package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.GetAssessmentsModifiedSinceQuery
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.GetAssessmentsModifiedSinceResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.QueriesRequest
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.QueriesResponse
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.UserDetails
import java.time.LocalDateTime

@Component
class AapApiClient(
  private val aapApiWebClient: WebClient,
) {
  fun queryModifiedSince(
    assessmentType: String,
    since: LocalDateTime,
    pageNumber: Int = 0,
    pageSize: Int = 50,
  ): GetAssessmentsModifiedSinceResult {
    log.info("Querying AAP for {} modified since {} (page {})", assessmentType, since, pageNumber)
    val request = QueriesRequest(
      queries = listOf(
        GetAssessmentsModifiedSinceQuery(
          user = SYNC_USER,
          assessmentType = assessmentType,
          since = since,
          pageNumber = pageNumber,
          pageSize = pageSize,
        ),
      ),
    )
    val response = aapApiWebClient.post()
      .uri("/query")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .retrieve()
      .bodyToMono<QueriesResponse>()
      .block()
      ?: error("Null response from AAP /query")
    return response.queries.first().result
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val SYNC_USER = UserDetails(id = "view-api-sync", name = "View API Sync")
  }
}
