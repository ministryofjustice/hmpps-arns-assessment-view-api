package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.IdentifierType
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity.SentencePlanEntity
import java.util.UUID

@Repository
interface SentencePlanRepository : JpaRepository<SentencePlanEntity, UUID> {
  @Query("SELECT sp FROM SentencePlanEntity sp JOIN sp.identifiers i WHERE i.type = :type AND i.value = :value AND sp.deleted = false")
  fun findByIdentifier(type: IdentifierType, value: String): List<SentencePlanEntity>
}
