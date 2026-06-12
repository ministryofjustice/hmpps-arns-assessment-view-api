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

  @Modifying
  @Transactional
  @Query("UPDATE SentencePlanEntity sp SET sp.deleted = :deleted WHERE sp.id = :id")
  fun markDeletedForEntity(@Param("id") id: UUID, @Param("deleted") deleted: Boolean): Int

  // one row per (id, version).
  fun findByIdAndVersion(id: UUID, version: Long): Optional<SentencePlanEntity>
}
