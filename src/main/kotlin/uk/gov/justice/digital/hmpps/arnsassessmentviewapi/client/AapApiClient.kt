package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.GetAssessmentsModifiedSinceQuery
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.GetAssessmentsModifiedSinceResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.GetAssessmentsSoftDeletedSinceQuery
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.GetAssessmentsSoftDeletedSinceResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.QueriesRequest
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.QueriesResponse
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.RequestableQuery
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.TimelineQuery
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.TimelineQueryResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.UserDetails
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.UuidIdentifier
import java.time.LocalDateTime
import java.util.UUID

@Component
class AapApiClient(
  private val aapApiWebClient: WebClient,
) {
  fun queryModifiedSince(
    assessmentType: String,
    since: LocalDateTime,
    after: UUID? = null,
    limit: Int = 50,
  ): GetAssessmentsModifiedSinceResult {
    log.info("Querying AAP for {} modified since {} (after={})", assessmentType, since, after)
    return executeQuery(
      GetAssessmentsModifiedSinceQuery(
        user = SYNC_USER,
        assessmentType = assessmentType,
        since = since,
        after = after,
        limit = limit,
      ),
    )
  }

  fun querySoftDeletedSince(
    assessmentType: String,
    since: LocalDateTime,
  ): List<UUID> {
    log.info("Querying AAP for {} soft-deleted since {}", assessmentType, since)
    val result: GetAssessmentsSoftDeletedSinceResult = executeQuery(
      GetAssessmentsSoftDeletedSinceQuery(user = SYNC_USER, assessmentType = assessmentType, since = since),
    )
    return result.assessments
  }

  fun queryTimeline(
    assessmentUuid: UUID,
    includeEventTypes: Set<String>? = null,
    includeCustomTypes: Set<String>? = null,
    pageNumber: Int = 0,
    pageSize: Int = 50,
  ): TimelineQueryResult = executeQuery(
    TimelineQuery(
      user = SYNC_USER,
      assessmentIdentifier = UuidIdentifier(assessmentUuid),
      includeEventTypes = includeEventTypes,
      includeCustomTypes = includeCustomTypes,
      pageNumber = pageNumber,
      pageSize = pageSize,
    ),
  )

  private inline fun <reified T> executeQuery(query: RequestableQuery): T {
    val response = aapApiWebClient.post()
      .uri("/query")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(QueriesRequest(queries = listOf(query)))
      .retrieve()
      .bodyToMono<QueriesResponse<T>>()
      .block()
      ?: error("Null response from AAP /query")
    return response.queries.first().result
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val SYNC_USER = UserDetails(id = "view-api-sync", name = "View API Sync")
  }
}
