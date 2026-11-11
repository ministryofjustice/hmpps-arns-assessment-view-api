package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.aap.request

import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.oasys.UserDetails
import java.util.UUID

data class CreateCollectionCommand(
  val name: String,
  val assessmentUuid: UUID,
  val user: UserDetails,
  val identifiers: Map<String, String>,
  val flags: List<String>,
) : Command
