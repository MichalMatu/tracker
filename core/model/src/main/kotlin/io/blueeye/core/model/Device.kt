package io.blueeye.core.model

/**
 * Domenowy model urządzenia Bluetooth.
 * Używany w całej aplikacji (UI, UseCases) zamiast encji bazodanowej.
 */
data class Device(
    val fingerprint: String,
    val macAddress: String,
    val macAddressType: MacAddressType,
    val technology: String,
    val name: String?,
    val deviceType: DeviceType,
    val vendorName: String?,
    val predictedModel: String?,
    // Status
    val trackingStatus: TrackingStatus,
    val followingScore: Float,
    val isSafeBeacon: Boolean,
    // Watchlist
    val isInWatchlist: Boolean,
    val userAlias: String?,
    val userNotes: String?,
    val alertSound: Boolean,
    val alertVibration: Boolean,
    val isTrackingEnabled: Boolean = true,
    /** Czy urządzenie jest ignorowane przez algorytm śledzenia (ręczny whitelist) */
    val isIgnoredForTracking: Boolean = false,
    // Stats
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val rssi: Int = 0,
    val encounterCount: Int,
    val sensorData: String? = null,
    val txPower: Int? = null,
    val isConnectable: Boolean? = null,
    val primaryPhy: Int? = null,
    val secondaryPhy: Int? = null,
    val advertisingIntervalMs: Long? = null,
    val beaconType: String? = null,
    // === Active Reconnaissance (GATT Probing) ===
    val connectionStatus: String = "NONE",
    val connectionAttempts: Int = 0,
    val lastProbeTimestamp: Long = 0,
    // Dane wyciągnięte z Device Info Service
    val modelNumber: String? = null,
    val serialNumber: String? = null,
    val firmwareRevision: String? = null,
    val hardwareRevision: String? = null,
    val softwareRevision: String? = null,
    val manufacturerName: String? = null,
    // Dane z Battery Service
    val batteryLevel: Int? = null,
    // Lista znalezionych usług
    val gattServices: String? = null,
    // Dane wszystkich charakterystyk
    val characteristicData: String? = null,
    val probeError: String? = null,
    val lastRawData: String? = null,
    val calibrationLabel: DeviceCalibrationLabel = DeviceCalibrationLabel.UNKNOWN,
    val identityCarryoverVerdict: IdentityCarryoverVerdict = IdentityCarryoverVerdict.UNREVIEWED,
    val evidence: List<DetectionEvidence> = emptyList(),
) {
    /** Zwraca najlepszą dostępną nazwę do wyświetlenia */
    fun getDisplayName(): String {
        return userAlias ?: name ?: predictedModel ?: vendorName?.let { "$it Device" } ?: macAddress
    }
}
