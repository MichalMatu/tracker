package io.blueeye.core.scanner.analysis.parsers

object EddystoneConstants {
    const val FRAME_TYPE_UID = 0x00
    const val FRAME_TYPE_URL = 0x10
    const val FRAME_TYPE_TLM = 0x20
    const val FRAME_TYPE_EID = 0x30

    const val TLM_VERSION_PLAIN = 0x00
    const val TLM_VERSION_ENCRYPTED = 0x01

    const val MIN_UID_SIZE = 18
    const val NAMESPACE_START = 2
    const val NAMESPACE_END = 11
    const val INSTANCE_START = 12
    const val INSTANCE_END = 17

    const val MIN_URL_SIZE = 3
    const val MIN_TLM_SIZE = 14
    const val MIN_ETLM_SIZE = 18

    const val PRINTABLE_ASCII_START = 0x20
    const val PRINTABLE_ASCII_END = 0x7E

    const val OFS_TLM_BATTERY = 2
    const val OFS_TLM_TEMP = 4
    const val OFS_TLM_ADV_CNT = 6
    const val OFS_TLM_UPTIME = 10

    const val OFS_EID_DATA = 2
    const val OFS_EID_SALT_START = 14
    const val OFS_EID_MIC_START = 16

    const val PRECISION_TEMP = 256.0
    const val TEMP_SIGN_BIT = 128
    const val TEN_FLOAT = 10.0
    const val SECONDS_PER_DAY = 86400
    const val SECONDS_PER_HOUR = 3600
    const val SECONDS_PER_MINUTE = 60

    object UrlSchemes {
        const val HTTP_WWW = 0x00
        const val HTTPS_WWW = 0x01
        const val HTTP = 0x02
        const val HTTPS = 0x03
    }

    val URL_EXPANSIONS = mapOf(
        0x00 to ".com/",
        0x01 to ".org/",
        0x02 to ".edu/",
        0x03 to ".net/",
        0x04 to ".info/",
        0x05 to ".biz/",
        0x06 to ".gov/",
        0x07 to ".com",
        0x08 to ".org",
        0x09 to ".edu",
        0x0A to ".net",
        0x0B to ".info",
        0x0C to ".biz",
        0x0D to ".gov"
    )
}
