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
            DiagnosticRow("BLE last seen", scanner.lastBleResultAt.formatTime())
            DiagnosticRow("Classic last seen", scanner.lastClassicResultAt.formatTime())
            DiagnosticRow("BLE/min", scanner.bleResultsPerMinute.toString())
            DiagnosticRow("Classic/min", scanner.classicResultsPerMinute.toString())
            DiagnosticRow("Dropped queue events", scanner.droppedQueueEvents.toString())
            DiagnosticRow("Last scan error", scanner.lastScanError ?: "None")

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
