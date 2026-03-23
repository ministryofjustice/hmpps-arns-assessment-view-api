package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.unit

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.arnsassessmentviewapi.ArnsAssessmentViewApi

class ArnsAssessmentViewApiTest {
  @Test
  fun `application class can be instantiated`() {
    assertNotNull(ArnsAssessmentViewApi())
  }
}
