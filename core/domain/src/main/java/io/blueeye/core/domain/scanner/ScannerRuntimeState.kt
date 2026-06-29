package io.blueeye.core.domain.scanner

sealed interface ScannerRuntimeState {
    object Idle : ScannerRuntimeState

    object Starting : ScannerRuntimeState

    object Running : ScannerRuntimeState

    data class Error(val message: String) : ScannerRuntimeState
}
