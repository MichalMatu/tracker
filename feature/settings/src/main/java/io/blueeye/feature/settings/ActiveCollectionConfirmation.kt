package io.blueeye.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.blueeye.core.ui.theme.BlueEyeTheme

@Composable
internal fun ActiveCollectionConfirmDialog(
    visible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable active collection?") },
        text = { Text(ActiveCollectionConfirmationCopy.TEXT) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Enable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

internal fun handleActiveCollectionSettingChange(
    enabled: Boolean,
    requestedEnabled: Boolean,
    onDisable: () -> Unit,
    onEnableRequested: () -> Unit,
) {
    when {
        !enabled && requestedEnabled -> onEnableRequested()
        enabled && !requestedEnabled -> onDisable()
    }
}

private object ActiveCollectionConfirmationCopy {
    const val TEXT =
        "BlueEye will connect to connectable BLE devices one at a time to collect GATT evidence. " +
            "Use this only when you explicitly want active collection; passive scanning remains the default."
}

@Preview(showBackground = true)
@Composable
fun ActiveCollectionConfirmDialogPreview() {
    BlueEyeTheme {
        ActiveCollectionConfirmDialog(
            visible = true,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
