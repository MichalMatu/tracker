package io.blueeye.core.data.classifier.tactical

import android.util.Log
import io.blueeye.core.alert.TacticalAlertRequest
import io.blueeye.core.alert.TacticalAlertService
import io.blueeye.core.alert.TacticalEvidenceFactory
import io.blueeye.core.data.classifier.vendor.TacticalOuiRegistry
import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import io.blueeye.core.data.classifier.vendor.tactical.TacticalCategory
import io.blueeye.core.data.classifier.vendor.tactical.TacticalNameMatch
import io.blueeye.core.data.classifier.vendor.tactical.TacticalNameMatcher
import io.blueeye.core.data.classifier.vendor.tactical.TacticalOuiInfo
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TacticalProcessor @Inject constructor(
    private val tacticalAlertService: TacticalAlertService
) {
    data class TacticalResult(
        val isTactical: Boolean,
        val deviceType: DeviceType,
        val beaconTypeStatus: String?,
        val categoryDescription: String?,
        val evidence: List<DetectionEvidence> = emptyList(),
    )

    @Suppress("LongParameterList")
    fun process(
        mac: String,
        rssi: Int,
        manufacturerId: Int?,
        manufacturerData: ByteArray?,
        manufacturerDataById: Map<Int, ByteArray> = emptyMap(),
        serviceUuids: List<String>,
        name: String?,
        timestamp: Long,
    ): TacticalResult {
        var deviceType = DeviceType.UNKNOWN
        var description: String? = null
        var beaconTypeStatus: String? = null
        var isTactical = false
        val evidence = mutableListOf<DetectionEvidence>()

        val tacticalMatch = TacticalOuiRegistry.lookup(mac)
        if (tacticalMatch != null) {
            isTactical = true
            val match = tacticalMatch.toUserFacingSignal()
            val detectionEvidence = buildOuiEvidence(match, tacticalMatch.ouiPrefix, timestamp)
            evidence += detectionEvidence
            tacticalAlertService.onDeviceDetected(
                TacticalAlertRequest(
                    macAddress = mac,
                    rssi = rssi,
                    match = match,
                    evidenceSource = EvidenceSource.OUI,
                    rawEvidenceValue = tacticalMatch.ouiPrefix,
                    evidenceProvenance = EvidenceProvenance.DEVICE_REGISTRY,
                    evidence = detectionEvidence,
                ),
            )
            deviceType = mapCategoryToDeviceType(tacticalMatch.category)
            description = tacticalMatch.description
            beaconTypeStatus = "Signal consistent with ${tacticalMatch.category.toDisplayLabel()}"
        }

        val manufacturerRecords = buildManufacturerRecords(manufacturerId, manufacturerData, manufacturerDataById)
        val msdResult = decodeMsd(mac, manufacturerData, manufacturerRecords, name)
        if (msdResult != null) {
            isTactical = true
            beaconTypeStatus = msdResult.statusLabel
            if (deviceType == DeviceType.UNKNOWN) {
                deviceType = msdResult.deviceType
            }

            val syntheticMatch = TacticalOuiRegistry.lookup(mac)?.toUserFacingSignal() ?: TacticalOuiInfo(
                ouiPrefix = mac.toOuiPrefix(),
                vendorName = msdResult.companyName,
                confidence = ConfidenceLevel.HIGH,
                category = TacticalCategory.BODY_CAMERA,
                deviceType = msdResult.deviceType,
                description = msdResult.statusLabel
            )
            val detectionEvidence = buildPayloadEvidence(syntheticMatch, msdResult.rawEvidenceValue, timestamp)
            evidence += detectionEvidence
            tacticalAlertService.onDeviceDetected(
                TacticalAlertRequest(
                    macAddress = mac,
                    rssi = rssi,
                    match = syntheticMatch,
                    evidenceSource = EvidenceSource.RAW_PAYLOAD,
                    rawEvidenceValue = msdResult.rawEvidenceValue,
                    evidenceProvenance = EvidenceProvenance.BLE_ADVERTISEMENT,
                    evidence = detectionEvidence,
                ),
            )
        }

        if (deviceType == DeviceType.UNKNOWN) {
            val fallback = checkFallbackIdentification(manufacturerRecords.keys, serviceUuids)
            if (fallback != null) {
                isTactical = true
                deviceType = fallback.deviceType
                description = fallback.description
                beaconTypeStatus = fallback.beaconLabel
                appendFallbackEvidence(
                    context = TacticalAlertContext(mac = mac, rssi = rssi, timestamp = timestamp),
                    fallback = fallback,
                    evidence = evidence,
                    tacticalAlertService = tacticalAlertService,
                )
            }
        }

        if (!isTactical) {
            buildNameOnlyResult(
                context = TacticalAlertContext(mac = mac, rssi = rssi, timestamp = timestamp),
                name = name,
                tacticalAlertService = tacticalAlertService,
            )?.let { nameOnly ->
                isTactical = true
                deviceType = nameOnly.deviceType
                description = nameOnly.description
                beaconTypeStatus = nameOnly.beaconTypeStatus
                evidence += nameOnly.evidence
            }
        }

        if (isTactical && beaconTypeStatus == null) {
            beaconTypeStatus = "Signal consistent with professional equipment"
        }

        return TacticalResult(isTactical, deviceType, beaconTypeStatus, description, evidence)
    }

    private data class MsdResult(
        val statusLabel: String,
        val deviceType: DeviceType,
        val companyName: String,
        val rawEvidenceValue: String,
    )

    private fun buildManufacturerRecords(
        manufacturerId: Int?,
        manufacturerData: ByteArray?,
        manufacturerDataById: Map<Int, ByteArray>,
    ): Map<Int, ByteArray> =
        buildMap {
            putAll(manufacturerDataById)
            if (manufacturerId != null && manufacturerData != null) {
                putIfAbsent(manufacturerId, manufacturerData)
            }
        }

    private fun decodeMsd(
        mac: String,
        primaryManufacturerData: ByteArray?,
        manufacturerRecords: Map<Int, ByteArray>,
        name: String?,
    ): MsdResult? {
        val tacticalState = manufacturerRecords.firstDecodedTacticalState(name)
            ?: TacticalMsdDecoder.decode(primaryManufacturerData)
            ?: TacticalMsdDecoder.decodeRadetec(primaryManufacturerData, name)
            ?: return null

        val label = "Signal consistent with ${tacticalState.companyName} payload [${tacticalState.status.toUserFacingLabel()}]"

        if (tacticalState.status == TacticalMsdDecoder.Status.ALARM ||
            tacticalState.status == TacticalMsdDecoder.Status.WEAPON_DRAWN) {
            Log.w(
                "TacticalProcessor",
                "Public-safety signal payload state: ${tacticalState.deviceType} from $mac - " +
                    "Status: 0x${tacticalState.rawStatusByte.toString(RADIX_HEX)}"
            )
        }
        return MsdResult(
            statusLabel = label,
            deviceType = mapTacticalDeviceType(tacticalState.deviceType),
            companyName = tacticalState.companyName,
            rawEvidenceValue = tacticalState.companyId.toHexCompanyId(),
        )
    }

    private fun Map<Int, ByteArray>.firstDecodedTacticalState(name: String?): TacticalMsdDecoder.TacticalState? =
        entries.firstNotNullOfOrNull { (manufacturerId, payload) ->
            val fullPayload = payload.withCompanyId(manufacturerId)
            TacticalMsdDecoder.decode(fullPayload)
                ?: TacticalMsdDecoder.decodeRadetec(fullPayload, name)
        }

    private fun ByteArray.withCompanyId(manufacturerId: Int): ByteArray {
        val alreadyIncludesCompanyId = if (size >= COMPANY_ID_BYTES) {
            val lowByte = this[0].toInt() and BYTE_MASK
            val highByte = (this[1].toInt() and BYTE_MASK) shl BYTE_BITS
            lowByte or highByte == manufacturerId
        } else {
            false
        }
        if (alreadyIncludesCompanyId) return this
        return byteArrayOf(
            (manufacturerId and BYTE_MASK).toByte(),
            ((manufacturerId shr BYTE_BITS) and BYTE_MASK).toByte(),
        ) + this
    }

    private fun checkFallbackIdentification(manufacturerIds: Set<Int>, serviceUuids: List<String>): FallbackResult? {
        val manufacturerFallback = manufacturerIds.firstNotNullOfOrNull { manufacturerId ->
            TacticalOuiRegistry.matchByManufacturerId(manufacturerId)
                ?.let { match ->
                    FallbackResult(
                        category = match.first,
                        deviceType = mapCategoryToDeviceType(match.first),
                        description = match.second,
                        beaconLabel = "Signal consistent with ${match.second}",
                        evidenceSource = EvidenceSource.MANUFACTURER_ID,
                        rawEvidenceValue = manufacturerId.toHexCompanyId(),
                        evidenceProvenance = EvidenceProvenance.BLE_ADVERTISEMENT,
                    )
                }
        }
        if (manufacturerFallback != null) return manufacturerFallback

        return TacticalOuiRegistry.matchByServiceUuid(serviceUuids)
            ?.let { match ->
                FallbackResult(
                    category = match.first,
                    deviceType = mapCategoryToDeviceType(match.first),
                    description = match.second,
                    beaconLabel = "Signal consistent with ${match.second}",
                    evidenceSource = EvidenceSource.SERVICE_UUID,
                    rawEvidenceValue = serviceUuids.sorted().joinToString(","),
                    evidenceProvenance = EvidenceProvenance.BLE_ADVERTISEMENT,
                )
            }
    }

    private fun mapCategoryToDeviceType(category: TacticalCategory): DeviceType {
        return when (category) {
            TacticalCategory.BODY_CAMERA -> DeviceType.BODY_CAMERA
            TacticalCategory.HOLSTER_SENSOR -> DeviceType.HOLSTER_SENSOR
            TacticalCategory.TACTICAL_AUDIO -> DeviceType.TACTICAL_AUDIO
            TacticalCategory.TACTICAL_RADIO -> DeviceType.TACTICAL_RADIO
            TacticalCategory.SMART_WEAPON -> DeviceType.SMART_WEAPON
            TacticalCategory.TACTICAL_EUD -> DeviceType.TACTICAL_EUD
            TacticalCategory.FIRE_EMS -> DeviceType.MEDICAL
            TacticalCategory.POLICE_EQUIPMENT -> DeviceType.POLICE
            TacticalCategory.VEHICLE_ROUTER -> DeviceType.VEHICLE_ROUTER
            TacticalCategory.DOCUMENT_READER -> DeviceType.DOCUMENT_READER
            TacticalCategory.FIREFIGHTER -> DeviceType.FIREFIGHTER
        }
    }

    private fun mapTacticalDeviceType(type: TacticalMsdDecoder.DeviceType): DeviceType {
        return when (type) {
            TacticalMsdDecoder.DeviceType.AXON_SIDEARM -> DeviceType.HOLSTER_SENSOR
            TacticalMsdDecoder.DeviceType.AXON_BODY_CAM -> DeviceType.BODY_CAMERA
            TacticalMsdDecoder.DeviceType.AXON_SIGNAL_VEHICLE -> DeviceType.VEHICLE_ROUTER
            TacticalMsdDecoder.DeviceType.YARDARM_HOLSTER -> DeviceType.HOLSTER_SENSOR
            TacticalMsdDecoder.DeviceType.MOTOROLA_V300 -> DeviceType.BODY_CAMERA
            TacticalMsdDecoder.DeviceType.RADETEC_SLIDE -> DeviceType.SMART_WEAPON
            TacticalMsdDecoder.DeviceType.UNKNOWN -> DeviceType.UNKNOWN
        }
    }

    private fun TacticalCategory.toDisplayLabel(): String =
        when (this) {
            TacticalCategory.BODY_CAMERA -> "body camera equipment"
            TacticalCategory.HOLSTER_SENSOR -> "holster sensor equipment"
            TacticalCategory.TACTICAL_AUDIO -> "professional audio equipment"
            TacticalCategory.TACTICAL_RADIO -> "professional radio equipment"
            TacticalCategory.SMART_WEAPON -> "smart equipment"
            TacticalCategory.TACTICAL_EUD -> "professional terminal equipment"
            TacticalCategory.FIRE_EMS -> "emergency medical equipment"
            TacticalCategory.POLICE_EQUIPMENT -> "public-safety equipment"
            TacticalCategory.VEHICLE_ROUTER -> "vehicle router equipment"
            TacticalCategory.DOCUMENT_READER -> "document reader equipment"
            TacticalCategory.FIREFIGHTER -> "firefighter telemetry equipment"
        }

    private fun TacticalOuiInfo.toUserFacingSignal(): TacticalOuiInfo =
        if (confidence == ConfidenceLevel.CRITICAL) {
            copy(confidence = ConfidenceLevel.HIGH)
        } else {
            this
        }

    private companion object {
        private const val RADIX_HEX = 16
        private const val BYTE_MASK = 0xFF
        private const val BYTE_BITS = 8
        private const val COMPANY_ID_BYTES = 2
    }
}

private fun buildNameOnlyResult(
    context: TacticalAlertContext,
    name: String?,
    tacticalAlertService: TacticalAlertService,
): NameOnlyTacticalResult? =
    name?.let { rawName ->
        TacticalNameMatcher.match(rawName)?.let { nameMatch ->
            val evidence = emitNameEvidence(
                context = context,
                rawName = rawName,
                nameMatch = nameMatch,
                tacticalAlertService = tacticalAlertService,
            )
            NameOnlyTacticalResult(
                deviceType = nameMatch.deviceType,
                description = nameMatch.evidenceDescription,
                beaconTypeStatus = "Signal consistent with ${nameMatch.evidenceDescription}",
                evidence = evidence,
            )
        }
    }

private fun emitNameEvidence(
    context: TacticalAlertContext,
    rawName: String,
    nameMatch: TacticalNameMatch,
    tacticalAlertService: TacticalAlertService,
): DetectionEvidence {
    val detectionEvidence = TacticalEvidenceFactory.buildName(
        match = nameMatch,
        rawValue = rawName,
        timestamp = context.timestamp,
    )
    val syntheticMatch = TacticalOuiInfo(
        ouiPrefix = context.mac.toOuiPrefix(),
        vendorName = nameMatch.evidenceDescription,
        confidence = ConfidenceLevel.MEDIUM,
        category = nameMatch.category,
        deviceType = nameMatch.deviceType,
        description = nameMatch.evidenceDescription,
    )
    tacticalAlertService.onDeviceDetected(
        TacticalAlertRequest(
            macAddress = context.mac,
            rssi = context.rssi,
            match = syntheticMatch,
            evidenceSource = EvidenceSource.NAME,
            rawEvidenceValue = rawName,
            evidenceProvenance = EvidenceProvenance.BLE_ADVERTISEMENT,
            evidence = detectionEvidence,
        ),
    )
    return detectionEvidence
}

private data class NameOnlyTacticalResult(
    val deviceType: DeviceType,
    val description: String,
    val beaconTypeStatus: String,
    val evidence: DetectionEvidence,
)

private data class FallbackResult(
    val category: TacticalCategory,
    val deviceType: DeviceType,
    val description: String,
    val beaconLabel: String,
    val evidenceSource: EvidenceSource,
    val rawEvidenceValue: String,
    val evidenceProvenance: EvidenceProvenance,
)

private data class TacticalAlertContext(
    val mac: String,
    val rssi: Int,
    val timestamp: Long,
)

private fun appendFallbackEvidence(
    context: TacticalAlertContext,
    fallback: FallbackResult,
    evidence: MutableList<DetectionEvidence>,
    tacticalAlertService: TacticalAlertService,
) {
    val syntheticMatch = TacticalOuiInfo(
        ouiPrefix = context.mac.toOuiPrefix(),
        vendorName = fallback.description,
        confidence = ConfidenceLevel.HIGH,
        category = fallback.category,
        deviceType = fallback.deviceType,
        description = fallback.description
    )
    val detectionEvidence = TacticalEvidenceFactory.build(
        match = syntheticMatch,
        source = fallback.evidenceSource,
        rawValue = fallback.rawEvidenceValue,
        timestamp = context.timestamp,
        provenance = fallback.evidenceProvenance,
    )
    evidence += detectionEvidence
    tacticalAlertService.onDeviceDetected(
        TacticalAlertRequest(
            macAddress = context.mac,
            rssi = context.rssi,
            match = syntheticMatch,
            evidenceSource = fallback.evidenceSource,
            rawEvidenceValue = fallback.rawEvidenceValue,
            evidenceProvenance = fallback.evidenceProvenance,
            evidence = detectionEvidence,
        ),
    )
}

private fun buildOuiEvidence(
    match: TacticalOuiInfo,
    ouiPrefix: String,
    timestamp: Long,
): DetectionEvidence =
    TacticalEvidenceFactory.build(
        match = match,
        source = EvidenceSource.OUI,
        rawValue = ouiPrefix,
        timestamp = timestamp,
        provenance = EvidenceProvenance.DEVICE_REGISTRY,
    )

private fun buildPayloadEvidence(
    match: TacticalOuiInfo,
    rawEvidenceValue: String,
    timestamp: Long,
): DetectionEvidence =
    TacticalEvidenceFactory.build(
        match = match,
        source = EvidenceSource.RAW_PAYLOAD,
        rawValue = rawEvidenceValue,
        timestamp = timestamp,
        provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
    )

private object TacticalProcessorConstants {
    const val OUI_PREFIX_HEX_LENGTH = 6
}

private fun String.toOuiPrefix(): String =
    replace(":", "").take(TacticalProcessorConstants.OUI_PREFIX_HEX_LENGTH)

private fun Int.toHexCompanyId(): String = "0x%04X".format(this)

private fun TacticalMsdDecoder.Status.toUserFacingLabel(): String =
    when (this) {
        TacticalMsdDecoder.Status.IDLE -> "IDLE"
        TacticalMsdDecoder.Status.ALARM,
        TacticalMsdDecoder.Status.WEAPON_DRAWN,
        -> "ACTIVE_STATE"
        TacticalMsdDecoder.Status.LOW_BATTERY -> "LOW_BATTERY"
        TacticalMsdDecoder.Status.LOW_AMMO -> "LOW_AMMO"
        TacticalMsdDecoder.Status.UNKNOWN -> "UNKNOWN"
    }
