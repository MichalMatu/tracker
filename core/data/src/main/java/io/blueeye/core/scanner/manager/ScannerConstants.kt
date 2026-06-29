package io.blueeye.core.scanner.manager

/**
 * Constants for scanner module configuration.
 * Centralizes "magic numbers" for easier tuning and maintenance.
 */
object ScannerConstants {
    // === BLE Scanner ===

    /** Delay after stopping scan before starting a new one (Bluetooth stack stabilization) */
    const val SCAN_TRANSITION_DELAY_MS = 500L

    // === Throttling ===

    /** Minimum interval between database updates for the same device (ms) */
    const val DB_UPDATE_THROTTLE_MS = 1000L

    /** High-frequency update interval for tactical/priority devices (ms) */
    const val TACTICAL_UPDATE_THROTTLE_MS = 100L

    /** Minimum interval between signal sample recordings (ms) */
    const val SIGNAL_SAMPLE_THROTTLE_MS = 2000L
}
