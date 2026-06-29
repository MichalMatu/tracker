package io.blueeye.core.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the "Active Probe" state to avoid circular dependencies between
 * BleScanHandler and DeviceRepository.
 */
@Singleton
class ProbeStateManager @Inject constructor() {
    private val _activeProbe = MutableStateFlow<String?>(null)
    val activeProbe: StateFlow<String?> = _activeProbe.asStateFlow()

    fun setActiveProbe(mac: String?) {
        _activeProbe.value = mac
    }
}
