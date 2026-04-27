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
@Table(name = "step")
class StepEntity(
  @Id
  val id: UUID,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "goal_id", nullable = false)
  val goal: GoalEntity,

  @Column(nullable = false)
  val description: String,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  val actor: ActorType,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  val status: StepStatus,

  @Column(name = "status_date")
  val statusDate: Instant? = null,

  @Column(name = "created_at", nullable = false)
  val createdAt: Instant,
)
