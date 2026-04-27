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
import java.util.UUID

@Entity
@Table(name = "plan_agreement")
class PlanAgreementEntity(
  @Id
  val id: UUID,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sentence_plan_id", nullable = false)
  val sentencePlan: SentencePlanEntity,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  val status: PlanStatus,

  @Column(name = "status_date")
  val statusDate: Instant? = null,

  @Column(name = "created_by_user_id", nullable = false)
  val createdByUserId: UUID,

  @Column(name = "created_at", nullable = false)
  val createdAt: Instant,

  @OneToMany(mappedBy = "planAgreement", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
  @BatchSize(size = 25)
  val freeTexts: MutableList<FreeTextEntity> = mutableListOf(),
)
