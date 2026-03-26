package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

data class GoalRelatedAreaOfNeedId(
  val goal: UUID = UUID.randomUUID(),
  val criminogenicNeed: CriminogenicNeed = CriminogenicNeed.ACCOMMODATION,
) : Serializable

@Entity
@Table(name = "goal_related_area_of_need")
@IdClass(GoalRelatedAreaOfNeedId::class)
class GoalRelatedAreaOfNeedEntity(
  @Id
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "goal_id", nullable = false)
  val goal: GoalEntity,

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "criminogenic_need", nullable = false)
  val criminogenicNeed: CriminogenicNeed,
)
