package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanEntity
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository.SentencePlanRepository
import java.time.Instant
import java.util.UUID

class SentencePlanRepositoryIntegrationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var repository: SentencePlanRepository

  private val entityId = UUID.randomUUID()
  private val otherEntityId = UUID.randomUUID()

  @BeforeEach
  fun clean() {
    repository.deleteAllInBatch()
  }

  // The bounded range case: coordinator soft-deletes a non-latest association, so a later
  // association is still alive. The current-state (-1) row doesn't flip
  @Test
  fun `bounded range flips snapshots in the half-open range and leaves the -1 sentinel untouched`() {
    insertSnapshot(version = 100)
    insertSnapshot(version = 200)
    insertSnapshot(version = 300)
    insertSnapshot(version = 400)
    insertSnapshot(version = 500)
    insertSnapshot(version = SentencePlanEntity.CURRENT_VERSION)

    val updated = repository.markDeletedForRange(
      id = entityId,
      deleted = true,
      versionFrom = 100,
      versionTo = 400,
    )

    assertThat(updated).isEqualTo(3)
    assertThat(deletedFor(version = 100)).isTrue
    assertThat(deletedFor(version = 200)).isTrue
    assertThat(deletedFor(version = 300)).isTrue
    assertThat(deletedFor(version = 400)).isFalse
    assertThat(deletedFor(version = 500)).isFalse
    assertThat(deletedFor(version = SentencePlanEntity.CURRENT_VERSION)).isFalse
  }

  // The open ended case: coordinator soft deletes the latest/current association. The current
  // state really is deleted, so the -1 sentinel must update.
  @Test
  fun `open-ended range (versionTo=null) flips snapshots and the -1 sentinel`() {
    insertSnapshot(version = 100, deleted = true) // already deleted by an earlier op
    insertSnapshot(version = 400)
    insertSnapshot(version = 500)
    insertSnapshot(version = SentencePlanEntity.CURRENT_VERSION)

    val updated = repository.markDeletedForRange(
      id = entityId,
      deleted = true,
      versionFrom = 400,
      versionTo = null,
    )

    assertThat(updated).isEqualTo(3)
    assertThat(deletedFor(version = 100)).isTrue
    assertThat(deletedFor(version = 400)).isTrue
    assertThat(deletedFor(version = 500)).isTrue
    assertThat(deletedFor(version = SentencePlanEntity.CURRENT_VERSION)).isTrue
  }

  @Test
  fun `bounded undelete flips snapshots back to deleted=false and leaves the -1 sentinel untouched`() {
    insertSnapshot(version = 100, deleted = true)
    insertSnapshot(version = 200, deleted = true)
    insertSnapshot(version = 300, deleted = true)
    insertSnapshot(version = 400, deleted = false)
    insertSnapshot(version = SentencePlanEntity.CURRENT_VERSION, deleted = true)

    val updated = repository.markDeletedForRange(
      id = entityId,
      deleted = false,
      versionFrom = 100,
      versionTo = 400,
    )

    assertThat(updated).isEqualTo(3)
    assertThat(deletedFor(version = 100)).isFalse
    assertThat(deletedFor(version = 200)).isFalse
    assertThat(deletedFor(version = 300)).isFalse
    assertThat(deletedFor(version = 400)).isFalse
    assertThat(deletedFor(version = SentencePlanEntity.CURRENT_VERSION)).isTrue
  }

  @Test
  fun `open-ended undelete (versionTo=null) flips snapshots back and the -1 sentinel`() {
    insertSnapshot(version = 400, deleted = true)
    insertSnapshot(version = 500, deleted = true)
    insertSnapshot(version = SentencePlanEntity.CURRENT_VERSION, deleted = true)

    val updated = repository.markDeletedForRange(
      id = entityId,
      deleted = false,
      versionFrom = 400,
      versionTo = null,
    )

    assertThat(updated).isEqualTo(3)
    assertThat(deletedFor(version = 400)).isFalse
    assertThat(deletedFor(version = 500)).isFalse
    assertThat(deletedFor(version = SentencePlanEntity.CURRENT_VERSION)).isFalse
  }

  // Sanity check that a delete event for entity A doesnt touch entity B's rows
  @Test
  fun `range delete only touches rows for the targeted entity id`() {
    insertSnapshot(version = 100, entityId = entityId)
    insertSnapshot(version = SentencePlanEntity.CURRENT_VERSION, entityId = entityId)
    insertSnapshot(version = 100, entityId = otherEntityId)
    insertSnapshot(version = SentencePlanEntity.CURRENT_VERSION, entityId = otherEntityId)

    repository.markDeletedForRange(
      id = entityId,
      deleted = true,
      versionFrom = 100,
      versionTo = null,
    )

    assertThat(deletedFor(version = 100, entityId = entityId)).isTrue
    assertThat(deletedFor(version = SentencePlanEntity.CURRENT_VERSION, entityId = entityId)).isTrue
    assertThat(deletedFor(version = 100, entityId = otherEntityId)).isFalse
    assertThat(deletedFor(version = SentencePlanEntity.CURRENT_VERSION, entityId = otherEntityId)).isFalse
  }

  @Test
  fun `range is inclusive of versionFrom and exclusive of versionTo`() {
    insertSnapshot(version = 99)
    insertSnapshot(version = 100)
    insertSnapshot(version = 399)
    insertSnapshot(version = 400)

    repository.markDeletedForRange(
      id = entityId,
      deleted = true,
      versionFrom = 100,
      versionTo = 400,
    )

    assertThat(deletedFor(version = 99)).isFalse
    assertThat(deletedFor(version = 100)).isTrue
    assertThat(deletedFor(version = 399)).isTrue
    assertThat(deletedFor(version = 400)).isFalse
  }

  private fun insertSnapshot(
    version: Long,
    entityId: UUID = this.entityId,
    deleted: Boolean = false,
  ) {
    val now = Instant.parse("2026-06-12T00:00:00Z")
    repository.save(
      SentencePlanEntity(
        snapshotId = UUID.randomUUID(),
        id = entityId,
        createdAt = now,
        updatedAt = now,
        lastSyncedAt = now,
        version = version,
        deleted = deleted,
      ),
    )
  }

  private fun deletedFor(version: Long, entityId: UUID = this.entityId): Boolean = repository
    .findByIdAndVersion(entityId, version)
    .orElseThrow { AssertionError("expected row for entity=$entityId version=$version") }
    .deleted
}
