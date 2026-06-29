package io.blueeye.core.data.repository.handler.common

import io.blueeye.core.model.DeviceType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceTypePriorityHelper @Inject constructor() {

    fun resolveBetterType(current: DeviceType, candidate: DeviceType): DeviceType {
        // If candidate is UNKNOWN, never downgrade unless current is also UNKNOWN
        if (candidate == DeviceType.UNKNOWN) return current
        
        // If current is UNKNOWN, always upgrade
        if (current == DeviceType.UNKNOWN) return candidate

        val currentPriority = getPriority(current)
        val candidatePriority = getPriority(candidate)

        val result =
            when {
                candidatePriority > currentPriority -> candidate
                candidatePriority == currentPriority && isSameFamily(current, candidate) -> candidate
                else -> current
            }
        
        if (result != candidate) {
             android.util.Log.i("DeviceTypePriority", "Prevented downgrade: $current ($currentPriority) kept over $candidate ($candidatePriority)")
        }
        
        return result
    }

    private fun getPriority(type: DeviceType): Int {
        return when (type) {
            // Tier 3: High Confidence / Specific Hardware
            DeviceType.PHONE,
            DeviceType.LAPTOP,
            DeviceType.PC,
            DeviceType.TABLET,
            DeviceType.CONSOLE,
            DeviceType.TV,
            DeviceType.CAR,
            DeviceType.CAR_AUDIO,
            DeviceType.HEADPHONES,
            DeviceType.WATCH,
            DeviceType.WEARABLE,
            DeviceType.SPEAKER,
            DeviceType.CAMERA,
            DeviceType.BODY_CAMERA,
            DeviceType.PRINTER,
            DeviceType.DRONE,
            DeviceType.SMART_HOME -> 3

            // Tier 2: Generic / Beacons / Trackers (Often inferred from proto)
            DeviceType.TRACKER,
            DeviceType.AIRTAG,
            DeviceType.TILE,
            DeviceType.SAMSUNG_TAG,
            DeviceType.TAG,
            DeviceType.BEACON,
            DeviceType.FITNESS,
            DeviceType.MEDICAL,
            DeviceType.ACCESS_CONTROL,
            DeviceType.POS,
            DeviceType.POLICE,
            DeviceType.FIREFIGHTER,
            DeviceType.TACTICAL,
            DeviceType.TACTICAL_AUDIO,
            DeviceType.TACTICAL_RADIO,
            DeviceType.TACTICAL_EUD -> 2

            // Tier 1: Broad Categories
            DeviceType.AUDIO,
            DeviceType.AUDIO_VIDEO,
            DeviceType.PERIPHERAL,
            DeviceType.SENSOR, 
            DeviceType.VEHICLE_ROUTER,
            DeviceType.DOCUMENT_READER -> 1

            // Tier 0: Unknown
            DeviceType.UNKNOWN -> 0
            
            else -> 1 
        }
    }

    private fun isSameFamily(
        current: DeviceType,
        candidate: DeviceType,
    ): Boolean = family(current) == family(candidate)

    private fun family(type: DeviceType): TypeFamily =
        when (type) {
            DeviceType.PHONE,
            DeviceType.TABLET,
            -> TypeFamily.MOBILE
            DeviceType.LAPTOP,
            DeviceType.PC,
            -> TypeFamily.COMPUTER
            DeviceType.HEADPHONES,
            DeviceType.SPEAKER,
            DeviceType.AUDIO,
            DeviceType.AUDIO_VIDEO,
            DeviceType.CAR_AUDIO,
            -> TypeFamily.AUDIO
            DeviceType.TV,
            DeviceType.CONSOLE,
            DeviceType.GAMING,
            -> TypeFamily.MEDIA
            DeviceType.WATCH,
            DeviceType.WEARABLE,
            DeviceType.FITNESS,
            -> TypeFamily.WEARABLE
            DeviceType.AIRTAG,
            DeviceType.TILE,
            DeviceType.SAMSUNG_TAG,
            DeviceType.TAG,
            DeviceType.TRACKER,
            DeviceType.BEACON,
            -> TypeFamily.TRACKER
            DeviceType.CAR,
            DeviceType.VEHICLE_ROUTER,
            -> TypeFamily.VEHICLE
            else -> TypeFamily.OTHER
        }

    private enum class TypeFamily {
        AUDIO,
        COMPUTER,
        MEDIA,
        MOBILE,
        OTHER,
        TRACKER,
        VEHICLE,
        WEARABLE,
    }
}
