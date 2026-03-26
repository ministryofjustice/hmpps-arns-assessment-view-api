package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

data class SentencePlanOasysPkId(
  val sentencePlan: UUID = UUID.randomUUID(),
  val oasysAssessmentPk: String = "",
) : Serializable

@Entity
@Table(name = "sentence_plan_oasys_pk")
@IdClass(SentencePlanOasysPkId::class)
class SentencePlanOasysPkEntity(
  @Id
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sentence_plan_id", nullable = false)
  val sentencePlan: SentencePlanEntity,

  @Id
  @Column(name = "oasys_assessment_pk", nullable = false)
  val oasysAssessmentPk: String,
)
