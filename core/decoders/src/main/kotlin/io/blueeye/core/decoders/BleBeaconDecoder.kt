package io.blueeye.core.decoders

import io.blueeye.core.model.SensorData

/** Interfejs dla wszystkich dekoderów pakietów reklamowych (Beaconów/Sensorów). */
data class BleBeaconScanInput(
    val mac: String,
    val manufacturerRecords: Map<Int, ByteArray>,
    val serviceUuids: List<String>,
    val rawData: ByteArray? = null,
) {
    val manufacturerId: Int?
        get() = manufacturerRecords.keys.firstOrNull()

    val data: ByteArray?
        get() = manufacturerId?.let(manufacturerRecords::get)

    fun candidateFor(
        manufacturerId: Int,
        data: ByteArray,
    ): BleBeaconScanInput =
        copy(manufacturerRecords = mapOf(manufacturerId to data))
}

interface BleBeaconDecoder {
    /**
     * Sprawdza, czy ten dekoder obsługuje dane urządzenie.
     */
    fun supports(input: BleBeaconScanInput): Boolean

    /**
     * Priorytet dekodera. Wyższe wartości oznaczają wyższy priorytet.
     * Domyślnie 0.
     * - > 0 dla wyspecjalizowanych dekoderów (np. Apple Watch, Xiaomi Thermometer)
     * - < 0 dla ogólnych/fallbacków (np. Generic Apple, Eddystone)
     */
    val priority: Int
        get() = 0

    /** Dekoduje dane na znormalizowany format SensorData. */
    fun decode(input: BleBeaconScanInput): SensorData?
}
