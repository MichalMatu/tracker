package io.blueeye.feature.details

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsFocusedScanPolicyTest {
    @Test
    fun `focused scan is available for ble devices`() {
        assertTrue(DetailsFocusedScanPolicy.canStartFocusedScan("BLE"))
    }

    @Test
    fun `focused scan is not available for classic devices`() {
        assertFalse(DetailsFocusedScanPolicy.canStartFocusedScan("CLASSIC"))
        assertFalse(DetailsFocusedScanPolicy.canStartFocusedScan("classic"))
    }
}
