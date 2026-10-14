package io.blueeye.core.alert

import io.blueeye.core.domain.alert.AlertDeliveryResult

/** Bounded process-local delivery decisions. Never stores alert keys in exports. */
internal class AlertDeliveryHistory(private val capacity: Int = MAX_RESULTS) {
    private val lock = Any()
    private val results = ArrayDeque<AlertDeliveryResult>()

    init {
        require(capacity > 0)
    }

    fun record(result: AlertDeliveryResult) {
        synchronized(lock) {
            results.addLast(result)
            while (results.size > capacity) results.removeFirst()
        }
    }

    fun snapshot(): List<AlertDeliveryResult> = synchronized(lock) { results.toList() }

    private companion object {
        const val MAX_RESULTS = 20
    }
}
