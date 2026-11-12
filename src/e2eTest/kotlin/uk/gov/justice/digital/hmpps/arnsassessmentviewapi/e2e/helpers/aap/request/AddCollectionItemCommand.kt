package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.aap.request

import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.UserDetails
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.Value
import java.util.UUID

data class AddCollectionItemCommand(
  val name: String,
  val assessmentUuid: UUID,
  val collectionUuid: UUID,
  val user: UserDetails,
  val identifiers: Map<String, String>,
  val flags: List<String>,
  val properties: Map<String, Value>,
  val answers: Map<String, Value>,
) : Command
