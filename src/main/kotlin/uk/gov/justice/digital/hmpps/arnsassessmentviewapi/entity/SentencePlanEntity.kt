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
  val id: UUID,

  @Column(name = "created_at", nullable = false)
  val createdAt: Instant,

  @Column(name = "updated_at", nullable = false)
  val updatedAt: Instant,

  @Column(name = "last_synced_at", nullable = false)
  var lastSyncedAt: Instant = Instant.now(),

  @Column(name = "oasys_pk")
  val oasysPk: Int? = null,

  @Column(nullable = false)
  val version: Int,

  @Column(name = "region_code")
  val regionCode: String? = null,

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
)
