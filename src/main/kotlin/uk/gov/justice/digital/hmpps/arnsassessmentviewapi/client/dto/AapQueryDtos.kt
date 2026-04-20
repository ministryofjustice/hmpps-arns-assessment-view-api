package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.LocalDateTime
import java.util.UUID

data class UserDetails(
  val id: String,
  val name: String,
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
sealed interface RequestableQuery {
  val user: UserDetails
}

@JsonTypeName("GetAssessmentsModifiedSinceQuery")
data class GetAssessmentsModifiedSinceQuery(
  override val user: UserDetails,
  val assessmentType: String,
  val since: LocalDateTime,
  val pageNumber: Int = 0,
  val pageSize: Int = 50,
) : RequestableQuery

data class QueriesRequest(val queries: List<RequestableQuery>)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  JsonSubTypes.Type(value = SingleValue::class, name = "Single"),
  JsonSubTypes.Type(value = MultiValue::class, name = "Multi"),
)
sealed interface Value

data class SingleValue(val value: String) : Value
data class MultiValue(val values: List<String>) : Value

data class AapUser(
  val id: UUID,
  val name: String,
)

enum class IdentifierType {
  CRN,
  PRN,
  NOMIS_ID,
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CollectionItem(
  val uuid: UUID,
  val createdAt: LocalDateTime,
  val updatedAt: LocalDateTime,
  val answers: Map<String, Value>,
  val properties: Map<String, Value>,
  val collections: List<Collection>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Collection(
  val uuid: UUID,
  val createdAt: LocalDateTime,
  val updatedAt: LocalDateTime,
  val name: String,
  val items: List<CollectionItem>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AssessmentVersionQueryResult(
  val assessmentUuid: UUID,
  val aggregateUuid: UUID,
  val assessmentType: String,
  val formVersion: String,
  val createdAt: LocalDateTime,
  val updatedAt: LocalDateTime,
  val answers: Map<String, Value>,
  val properties: Map<String, Value>,
  val collections: List<Collection>,
  val collaborators: Set<AapUser>,
  val identifiers: Map<IdentifierType, String>,
  val assignedUser: AapUser?,
  val flags: List<String> = emptyList(),
)

data class PageInfo(val pageNumber: Int, val totalPages: Int)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GetAssessmentsModifiedSinceResult(
  val assessments: List<AssessmentVersionQueryResult>,
  val pageInfo: PageInfo,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QueryResponse(
  val result: GetAssessmentsModifiedSinceResult,
)

data class QueriesResponse(val queries: List<QueryResponse>)
