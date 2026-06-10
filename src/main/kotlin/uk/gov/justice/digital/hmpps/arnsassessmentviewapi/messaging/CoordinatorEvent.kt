package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.messaging

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDateTime
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class CoordinatorEvent(
  val eventType: EventType,
  val entityType: String,
  val entityUuid: UUID,
  val occurredAt: LocalDateTime,
  @JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
    property = "eventType",
  )
  @JsonSubTypes(
    JsonSubTypes.Type(value = VersionPayload::class, name = "OASYS_VERSION_EVENT"),
    JsonSubTypes.Type(value = DeleteFlagUpdatePayload::class, name = "OASYS_DELETE_FLAG_UPDATE_EVENT"),
  )
  val message: EventPayload,
)

enum class EventType {
  OASYS_VERSION_EVENT,
  OASYS_DELETE_FLAG_UPDATE_EVENT,
}

sealed class EventPayload

@JsonIgnoreProperties(ignoreUnknown = true)
data class VersionPayload(
  val version: Long,
  val oasysEvent: OasysEvent,
  val incrementedAt: LocalDateTime,
  val deleted: Boolean,
  val association: AssociationPayload,
) : EventPayload()

@JsonIgnoreProperties(ignoreUnknown = true)
data class DeleteFlagUpdatePayload(
  val deleted: Boolean,
  val versionFrom: Long,
  val versionTo: Long?,
) : EventPayload()

@JsonIgnoreProperties(ignoreUnknown = true)
data class AssociationPayload(
  val oasysAssessmentPk: String,
  val regionPrisonCode: String?,
  val baseVersion: Long,
)

enum class OasysEvent {
  AWAITING_COUNTERSIGN,
  AWAITING_DOUBLE_COUNTERSIGN,
  CLONED,
  COUNTERSIGNED,
  CREATED,
  DOUBLE_COUNTERSIGNED,
  LOCKED,
  REJECTED,
  ROLLED_BACK,
  SELF_SIGNED,
}
