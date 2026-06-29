package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.Device

data class RadarParsedSensorData(
    val batteryText: String? = null,
    val temperatureText: String? = null,
    val humidityText: String? = null,
    val voltageText: String? = null,
    val extraText: String? = null
)

@Suppress("CyclomaticComplexMethod")
object RadarSensorDataParser {
    private val TEMP_REGEX = Regex("Temp:?\\s*(-?\\d+[.,]?\\d*)\\s*°C", RegexOption.IGNORE_CASE)
    private val HUM_REGEX = Regex("Hum[idity]*:?\\s*(\\d+[.,]?\\d*)\\s*%", RegexOption.IGNORE_CASE)
    private val VOLT_REGEX = Regex("Volt[age]*:?\\s*(\\d+[.,]?\\d*)\\s*V", RegexOption.IGNORE_CASE)
    private val CO2_REGEX = Regex("CO2:?\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val NC_REGEX = Regex("NC:\\s*([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
    private val AUTO_OFF_REGEX = Regex("AutoOff:\\s*(\\d+\\s*min|Never)", RegexOption.IGNORE_CASE)
    private val LANG_REGEX = Regex("Lang:\\s*([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)

    // Clean string regexes
    private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val SHORT_UUID_PATTERN = Regex("0x[0-9a-fA-F]{2,4}")
    private val HEX_PATTERN = Regex(",?\\s*[0-9a-fA-F]{4,}")
    private val BATTERY_PATTERN = Regex("\\|?\\s*Bat[tery]*:?\\s*\\d*%?", RegexOption.IGNORE_CASE)
    private val LANG_PATTERN = Regex("\\|?\\s*Lang:?\\s*", RegexOption.IGNORE_CASE)
    private val LEADING_DIGITS_PATTERN = Regex("^\\d+%?\\s*[|•-]?\\s*")
    private val CLEANUP_PATTERN = Regex("^[,|\\s]+|[,|\\s]+$")
    private val COMMA_PATTERN = Regex(",\\s*,")
    private val PIPE_PATTERN = Regex("\\|\\s*\\|")
    private val REMOVE_BATTERY_FROM_SENSOR = Regex("\\|?\\s*Bat[tery]*:?\\s*\\d*%?", RegexOption.IGNORE_CASE)

    fun parse(device: Device): RadarParsedSensorData {
        var batteryText: String? = null
        var temperatureText: String? = null
        var humidityText: String? = null
        var voltageText: String? = null
        var extraText: String? = null

        device.batteryLevel?.let { batteryText = "🔋$it%" }

        device.sensorData?.let { sensor ->
            TEMP_REGEX.find(sensor)?.let { temperatureText = "${it.groupValues[1]}°C" }
            HUM_REGEX.find(sensor)?.let { humidityText = "${it.groupValues[1]}%💧" }
            VOLT_REGEX.find(sensor)?.let { voltageText = "${it.groupValues[1]}V⚡" }
            CO2_REGEX.find(sensor)?.let { extraText = "CO₂ ${it.groupValues[1]}" }
        }

        device.gattServices?.let { services ->
            // Bose debug logging kept? Maybe remove if not needed, but for now extracting logic.

            val extras = mutableListOf<String>()
            NC_REGEX.find(services)?.let { extras.add("NC: ${it.groupValues[1]}") }
            AUTO_OFF_REGEX.find(services)?.let { extras.add("OFF: ${it.groupValues[1].replace(" min", "m")}") }
            LANG_REGEX.find(services)?.let { extras.add("LN: ${it.groupValues[1]}") }

            if (extras.isNotEmpty()) {
                val boseInfo = extras.joinToString(" | ")
                extraText = if (extraText != null) "$extraText | $boseInfo" else boseInfo
            }
        }

        // TACTICAL INFO (from beaconType)
        device.beaconType?.let { beaconInfo ->
            extraText = if (extraText != null) "$beaconInfo | $extraText" else beaconInfo
        }

        return RadarParsedSensorData(batteryText, temperatureText, humidityText, voltageText, extraText)
    }

    fun formatFullSensorString(device: Device): String? {
        val parts = mutableListOf<String>()

        // Battery
        device.batteryLevel?.let { parts.add("🔋 $it%") }

        // Model
        device.modelNumber?.takeIf { it.isNotBlank() && it != device.name }?.let { parts.add(it) }

        // Firmware
        device.firmwareRevision?.takeIf { it.isNotBlank() }?.let { parts.add("v$it") }

        // Manufacturer (if different from vendor)
        device.manufacturerName?.takeIf {
            it.isNotBlank() && it.lowercase() != device.vendorName?.lowercase()
        }?.let { parts.add("by $it") }

        // Cleaned Services
        device.gattServices?.let { services ->
            if (services.isNotBlank() && services != "null" && !services.contains(":[")) {
                val cleaned = cleanServiceString(services)
                if (cleaned.isNotBlank()) parts.add(cleaned)
            }
        }

        // Sensor Data
        device.sensorData?.let { sensor ->
            if (sensor.isNotBlank() && !sensor.contains("MfgData:")) {
                var cleaned = sensor
                if (device.batteryLevel != null) {
                    cleaned = cleaned.replace(REMOVE_BATTERY_FROM_SENSOR, "")
                }
                cleaned = cleaned.trim(',', '|', ' ').replace(CLEANUP_PATTERN, "")
                if (cleaned.isNotBlank()) parts.add(cleaned)
            }
        }

        return if (parts.isNotEmpty()) parts.joinToString(" | ") else null
    }

    private fun cleanServiceString(raw: String): String {
        return raw
            .replace(UUID_PATTERN, "")
            .replace(SHORT_UUID_PATTERN, "")
            .replace(LEADING_DIGITS_PATTERN, "")
            .replace(HEX_PATTERN, "")
            .replace(BATTERY_PATTERN, "")
            .replace(LANG_PATTERN, "")
            .replace(COMMA_PATTERN, ",")
            .replace(PIPE_PATTERN, "|")
            .replace(CLEANUP_PATTERN, "")
            .trim()
    }
}
