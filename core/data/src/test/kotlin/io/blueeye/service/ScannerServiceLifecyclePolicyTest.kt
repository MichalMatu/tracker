package io.blueeye.service

import io.blueeye.core.domain.scanner.ScannerRuntimeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerServiceLifecyclePolicyTest {
    @Test
    fun `start is idempotent while starting or running`() {
        assertTrue(ScannerServiceLifecyclePolicy.shouldStart(ScannerRuntimeState.Idle, startupInProgress = false))
        assertFalse(ScannerServiceLifecyclePolicy.shouldStart(ScannerRuntimeState.Starting, startupInProgress = true))
        assertFalse(ScannerServiceLifecyclePolicy.shouldStart(ScannerRuntimeState.Running, startupInProgress = false))
    }

    @Test
    fun `stop is idempotent when already idle`() {
        assertFalse(ScannerServiceLifecyclePolicy.shouldStop(ScannerRuntimeState.Idle, startupInProgress = false))
        assertTrue(ScannerServiceLifecyclePolicy.shouldStop(ScannerRuntimeState.Starting, startupInProgress = true))
        assertTrue(ScannerServiceLifecyclePolicy.shouldStop(ScannerRuntimeState.Running, startupInProgress = false))
    }

    @Test
    fun `scan mode switching requires owned running service`() {
        assertFalse(ScannerServiceLifecyclePolicy.canSwitchScanMode(ScannerRuntimeState.Idle))
        assertFalse(ScannerServiceLifecyclePolicy.canSwitchScanMode(ScannerRuntimeState.Starting))
        assertTrue(ScannerServiceLifecyclePolicy.canSwitchScanMode(ScannerRuntimeState.Running))
    }

    @Test
    fun `stop invalidates stale startup across repeated restart cycles`() {
        val ownership = ScannerStartupOwnership()

        repeat(20) {
            val staleToken = ownership.beginStartup()
            ownership.invalidate { }
            val freshToken = ownership.beginStartup()

            assertFalse(ownership.runIfCurrent(staleToken) { error("stale startup executed") })
            var freshStarted = false
            assertTrue(ownership.runIfCurrent(freshToken) { freshStarted = true })
            assertTrue(freshStarted)
        }
    }
}
