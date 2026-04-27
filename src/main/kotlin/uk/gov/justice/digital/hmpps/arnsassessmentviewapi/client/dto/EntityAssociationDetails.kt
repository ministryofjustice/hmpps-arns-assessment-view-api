package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto

data class EntityAssociationDetails(
  val oasysAssessmentPk: String,
  val regionPrisonCode: String?,
  val baseVersion: Long,
  val deleted: Boolean = false,
)
