package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures

import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.AssessmentVersionQueryResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.Collection
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.CollectionItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.EntityAssociationDetails
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.GetAssessmentsModifiedSinceResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.IdentifierType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.MultiValue
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.PageInfo
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.SingleValue
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.TimelineItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.TimelineQueryResult
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.TimelineUser
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.Value
import java.time.LocalDateTime
import java.util.UUID

private val DEFAULT_TIME: LocalDateTime = LocalDateTime.of(2026, 1, 1, 10, 0)

fun assessment(
  uuid: UUID = UUID.randomUUID(),
  identifiers: Map<IdentifierType, String> = mapOf(IdentifierType.CRN to "X000001"),
  collections: List<Collection> = emptyList(),
  deleted: Boolean = false,
  createdAt: LocalDateTime = DEFAULT_TIME,
  updatedAt: LocalDateTime = DEFAULT_TIME,
): AssessmentVersionQueryResult = AssessmentVersionQueryResult(
  assessmentUuid = uuid,
  aggregateUuid = UUID.randomUUID(),
  assessmentType = "SENTENCE_PLAN",
  formVersion = "1.0",
  createdAt = createdAt,
  updatedAt = updatedAt,
  answers = emptyMap(),
  properties = emptyMap(),
  collections = collections,
  collaborators = emptySet(),
  identifiers = identifiers,
  assignedUser = null,
  flags = emptyList(),
  deleted = deleted,
)

fun goalsCollection(items: List<CollectionItem>): Collection = collection("GOALS", items)
fun agreementsCollection(items: List<CollectionItem>): Collection = collection("PLAN_AGREEMENTS", items)
fun stepsCollection(items: List<CollectionItem>): Collection = collection("STEPS", items)
fun notesCollection(items: List<CollectionItem>): Collection = collection("NOTES", items)

private fun collection(name: String, items: List<CollectionItem>): Collection = Collection(
  uuid = UUID.randomUUID(),
  createdAt = DEFAULT_TIME,
  updatedAt = DEFAULT_TIME,
  name = name,
  items = items,
)

fun goalItem(
  uuid: UUID = UUID.randomUUID(),
  title: String? = "A goal",
  areaSlug: String? = "accommodation",
  statusKey: String? = "ACTIVE",
  statusDate: String? = null,
  targetDate: String? = null,
  relatedAreas: List<String>? = null,
  singleRelatedArea: String? = null,
  steps: List<CollectionItem> = emptyList(),
  notes: List<CollectionItem> = emptyList(),
  createdAt: LocalDateTime = DEFAULT_TIME,
  updatedAt: LocalDateTime = DEFAULT_TIME,
): CollectionItem {
  val answers = mutableMapOf<String, Value>()
  if (title != null) answers["title"] = SingleValue(title)
  if (areaSlug != null) answers["area_of_need"] = SingleValue(areaSlug)
  if (targetDate != null) answers["target_date"] = SingleValue(targetDate)
  if (relatedAreas != null) answers["related_areas_of_need"] = MultiValue(relatedAreas)
  if (singleRelatedArea != null) answers["related_areas_of_need"] = SingleValue(singleRelatedArea)

  val properties = mutableMapOf<String, Value>()
  if (statusKey != null) properties["status"] = SingleValue(statusKey)
  if (statusDate != null) properties["status_date"] = SingleValue(statusDate)

  val nested = buildList {
    if (steps.isNotEmpty()) add(stepsCollection(steps))
    if (notes.isNotEmpty()) add(notesCollection(notes))
  }

  return CollectionItem(uuid, createdAt, updatedAt, answers, properties, nested)
}

fun agreementItem(
  uuid: UUID = UUID.randomUUID(),
  statusKey: String? = "AGREED",
  statusDate: String? = null,
  detailsNo: String? = null,
  detailsCouldNotAnswer: String? = null,
  notes: String? = null,
  createdAt: LocalDateTime = DEFAULT_TIME,
): CollectionItem {
  val answers = mutableMapOf<String, Value>()
  if (detailsNo != null) answers["details_no"] = SingleValue(detailsNo)
  if (detailsCouldNotAnswer != null) answers["details_could_not_answer"] = SingleValue(detailsCouldNotAnswer)
  if (notes != null) answers["notes"] = SingleValue(notes)

  val properties = mutableMapOf<String, Value>()
  if (statusKey != null) properties["status"] = SingleValue(statusKey)
  if (statusDate != null) properties["status_date"] = SingleValue(statusDate)

  return CollectionItem(uuid, createdAt, createdAt, answers, properties, emptyList())
}

fun stepItem(
  uuid: UUID = UUID.randomUUID(),
  description: String? = "Do the thing",
  actorKey: String? = "probation_practitioner",
  statusKey: String? = "NOT_STARTED",
  statusDate: String? = null,
): CollectionItem {
  val answers = mutableMapOf<String, Value>()
  if (description != null) answers["description"] = SingleValue(description)
  if (actorKey != null) answers["actor"] = SingleValue(actorKey)
  if (statusKey != null) answers["status"] = SingleValue(statusKey)

  val properties = mutableMapOf<String, Value>()
  if (statusDate != null) properties["status_date"] = SingleValue(statusDate)

  return CollectionItem(uuid, DEFAULT_TIME, DEFAULT_TIME, answers, properties, emptyList())
}

fun noteItem(
  uuid: UUID = UUID.randomUUID(),
  text: String? = "A note",
  typeKey: String? = null,
  createdAtIso: String? = "2026-01-01T10:00:00Z",
): CollectionItem {
  val answers = mutableMapOf<String, Value>()
  if (text != null) answers["note"] = SingleValue(text)

  val properties = mutableMapOf<String, Value>()
  if (typeKey != null) properties["type"] = SingleValue(typeKey)
  if (createdAtIso != null) properties["created_at"] = SingleValue(createdAtIso)

  return CollectionItem(uuid, DEFAULT_TIME, DEFAULT_TIME, answers, properties, emptyList())
}

fun page(
  items: List<AssessmentVersionQueryResult> = emptyList(),
  nextCursor: UUID? = null,
): GetAssessmentsModifiedSinceResult = GetAssessmentsModifiedSinceResult(items, nextCursor)

fun timelinePage(
  itemToUser: Map<UUID, UUID>,
  assessmentUuid: UUID = UUID.randomUUID(),
  totalPages: Int = 1,
  pageNumber: Int = 0,
): TimelineQueryResult = TimelineQueryResult(
  timeline = itemToUser.map { (itemUuid, userUuid) ->
    TimelineItem(
      uuid = UUID.randomUUID(),
      timestamp = DEFAULT_TIME,
      user = TimelineUser(id = userUuid, name = "User $userUuid"),
      assessment = assessmentUuid,
      event = "CollectionItemAddedEvent",
      data = mapOf("collectionItemUuid" to itemUuid.toString()),
    )
  },
  pageInfo = PageInfo(pageNumber = pageNumber, totalPages = totalPages),
)

fun emptyTimelinePage(): TimelineQueryResult = TimelineQueryResult(emptyList(), PageInfo(0, 0))

fun association(
  oasysPk: String = "1",
  regionCode: String? = null,
  baseVersion: Long = 1,
): EntityAssociationDetails = EntityAssociationDetails(oasysPk, regionCode, baseVersion)
