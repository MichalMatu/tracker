package io.blueeye.core.alert

import io.blueeye.core.domain.alert.AlertDeliveryResult
import io.blueeye.core.domain.alert.AlertDeliveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertDeliveryHistoryTest {
    @Test
    fun `no attempt yet is not a policy-blocked alert`() {
        assertTrue(AlertDeliveryHistory().snapshot().isEmpty())
        assertEquals(AlertDeliveryStatus.NOT_ATTEMPTED, AlertDeliveryResult().status)
    }

    @Test
    fun `history is bounded and preserves chronology`() {
        val history = AlertDeliveryHistory(capacity = 3)
        (1L..5L).forEach { time ->
            history.record(
                AlertDeliveryResult(
                    status = AlertDeliveryStatus.COOLED_DOWN,
                    timestamp = time,
                ),
            )
        }
        assertEquals(listOf(3L, 4L, 5L), history.snapshot().map { it.timestamp })
    }
}
