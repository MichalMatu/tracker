package io.blueeye.core.scanner.analysis.parsers

import io.blueeye.core.scanner.analysis.BleBinaryConstants
import io.blueeye.core.scanner.analysis.BleServiceUuids
import java.lang.StringBuilder

/**
 * Parser for Eddystone beacon frames.
 */
object EddystoneParser {
    private const val OFS_TX_POWER = 1
    private const val OFS_URL_SCHEME = 2

    fun parse(uuid: Int, rest: ByteArray): String? {
        if (uuid != BleServiceUuids.EDDYSTONE || rest.isEmpty()) return null

        val frameType = rest[0].toInt() and BleBinaryConstants.MASK_BYTE

        return when (frameType) {
            EddystoneConstants.FRAME_TYPE_UID -> parseUid(rest)
            EddystoneConstants.FRAME_TYPE_URL -> parseUrl(rest)
            EddystoneConstants.FRAME_TYPE_TLM -> parseTlm(rest)
            EddystoneConstants.FRAME_TYPE_EID -> "Eddystone-EID"
            else -> "Eddystone (Unknown Frame Type: 0x%02X)".format(frameType)
        }
    }

    private fun parseUid(rest: ByteArray): String {
        return if (rest.size >= EddystoneConstants.MIN_UID_SIZE) {
            val txPower = rest[OFS_TX_POWER].toInt()
            val ns = rest.sliceArray(EddystoneConstants.NAMESPACE_START..EddystoneConstants.NAMESPACE_END)
                .joinToString("") { "%02X".format(it) }
            val inst = rest.sliceArray(EddystoneConstants.INSTANCE_START..EddystoneConstants.INSTANCE_END)
                .joinToString("") { "%02X".format(it) }
            "Eddystone-UID: Namespace=$ns, Instance=$inst, TxPower=$txPower dBm"
        } else {
            "Eddystone-UID (malformed)"
        }
    }

    private fun parseUrl(rest: ByteArray): String {
        if (rest.size < EddystoneConstants.MIN_URL_SIZE) return "Eddystone-URL (malformed)"

        val txPower = rest[OFS_TX_POWER].toInt()
        val schemeCode = rest[OFS_URL_SCHEME].toInt() and BleBinaryConstants.MASK_BYTE
        val scheme = when (schemeCode) {
            EddystoneConstants.UrlSchemes.HTTP_WWW -> "http://www."
            EddystoneConstants.UrlSchemes.HTTPS_WWW -> "https://www."
            EddystoneConstants.UrlSchemes.HTTP -> "http://"
            EddystoneConstants.UrlSchemes.HTTPS -> "https://"
            else -> ""
        }

        val urlBodyBytes = rest.sliceArray(EddystoneConstants.MIN_URL_SIZE until rest.size)
        val urlBody = StringBuilder()
        for (b in urlBodyBytes) {
            val code = b.toInt() and BleBinaryConstants.MASK_BYTE
            val expansion = EddystoneConstants.URL_EXPANSIONS[code]
            if (expansion != null) {
                urlBody.append(expansion)
            } else if (code in EddystoneConstants.PRINTABLE_ASCII_START..EddystoneConstants.PRINTABLE_ASCII_END) {
                urlBody.append(code.toChar())
            }
        }
        return "Eddystone-URL: $scheme$urlBody (TxPower=$txPower dBm)"
    }

    private fun parseTlm(rest: ByteArray): String {
        val version = rest.getOrNull(1)?.toInt()?.and(BleBinaryConstants.MASK_BYTE) ?: -1
        return when (version) {
            EddystoneConstants.TLM_VERSION_PLAIN -> parseTlmPlain(rest)
            EddystoneConstants.TLM_VERSION_ENCRYPTED -> parseTlmEncrypted(rest)
            else -> "Eddystone-TLM (Unknown Version: 0x%02X)".format(version)
        }
    }

    private fun parseTlmPlain(rest: ByteArray): String {
        if (rest.size < EddystoneConstants.MIN_TLM_SIZE) return "Eddystone-TLM (malformed)"

        val mask = BleBinaryConstants.MASK_BYTE
        val s8 = BleBinaryConstants.SHIFT_8
        val s16 = BleBinaryConstants.SHIFT_16
        val s24 = BleBinaryConstants.SHIFT_24
        val o1 = BleBinaryConstants.OFS_1
        val o2 = BleBinaryConstants.OFS_2
        val o3 = BleBinaryConstants.OFS_3

        val batt = ((rest[EddystoneConstants.OFS_TLM_BATTERY].toInt() and mask) shl s8) or
            (rest[EddystoneConstants.OFS_TLM_BATTERY + o1].toInt() and mask)

        val tInt = rest[EddystoneConstants.OFS_TLM_TEMP].toInt()
        val temp = if (tInt == -EddystoneConstants.TEMP_SIGN_BIT &&
            rest[EddystoneConstants.OFS_TLM_TEMP + o1].toInt() == 0
        ) {
            null
        } else {
            val f = (rest[EddystoneConstants.OFS_TLM_TEMP + o1].toInt() and mask) / EddystoneConstants.PRECISION_TEMP
            tInt + f
        }

        val maskL = mask.toLong()
        val oA = EddystoneConstants.OFS_TLM_ADV_CNT
        val adv = ((rest[oA].toLong() and maskL) shl s24) or ((rest[oA + o1].toLong() and maskL) shl s16) or
            ((rest[oA + o2].toLong() and maskL) shl s8) or (rest[oA + o3].toLong() and maskL)

        val oU = EddystoneConstants.OFS_TLM_UPTIME
        val upt = ((rest[oU].toLong() and maskL) shl s24) or ((rest[oU + o1].toLong() and maskL) shl s16) or
            ((rest[oU + o2].toLong() and maskL) shl s8) or (rest[oU + o3].toLong() and maskL)

        val uSec = upt / EddystoneConstants.TEN_FLOAT
        val uStr = when {
            uSec >= EddystoneConstants.SECONDS_PER_DAY -> "%.1f days".format(uSec / EddystoneConstants.SECONDS_PER_DAY)
            uSec >= EddystoneConstants.SECONDS_PER_HOUR -> "%.1f hours".format(
                uSec / EddystoneConstants.SECONDS_PER_HOUR
            )
            else -> "%.1f sec".format(uSec)
        }

        val tStr = temp?.let { "%.1f°C".format(it) } ?: "N/A"
        val bStr = if (batt > 0) "${batt}mV" else "N/A"
        return "Eddystone-TLM: Bat=$bStr, Temp=$tStr, AdvCnt=$adv, Uptime=$uStr"
    }

    private fun parseTlmEncrypted(rest: ByteArray): String {
        if (rest.size < EddystoneConstants.MIN_ETLM_SIZE) return "Eddystone-eTLM (malformed)"

        val mask = BleBinaryConstants.MASK_BYTE
        val s8 = BleBinaryConstants.SHIFT_8
        val o1 = BleBinaryConstants.OFS_1

        val etlm = rest.sliceArray(EddystoneConstants.OFS_EID_DATA until EddystoneConstants.OFS_EID_SALT_START)
            .joinToString("") { "%02X".format(it) }

        val salt = ((rest[EddystoneConstants.OFS_EID_SALT_START].toInt() and mask) shl s8) or
            (rest[EddystoneConstants.OFS_EID_SALT_START + o1].toInt() and mask)

        val mic = ((rest[EddystoneConstants.OFS_EID_MIC_START].toInt() and mask) shl s8) or
            (rest[EddystoneConstants.OFS_EID_MIC_START + o1].toInt() and mask)

        return "eTLM (Encrypted): Salt=0x%04X, MIC=0x%04X, ETLM=$etlm".format(salt, mic)
    }
}
