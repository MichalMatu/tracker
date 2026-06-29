package io.blueeye.core.data.tracker.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofingDetectorTest {

    @Test
    fun `flags anomaly only after enough unique fingerprints`() {
        val detector = SpoofingDetector()

        repeat(UNIQUE_THRESHOLD - 1) { index ->
            assertFalse(detector.onDeviceIdentified(NOW + index, "Known Model $index"))
        }

        assertFalse(detector.hasFingerprintAnomaly)
        assertTrue(detector.onDeviceIdentified(NOW + UNIQUE_THRESHOLD, "Known Model $UNIQUE_THRESHOLD"))
        assertTrue(detector.hasFingerprintAnomaly)
        assertTrue(detector.getAnomalyDetails().contains("Fingerprint anomaly"))
    }

    @Test
    fun `clears anomaly when old fingerprints leave detection window`() {
        val detector = SpoofingDetector()

        repeat(UNIQUE_THRESHOLD) { index ->
            detector.onDeviceIdentified(NOW + index, "Known Model $index")
        }

        assertTrue(detector.hasFingerprintAnomaly)
        assertFalse(detector.onDeviceIdentified(NOW + WINDOW_MS + UNIQUE_THRESHOLD, "Current Model"))
        assertFalse(detector.hasFingerprintAnomaly)
    }

    private companion object {
        private const val NOW = 1_750_000_000_000L
        private const val WINDOW_MS = 15_000L
        private const val UNIQUE_THRESHOLD = 20
    }
}
