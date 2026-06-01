package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.AapUser
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.AssessmentVersionQueryResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.Collection
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.CollectionItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.IdentifierType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.MultiValue
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.SingleValue
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.Value
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.ActorType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.CriminogenicNeed
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.StepStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class AssessmentVersionToResponseMapperTest {

  private val mapper = AssessmentVersionToResponseMapper()
  private val baseTime: LocalDateTime = LocalDateTime.parse("2025-06-01T12:00:00")

  @Test
  fun `maps identifiers, latest agreement status, and ordered goals`() {
    val result = mapper.toResponse(
      assessment(
        identifiers = mapOf(IdentifierType.CRN to "X123456", IdentifierType.NOMIS_ID to "A7779DY"),
        collections = listOf(
          agreementsCollection(
            agreementItem(createdAt = baseTime.minusDays(2), status = "DRAFT"),
            agreementItem(createdAt = baseTime, status = "AGREED"),
          ),
          goalsCollection(
            goalItem(title = "First goal", areaOfNeed = "accommodation", status = "ACTIVE"),
            goalItem(title = "Second goal", areaOfNeed = "finances", status = "FUTURE"),
          ),
        ),
      ),
    )

    assertThat(result.crn).isEqualTo("X123456")
    assertThat(result.nomis).isEqualTo("A7779DY")
    assertThat(result.planStatus).isEqualTo(PlanStatus.AGREED)
    assertThat(result.goals).hasSize(2)
    assertThat(result.goals[0].areaOfNeed).isEqualTo(CriminogenicNeed.ACCOMMODATION)
    assertThat(result.goals[1].areaOfNeed).isEqualTo(CriminogenicNeed.FINANCES)
  }

  @Test
  fun `goal carries title, target date, related areas, and steps`() {
    val title = "Find stable accommodation"
    val result = mapper.toResponse(
      assessment(
        identifiers = mapOf(IdentifierType.CRN to "X123456"),
        collections = listOf(
          goalsCollection(
            goalItem(
              title = title,
              areaOfNeed = "accommodation",
              status = "ACTIVE",
              targetDate = "2025-12-31",
              relatedAreas = listOf("finances", "drug-use"),
              steps = listOf(
                stepItem(
                  description = "Contact housing provider",
                  actor = "probation_practitioner",
                  status = "NOT_STARTED",
                  statusDate = "2025-06-01T12:00:00Z",
                ),
              ),
            ),
          ),
        ),
      ),
    )

    val goal = result.goals.single()
    assertThat(goal.goalTitle).isEqualTo(title)
    assertThat(goal.targetDate).isEqualTo(LocalDate.of(2025, 12, 31))
    assertThat(goal.goalStatus).isEqualTo(GoalStatus.ACTIVE)
    assertThat(goal.relatedAreasOfNeed).containsExactly(CriminogenicNeed.FINANCES, CriminogenicNeed.DRUG_USE)
    val step = goal.steps.single()
    assertThat(step.description).isEqualTo("Contact housing provider")
    assertThat(step.actor).isEqualTo(ActorType.PROBATION_PRACTITIONER)
    assertThat(step.status).isEqualTo(StepStatus.NOT_STARTED)
    assertThat(step.statusDate).isEqualTo(Instant.parse("2025-06-01T12:00:00Z"))
  }

  @Test
  fun `no agreements - planStatus is null`() {
    val result = mapper.toResponse(
      assessment(
        identifiers = mapOf(IdentifierType.CRN to "X999999"),
        collections = listOf(goalsCollection()),
      ),
    )
    assertThat(result.planStatus).isNull()
    assertThat(result.goals).isEmpty()
  }

  @Test
  fun `missing NOMIS identifier serialises as null`() {
    val result = mapper.toResponse(
      assessment(identifiers = mapOf(IdentifierType.CRN to "X111111")),
    )
    assertThat(result.crn).isEqualTo("X111111")
    assertThat(result.nomis).isNull()
  }

  @Test
  fun `goal with missing target_date returns null targetDate`() {
    val result = mapper.toResponse(
      assessment(
        identifiers = mapOf(IdentifierType.CRN to "X111111"),
        collections = listOf(
          goalsCollection(
            goalItem(title = "g", areaOfNeed = "accommodation", status = "ACTIVE", targetDate = null),
          ),
        ),
      ),
    )
    assertThat(result.goals.single().targetDate).isNull()
  }

  @Test
  fun `goal with unknown area_of_need is dropped`() {
    val result = mapper.toResponse(
      assessment(
        identifiers = mapOf(IdentifierType.CRN to "X111111"),
        collections = listOf(
          goalsCollection(
            goalItem(title = "valid", areaOfNeed = "accommodation", status = "ACTIVE"),
            goalItem(title = "invalid", areaOfNeed = "made-up-area", status = "ACTIVE"),
          ),
        ),
      ),
    )
    assertThat(result.goals).hasSize(1)
    assertThat(result.goals.single().areaOfNeed).isEqualTo(CriminogenicNeed.ACCOMMODATION)
  }

  @Test
  fun `goal with unknown status is dropped`() {
    val result = mapper.toResponse(
      assessment(
        identifiers = mapOf(IdentifierType.CRN to "X111111"),
        collections = listOf(
          goalsCollection(
            goalItem(title = "g", areaOfNeed = "accommodation", status = "BOGUS"),
          ),
        ),
      ),
    )
    assertThat(result.goals).isEmpty()
  }

  @Test
  fun `unknown related area is dropped but goal still maps`() {
    val result = mapper.toResponse(
      assessment(
        identifiers = mapOf(IdentifierType.CRN to "X111111"),
        collections = listOf(
          goalsCollection(
            goalItem(
              title = "g",
              areaOfNeed = "accommodation",
              status = "ACTIVE",
              relatedAreas = listOf("finances", "not-a-real-area"),
            ),
          ),
        ),
      ),
    )
    assertThat(result.goals.single().relatedAreasOfNeed).containsExactly(CriminogenicNeed.FINANCES)
  }

  @Test
  fun `step with unknown actor is dropped but goal still maps`() {
    val result = mapper.toResponse(
      assessment(
        identifiers = mapOf(IdentifierType.CRN to "X111111"),
        collections = listOf(
          goalsCollection(
            goalItem(
              title = "g",
              areaOfNeed = "accommodation",
              status = "ACTIVE",
              steps = listOf(
                stepItem(description = "valid", actor = "probation_practitioner", status = "NOT_STARTED"),
                stepItem(description = "broken", actor = "ceo", status = "NOT_STARTED"),
              ),
            ),
          ),
        ),
      ),
    )
    val goal = result.goals.single()
    assertThat(goal.steps).hasSize(1)
    assertThat(goal.steps.single().description).isEqualTo("valid")
  }

  private fun assessment(
    identifiers: Map<IdentifierType, String>,
    collections: List<Collection> = emptyList(),
  ): AssessmentVersionQueryResult = AssessmentVersionQueryResult(
    assessmentUuid = UUID.randomUUID(),
    aggregateUuid = UUID.randomUUID(),
    assessmentType = "SENTENCE_PLAN",
    formVersion = "v1.0",
    createdAt = baseTime,
    updatedAt = baseTime,
    answers = emptyMap(),
    properties = emptyMap(),
    collections = collections,
    collaborators = emptySet<AapUser>(),
    identifiers = identifiers,
    assignedUser = null,
    flags = emptyList(),
  )

  private fun agreementsCollection(vararg items: CollectionItem): Collection = Collection(
    uuid = UUID.randomUUID(),
    createdAt = baseTime,
    updatedAt = baseTime,
    name = "PLAN_AGREEMENTS",
    items = items.toList(),
  )

  private fun agreementItem(createdAt: LocalDateTime, status: String): CollectionItem = CollectionItem(
    uuid = UUID.randomUUID(),
    createdAt = createdAt,
    updatedAt = createdAt,
    answers = emptyMap(),
    properties = mapOf("status" to SingleValue(status)),
    collections = emptyList(),
  )

  private fun goalsCollection(vararg items: CollectionItem): Collection = Collection(
    uuid = UUID.randomUUID(),
    createdAt = baseTime,
    updatedAt = baseTime,
    name = "GOALS",
    items = items.toList(),
  )

  private fun goalItem(
    title: String,
    areaOfNeed: String,
    status: String,
    targetDate: String? = null,
    relatedAreas: List<String> = emptyList(),
    steps: List<CollectionItem> = emptyList(),
  ): CollectionItem {
    val answers = buildMap<String, Value> {
      put("title", SingleValue(title))
      put("area_of_need", SingleValue(areaOfNeed))
      if (relatedAreas.isNotEmpty()) put("related_areas_of_need", MultiValue(relatedAreas))
      if (targetDate != null) put("target_date", SingleValue(targetDate))
    }
    val stepsCollection = if (steps.isNotEmpty()) {
      listOf(
        Collection(
          uuid = UUID.randomUUID(),
          createdAt = baseTime,
          updatedAt = baseTime,
          name = "STEPS",
          items = steps,
        ),
      )
    } else {
      emptyList()
    }
    return CollectionItem(
      uuid = UUID.randomUUID(),
      createdAt = baseTime,
      updatedAt = baseTime,
      answers = answers,
      properties = mapOf("status" to SingleValue(status)),
      collections = stepsCollection,
    )
  }

  private fun stepItem(
    description: String,
    actor: String,
    status: String,
    statusDate: String? = null,
  ): CollectionItem {
    val properties = buildMap<String, Value> {
      if (statusDate != null) put("status_date", SingleValue(statusDate))
    }
    return CollectionItem(
      uuid = UUID.randomUUID(),
      createdAt = baseTime,
      updatedAt = baseTime,
      answers = mapOf(
        "description" to SingleValue(description),
        "actor" to SingleValue(actor),
        "status" to SingleValue(status),
      ),
      properties = properties,
      collections = emptyList(),
    )
  }
}
