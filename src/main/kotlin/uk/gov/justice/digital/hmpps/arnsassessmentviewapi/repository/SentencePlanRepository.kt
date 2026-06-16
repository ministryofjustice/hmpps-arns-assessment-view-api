package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.IdentifierType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanEntity
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

  // one row per (id, version).
  fun findByIdAndVersion(id: UUID, version: Long): Optional<SentencePlanEntity>
}
