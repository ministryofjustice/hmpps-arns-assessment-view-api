package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "free_text")
class FreeTextEntity(
  @Id
  val id: UUID,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  val type: FreeTextType,

  @Column(name = "text_length", nullable = false)
  val textLength: Int,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "goal_id")
  val goal: GoalEntity? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "plan_agreement_id")
  val planAgreement: PlanAgreementEntity? = null,

  @Column(name = "created_by_user_id", nullable = false)
  val createdByUserId: String,

  @Column(name = "created_at", nullable = false)
  val createdAt: Instant,
)
