package io.blueeye.feature.settings

internal fun String.withFieldMvpDiagnostics(uiState: SettingsUiState): String {
    val insertionPoint = lastIndexOf('}')
    if (insertionPoint <= 0) return this
    val scanner = uiState.scannerDiagnostics
    val alerts = uiState.alertDeliveryDiagnostics
    val payload =
        """
        ,
          "fieldMvpDiagnostics": {
            "scanner": {
              "state": "${scanner.state}",
              "startedAt": ${scanner.startedAt.jsonValue()},
              "lastBleSeenAt": ${scanner.lastBleResultAt.jsonValue()},
              "lastClassicSeenAt": ${scanner.lastClassicResultAt.jsonValue()},
              "bleResultsPerMinute": ${scanner.bleResultsPerMinute},
              "classicResultsPerMinute": ${scanner.classicResultsPerMinute},
              "droppedQueueEvents": ${scanner.droppedQueueEvents},
              "lastError": ${scanner.lastScanError.jsonStringOrNull()}
            },
            "alerts": {
              "postNotificationsGranted": ${alerts.postNotificationsGranted},
              "notificationsEnabled": ${alerts.notificationsEnabled},
              "headsUpChannelBlocked": ${alerts.headsUpChannel.blocked},
              "trayChannelBlocked": ${alerts.trayChannel.blocked},
              "lastStatus": "${alerts.lastResult.status.name}",
              "lastMessage": ${alerts.lastResult.message.jsonStringOrNull()},
              "lastPostedNotification": ${alerts.lastResult.postedNotification},
              "lastSoundPlayed": ${alerts.lastResult.soundPlayed},
              "lastVibrationTriggered": ${alerts.lastResult.vibrationTriggered}
            }
          }
        """.trimIndent()
    return substring(0, insertionPoint).trimEnd() + payload + substring(insertionPoint)
}

private fun Long?.jsonValue(): String = this?.toString() ?: "null"

private fun String?.jsonStringOrNull(): String = this?.let { "\"${it.escapeJson()}\"" } ?: "null"

private fun String.escapeJson(): String =
    buildString {
        for (char in this@escapeJson) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
