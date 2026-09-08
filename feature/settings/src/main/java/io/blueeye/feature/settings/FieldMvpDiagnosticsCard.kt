package io.blueeye.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.blueeye.core.domain.alert.AlertChannelDiagnostics
import io.blueeye.core.domain.scanner.ScannerRuntimeState
import io.blueeye.core.ui.theme.Dimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FieldMvpDiagnosticsCard(
    uiState: SettingsUiState,
    onTestAlert: () -> Unit,
) {
    val scanner = uiState.scannerDiagnostics
    val alerts = uiState.alertDeliveryDiagnostics
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Field MVP diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            DiagnosticRow("Runtime profile", scanner.runtimeProfile.name)
            DiagnosticRow("Scanner", scanner.state.label())
            val ingest = scanner.ingest
            DiagnosticRow("Ingest window", ingest.windowStartedAt.formatTime())
            DiagnosticRow("BLE last seen", scanner.lastBleResultAt.formatTime())
            DiagnosticRow("Classic last seen", scanner.lastClassicResultAt.formatTime())
            DiagnosticRow("Raw BLE/min", ingest.rawBleCallbacksPerMinute.toString())
            DiagnosticRow("Queued/min", ingest.enqueueAcceptedPerMinute.toString())
            DiagnosticRow("Coalesced/min", ingest.coalescedPerMinute.toString())
            DiagnosticRow("Processed/min", ingest.processedPerMinute.toString())
            DiagnosticRow("Persisted/min", ingest.persistedDeviceUpdatesPerMinute.toString())
            DiagnosticRow("Signal samples/min", ingest.signalSamplesWrittenPerMinute.toString())
            DiagnosticRow(
                "Raw / queued / coalesced total",
                "${ingest.rawBleCallbacksTotal} / ${ingest.enqueueAcceptedTotal} / ${ingest.coalescedTotal}",
            )
            DiagnosticRow(
                "Processed / persisted / samples total",
                "${ingest.processingSucceededTotal} / ${ingest.persistedDeviceUpdatesTotal} / " +
                    ingest.signalSamplesWrittenTotal,
            )
            DiagnosticRow("Queue depth", ingest.queueDepth.toString())
            DiagnosticRow("Queue high-water", ingest.queueHighWaterMark.toString())
            DiagnosticRow("Queue rejected", ingest.enqueueRejectedTotal.toString())
            DiagnosticRow("Queue dropped", ingest.queueDroppedTotal.toString())
            DiagnosticRow("Processing failed", ingest.processingFailedTotal.toString())
            DiagnosticRow("Provisional discard", ingest.provisionalDiscardedTotal.toString())
            DiagnosticRow("Sample write failed", ingest.signalSampleWriteFailuresTotal.toString())
            DiagnosticRow(
                "Queue wait avg/max",
                "${ingest.averageQueueWaitMs()}/${ingest.maxQueueWaitMs} ms",
            )
            DiagnosticRow(
                "Processing avg/max",
                "${ingest.averageProcessingDurationMs()}/${ingest.maxProcessingDurationMs} ms",
            )
            DiagnosticRow("Classic/min", scanner.classicResultsPerMinute.toString())
            DiagnosticRow("Last scan error", scanner.lastScanError ?: "None")
            DiagnosticRow("Lifecycle", scanner.lastLifecycleTransition?.name ?: "None")
            DiagnosticRow("Lifecycle at", scanner.lastLifecycleTransitionAt.formatTime())
            DiagnosticRow("Lifecycle transitions", scanner.lifecycleTransitionCount.toString())

            Spacer(Modifier.height(4.dp))
            DiagnosticRow("POST_NOTIFICATIONS", if (alerts.postNotificationsGranted) "Granted" else "Missing")
            DiagnosticRow("Notifications enabled", if (alerts.notificationsEnabled) "Enabled" else "Blocked")
            DiagnosticRow("Heads-up channel", alerts.headsUpChannel.summary())
            DiagnosticRow("Tray channel", alerts.trayChannel.summary())
            DiagnosticRow("Last alert result", alerts.lastResult.status.name)
            Text(
                text = alerts.lastResult.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onTestAlert,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Test alert")
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun io.blueeye.core.domain.scanner.ScannerIngestDiagnostics.averageQueueWaitMs(): Long =
    if (processingStartedTotal == 0L) 0L else totalQueueWaitMs / processingStartedTotal

private fun io.blueeye.core.domain.scanner.ScannerIngestDiagnostics.averageProcessingDurationMs(): Long {
    val completed = processingSucceededTotal + processingFailedTotal
    return if (completed == 0L) 0L else totalProcessingDurationMs / completed
}

private fun ScannerRuntimeState.label(): String =
    when (this) {
        ScannerRuntimeState.Idle -> "Idle"
        ScannerRuntimeState.Starting -> "Starting"
        ScannerRuntimeState.Running -> "Running"
        is ScannerRuntimeState.Error -> "Error: $message"
    }

private fun Long?.formatTime(): String =
    if (this == null || this == 0L) {
        "No data"
    } else {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(this))
    }

private fun AlertChannelDiagnostics.summary(): String {
    val importanceText = importance?.toString() ?: "n/a"
    val blockedText = if (blocked) "blocked" else "ok"
    return "importance $importanceText, $blockedText"
}
