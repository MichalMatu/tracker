package io.blueeye.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

internal fun prepareDatabaseExport(
    context: Context,
    viewModel: SettingsViewModel,
    onExportReady: (String) -> Unit,
) {
    Toast.makeText(context, "Preparing session export...", Toast.LENGTH_SHORT).show()
    viewModel.exportDatabase { json ->
        if (json == null) {
            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
        } else {
            onExportReady(json)
        }
    }
}

internal fun copyExportToClipboard(
    context: Context,
    json: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("BlueEye Session Export JSON", json)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied ${json.length} chars to clipboard", Toast.LENGTH_LONG).show()
}

internal fun shareExport(
    context: Context,
    json: String,
) {
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "BlueEye session export")
            putExtra(Intent.EXTRA_TEXT, json)
        }
    val chooser = Intent.createChooser(sendIntent, "Share BlueEye session export")
    runCatching {
        context.startActivity(chooser)
    }.onFailure {
        Toast.makeText(context, "No app can share the export", Toast.LENGTH_SHORT).show()
    }
}
