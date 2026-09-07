package io.blueeye.service

import io.blueeye.core.domain.scanner.ScannerRuntimeState

internal object ScannerServiceLifecyclePolicy {
    fun shouldStart(
        state: ScannerRuntimeState,
        startupInProgress: Boolean,
    ): Boolean =
        !startupInProgress &&
            state !is ScannerRuntimeState.Starting &&
            state !is ScannerRuntimeState.Running

    fun shouldStop(
        state: ScannerRuntimeState,
        startupInProgress: Boolean,
    ): Boolean = startupInProgress || state !is ScannerRuntimeState.Idle

    fun canSwitchScanMode(state: ScannerRuntimeState): Boolean = state is ScannerRuntimeState.Running
}

internal class ScannerStartupOwnership {
    private var generation = 0L

    @Synchronized
    fun beginStartup(): Long {
        generation += 1
        return generation
    }

    @Synchronized
    fun runIfCurrent(
        token: Long,
        block: () -> Unit,
    ): Boolean {
        if (token != generation) return false
        block()
        return true
    }

    @Synchronized
    fun invalidate(block: () -> Unit) {
        generation += 1
        block()
    }

    @Synchronized
    fun isCurrent(token: Long): Boolean = token == generation
}
