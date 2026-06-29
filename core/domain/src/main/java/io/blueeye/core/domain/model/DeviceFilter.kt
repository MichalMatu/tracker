package io.blueeye.core.domain.model

import io.blueeye.core.model.DeviceType

/**
 * Filter configuration for the Radar device list. Supports multi-select for technologies, device
 * types, and vendors.
 */
data class DeviceFilter(
    /** Selected technology filters (empty = all) */
    val technologies: Set<TechnologyFilter> = emptySet(),
    /** Selected device types (empty = all) */
    val deviceTypes: Set<DeviceType> = emptySet(),
    /** Selected vendor names (empty = all) */
    val vendors: Set<String> = emptySet(),
    /** Show only connectable devices */
    val onlyConnectable: Boolean = false,
    /** Hide devices with UNKNOWN type */
    val hideUnknown: Boolean = false,
) {
    /** Returns true if any filter is active */
    fun isActive(): Boolean {
        return technologies.isNotEmpty() ||
            deviceTypes.isNotEmpty() ||
            vendors.isNotEmpty() ||
            onlyConnectable ||
            hideUnknown
    }

    /** Count of active filter categories */
    fun activeFilterCount(): Int {
        var count = 0
        if (technologies.isNotEmpty()) count++
        if (deviceTypes.isNotEmpty()) count++
        if (vendors.isNotEmpty()) count++
        if (onlyConnectable) count++
        if (hideUnknown) count++
        return count
    }
}

/** Bluetooth technology types for filtering. */
enum class TechnologyFilter(val displayName: String, val technologyValues: List<String>) {
    BLE_4("Legacy BLE", listOf("BLE")),
    BLE_5("BLE Extended / Coded", listOf("BLE_5", "BLE 5+ (Ext)", "BLE 5+ (Phy)")),
    CLASSIC("Bluetooth Classic", listOf("CLASSIC")),
    ;

    companion object {
        fun fromTechnology(technology: String): TechnologyFilter? {
            return entries.find { it.technologyValues.contains(technology) }
        }
    }
}
