package io.blueeye.core.decoders

import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TP357SDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // TP357S encoding tactic:
        // ID nie jest stałe! Górny bajt to temperatura (np. C0 = 192 -> 19.2C).
        // Dolny bajt to stała sygnatura 0xC2.

        if (manufacturerId == null || data == null) return false

        // Sprawdzamy czy ID kończy się na C2 (to nasza kotwica identyfikująca TP357S)
        // ORAZ czy długość danych to co najmniej 5 (format 00 19 22 0B 01)
        val isTp357S = (manufacturerId and 0xFF) == 0xC2 && data.size >= 5

        // Wspieramy też stare/znane ID (dla kompatybilności wstecznej/statycznych odczytów)
        val isKnownStaticId =
            manufacturerId == 50626 || // 0xC5C2
                manufacturerId == 49346 || // 0xC0C2
                manufacturerId == 0xEC88 ||
                manufacturerId == 0x0001

        return isTp357S || isKnownStaticId
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val manufacturerId = input.manufacturerId
        val data = input.data ?: byteArrayOf()
        val hexData = data.joinToString(" ") { "%02X".format(it) }

        // Analiza na podstawie obserwacji:
        // MfgId: C0C2 -> C0 (192) = 19.2C
        // Data[1]: 19 -> 25% Hum

        var temp: Double? = null
        var hum: Double? = null
        var batLevel: Int? = null

        if (manufacturerId != null && data.size >= 5) {
            try {
                // Logika z C++:
                // payload[1] = ID_HI (np. C0)
                // payload[2] = Data[0] (np. 00)
                // TempRaw = (payload[2] << 8) | payload[1]

                val tLow = (manufacturerId ushr 8) and 0xFF
                val tHigh = data[0].toInt() and 0xFF

                // Używamy short dla poprawnej obsługi liczb ujemnych
                val tRaw = ((tHigh shl 8) or tLow).toShort()
                temp = tRaw / 10.0

                // Hum from Data[1] (payload[3])
                val hRaw = data[1].toInt() and 0xFF
                hum = hRaw.toDouble()

                // Battery from Data[2] (payload[4]) - jako %
                val bVal = data[2].toInt() and 0xFF
                if (bVal <= 100) {
                    batLevel = bVal
                }

                // Opcjonalnie: Data[4] jako status binarny (jak wcześniej)
                // Ale ufamy C++ że bateria jest w bajcie 4 (wirtualnego payloadu), czyli Data[2]
            } catch (e: Exception) {
                android.util.Log.e("TP357SDecoder", "Error decoding", e)
            }
        }

        val typeName =
            when {
                manufacturerId == 0x0001 -> "ThermoPro TP357 (BTH Format)"
                manufacturerId == 0xEC88 -> "ThermoPro TP357 (Govee Format)"
                else -> "ThermoPro TP357S"
            }

        return SensorData(
            temperatureCelcius = temp,
            humidityPercent = hum,
            batteryLevel = batLevel,
            beaconType = typeName,
            rawData = "TP357S: $hexData",
        )
    }
}
