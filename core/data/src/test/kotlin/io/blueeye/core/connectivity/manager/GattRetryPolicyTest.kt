package io.blueeye.core.connectivity.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GattRetryPolicyTest {
    @Test
    fun `retries only transient failures before GATT discovery once`() {
        for (status in listOf(133, 147)) {
            assertTrue(GattRetryPolicy.shouldRetry(status, 0, false, 1_000L))
            assertFalse(GattRetryPolicy.shouldRetry(status, 1, false, 1_000L))
            assertFalse(GattRetryPolicy.shouldRetry(status, 0, true, 1_000L))
        }
    }

    @Test
    fun `never retries after timeout near finish or for unrelated failures`() {
        assertFalse(GattRetryPolicy.shouldRetry(8, 0, false, 1_000L))
        assertFalse(GattRetryPolicy.shouldRetry(null, 0, false, 1_000L))
        assertFalse(GattRetryPolicy.shouldRetry(133, 0, false, -1L))
        assertFalse(GattRetryPolicy.shouldRetry(147, 0, false, 11_000L))
    }
}
