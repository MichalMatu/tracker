package io.blueeye.feature.settings

import java.io.Writer

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
              "runtimeProfile": "${scanner.runtimeProfile.name}",
              "state": ${scanner.state.toString().jsonStringOrNull()},
              "startedAt": ${scanner.startedAt.jsonValue()},
              "lastBleSeenAt": ${scanner.lastBleResultAt.jsonValue()},
              "lastClassicSeenAt": ${scanner.lastClassicResultAt.jsonValue()},
              "bleResultsPerMinute": ${scanner.bleResultsPerMinute},
              "classicResultsPerMinute": ${scanner.classicResultsPerMinute},
              "droppedQueueEvents": ${scanner.droppedQueueEvents},
              "ingest": {
                "windowStartedAt": ${scanner.ingest.windowStartedAt},
                "rawBleCallbacksTotal": ${scanner.ingest.rawBleCallbacksTotal},
                "rawBleCallbacksPerMinute": ${scanner.ingest.rawBleCallbacksPerMinute},
                "enqueueAcceptedTotal": ${scanner.ingest.enqueueAcceptedTotal},
                "enqueueAcceptedPerMinute": ${scanner.ingest.enqueueAcceptedPerMinute},
                "enqueueRejectedTotal": ${scanner.ingest.enqueueRejectedTotal},
                "queueDroppedTotal": ${scanner.ingest.queueDroppedTotal},
                "coalescedTotal": ${scanner.ingest.coalescedTotal},
                "coalescedPerMinute": ${scanner.ingest.coalescedPerMinute},
                "processingStartedTotal": ${scanner.ingest.processingStartedTotal},
                "processingSucceededTotal": ${scanner.ingest.processingSucceededTotal},
                "processedPerMinute": ${scanner.ingest.processedPerMinute},
                "processingFailedTotal": ${scanner.ingest.processingFailedTotal},
                "provisionalDiscardedTotal": ${scanner.ingest.provisionalDiscardedTotal},
                "persistedDeviceUpdatesTotal": ${scanner.ingest.persistedDeviceUpdatesTotal},
                "persistedDeviceUpdatesPerMinute": ${scanner.ingest.persistedDeviceUpdatesPerMinute},
                "deviceUpdateThrottledTotal": ${scanner.ingest.deviceUpdateThrottledTotal},
                "signalSamplesWrittenTotal": ${scanner.ingest.signalSamplesWrittenTotal},
                "signalSamplesWrittenPerMinute": ${scanner.ingest.signalSamplesWrittenPerMinute},
                "signalSamplesThrottledTotal": ${scanner.ingest.signalSamplesThrottledTotal},
                "signalSampleWriteFailuresTotal": ${scanner.ingest.signalSampleWriteFailuresTotal},
                "queueDepth": ${scanner.ingest.queueDepth},
                "queueHighWaterMark": ${scanner.ingest.queueHighWaterMark},
                "lastQueueWaitMs": ${scanner.ingest.lastQueueWaitMs.jsonValue()},
                "totalQueueWaitMs": ${scanner.ingest.totalQueueWaitMs},
                "maxQueueWaitMs": ${scanner.ingest.maxQueueWaitMs},
                "lastProcessingDurationMs": ${scanner.ingest.lastProcessingDurationMs.jsonValue()},
                "totalProcessingDurationMs": ${scanner.ingest.totalProcessingDurationMs},
                "maxProcessingDurationMs": ${scanner.ingest.maxProcessingDurationMs}
              },
              "lastError": ${scanner.lastScanError.jsonStringOrNull()},
              "lastLifecycleTransition": ${scanner.lastLifecycleTransition?.name.jsonStringOrNull()},
              "lastLifecycleTransitionAt": ${scanner.lastLifecycleTransitionAt.jsonValue()},
              "lifecycleTransitionCount": ${scanner.lifecycleTransitionCount}
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


internal fun Writer.writeWithFieldMvpDiagnostics(
    json: String,
    uiState: SettingsUiState,
) {
    val insertionPoint = json.lastIndexOf('}')
    if (insertionPoint <= 0) {
        write(json)
        return
    }

    // Build only the small diagnostics fragment. Never create a second copy of the full export.
    val decoratedEmptyObject = "{}".withFieldMvpDiagnostics(uiState)
    val diagnosticsFragment = decoratedEmptyObject.substring(1, decoratedEmptyObject.length - 1)
    var prefixEnd = insertionPoint
    while (prefixEnd > 0 && json[prefixEnd - 1].isWhitespace()) {
        prefixEnd -= 1
    }

    write(json, 0, prefixEnd)
    write(diagnosticsFragment)
    write(json, insertionPoint, json.length - insertionPoint)
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
