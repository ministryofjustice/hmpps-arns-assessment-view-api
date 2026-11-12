package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.oasys

import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class OasysCreateRequest(
  @field:Size(min = Constraints.OASYS_PK_MIN_LENGTH, max = Constraints.OASYS_PK_MAX_LENGTH)
  @field:Pattern(regexp = "\\d+", message = "Must only contain numeric characters")
  val oasysAssessmentPk: String,

  @field:Size(min = Constraints.OASYS_PK_MIN_LENGTH, max = Constraints.OASYS_PK_MAX_LENGTH)
  @field:Pattern(regexp = "\\d+", message = "Must only contain numeric characters")
  val previousOasysSanPk: String? = null,

  @field:Size(min = Constraints.OASYS_PK_MIN_LENGTH, max = Constraints.OASYS_PK_MAX_LENGTH)
  @field:Pattern(regexp = "\\d+", message = "Must only contain numeric characters")
  val previousOasysSpPk: String? = null,

  @field:Size(max = Constraints.REGION_PRISON_CODE_MAX_LENGTH)
  val regionPrisonCode: String? = null,

  val planType: PlanType,

  val assessmentType: AssessmentType,

  @field:Valid
  val userDetails: OasysUserDetails,

  @field:Valid
  val subjectDetails: SubjectDetails? = null,

  @field:Pattern(regexp = "^[YN]$", message = "Must be 'Y' or 'N'")
  val newPeriodOfSupervision: String? = null,

  val sanAssessmentType: SanAssessmentType? = null,

  val oasysData: Map<String, Any?>? = null,
)

class Constraints {
  companion object {
    const val OASYS_PK_MAX_LENGTH = 15
    const val OASYS_PK_MIN_LENGTH = 1
    const val REGION_PRISON_CODE_MAX_LENGTH = 15
    const val OASYS_USER_ID_MAX_LENGTH = 30
    const val OASYS_USER_NAME_MAX_LENGTH = 64
  }
}

enum class PlanType {
  INITIAL,
  REVIEW,
  UPW,
  PSR_OUTLINE,
}

enum class AssessmentType {
  SAN_SP,
  SP,
}

data class OasysUserDetails(
  @field:Size(max = Constraints.OASYS_USER_ID_MAX_LENGTH)
  val id: String = "",

  @field:Size(max = Constraints.OASYS_USER_NAME_MAX_LENGTH)
  val name: String = "",
)

data class SubjectDetails(
  val crn: String? = null,
  val nomisId: String? = null,
)

enum class SanAssessmentType {
  COMMUNITY,
  CUSTODY,
}
