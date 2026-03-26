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
  val lastSyncedAt: Instant = Instant.now(),

  @OneToMany(mappedBy = "sentencePlan", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
  @BatchSize(size = 25)
  val identifiers: MutableList<SentencePlanIdentifierEntity> = mutableListOf(),

  @OneToMany(mappedBy = "sentencePlan", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
  @BatchSize(size = 25)
  val oasysPks: MutableList<SentencePlanOasysPkEntity> = mutableListOf(),

  @OneToMany(mappedBy = "sentencePlan", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
  @BatchSize(size = 25)
  val agreements: MutableList<PlanAgreementEntity> = mutableListOf(),

  @OneToMany(mappedBy = "sentencePlan", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
  @BatchSize(size = 25)
  val goals: MutableList<GoalEntity> = mutableListOf(),
)
