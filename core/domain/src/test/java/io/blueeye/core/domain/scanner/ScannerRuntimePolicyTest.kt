package io.blueeye.core.domain.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScannerRuntimePolicyTest {
    @Test
    fun `stable core disables advanced automatic collection paths`() {
        assertEquals(ScannerRuntimeProfile.STABLE_CORE, ScannerRuntimePolicy.profile)
        assertFalse(ScannerRuntimePolicy.allowsClassicDiscovery)
        assertFalse(ScannerRuntimePolicy.allowsAutomaticActiveProbe)
        assertFalse(ScannerRuntimePolicy.allowsAutomaticRfcommProbe)
        assertFalse(ScannerRuntimePolicy.allowsAutomaticPublicSafetyAlertSideEffects)
    }

    @Test
    fun `scanner diagnostics expose stable core by default`() {
        assertEquals(
            ScannerRuntimeProfile.STABLE_CORE,
            ScannerRuntimeDiagnostics().runtimeProfile,
        )
    }
}
