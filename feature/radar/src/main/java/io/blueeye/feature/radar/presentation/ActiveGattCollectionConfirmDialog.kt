package io.blueeye.feature.radar.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.blueeye.core.ui.theme.BlueEyeTheme

@Composable
internal fun ActiveGattCollectionConfirmDialog(
    visible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start active GATT collection?") },
        text = { Text(ActiveGattCollectionCopy.CONFIRMATION_TEXT) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Start active GATT")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

internal fun handleAutoActiveProbeToggle(
    enabled: Boolean,
    onDisable: () -> Unit,
    onEnableRequested: () -> Unit,
) {
    if (enabled) {
        onDisable()
    } else {
        onEnableRequested()
    }
}

private object ActiveGattCollectionCopy {
    const val CONFIRMATION_TEXT =
        "BlueEye will connect to connectable BLE devices one at a time to collect GATT evidence. " +
            "Use this only when you explicitly want active collection; passive scanning remains the default."
}

@Preview(showBackground = true)
@Composable
fun ActiveGattCollectionConfirmDialogPreview() {
    BlueEyeTheme {
        ActiveGattCollectionConfirmDialog(
            visible = true,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
