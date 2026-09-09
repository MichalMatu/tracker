package io.blueeye.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

private const val ClipboardSafeMaxBytes = 512 * 1024

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
            onExportReady(json.withFieldMvpDiagnostics(viewModel.uiState.value))
        }
    }
}

internal fun isClipboardPayloadSafe(json: String): Boolean {
    return json.toByteArray(Charsets.UTF_8).size <= ClipboardSafeMaxBytes
}

internal fun copyExportToClipboard(
    context: Context,
    json: String,
) {
    if (!isClipboardPayloadSafe(json)) {
        Toast.makeText(
            context,
            "Export is too large for clipboard. Use Share to send the JSON file.",
            Toast.LENGTH_LONG,
        ).show()
        return
    }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    runCatching {
        clipboard.setPrimaryClip(ClipData.newPlainText("BlueEye Session Export JSON", json))
    }.onSuccess {
        Toast.makeText(context, "Copied ${json.length} chars to clipboard", Toast.LENGTH_LONG).show()
    }.onFailure {
        Toast.makeText(
            context,
            "Clipboard export failed. Use Share to send the JSON file.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

internal fun shareExport(
    context: Context,
    json: String,
) {
    runCatching {
        val exportFile = writeSessionExportFile(context, json)
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile,
            )
        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "BlueEye session export")
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("BlueEye Session Export JSON", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val chooser =
            Intent.createChooser(sendIntent, "Share BlueEye session export").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(chooser)
    }.onFailure {
        Toast.makeText(context, "Unable to share session export", Toast.LENGTH_SHORT).show()
    }
}

private fun writeSessionExportFile(
    context: Context,
    json: String,
): File {
    val directory = File(context.cacheDir, "session_exports")
    if (!directory.exists() && !directory.mkdirs()) {
        throw IOException("Unable to create session export directory")
    }

    val target = File(directory, "blueeye-session-export.json")
    val temporary = File(directory, "blueeye-session-export.json.tmp")
    temporary.bufferedWriter(Charsets.UTF_8).use { writer ->
        writer.write(json)
    }

    if (target.exists() && !target.delete()) {
        temporary.delete()
        throw IOException("Unable to replace previous session export")
    }
    if (!temporary.renameTo(target)) {
        temporary.copyTo(target, overwrite = true)
        temporary.delete()
    }
    return target
}
