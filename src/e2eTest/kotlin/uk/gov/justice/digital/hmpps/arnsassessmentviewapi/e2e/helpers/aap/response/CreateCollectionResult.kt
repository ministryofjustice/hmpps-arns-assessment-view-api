package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.aap.response

import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.oasys.UserDetails
import java.util.UUID

data class CreateCollectionResult(
  val commands: List<CreateCollectionCommandResponse>,
)

data class CreateCollectionCommandResponse(
  val request: CreateCollectionRequest,
  val result: CreateCollectionCommandResult,
)

data class CreateCollectionRequest(
  val type: String,
  val formVersion: String? = null,
  val timeline: Timeline? = null,
  val user: UserDetails,
)

data class CreateCollectionCommandResult(
  val type: String,
  val collectionUuid: UUID,
  val message: String,
  val success: Boolean,
)

data class Timeline(
  val type: String,
  val data: Map<String, Any> = emptyMap(),
)
