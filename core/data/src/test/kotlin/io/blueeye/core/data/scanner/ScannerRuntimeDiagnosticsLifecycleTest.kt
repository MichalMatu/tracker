package io.blueeye.core.data.scanner

import io.blueeye.core.domain.scanner.ScannerLifecycleTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerRuntimeDiagnosticsLifecycleTest {
    @Test
    fun `lifecycle transition is retained with timestamp and count`() {
        val before = ScannerRuntimeDiagnosticsStore.diagnostics.value.lifecycleTransitionCount

        ScannerRuntimeDiagnosticsStore.recordLifecycleTransition(ScannerLifecycleTransition.START_REQUESTED)

        val diagnostics = ScannerRuntimeDiagnosticsStore.diagnostics.value
        assertEquals(ScannerLifecycleTransition.START_REQUESTED, diagnostics.lastLifecycleTransition)
        assertEquals(before + 1, diagnostics.lifecycleTransitionCount)
        assertTrue((diagnostics.lastLifecycleTransitionAt ?: 0L) > 0L)
    }
}
