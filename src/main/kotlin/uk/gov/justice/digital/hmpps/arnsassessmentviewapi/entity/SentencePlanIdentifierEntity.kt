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
import java.util.UUID

@Entity
@Table(name = "sentence_plan_identifier")
class SentencePlanIdentifierEntity(
  @Id
  val id: UUID,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sentence_plan_id", nullable = false)
  val sentencePlan: SentencePlanEntity,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  val type: IdentifierType,

  @Column(nullable = false)
  val value: String,
)
