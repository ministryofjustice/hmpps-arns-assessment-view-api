package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "sentence_plan")
class SentencePlanEntity(
  @Id
  @Column(name = "snapshot_id", nullable = false)
  val snapshotId: UUID = UUID.randomUUID(),

  // Logical sentence plan identifier
  @Column(name = "id", nullable = false)
  val id: UUID,

  @Column(name = "created_at", nullable = false)
  var createdAt: Instant,

  @Column(name = "updated_at", nullable = false)
  var updatedAt: Instant,

  @Column(name = "last_synced_at", nullable = false)
  var lastSyncedAt: Instant = Instant.now(),

  @Column(name = "oasys_pk")
  var oasysPk: String? = null,

  @Column(nullable = false)
  var version: Long,

  @Column(name = "oasys_event")
  var oasysEvent: String? = null,

  @Column(name = "region_code")
  var regionCode: String? = null,

  @Column(name = "deleted", nullable = false)
  var deleted: Boolean = false,

  @OneToMany(mappedBy = "sentencePlan", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
  @BatchSize(size = 25)
  val identifiers: MutableList<SentencePlanIdentifierEntity> = mutableListOf(),

  @OneToMany(mappedBy = "sentencePlan", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
  @BatchSize(size = 25)
  val agreements: MutableList<PlanAgreementEntity> = mutableListOf(),

  @OneToMany(mappedBy = "sentencePlan", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
  @BatchSize(size = 25)
  val goals: MutableList<GoalEntity> = mutableListOf(),
) {
  companion object {
    // Version for the single mutable "current state" row per entity. Versioned snapshots
    // written on Oasys events use version > 0.
    const val CURRENT_VERSION = -1L
  }
}
