package io.blueeye.core.data.classifier.tactical

import io.blueeye.core.data.classifier.vendor.tactical.TacticalUuids
import io.blueeye.core.scanner.analysis.BleBinaryConstants

/**
 * Decoder for Manufacturer Specific Data (MSD) from tactical devices.
 */
object TacticalMsdDecoder {
    private const val RADETEC_COMPANY_ID = 0x0059
    private const val STATUS_MASK_ALARM = 0x80
    private const val TYPE_MASK_EVENT = 0x80
    private const val TYPE_MASK_DEVICE = 0x7F

    private const val AXON_TYPE_BODY_CAM = 0x01
    private const val AXON_TYPE_SIDEARM = 0x02
    private const val AXON_TYPE_VEHICLE = 0x03

    private const val MOTO_STATUS_RECORDING = 0x01
    private const val MOTO_STATUS_HOLSTER = 0x04

    private const val YARDARM_STATUS_ALARM = 0x01
    private const val YARDARM_STATUS_IDLE = 0x00

    private const val RADETEC_AMMO_THRESHOLD = 3
    private const val RADETEC_MAX_AMMO = 18

    // Common Offsets and Sizes
    private const val SIZE_MIN_3 = 3
    private const val SIZE_MIN_4 = 4
    private const val SIZE_MIN_8 = 8
    private const val SIZE_MIN_11 = 11
    private const val OFFSET_PACKET_TYPE = 2
    private const val OFFSET_AXON_STATUS = 3
    private const val OFFSET_SERIAL_START = 4
    private const val OFFSET_SERIAL_END = 7
    private const val OFFSET_AXON_BATTERY = 8
    private const val OFFSET_MOTO_STATUS = 10
    private const val OFFSET_RADETEC_AMMO = 28
    private const val OFFSET_CID_LOW = 0
    private const val OFFSET_CID_HIGH = 1

    /**
     * Tactical device state decoded from MSD.
     */
    data class TacticalState(
        val companyId: Int,
        val companyName: String,
        val deviceType: DeviceType,
        val status: Status,
        val rawStatusByte: Int,
        val serialHash: String? = null,
        val ammoCount: Int? = null,
        val batteryLevel: Int? = null
    )

    enum class DeviceType {
        AXON_SIDEARM, AXON_BODY_CAM, AXON_SIGNAL_VEHICLE, YARDARM_HOLSTER, MOTOROLA_V300, RADETEC_SLIDE, UNKNOWN
    }

    enum class Status {
        IDLE, ALARM, LOW_BATTERY, LOW_AMMO, WEAPON_DRAWN, UNKNOWN
    }

    /**
     * Decode Manufacturer Specific Data payload.
     */
    fun decode(manufacturerData: ByteArray?): TacticalState? {
        if (manufacturerData == null || manufacturerData.size < SIZE_MIN_3) return null

        val lowByte = manufacturerData[OFFSET_CID_LOW].toInt() and BleBinaryConstants.MASK_BYTE
        val highByte = (manufacturerData[OFFSET_CID_HIGH].toInt() and BleBinaryConstants.MASK_BYTE) shl
            BleBinaryConstants.SHIFT_8
        val companyId = lowByte or highByte

        return when (companyId) {
            TacticalUuids.AXON_COMPANY_ID -> decodeAxon(manufacturerData)
            TacticalUuids.MOTOROLA_COMPANY_ID -> decodeMotorola(manufacturerData)
            TacticalUuids.YARDARM_COMPANY_ID -> decodeYardarm(manufacturerData)
            else -> null
        }
    }

    private const val OFFSET_AXON_TRIGGER = 5
    private const val MASK_AXON_TRIGGER_WEAPON_DRAWN = 0x02

    private fun decodeAxon(data: ByteArray): TacticalState? {
        if (data.size < SIZE_MIN_4) return null

        val deviceTypeByte = data[OFFSET_PACKET_TYPE].toInt() and BleBinaryConstants.MASK_BYTE
        val statusOfs = OFFSET_AXON_STATUS
        val statusByte = if (data.size > statusOfs) data[statusOfs].toInt() and BleBinaryConstants.MASK_BYTE else 0

        // New Logic (2026 Audit): Check Trigger Event at Offset 7 (Index 5)
        val triggerByte = if (data.size > OFFSET_AXON_TRIGGER) {
            data[OFFSET_AXON_TRIGGER].toInt() and BleBinaryConstants.MASK_BYTE
        } else {
            0
        }
        val isWeaponDrawn = (triggerByte and MASK_AXON_TRIGGER_WEAPON_DRAWN) != 0

        val deviceType = when (deviceTypeByte and TYPE_MASK_DEVICE) {
            AXON_TYPE_BODY_CAM -> DeviceType.AXON_BODY_CAM
            AXON_TYPE_SIDEARM -> DeviceType.AXON_SIDEARM
            AXON_TYPE_VEHICLE -> DeviceType.AXON_SIGNAL_VEHICLE
            else -> DeviceType.UNKNOWN
        }

        val status = when {
            (deviceTypeByte and TYPE_MASK_EVENT) != 0 -> Status.ALARM
            statusByte == BleBinaryConstants.MASK_BYTE -> Status.ALARM
            statusByte >= STATUS_MASK_ALARM -> Status.ALARM
            isWeaponDrawn -> Status.WEAPON_DRAWN
            statusByte == 0 -> Status.IDLE
            else -> Status.UNKNOWN
        }

        val serialHash = if (data.size >= SIZE_MIN_8) {
            data.slice(OFFSET_SERIAL_START..OFFSET_SERIAL_END).joinToString("") { "%02X".format(it) }
        } else {
            null
        }

        val battery = if (data.size > OFFSET_AXON_BATTERY) {
            data[OFFSET_AXON_BATTERY].toInt() and BleBinaryConstants.MASK_BYTE
        } else {
            null
        }

        return TacticalState(
            companyId = TacticalUuids.AXON_COMPANY_ID,
            companyName = "Axon Enterprise",
            deviceType = deviceType,
            status = status,
            rawStatusByte = if (isWeaponDrawn) triggerByte else statusByte,
            serialHash = serialHash,
            batteryLevel = battery
        )
    }

    private fun decodeMotorola(data: ByteArray): TacticalState? {
        if (data.size < SIZE_MIN_11) return null

        val statusOfs = OFFSET_MOTO_STATUS
        val packetType = data[OFFSET_PACKET_TYPE].toInt() and BleBinaryConstants.MASK_BYTE
        if (packetType != 0x03) return null // Only status updates are decoded here
        
        val statusByte = data[statusOfs].toInt() and BleBinaryConstants.MASK_BYTE

        val status = when {
            (statusByte and MOTO_STATUS_HOLSTER) != 0 -> Status.ALARM
            (statusByte and MOTO_STATUS_RECORDING) != 0 -> Status.ALARM
            else -> Status.IDLE
        }

        val serialHash = data.slice(OFFSET_SERIAL_START..OFFSET_SERIAL_END)
            .joinToString("") { "%02X".format(it) }

        return TacticalState(
            companyId = TacticalUuids.MOTOROLA_COMPANY_ID,
            companyName = "Motorola Solutions",
            deviceType = DeviceType.MOTOROLA_V300,
            status = status,
            rawStatusByte = statusByte,
            serialHash = serialHash
        )
    }

    private fun decodeYardarm(data: ByteArray): TacticalState? {
        if (data.size < SIZE_MIN_3) return null

        val statusOffset = data.size - 1
        val statusByte = data[statusOffset].toInt() and BleBinaryConstants.MASK_BYTE
        val status = when (statusByte) {
            YARDARM_STATUS_ALARM -> Status.ALARM
            YARDARM_STATUS_IDLE -> Status.IDLE
            else -> Status.UNKNOWN
        }

        val serialHash = if (data.size > SIZE_MIN_3) {
            data.slice(OFFSET_PACKET_TYPE until statusOffset).joinToString("") { "%02X".format(it) }
        } else {
            null
        }

        return TacticalState(
            companyId = TacticalUuids.YARDARM_COMPANY_ID,
            companyName = "Yardarm/Motorola",
            deviceType = DeviceType.YARDARM_HOLSTER,
            status = status,
            rawStatusByte = statusByte,
            serialHash = serialHash
        )
    }

    fun decodeRadetec(data: ByteArray?, deviceName: String?): TacticalState? {
        if (data == null || data.size < OFFSET_MOTO_STATUS || deviceName == null) return null

        val isRadetec = deviceName.contains("SmartSlide", ignoreCase = true) ||
            deviceName.contains("RISC", ignoreCase = true) ||
            deviceName.contains("Radetec", ignoreCase = true)

        return if (isRadetec) {
            val ammoCountOffset = minOf(OFFSET_RADETEC_AMMO, data.size - 1)
            val rawAmmo = data[ammoCountOffset].toInt() and BleBinaryConstants.MASK_BYTE
            val ammoCount = if (rawAmmo <= RADETEC_MAX_AMMO) rawAmmo else null

            val status = when {
                ammoCount == null -> Status.UNKNOWN
                ammoCount <= RADETEC_AMMO_THRESHOLD -> Status.LOW_AMMO
                ammoCount == 0 -> Status.ALARM
                else -> Status.IDLE
            }

            TacticalState(
                companyId = RADETEC_COMPANY_ID,
                companyName = "Radetec",
                deviceType = DeviceType.RADETEC_SLIDE,
                status = status,
                rawStatusByte = rawAmmo,
                ammoCount = ammoCount
            )
        } else {
            null
        }
    }

    fun isAlarmState(manufacturerData: ByteArray?, deviceName: String? = null): Boolean {
        val state = decode(manufacturerData) ?: decodeRadetec(manufacturerData, deviceName)
        return state?.status == Status.ALARM || state?.status == Status.LOW_AMMO || state?.status == Status.WEAPON_DRAWN
    }
}
