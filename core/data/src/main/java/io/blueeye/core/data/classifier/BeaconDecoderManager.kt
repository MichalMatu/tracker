package io.blueeye.core.data.classifier

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BeaconDecoderManager
@Inject
constructor(private val decoderSet: Set<@JvmSuppressWildcards BleBeaconDecoder>) {
    private val decoders: List<BleBeaconDecoder> by lazy {
        // Sort by priority descending (Highest priority first)
        decoderSet.sortedByDescending { it.priority }
    }
    private val sensorStateCache = ConcurrentHashMap<String, SensorData>()

    fun decode(
        mac: String,
        manufacturerRecords: Map<Int, ByteArray>,
        serviceUuids: List<String>,
        rawData: ByteArray? = null,
    ): SensorData? {
        if (manufacturerRecords.isEmpty() && serviceUuids.isEmpty() && rawData == null) return null

        val inputs =
            scanInputs(
                BleBeaconScanInput(
                    mac = mac,
                    manufacturerRecords = manufacturerRecords,
                    serviceUuids = serviceUuids,
                    rawData = rawData,
                ),
            )

        val match =
            decoders
                .asSequence()
                .flatMap { decoder ->
                    inputs.asSequence().map { input -> decoder to input }
                }.firstOrNull { (decoder, input) ->
                    decoder.supports(input)
                }

        val newData = match?.let { (decoder, input) -> decoder.decode(input) }

        return if (newData != null) {
            // Merge with cached state to handle split packets (e.g. BTHome Volt + Temp)
            val cached = sensorStateCache[mac]
            val merged = if (cached != null) merge(cached, newData) else newData
            sensorStateCache[mac] = merged
            merged
        } else {
            null
        }
    }

    private fun scanInputs(base: BleBeaconScanInput): List<BleBeaconScanInput> =
        buildList {
            base.manufacturerRecords.forEach { (manufacturerId, data) ->
                add(base.candidateFor(manufacturerId, data))
            }
            add(base.copy(manufacturerRecords = emptyMap()))
        }

    @Suppress("CyclomaticComplexMethod")
    private fun merge(old: SensorData, new: SensorData): SensorData {
        return old.copy(
            temperatureCelcius = new.temperatureCelcius ?: old.temperatureCelcius,
            humidityPercent = new.humidityPercent ?: old.humidityPercent,
            batteryLevel = new.batteryLevel ?: old.batteryLevel,
            pressureHpa = new.pressureHpa ?: old.pressureHpa,
            accelerationX = new.accelerationX ?: old.accelerationX,
            accelerationY = new.accelerationY ?: old.accelerationY,
            accelerationZ = new.accelerationZ ?: old.accelerationZ,
            txPower = new.txPower ?: old.txPower,
            movementDetected = new.movementDetected ?: old.movementDetected,
            illuminanceLux = new.illuminanceLux ?: old.illuminanceLux,
            co2Ppm = new.co2Ppm ?: old.co2Ppm,
            soilMoisturePercent = new.soilMoisturePercent ?: old.soilMoisturePercent,
            voltageV = new.voltageV ?: old.voltageV,
            doorOpen = new.doorOpen ?: old.doorOpen,
            fertilityUsCm = new.fertilityUsCm ?: old.fertilityUsCm,
            pm25Ugm3 = new.pm25Ugm3 ?: old.pm25Ugm3,
            pm10Ugm3 = new.pm10Ugm3 ?: old.pm10Ugm3,
            formaldehydeMgm3 = new.formaldehydeMgm3 ?: old.formaldehydeMgm3,
            sensorStatus = new.sensorStatus ?: old.sensorStatus,
            beaconType = new.beaconType ?: old.beaconType,
            rawData = new.rawData ?: old.rawData,
            weightKg = new.weightKg ?: old.weightKg
        )
    }
}
