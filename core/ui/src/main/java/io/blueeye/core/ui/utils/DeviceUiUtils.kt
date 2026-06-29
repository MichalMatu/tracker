package io.blueeye.core.ui.utils

import android.graphics.Color
import io.blueeye.core.model.DeviceType
import io.blueeye.core.ui.R
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/** UI utility functions shared across features. */
object DeviceUiUtils {
    private const val ONE_SECOND_MS = 1000L
    private const val ONE_MINUTE_MS = 60000L
    private const val ONE_HOUR_MS = 3600000L
    private const val PATH_LOSS_EXPONENT = 2.5
    private const val TX_POWER_DEFAULT = -65
    private const val TX_POWER_OFFSET = 60
    private const val TX_POWER_THRESHOLD = -30
    private const val DISTANCE_FAR = 50.0
    private const val DISTANCE_NEAR = 10.0
    private const val PATH_LOSS_DIVISOR = 10.0

    /** Adjusts the alpha channel of a color. */
    fun adjustAlpha(
        color: Int,
        factor: Float,
    ): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    /** Formats a time difference into a human-readable short string. */
    fun formatTimeDiff(diffMillis: Long): String {
        return when {
            diffMillis < ONE_SECOND_MS -> "now"
            diffMillis < ONE_MINUTE_MS -> "${TimeUnit.MILLISECONDS.toSeconds(diffMillis)}s"
            diffMillis < ONE_HOUR_MS -> "${TimeUnit.MILLISECONDS.toMinutes(diffMillis)}m"
            else -> ">1h"
        }
    }

    /**
     * Calculates an approximate distance based on RSSI and TX Power. Uses the path-loss model with
     * a standard exponent.
     */
    fun calculateDistance(
        txPower: Int?,
        rssi: Int,
    ): String {
        val txAt1m =
            when {
                txPower == null -> TX_POWER_DEFAULT
                txPower > TX_POWER_THRESHOLD -> txPower - TX_POWER_OFFSET
                else -> txPower
            }
        val powerDiff = txAt1m - rssi
        if (powerDiff <= 0) return "< 1m"

        val dist = PATH_LOSS_DIVISOR.pow(powerDiff / (PATH_LOSS_DIVISOR * PATH_LOSS_EXPONENT))
        return when {
            dist > DISTANCE_FAR -> "> 50m"
            dist >= DISTANCE_NEAR -> "~${dist.toInt()}m"
            else -> "~%.1fm".format(dist)
        }
    }

    /** Maps a DeviceType enum to a display string. */
    fun mapDeviceTypeToString(type: DeviceType): String {
        return when (type) {
            DeviceType.POLICE -> "Public-safety-like signal"
            DeviceType.AXON -> "Axon-like signal"
            DeviceType.BODY_CAMERA -> "Body-camera-like signal"
            DeviceType.TACTICAL_AUDIO -> "Professional audio signal"
            DeviceType.TACTICAL_RADIO -> "Professional radio signal"
            DeviceType.TACTICAL_EUD -> "Professional terminal signal"
            DeviceType.HOLSTER_SENSOR -> "Holster-sensor-like signal"
            DeviceType.SMART_WEAPON -> "Smart equipment signal"
            DeviceType.VEHICLE_ROUTER -> "Vehicle router signal"
            DeviceType.DOCUMENT_READER -> "Document reader signal"
            DeviceType.FIREFIGHTER -> "Fire telemetry signal"
            DeviceType.TACTICAL -> "Professional equipment signal"
            else -> type.name.replace("_", " ")
        }
    }

    /** Returns the drawable resource ID for a given device type. */
    @Suppress("CyclomaticComplexMethod")
    fun getIconForType(type: DeviceType): Int {
        return when (type) {
            DeviceType.PHONE -> R.drawable.ic_smartphone
            DeviceType.LAPTOP,
            DeviceType.PC,
            DeviceType.POS,
            DeviceType.PRINTER,
            DeviceType.MEDICAL,
            -> R.drawable.ic_laptop
            DeviceType.HEADPHONES -> R.drawable.ic_headphones
            DeviceType.SPEAKER -> R.drawable.ic_speaker
            DeviceType.WEARABLE, DeviceType.WATCH, DeviceType.FITNESS -> R.drawable.ic_watch
            DeviceType.TV, DeviceType.CONSOLE, DeviceType.GAMING -> R.drawable.ic_tv
            DeviceType.TABLET -> R.drawable.ic_tablet
            DeviceType.CAR, DeviceType.CAR_AUDIO, DeviceType.TACHOGRAPH ->
                R.drawable.ic_car
            // Tactical Icons
            DeviceType.POLICE -> R.drawable.ic_shield // Police -> Shield
            DeviceType.TACTICAL_RADIO,
            DeviceType.TACTICAL_AUDIO,
            DeviceType.TACTICAL_EUD,
            -> R.drawable.ic_headphones
            DeviceType.CAMERA, DeviceType.AXON, DeviceType.BODY_CAMERA, DeviceType.DRONE -> R.drawable.ic_camera
            DeviceType.SMART_WEAPON, DeviceType.HOLSTER_SENSOR -> R.drawable.ic_tracker // Weapon -> Target/Tracker

            DeviceType.AIRTAG,
            DeviceType.TILE,
            DeviceType.SAMSUNG_TAG,
            DeviceType.TAG,
            DeviceType.BEACON,
            -> R.drawable.ic_tracker
            DeviceType.SMART_HOME, DeviceType.ACCESS_CONTROL -> R.drawable.ic_home
            else -> R.drawable.ic_bluetooth
        }
    }
}
