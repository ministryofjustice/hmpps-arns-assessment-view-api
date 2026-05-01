package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.MultiValue
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.ActorType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.CriminogenicNeed
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.FreeTextType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalNoteType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.GoalStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanAgreementEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.PlanStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.StepStatus
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.agreementItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.agreementsCollection
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.assessment
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.association
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.authorshipFor
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.goalItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.goalsCollection
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.noteItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.sha256Hex
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.stepItem
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures.withTimeZone
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.client.dto.IdentifierType as AapIdentifierType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.IdentifierType as EntityIdentifierType

class SentencePlanMapperTest {

  private val mapper = SentencePlanMapper()

  private fun authorOf(user: UUID) = ItemAuthorship(createdBy = user, updatedBy = user)

  @Nested
  inner class ToEntityTopLevel {

    @Test
    fun `creates new entity with id, version, oasysPk, regionCode, and deleted flag from source and association`() {
      // GIVEN an assessment and association with a fresh UUID and no existing entity
      val uuid = UUID.randomUUID()
      val source = assessment(uuid = uuid)
      val assoc = association(oasysPk = "12345", regionCode = "LDN", baseVersion = 3)

      // WHEN mapped
      val plan = mapper.toEntity(source, assoc, existing = null, authorship = authorshipFor(source))

      // THEN top level fields come from source + association
      assertThat(plan.id).isEqualTo(uuid)
      assertThat(plan.oasysPk).isEqualTo("12345")
      assertThat(plan.version).isEqualTo(3)
      assertThat(plan.regionCode).isEqualTo("LDN")
      assertThat(plan.deleted).isFalse
    }

    @Test
    fun `regionCode is null when association regionPrisonCode is null`() {
      // GIVEN an association with no regionPrisonCode
      val source = assessment()
      val assoc = association(regionCode = null)

      // WHEN mapped
      val plan = mapper.toEntity(source, assoc, existing = null, authorship = authorshipFor(source))

      // THEN regionCode is null on the entity
      assertThat(plan.regionCode).isNull()
    }

    @Test
    fun `lastSyncedAt is set to current time on insert`() {
      // GIVEN an assessment with no existing entity
      val source = assessment()

      // WHEN mapped between two captured instants
      val before = Instant.now()
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))
      val after = Instant.now()

      // THEN lastSyncedAt falls inside that window
      assertThat(plan.lastSyncedAt).isBetween(before, after)
    }

    @Test
    fun `update path mutates existing entity, refreshes lastSyncedAt, and clears nested collections`() {
      // GIVEN an existing entity already holding stale children, and an empty source so anything left
      // in the resulting collections must be a clear() failure rather than freshly added rows
      val uuid = UUID.randomUUID()
      val existing = existingPlan(uuid, deleted = false, lastSyncedAt = Instant.parse("2026-01-01T00:00:00Z"))
      existing.agreements.add(
        PlanAgreementEntity(
          id = UUID.randomUUID(),
          sentencePlan = existing,
          status = PlanStatus.DRAFT,
          createdByUserId = UUID.randomUUID(),
          createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        ),
      )

      val source = assessment(uuid = uuid, identifiers = emptyMap(), collections = emptyList())

      // WHEN re-mapped
      val before = Instant.now()
      val plan = mapper.toEntity(source, association(), existing = existing, authorship = authorshipFor(source))
      val after = Instant.now()

      // THEN the same instance is returned, lastSyncedAt is refreshed, and stale children are gone
      assertThat(plan).isSameAs(existing)
      assertThat(plan.lastSyncedAt).isBetween(before, after)
      assertThat(plan.agreements).isEmpty()
      assertThat(plan.identifiers).isEmpty()
      assertThat(plan.goals).isEmpty()
    }

    @Test
    fun `update path refreshes createdAt, updatedAt, oasysPk, version, and regionCode from new source and association`() {
      // GIVEN an existing entity with one set of values
      val uuid = UUID.randomUUID()
      val existing = SentencePlanEntity(
        id = uuid,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        lastSyncedAt = Instant.parse("2026-01-01T00:00:00Z"),
        oasysPk = "OLD-PK",
        version = 1,
        regionCode = "OLD",
        deleted = false,
      )

      // WHEN re-mapped against a source/association carrying different values for every field
      val newCreated = LocalDateTime.of(2026, 6, 1, 9, 0)
      val newUpdated = LocalDateTime.of(2026, 6, 15, 14, 30)
      val source = assessment(uuid = uuid, createdAt = newCreated, updatedAt = newUpdated)
      val newAssoc = association(oasysPk = "NEW-PK", regionCode = "NEW", baseVersion = 7)

      mapper.toEntity(source, newAssoc, existing = existing, authorship = authorshipFor(source))

      // THEN every field on the existing entity reflects the new values.
      assertThat(existing.createdAt).isEqualTo(newCreated.atZone(java.time.ZoneId.systemDefault()).toInstant())
      assertThat(existing.updatedAt).isEqualTo(newUpdated.atZone(java.time.ZoneId.systemDefault()).toInstant())
      assertThat(existing.oasysPk).isEqualTo("NEW-PK")
      assertThat(existing.version).isEqualTo(7)
      assertThat(existing.regionCode).isEqualTo("NEW")
    }

    @Test
    fun `mapper does not touch the deleted flag - it is owned by the soft-delete pass`() {
      // GIVEN a previously deleted plan re-emitted by the modified since stream
      val uuid = UUID.randomUUID()
      val existing = existingPlan(uuid, deleted = true)
      val source = assessment(uuid = uuid)

      // WHEN re-mapped from the modified-since path
      val plan = mapper.toEntity(source, association(), existing = existing, authorship = authorshipFor(source))

      // THEN the deleted flag is preserved (would be flipped only by the soft delete pass)
      assertThat(plan.deleted).isTrue
    }

    @Test
    fun `LocalDateTime createdAt and updatedAt are converted in JVM default zone`() {
      // GIVEN a London wall clock LocalDateTime under BST
      withTimeZone("Europe/London") {
        val bstLocal = LocalDateTime.of(2026, 6, 15, 10, 0)
        val source = assessment(createdAt = bstLocal, updatedAt = bstLocal)

        // WHEN mapped
        val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

        // THEN the wall-clock time is interpreted as London-zone, producing the corresponding UTC instant
        assertThat(plan.createdAt).isEqualTo(Instant.parse("2026-06-15T09:00:00Z"))
        assertThat(plan.updatedAt).isEqualTo(Instant.parse("2026-06-15T09:00:00Z"))
      }
    }
  }

  @Nested
  inner class MapIdentifiers {

    @ParameterizedTest
    @CsvSource(
      "CRN, CRN",
      "NOMIS_ID, NOMIS",
    )
    fun `maps supported AAP identifier types onto entity types`(aapTypeName: String, expectedTypeName: String) {
      // GIVEN a source carrying the supported AAP identifier
      val aapType = AapIdentifierType.valueOf(aapTypeName)
      val expected = EntityIdentifierType.valueOf(expectedTypeName)
      val source = assessment(identifiers = mapOf(aapType to "value-1"))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN it surfaces with the matching entity type and the same value
      assertThat(plan.identifiers.map { it.type to it.value }).containsExactly(expected to "value-1")
    }

    @Test
    fun `silently drops unsupported PRN identifier`() {
      // GIVEN a source with an unsupported PRN identifier alongside supported ones
      val source = assessment(
        identifiers = mapOf(
          AapIdentifierType.CRN to "X1",
          AapIdentifierType.PRN to "P1",
          AapIdentifierType.NOMIS_ID to "A0001BC",
        ),
      )

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN PRN is dropped while CRN and NOMIS persist
      assertThat(plan.identifiers.map { it.type to it.value })
        .containsExactlyInAnyOrder(
          EntityIdentifierType.CRN to "X1",
          EntityIdentifierType.NOMIS to "A0001BC",
        )
    }

    @Test
    fun `produces empty list when source has no identifiers`() {
      // GIVEN an assessment with no identifiers at all
      val source = assessment(identifiers = emptyMap())

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN no identifier rows are produced
      assertThat(plan.identifiers).isEmpty()
    }
  }

  @Nested
  inner class MapAgreements {

    @Test
    fun `returns empty list when PLAN_AGREEMENTS collection is absent`() {
      // GIVEN an assessment with no agreements collection
      val source = assessment(collections = emptyList())

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN no agreements are produced
      assertThat(plan.agreements).isEmpty()
    }

    @Test
    fun `returns empty list when PLAN_AGREEMENTS collection is present but empty`() {
      // GIVEN an assessment with an empty PLAN_AGREEMENTS collection
      val source = assessment(collections = listOf(agreementsCollection(emptyList())))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN no agreements are produced
      assertThat(plan.agreements).isEmpty()
    }

    @ParameterizedTest
    @CsvSource(
      "DRAFT, DRAFT",
      "AGREED, AGREED",
      "DO_NOT_AGREE, DO_NOT_AGREE",
      "COULD_NOT_ANSWER, COULD_NOT_ANSWER",
      "UPDATED_AGREED, UPDATED_AGREED",
      "UPDATED_DO_NOT_AGREE, UPDATED_DO_NOT_AGREE",
    )
    fun `maps known plan status keys to PlanStatus values`(statusKey: String, expectedName: String) {
      // GIVEN an agreement with the given status key
      val agreementUuid = UUID.randomUUID()
      val source = assessment(
        collections = listOf(agreementsCollection(listOf(agreementItem(uuid = agreementUuid, statusKey = statusKey)))),
      )
      val authorship = authorshipFor(source, overrides = mapOf(agreementUuid to authorOf(UUID.randomUUID())))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorship)

      // THEN the status enum matches the lookup
      assertThat(plan.agreements.single().status).isEqualTo(PlanStatus.valueOf(expectedName))
    }

    @Test
    fun `skips agreement when status property is missing`() {
      // GIVEN an agreement with no status property
      val source = assessment(
        collections = listOf(agreementsCollection(listOf(agreementItem(statusKey = null)))),
      )

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the agreement is skipped, not surfaced as an entity
      assertThat(plan.agreements).isEmpty()
    }

    @Test
    fun `skips agreement when status key is unknown`() {
      // GIVEN an agreement with an unrecognised status key
      val source = assessment(
        collections = listOf(agreementsCollection(listOf(agreementItem(statusKey = "MAYBE_AGREED")))),
      )

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the agreement is skipped rather than failing the whole mapping
      assertThat(plan.agreements).isEmpty()
    }

    @Test
    fun `statusDate is null when status_date is absent`() {
      // GIVEN an agreement without status_date
      val agreementUuid = UUID.randomUUID()
      val source = assessment(
        collections = listOf(agreementsCollection(listOf(agreementItem(uuid = agreementUuid, statusDate = null)))),
      )
      val authorship = authorshipFor(source, overrides = mapOf(agreementUuid to authorOf(UUID.randomUUID())))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorship)

      // THEN statusDate falls back to null
      assertThat(plan.agreements.single().statusDate).isNull()
    }

    @ParameterizedTest
    @CsvSource(
      "details_no, AGREEMENT_DETAILS",
      "details_could_not_answer, AGREEMENT_DETAILS",
      "notes, AGREEMENT_NOTES",
    )
    fun `agreement answer key produces a free-text with the matching type, length, and SHA-256 hash`(
      answerKey: String,
      expectedTypeName: String,
    ) {
      // GIVEN an agreement carrying the answer key under test
      val agreementUuid = UUID.randomUUID()
      val text = "answer text"
      val source = assessment(
        collections = listOf(
          agreementsCollection(
            listOf(
              agreementItem(
                uuid = agreementUuid,
                detailsNo = text.takeIf { answerKey == "details_no" },
                detailsCouldNotAnswer = text.takeIf { answerKey == "details_could_not_answer" },
                notes = text.takeIf { answerKey == "notes" },
              ),
            ),
          ),
        ),
      )
      val authorship = authorshipFor(source, overrides = mapOf(agreementUuid to authorOf(UUID.randomUUID())))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorship)

      // THEN one free-text is produced with the expected type and hash+length redaction
      val ft = plan.agreements.single().freeTexts.single()
      assertThat(ft.type).isEqualTo(FreeTextType.valueOf(expectedTypeName))
      assertThat(ft.textLength).isEqualTo(text.length)
      assertThat(ft.textHash).isEqualTo(sha256Hex(text))
    }

    @Test
    fun `details and notes both produce two free-texts in AGREEMENT_FREE_TEXT_ANSWERS declared order`() {
      // GIVEN an agreement with both details_no and notes
      val agreementUuid = UUID.randomUUID()
      val source = assessment(
        collections = listOf(
          agreementsCollection(listOf(agreementItem(uuid = agreementUuid, statusKey = "DO_NOT_AGREE", detailsNo = "reason", notes = "context"))),
        ),
      )
      val authorship = authorshipFor(source, overrides = mapOf(agreementUuid to authorOf(UUID.randomUUID())))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorship)

      // THEN AGREEMENT_DETAILS appears before AGREEMENT_NOTES (mapper iterates the constant in declared order)
      assertThat(plan.agreements.single().freeTexts.map { it.type })
        .containsExactly(FreeTextType.AGREEMENT_DETAILS, FreeTextType.AGREEMENT_NOTES)
    }

    @Test
    fun `agreement free-texts inherit creator from the agreement, not from a separate timeline entry`() {
      // GIVEN an agreement with a free-text and a creator map keyed only by the agreement uuid
      val agreementUuid = UUID.randomUUID()
      val agreementCreator = UUID.randomUUID()
      val source = assessment(
        collections = listOf(agreementsCollection(listOf(agreementItem(uuid = agreementUuid, statusKey = "DO_NOT_AGREE", detailsNo = "reason", notes = "context")))),
      )
      val authorship = authorshipFor(source, overrides = mapOf(agreementUuid to authorOf(agreementCreator)))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorship)

      // THEN both the agreement and its free texts attribute to the same agreement creator
      val agreement = plan.agreements.single()
      assertThat(agreement.createdByUserId).isEqualTo(agreementCreator)
      assertThat(agreement.freeTexts).allMatch { it.createdByUserId == agreementCreator }
    }

    @Test
    fun `throws when authorship map has no entry for an otherwise-valid agreement`() {
      // GIVEN an agreement that should map but no timeline creator
      val source = assessment(collections = listOf(agreementsCollection(listOf(agreementItem()))))

      // WHEN mapped without a creator
      // THEN the mapper escalates rather than silently emitting an unauthored agreement
      assertThrows<IllegalStateException> {
        mapper.toEntity(source, association(), existing = null, authorship = emptyMap())
      }
    }
  }

  @Nested
  inner class MapGoals {

    @Test
    fun `returns empty list when GOALS collection is absent`() {
      // GIVEN no goals collection
      val source = assessment(collections = emptyList())

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN no goals are produced
      assertThat(plan.goals).isEmpty()
    }

    @Test
    fun `skips goal when title answer is missing`() {
      // GIVEN a goal whose title answer is absent
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(title = null)))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the goal is skipped
      assertThat(plan.goals).isEmpty()
    }

    @Test
    fun `skips goal when area_of_need answer is missing`() {
      // GIVEN a goal whose area_of_need answer is absent
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(areaSlug = null)))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the goal is skipped
      assertThat(plan.goals).isEmpty()
    }

    @Test
    fun `skips goal when area_of_need slug is unknown`() {
      // GIVEN a goal with an unrecognised area_of_need slug
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(areaSlug = "not-a-real-area")))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the goal is skipped rather than mapped to a placeholder
      assertThat(plan.goals).isEmpty()
    }

    @Test
    fun `skips goal when status property is missing`() {
      // GIVEN a goal with no status property
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(statusKey = null)))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the goal is skipped
      assertThat(plan.goals).isEmpty()
    }

    @Test
    fun `skips goal when status key is unknown`() {
      // GIVEN a goal with an unrecognised status key
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(statusKey = "MAYBE_ACTIVE")))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the goal is skipped
      assertThat(plan.goals).isEmpty()
    }

    @Test
    fun `valid and invalid goals coexist - bad ones dropped, good ones kept with stable order`() {
      // GIVEN three goals: bad (no title), good, bad (unknown area)
      val keptUuid = UUID.randomUUID()
      val source = assessment(
        collections = listOf(
          goalsCollection(
            listOf(
              goalItem(title = null),
              goalItem(uuid = keptUuid, title = "Survivor"),
              goalItem(areaSlug = "not-real"),
            ),
          ),
        ),
      )

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN only the valid goal is kept and goalOrder reflects its index in the source list
      assertThat(plan.goals).hasSize(1)
      assertThat(plan.goals.single().id).isEqualTo(keptUuid)
      assertThat(plan.goals.single().goalOrder).isEqualTo(1)
    }

    @ParameterizedTest
    @CsvSource(
      "accommodation, ACCOMMODATION",
      "employment-and-education, EMPLOYMENT_AND_EDUCATION",
      "finances, FINANCES",
      "drug-use, DRUG_USE",
      "alcohol-use, ALCOHOL_USE",
      "health-and-wellbeing, HEALTH_AND_WELLBEING",
      "personal-relationships-and-community, PERSONAL_RELATIONSHIPS_AND_COMMUNITY",
      "thinking-behaviours-and-attitudes, THINKING_BEHAVIOURS_AND_ATTITUDES",
    )
    fun `maps known area_of_need slugs to CriminogenicNeed values`(slug: String, expectedName: String) {
      // GIVEN a goal whose area_of_need is the slug under test
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(areaSlug = slug)))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the entity carries the matching enum
      assertThat(plan.goals.single().areaOfNeed).isEqualTo(CriminogenicNeed.valueOf(expectedName))
    }

    @ParameterizedTest
    @CsvSource(
      "ACTIVE, ACTIVE",
      "FUTURE, FUTURE",
      "ACHIEVED, ACHIEVED",
      "REMOVED, REMOVED",
    )
    fun `maps known goal status keys to GoalStatus values`(statusKey: String, expectedName: String) {
      // GIVEN a goal with the given status key
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(statusKey = statusKey)))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the status enum matches
      assertThat(plan.goals.single().status).isEqualTo(GoalStatus.valueOf(expectedName))
    }

    @Test
    fun `parses target_date when present`() {
      // GIVEN a goal with an ISO target_date
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(targetDate = "2026-12-31")))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN targetDate parses to the expected LocalDate
      assertThat(plan.goals.single().targetDate).isEqualTo(LocalDate.of(2026, 12, 31))
    }

    @Test
    fun `targetDate is null when target_date is absent`() {
      // GIVEN a goal with no target_date answer
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(targetDate = null)))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN targetDate is null
      assertThat(plan.goals.single().targetDate).isNull()
    }

    @Test
    fun `targetDate is null when target_date string is unparseable`() {
      // GIVEN a target_date answer that doesn't parse as ISO date
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(targetDate = "not-a-date")))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN targetDate falls back to null rather than throwing
      assertThat(plan.goals.single().targetDate).isNull()
    }

    @Test
    fun `goalOrder reflects the index of the goal in the source list`() {
      // GIVEN two goals
      val firstUuid = UUID.randomUUID()
      val secondUuid = UUID.randomUUID()
      val source = assessment(
        collections = listOf(goalsCollection(listOf(goalItem(uuid = firstUuid), goalItem(uuid = secondUuid)))),
      )

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN each goal carries its index as goalOrder
      assertThat(plan.goals.map { it.id to it.goalOrder })
        .containsExactly(firstUuid to 0, secondUuid to 1)
    }

    @Test
    fun `multi-value related_areas_of_need maps each known slug`() {
      // GIVEN a goal with two related areas in a MultiValue
      val source = assessment(
        collections = listOf(goalsCollection(listOf(goalItem(relatedAreas = listOf("finances", "alcohol-use"))))),
      )

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN both related areas are persisted
      assertThat(plan.goals.single().relatedAreasOfNeed.map { it.criminogenicNeed })
        .containsExactlyInAnyOrder(CriminogenicNeed.FINANCES, CriminogenicNeed.ALCOHOL_USE)
    }

    @Test
    fun `single-value related_areas_of_need is treated as a single-element list`() {
      // GIVEN a goal whose related_areas_of_need was emitted as SingleValue (legacy migrator data)
      val source = assessment(
        collections = listOf(goalsCollection(listOf(goalItem(singleRelatedArea = "drug-use")))),
      )

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the one slug is persisted
      assertThat(plan.goals.single().relatedAreasOfNeed.map { it.criminogenicNeed })
        .containsExactly(CriminogenicNeed.DRUG_USE)
    }

    @Test
    fun `unknown related-area slugs are silently dropped, valid siblings are kept`() {
      // GIVEN a mixture of valid and unknown related-area slugs
      val source = assessment(
        collections = listOf(goalsCollection(listOf(goalItem(relatedAreas = listOf("finances", "not-real-area"))))),
      )

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN only the valid one persists
      assertThat(plan.goals.single().relatedAreasOfNeed.map { it.criminogenicNeed })
        .containsExactly(CriminogenicNeed.FINANCES)
    }

    @Test
    fun `goal title is stored only as length and SHA-256 hash, never as raw text`() {
      // GIVEN a goal with sensitive title text
      val rawTitle = "Sensitive personally-identifying goal text"
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(title = rawTitle)))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the entity carries hash + length, the redaction contract holds
      val goal = plan.goals.single()
      assertThat(goal.titleLength).isEqualTo(rawTitle.length)
      assertThat(goal.titleHash).isEqualTo(sha256Hex(rawTitle))
    }
  }

  @Nested
  inner class MapSteps {

    @Test
    fun `produces empty steps when STEPS collection is absent on the goal`() {
      // GIVEN a goal with no nested STEPS collection
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(steps = emptyList())))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the goal has no steps
      assertThat(plan.goals.single().steps).isEmpty()
    }

    @Test
    fun `skips step when description is missing`() {
      // GIVEN a step missing 'description'
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(steps = listOf(stepItem(description = null)))))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the step is skipped
      assertThat(plan.goals.single().steps).isEmpty()
    }

    @Test
    fun `skips step when actor is missing`() {
      // GIVEN a step missing 'actor'
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(steps = listOf(stepItem(actorKey = null)))))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the step is skipped
      assertThat(plan.goals.single().steps).isEmpty()
    }

    @Test
    fun `skips step when actor key is unknown`() {
      // GIVEN a step with an unrecognised actor
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(steps = listOf(stepItem(actorKey = "wizard")))))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the step is skipped
      assertThat(plan.goals.single().steps).isEmpty()
    }

    @Test
    fun `skips step when status is missing`() {
      // GIVEN a step missing 'status'
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(steps = listOf(stepItem(statusKey = null)))))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the step is skipped
      assertThat(plan.goals.single().steps).isEmpty()
    }

    @Test
    fun `skips step when status key is unknown`() {
      // GIVEN a step with an unrecognised status
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(steps = listOf(stepItem(statusKey = "PAUSED")))))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the step is skipped
      assertThat(plan.goals.single().steps).isEmpty()
    }

    @ParameterizedTest
    @CsvSource(
      "person_on_probation, PERSON_ON_PROBATION",
      "probation_practitioner, PROBATION_PRACTITIONER",
      "programme_staff, PROGRAMME_STAFF",
      "partnership_agency, PARTNERSHIP_AGENCY",
      "crs_provider, CRS_PROVIDER",
      "prison_offender_manager, PRISON_OFFENDER_MANAGER",
      "someone_else, SOMEONE_ELSE",
    )
    fun `maps known actor keys to ActorType values`(actorKey: String, expectedName: String) {
      // GIVEN a step with the given actor key
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(steps = listOf(stepItem(actorKey = actorKey)))))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the step carries the matching enum
      assertThat(plan.goals.single().steps.single().actor).isEqualTo(ActorType.valueOf(expectedName))
    }

    @ParameterizedTest
    @CsvSource(
      "NOT_STARTED, NOT_STARTED",
      "IN_PROGRESS, IN_PROGRESS",
      "COMPLETED, COMPLETED",
      "CANNOT_BE_DONE_YET, CANNOT_BE_DONE_YET",
      "NO_LONGER_NEEDED, NO_LONGER_NEEDED",
    )
    fun `maps known step status keys to StepStatus values`(statusKey: String, expectedName: String) {
      // GIVEN a step with the given status key
      val source = assessment(collections = listOf(goalsCollection(listOf(goalItem(steps = listOf(stepItem(statusKey = statusKey)))))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the step carries the matching enum
      assertThat(plan.goals.single().steps.single().status).isEqualTo(StepStatus.valueOf(expectedName))
    }

    @Test
    fun `parses status_date when present`() {
      // GIVEN a step with an ISO status_date
      val source = assessment(
        collections = listOf(goalsCollection(listOf(goalItem(steps = listOf(stepItem(statusDate = "2026-03-15T12:00:00Z")))))),
      )

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN statusDate parses to the expected instant
      assertThat(plan.goals.single().steps.single().statusDate).isEqualTo(Instant.parse("2026-03-15T12:00:00Z"))
    }
  }

  @Nested
  inner class MapGoalNotes {

    @Test
    fun `skips note when 'note' answer is missing`() {
      // GIVEN a note missing the 'note' text answer
      val source = assessment(
        collections = listOf(goalsCollection(listOf(goalItem(notes = listOf(noteItem(text = null)))))),
      )

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the note is skipped
      assertThat(plan.goals.single().freeTexts).isEmpty()
    }

    @Test
    fun `skips note when created_at property is missing`() {
      // GIVEN a note with no created_at
      val source = assessment(
        collections = listOf(goalsCollection(listOf(goalItem(notes = listOf(noteItem(createdAtIso = null)))))),
      )

      // WHEN mapped, authorship map intentionally empty since we expect skip before lookup
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the note is skipped
      assertThat(plan.goals.single().freeTexts).isEmpty()
    }

    @Test
    fun `defaults to PROGRESS when type property is absent`() {
      // GIVEN a goal note with no 'type' property
      val noteUuid = UUID.randomUUID()
      val source = assessment(
        collections = listOf(goalsCollection(listOf(goalItem(notes = listOf(noteItem(uuid = noteUuid, typeKey = null)))))),
      )
      val authorship = authorshipFor(source, overrides = mapOf(noteUuid to authorOf(UUID.randomUUID())))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorship)

      // THEN goalNoteType is PROGRESS (the legacy/aap-ui default)
      assertThat(plan.goals.single().freeTexts.single().goalNoteType).isEqualTo(GoalNoteType.PROGRESS)
    }

    @ParameterizedTest
    @CsvSource(
      "ACHIEVED, ACHIEVED",
      "REMOVED, REMOVED",
      "READDED, READDED",
      "PROGRESS, PROGRESS",
    )
    fun `maps known note type keys to GoalNoteType values`(typeKey: String, expectedName: String) {
      // GIVEN a goal note with the given type
      val noteUuid = UUID.randomUUID()
      val source = assessment(
        collections = listOf(goalsCollection(listOf(goalItem(notes = listOf(noteItem(uuid = noteUuid, typeKey = typeKey)))))),
      )
      val authorship = authorshipFor(source, overrides = mapOf(noteUuid to authorOf(UUID.randomUUID())))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorship)

      // THEN goalNoteType matches the lookup
      assertThat(plan.goals.single().freeTexts.single().goalNoteType)
        .isEqualTo(GoalNoteType.valueOf(expectedName))
    }

    @Test
    fun `skips note when type key is unknown`() {
      // GIVEN a note with an unrecognised type
      val noteUuid = UUID.randomUUID()
      val source = assessment(
        collections = listOf(goalsCollection(listOf(goalItem(notes = listOf(noteItem(uuid = noteUuid, typeKey = "INVALID_TYPE")))))),
      )
      val authorship = authorshipFor(source, overrides = mapOf(noteUuid to authorOf(UUID.randomUUID())))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorship)

      // THEN the note is skipped, not coerced to a default
      assertThat(plan.goals.single().freeTexts).isEmpty()
    }

    @Test
    fun `note text is stored as length and SHA-256 hash, not raw text`() {
      // GIVEN a note with sensitive text
      val text = "Privately disclosed sensitive information"
      val noteUuid = UUID.randomUUID()
      val source = assessment(
        collections = listOf(goalsCollection(listOf(goalItem(notes = listOf(noteItem(uuid = noteUuid, text = text)))))),
      )
      val authorship = authorshipFor(source, overrides = mapOf(noteUuid to authorOf(UUID.randomUUID())))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorship)

      // THEN the redaction contract holds
      val ft = plan.goals.single().freeTexts.single()
      assertThat(ft.textLength).isEqualTo(text.length)
      assertThat(ft.textHash).isEqualTo(sha256Hex(text))
    }

    @Test
    fun `throws when authorship map has no entry for an otherwise-valid note`() {
      // GIVEN an otherwise-valid note but no timeline creator for it
      val goal = goalItem(notes = listOf(noteItem()))
      val source = assessment(collections = listOf(goalsCollection(listOf(goal))))
      // Authorship covers the goal (so the goal mapping succeeds) but not the note inside it.
      val authorship = mapOf(goal.uuid to authorOf(UUID.randomUUID()))

      // WHEN mapped without a matching note creator
      // THEN the mapper escalates rather than fabricate authorship
      assertThrows<IllegalStateException> {
        mapper.toEntity(source, association(), existing = null, authorship = authorship)
      }
    }
  }

  @Nested
  inner class Helpers {

    // The asString / asStringList / toInstantOrNull / toLocalDateOrNull / sha256Hex / toInstant
    // helpers are private, we exercise their edges via the public toEntity surface.

    @Test
    fun `asString returns null when the answer is a MultiValue (wrong shape) and the field falls through`() {
      // GIVEN a goal whose 'title' answer is a MultiValue rather than SingleValue
      val base = goalItem()
      val item = base.copy(answers = base.answers + ("title" to MultiValue(listOf("a", "b"))))
      val source = assessment(collections = listOf(goalsCollection(listOf(item))))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorshipFor(source))

      // THEN the goal is skipped because asString returned null on the wrong shape
      assertThat(plan.goals).isEmpty()
    }

    @Test
    fun `toInstantOrNull falls back from ISO-8601 to LocalDateTime parsing in JVM zone`() {
      // GIVEN a status_date in plain LocalDateTime form (no offset) under London zone
      withTimeZone("Europe/London") {
        val agreementUuid = UUID.randomUUID()
        val source = assessment(
          collections = listOf(agreementsCollection(listOf(agreementItem(uuid = agreementUuid, statusDate = "2026-01-15T10:00:00")))),
        )
        val authorship = authorshipFor(source, overrides = mapOf(agreementUuid to authorOf(UUID.randomUUID())))

        // WHEN mapped
        val plan = mapper.toEntity(source, association(), existing = null, authorship = authorship)

        // THEN the wall-clock string is interpreted in the JVM zone (GMT in January)
        assertThat(plan.agreements.single().statusDate).isEqualTo(Instant.parse("2026-01-15T10:00:00Z"))
      }
    }

    @Test
    fun `toInstantOrNull yields null for unparseable input rather than throwing`() {
      // GIVEN a status_date that's neither ISO instant nor LocalDateTime
      val agreementUuid = UUID.randomUUID()
      val source = assessment(
        collections = listOf(agreementsCollection(listOf(agreementItem(uuid = agreementUuid, statusDate = "not-a-date")))),
      )
      val authorship = authorshipFor(source, overrides = mapOf(agreementUuid to authorOf(UUID.randomUUID())))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorship)

      // THEN statusDate is null rather than the mapping crashing
      assertThat(plan.agreements.single().statusDate).isNull()
    }

    @Test
    fun `goal note created_at parsed via ISO-8601 instant variant`() {
      // GIVEN a note whose created_at is an ISO-8601 instant (the AAP common case)
      val noteUuid = UUID.randomUUID()
      val source = assessment(
        collections = listOf(goalsCollection(listOf(goalItem(notes = listOf(noteItem(uuid = noteUuid, createdAtIso = "2026-04-01T08:30:00Z")))))),
      )
      val authorship = authorshipFor(source, overrides = mapOf(noteUuid to authorOf(UUID.randomUUID())))

      // WHEN mapped
      val plan = mapper.toEntity(source, association(), existing = null, authorship = authorship)

      // THEN created_at parses in the ISO branch (no zone fallback needed)
      assertThat(plan.goals.single().freeTexts.single().createdAt).isEqualTo(Instant.parse("2026-04-01T08:30:00Z"))
    }
  }

  // --- helpers private to this test class ---

  private fun existingPlan(
    uuid: UUID,
    deleted: Boolean = false,
    lastSyncedAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
  ): SentencePlanEntity = SentencePlanEntity(
    id = uuid,
    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    lastSyncedAt = lastSyncedAt,
    oasysPk = "1",
    version = 1,
    deleted = deleted,
  )
}
