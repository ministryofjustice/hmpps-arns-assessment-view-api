package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers.aap.request

data class CommandsRequest(
  val commands: List<Command>,
) {
  companion object {
    fun of(vararg commands: Command) = CommandsRequest(commands.toList())
  }
}
