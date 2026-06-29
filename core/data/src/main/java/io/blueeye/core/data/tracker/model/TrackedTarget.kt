package io.blueeye.core.data.tracker.model

/** Represents a tracked target that may have multiple MAC addresses. */
data class TrackedTarget(
    val targetId: String, // Internal stable ID
    val primaryMac: String, // First seen MAC
    val macAliases: MutableSet<String>, // Other MACs that belong to this target
    var lastPayloadHash: Int, // Hash of manufacturer data
    var lastPayload: ByteArray?, // Raw payload for fuzzy matching
    var lastServiceUuids: Set<String>, // Service UUIDs
    var lastDeviceName: String?, // Device name
    var lastRssi: Int, // Last RSSI
    var lastSeenAt: Long, // Timestamp
    var riskScore: Int = 0, // Follow-me risk score
    var macChangeCount: Int = 0, // Number of MAC changes detected
    var lastAdvertisingInterval: Long? = null, // Advertising Interval (0x1A) or calculated
)
