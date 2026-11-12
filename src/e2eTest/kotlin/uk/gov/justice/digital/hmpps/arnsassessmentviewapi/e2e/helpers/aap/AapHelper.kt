package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.aap

import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.MultiValue
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.SingleValue
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.UserDetails
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.aap.request.AddCollectionItemCommand
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.aap.request.CommandsRequest
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.aap.request.CreateCollectionCommand
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.aap.response.CreateCollectionItemResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.aap.response.CreateCollectionResult
import java.util.UUID

object AapHelper {
  fun createGoal(aapTestClient: WebTestClient, assessmentUuid: UUID, crn: String): UUID? {
    val collectionRequest = CreateCollectionCommand(
      assessmentUuid = assessmentUuid,
      user = UserDetails("test-user", "Test User"),
      name = "GOALS",
      identifiers = mapOf("CRN" to crn),
      flags = listOf("SAN_BETA"),
    )

    val goalCollection = CommandsRequest(commands = listOf(collectionRequest))

    val getCollectionResponse = aapTestClient.post().uri("/command")
      .bodyValue(goalCollection)
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody<CreateCollectionResult>()
      .returnResult().responseBody

    val itemRequest = AddCollectionItemCommand(
      name = "GOALS",
      assessmentUuid = assessmentUuid,
      collectionUuid = getCollectionResponse?.commands?.firstOrNull()?.result?.collectionUuid!!,
      user = UserDetails("test-user", "Test User"),
      identifiers = mapOf("CRN" to crn),
      flags = listOf("SAN_BETA"),
      properties = mapOf(
        "status" to SingleValue("ACTIVE"),
        "status_date" to SingleValue("2026-02-23T17:30:11.179Z"),
      ),
      answers = mapOf(
        "title" to SingleValue("I will work towards finding accommodation, so that I am no longer homeless"),
        "target_date" to SingleValue("2026-05-23T16:30:11.151Z"),
        "area_of_need" to SingleValue("accommodation"),
        "related_areas_of_need" to MultiValue(listOf("alcohol-use")),
      ),
    )

    val goal = CommandsRequest(commands = listOf(itemRequest))

    val getGoalResponse = aapTestClient.post().uri("/command")
      .bodyValue(goal)
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody<CreateCollectionItemResult>()
      .returnResult().responseBody

    return getGoalResponse?.commands?.get(0)?.result?.collectionItemUuid
  }
}
