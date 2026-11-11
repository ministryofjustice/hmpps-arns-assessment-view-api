package uk.gov.justice.digital.hmpps.arnsassessmentviewapi.e2e.helpers

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import org.springframework.test.web.reactive.server.EntityExchangeResult
import kotlin.collections.forEach

class FailedTestLogger : TestWatcher {

  companion object {
    private val exchangeResults = ThreadLocal<MutableList<EntityExchangeResult<*>>>()

    fun record(result: EntityExchangeResult<*>) {
      exchangeResults.get()?.add(result) ?: run {
        exchangeResults.set(mutableListOf(result))
      }
    }

    fun clear() {
      exchangeResults.remove()
    }
  }

  override fun testFailed(context: ExtensionContext, cause: Throwable?) {
    val logs = exchangeResults.get()
    if (!logs.isNullOrEmpty()) {
      println("=== HTTP EXCHANGES FOR FAILED TEST: ${context.displayName} ===")
      logs.forEach { println(it) }
    }
    clear()
  }

  override fun testSuccessful(context: ExtensionContext) {
    clear() // Discard logs if test passes
  }
}
