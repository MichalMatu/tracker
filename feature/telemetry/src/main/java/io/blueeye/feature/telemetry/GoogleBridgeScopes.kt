package io.blueeye.feature.telemetry

import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope

internal object GoogleBridgeScopes {
    const val DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    const val GMAIL_SEND = "https://www.googleapis.com/auth/gmail.send"

    val requested: List<Scope> =
        listOf(
            Scope(Scopes.EMAIL),
            Scope(DRIVE_FILE),
            Scope(GMAIL_SEND),
        )
}
