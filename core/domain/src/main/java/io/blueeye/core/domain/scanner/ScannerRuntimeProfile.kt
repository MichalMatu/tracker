package io.blueeye.core.domain.scanner

enum class ScannerRuntimeProfile {
    STABLE_CORE,
}

object ScannerRuntimePolicy {
    val profile: ScannerRuntimeProfile = ScannerRuntimeProfile.STABLE_CORE
    const val allowsClassicDiscovery: Boolean = false
    const val allowsAutomaticActiveProbe: Boolean = false
    const val allowsAutomaticRfcommProbe: Boolean = false
    const val allowsAutomaticPublicSafetyAlertSideEffects: Boolean = false
}
