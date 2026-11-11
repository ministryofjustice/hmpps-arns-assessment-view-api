import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
open class OasysVersionedEntityResponse(
  open var sanAssessmentId: UUID = UUID(0, 0),
  open var sanAssessmentVersion: Long = 0,
  open var sentencePlanId: UUID = UUID(0, 0),
  open var sentencePlanVersion: Long = 0,
) {
  @JsonIgnore
  var aapPlanVersionedEntity: VersionedEntity? = null
    private set

  open fun addVersionedEntity(versionedEntity: VersionedEntity) {
    when (versionedEntity.entityType) {
      EntityType.PLAN, EntityType.AAP_PLAN -> {
        this.sentencePlanId = versionedEntity.id
        this.sentencePlanVersion = versionedEntity.version
        if (versionedEntity.entityType == EntityType.AAP_PLAN) {
          this.aapPlanVersionedEntity = versionedEntity
        }
      }
      EntityType.ASSESSMENT, EntityType.AAP_SAN -> {
        this.sanAssessmentId = versionedEntity.id
        this.sanAssessmentVersion = versionedEntity.version
      }
    }
  }
}

data class VersionedEntity(
  val id: UUID,
  val version: Long,
  val entityType: EntityType,
  val deleted: Boolean = false,
  val updatedAt: LocalDateTime? = null,
  val createdBy: OasysEvent? = null,
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

enum class EntityType {
  ASSESSMENT,
  PLAN,
  AAP_PLAN,
  AAP_SAN,
}

