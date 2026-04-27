package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "goal")
class GoalEntity(
  @Id
  val id: UUID,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sentence_plan_id", nullable = false)
  val sentencePlan: SentencePlanEntity,

  @Column(name = "title_length", nullable = false)
  val titleLength: Int,

  @Column(name = "title_hash", nullable = false)
  val titleHash: String,

  @Enumerated(EnumType.STRING)
  @Column(name = "area_of_need", nullable = false)
  val areaOfNeed: CriminogenicNeed,

  @Column(name = "target_date")
  val targetDate: LocalDate? = null,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  val status: GoalStatus,

  @Column(name = "status_date")
  val statusDate: Instant? = null,

  @Column(name = "created_at", nullable = false)
  val createdAt: Instant,

  @Column(name = "updated_at", nullable = false)
  val updatedAt: Instant,

  @Column(name = "goal_order", nullable = false)
  val goalOrder: Int,

  @OneToMany(mappedBy = "goal", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
  @BatchSize(size = 25)
  val relatedAreasOfNeed: MutableList<GoalRelatedAreaOfNeedEntity> = mutableListOf(),

  @OneToMany(mappedBy = "goal", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
  @BatchSize(size = 25)
  val freeTexts: MutableList<FreeTextEntity> = mutableListOf(),

  @OneToMany(mappedBy = "goal", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
  @BatchSize(size = 25)
  val steps: MutableList<StepEntity> = mutableListOf(),
)
