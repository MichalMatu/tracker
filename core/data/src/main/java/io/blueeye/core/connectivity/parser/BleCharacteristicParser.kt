package io.blueeye.core.connectivity.parser

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleCharacteristicParser
@Inject
constructor() {
    fun parseStringValue(value: ByteArray): String {
        return String(value, Charsets.UTF_8).filter {
            it.isLetterOrDigit() || it.isWhitespace() || it == '.' || it == '-'
        }
    }
}
