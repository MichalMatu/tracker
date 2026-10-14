package io.blueeye.core.connectivity.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GattEventSessionPolicyTest {
    @Test
    fun `closed or replaced GATT connection cannot replay old events`() {
        assertTrue(GattEventSessionPolicy.isCurrent(eventEpoch = 7L, currentEpoch = 7L))
        assertFalse(GattEventSessionPolicy.isCurrent(eventEpoch = 7L, currentEpoch = 8L))
        assertFalse(GattEventSessionPolicy.isCurrent(eventEpoch = 6L, currentEpoch = 8L))
    }
}
