package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.IdentifierType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanEntity
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface SentencePlanRepository : JpaRepository<SentencePlanEntity, UUID> {

  @Query("SELECT sp FROM SentencePlanEntity sp JOIN sp.identifiers i WHERE i.type = :type AND i.value = :value AND sp.deleted = false")
  fun findByIdentifier(type: IdentifierType, value: String): List<SentencePlanEntity>

  // The current state row (indicated by version -1) only 'deleted' when versionTo is null
  // (i.e. open ended). When versionTo is set then latest is not marked 'deleted'
  @Modifying
  @Transactional
  @Query(
    """
      UPDATE SentencePlanEntity sp
      SET sp.deleted = :deleted
      WHERE sp.id = :id
        AND (
          (sp.version >= :versionFrom
            AND (:versionTo IS NULL OR sp.version < :versionTo))
          OR (sp.version = -1 AND :versionTo IS NULL)
        )
    """,
  )
  fun markDeletedForRange(
    @Param("id") id: UUID,
    @Param("deleted") deleted: Boolean,
    @Param("versionFrom") versionFrom: Long,
    @Param("versionTo") versionTo: Long?,
  ): Int

  @Modifying
  @Transactional
  @Query(
    value = """
      UPDATE sentence_plan
      SET oasys_pk = :oasysPk, region_code = :regionCode
      WHERE id = :id
        AND version = :version
        AND updated_at = :updatedAt
        AND (oasys_pk IS DISTINCT FROM :oasysPk OR region_code IS DISTINCT FROM :regionCode)
    """,
    nativeQuery = true,
  )
  fun updateSnapshotAssociation(
    @Param("id") id: UUID,
    @Param("version") version: Long,
    @Param("updatedAt") updatedAt: Instant,
    @Param("oasysPk") oasysPk: String?,
    @Param("regionCode") regionCode: String?,
  ): Int

  // one row per (id, version).
  fun findByIdAndVersion(id: UUID, version: Long): Optional<SentencePlanEntity>
}
