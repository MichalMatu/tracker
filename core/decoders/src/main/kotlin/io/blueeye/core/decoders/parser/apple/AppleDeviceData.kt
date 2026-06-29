package io.blueeye.core.decoders.parser.apple

data class AppleDeviceData(
    val deviceModel: String? = null,
    val statusFlags: Int? = null,
    // Nearby Info (0x10)
    val nearbyStatusFlags: Int? = null,
    val nearbyActionCode: Int? = null,
    val nearbyActionDescription: String? = null,
    // Nearby Action / Wi‑Fi Join (0x0F)
    val wifiSsidHashPrefix: ByteArray? = null,
    // Handoff (0x0C)
    val handoffIv: Int? = null,
    // Proximity Pairing (0x07)
    val proximitySubtype: Int? = null,
    // Tethering (0x0D/0x0E)
    val tetheringType: Int? = null,
    val tetheringPayload: ByteArray? = null,
    val batteryLevelLeft: Int? = null,
    val batteryLevelRight: Int? = null,
    val batteryLevelCase: Int? = null,
    val findMyKey: ByteArray? = null, // Public Search Key
    val airDropHash: ByteArray? = null,
    val airDropMode: String? = null,
    // HomeKit (0x06)
    val homeKitStatus: Int? = null,
    // AirPlay (0x09)
    val airPlayFlags: Int? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AppleDeviceData

        if (deviceModel != other.deviceModel) return false
        if (statusFlags != other.statusFlags) return false
        if (nearbyStatusFlags != other.nearbyStatusFlags) return false
        if (nearbyActionCode != other.nearbyActionCode) return false
        if (nearbyActionDescription != other.nearbyActionDescription) return false
        if (handoffIv != other.handoffIv) return false
        if (proximitySubtype != other.proximitySubtype) return false
        if (tetheringType != other.tetheringType) return false
        if (batteryLevelLeft != other.batteryLevelLeft) return false
        if (batteryLevelRight != other.batteryLevelRight) return false
        if (batteryLevelCase != other.batteryLevelCase) return false
        if (wifiSsidHashPrefix != null) {
            if (other.wifiSsidHashPrefix == null) return false
            if (!wifiSsidHashPrefix.contentEquals(other.wifiSsidHashPrefix)) return false
        } else if (other.wifiSsidHashPrefix != null) {
            return false
        }
        if (tetheringPayload != null) {
            if (other.tetheringPayload == null) return false
            if (!tetheringPayload.contentEquals(other.tetheringPayload)) return false
        } else if (other.tetheringPayload != null) {
            return false
        }
        if (findMyKey != null) {
            if (other.findMyKey == null) return false
            if (!findMyKey.contentEquals(other.findMyKey)) return false
        } else if (other.findMyKey != null) {
            return false
        }
        if (airDropHash != null) {
            if (other.airDropHash == null) return false
            if (!airDropHash.contentEquals(other.airDropHash)) return false
        } else if (other.airDropHash != null) {
            return false
        }
        if (airDropMode != other.airDropMode) return false
        if (homeKitStatus != other.homeKitStatus) return false
        if (airPlayFlags != other.airPlayFlags) return false

        return true
    }

    override fun hashCode(): Int {
        var result = deviceModel?.hashCode() ?: 0
        result = 31 * result + (statusFlags ?: 0)
        result = 31 * result + (nearbyStatusFlags ?: 0)
        result = 31 * result + (nearbyActionCode ?: 0)
        result = 31 * result + (nearbyActionDescription?.hashCode() ?: 0)
        result = 31 * result + (wifiSsidHashPrefix?.contentHashCode() ?: 0)
        result = 31 * result + (handoffIv ?: 0)
        result = 31 * result + (proximitySubtype ?: 0)
        result = 31 * result + (tetheringType ?: 0)
        result = 31 * result + (tetheringPayload?.contentHashCode() ?: 0)
        result = 31 * result + (batteryLevelLeft ?: 0)
        result = 31 * result + (batteryLevelRight ?: 0)
        result = 31 * result + (batteryLevelCase ?: 0)
        result = 31 * result + (findMyKey?.contentHashCode() ?: 0)
        result = 31 * result + (airDropHash?.contentHashCode() ?: 0)
        result = 31 * result + (airDropMode?.hashCode() ?: 0)
        result = 31 * result + (homeKitStatus ?: 0)
        result = 31 * result + (airPlayFlags ?: 0)
        return result
    }
}
