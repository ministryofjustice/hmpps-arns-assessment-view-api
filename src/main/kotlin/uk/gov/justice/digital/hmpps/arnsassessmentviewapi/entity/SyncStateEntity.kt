package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "sync_state")
class SyncStateEntity(
  @Id
  val id: String,

  @Column(name = "last_sync_started_at")
  var lastSyncStartedAt: Instant? = null,
)
