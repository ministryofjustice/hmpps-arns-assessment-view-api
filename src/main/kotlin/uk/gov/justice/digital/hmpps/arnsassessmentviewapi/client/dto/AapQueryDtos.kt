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
  val after: UUID? = null,
  val limit: Int = 50,
) : RequestableQuery

@JsonTypeName("TimelineQuery")
data class TimelineQuery(
  override val user: UserDetails,
  val assessmentIdentifier: AssessmentIdentifier,
  val includeEventTypes: Set<String>? = null,
  val includeCustomTypes: Set<String>? = null,
  val pageNumber: Int = 0,
  val pageSize: Int = 50,
) : RequestableQuery

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(JsonSubTypes.Type(value = UuidIdentifier::class, name = "UUID"))
sealed interface AssessmentIdentifier

@JsonTypeName("UUID")
data class UuidIdentifier(val uuid: UUID) : AssessmentIdentifier

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
  val deleted: Boolean = false,
)

data class PageInfo(val pageNumber: Int, val totalPages: Int)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GetAssessmentsModifiedSinceResult(
  val assessments: List<AssessmentVersionQueryResult>,
  val nextCursor: UUID? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QueryResponse<T>(
  val result: T,
)

data class QueriesResponse<T>(val queries: List<QueryResponse<T>>)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TimelineItem(
  val uuid: UUID,
  val timestamp: LocalDateTime,
  val user: TimelineUser,
  val assessment: UUID,
  val event: String?,
  val data: Map<String, Any> = emptyMap(),
  val customType: String? = null,
  val customData: Map<String, Any>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TimelineUser(
  val id: UUID,
  val name: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TimelineQueryResult(
  val timeline: List<TimelineItem>,
  val pageInfo: PageInfo,
)
