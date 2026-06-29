package io.blueeye.core.data.repository.handler.ble

import io.blueeye.core.model.SensorData

/** Formats decoded sensor data into a human-readable string for storage/display. */
object SensorDataFormatter {
    /**
     * Formats SensorData into a display string. Returns null if no meaningful data is available.
     */
    fun format(sensorData: SensorData?): String? {
        if (sensorData == null) return null

        return buildString {
            sensorData.sensorStatus?.let { s -> append("$s ") }
            sensorData.temperatureCelcius?.let { t -> append("Temp: %.1f°C ".format(t)) }
            sensorData.humidityPercent?.let { h -> append("Hum: %.1f%% ".format(h)) }
            sensorData.pressureHpa?.let { p -> append("Pres: %.1f hPa ".format(p)) }
            sensorData.voltageV?.let { v -> append("Volt: %.2fV ".format(v)) }
            sensorData.batteryLevel?.let { b -> append("Bat: $b% ") }
            sensorData.weightKg?.let { w -> append("Wgt: %.2f kg ".format(w)) }
        }
            .trim()
            .takeIf { it.isNotEmpty() }
    }
}
