package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.fixtures

import java.security.MessageDigest
import java.util.TimeZone

fun sha256Hex(text: String): String = MessageDigest.getInstance("SHA-256")
  .digest(text.toByteArray())
  .joinToString("") { "%02x".format(it) }

fun <T> withTimeZone(zoneId: String, block: () -> T): T {
  val previous = TimeZone.getDefault()
  TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
  try {
    return block()
  } finally {
    TimeZone.setDefault(previous)
  }
}
