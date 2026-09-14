package io.blueeye.feature.telemetry

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TelemetryAuthorizationState {
    DISCONNECTED,
    AUTHORIZING,
    AUTHORIZED,
    REVOKING,
}

data class TelemetryBridgeUiState(
    val authorizationState: TelemetryAuthorizationState = TelemetryAuthorizationState.DISCONNECTED,
    val selectedAccountEmail: String? = null,
    val grantedScopes: Set<String> = emptySet(),
    val isBusy: Boolean = false,
    val statusMessage: String = "Not connected",
    val errorMessage: String? = null,
    val lastDriveFileId: String? = null,
    val lastDriveFileName: String? = null,
    val lastGmailMessageId: String? = null,
) {
    val isAuthorized: Boolean
        get() = authorizationState == TelemetryAuthorizationState.AUTHORIZED

    val hasDriveAccess: Boolean
        get() = GoogleBridgeScopes.DRIVE_FILE in grantedScopes

    val hasGmailSendAccess: Boolean
        get() = GoogleBridgeScopes.GMAIL_SEND in grantedScopes

    val canRunTest: Boolean
        get() =
            isAuthorized &&
                selectedAccountEmail != null &&
                hasDriveAccess &&
                hasGmailSendAccess
}

@HiltViewModel
class TelemetryBridgeViewModel
    @Inject
    internal constructor(
        private val bridgeApi: GoogleWorkspaceBridgeApi,
        @ApplicationContext context: Context,
    ) : ViewModel() {
        private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        private val persistedAccountEmail = preferences.getString(KEY_ACCOUNT_EMAIL, null)
        private val _uiState =
            MutableStateFlow(
                TelemetryBridgeUiState(
                    selectedAccountEmail = persistedAccountEmail,
                    statusMessage =
                        if (persistedAccountEmail == null) {
                            "Not connected"
                        } else {
                            "Restoring Google authorization..."
                        },
                ),
            )
        val uiState: StateFlow<TelemetryBridgeUiState> = _uiState.asStateFlow()

        private var accessToken: String? = null

        fun onAuthorizationStarted(forTest: Boolean) {
            _uiState.update { state ->
                state.copy(
                    authorizationState = TelemetryAuthorizationState.AUTHORIZING,
                    isBusy = true,
                    statusMessage =
                        if (forTest) {
                            "Refreshing Google authorization..."
                        } else {
                            "Connecting Google account..."
                        },
                    errorMessage = null,
                )
            }
        }

        fun onAuthorizationRestoreStarted() {
            _uiState.update { state ->
                state.copy(
                    authorizationState = TelemetryAuthorizationState.AUTHORIZING,
                    grantedScopes = emptySet(),
                    isBusy = true,
                    statusMessage = "Restoring Google authorization...",
                    errorMessage = null,
                )
            }
        }

        fun onAuthorizationRestoreRequiresInteraction() {
            accessToken = null
            _uiState.update { state ->
                state.copy(
                    authorizationState = TelemetryAuthorizationState.DISCONNECTED,
                    grantedScopes = emptySet(),
                    isBusy = false,
                    statusMessage = "Google access needs reconnection",
                    errorMessage = "Tap Connect Google account to continue.",
                )
            }
        }

        fun onAuthorizationRestoreFailed(message: String) {
            accessToken = null
            _uiState.update { state ->
                state.copy(
                    authorizationState = TelemetryAuthorizationState.DISCONNECTED,
                    grantedScopes = emptySet(),
                    isBusy = false,
                    statusMessage = "Unable to restore Google authorization",
                    errorMessage = message,
                )
            }
        }

        fun onAuthorizationFailed(message: String) {
            _uiState.update { state ->
                state.copy(
                    authorizationState =
                        if (accessToken == null) {
                            TelemetryAuthorizationState.DISCONNECTED
                        } else {
                            TelemetryAuthorizationState.AUTHORIZED
                        },
                    isBusy = false,
                    statusMessage = "Authorization failed",
                    errorMessage = message,
                )
            }
        }

        fun onAuthorizationResult(
            token: String?,
            grantedScopes: Set<String>,
            runTestAfterAuthorization: Boolean,
        ) {
            if (token.isNullOrBlank()) {
                onAuthorizationFailed("Google authorization returned no access token")
                return
            }

            if (!GoogleBridgeScopes.requiredForTest.all(grantedScopes::contains)) {
                accessToken = null
                _uiState.update { state ->
                    state.copy(
                        authorizationState = TelemetryAuthorizationState.DISCONNECTED,
                        grantedScopes = grantedScopes,
                        isBusy = false,
                        statusMessage = "Required Google access was not granted",
                        errorMessage = "Drive file and Gmail send access are both required for the T0 bridge test.",
                    )
                }
                return
            }

            accessToken = token
            _uiState.update { state ->
                state.copy(
                    authorizationState = TelemetryAuthorizationState.AUTHORIZED,
                    grantedScopes = grantedScopes,
                    isBusy = true,
                    statusMessage = "Authorization granted; loading account identity...",
                    errorMessage = null,
                )
            }

            viewModelScope.launch {
                bridgeApi.fetchAccountEmail(token)
                    .onSuccess { email ->
                        preferences.edit().putString(KEY_ACCOUNT_EMAIL, email).apply()
                        _uiState.update { state ->
                            state.copy(
                                selectedAccountEmail = email,
                                isBusy = false,
                                statusMessage = "Connected to $email",
                                errorMessage = null,
                            )
                        }
                        if (runTestAfterAuthorization) {
                            runBridgeTest(token, email)
                        }
                    }.onFailure { error ->
                        _uiState.update { state ->
                            state.copy(
                                isBusy = false,
                                statusMessage = "Authorized, but account identity is unavailable",
                                errorMessage = error.message ?: "Unable to read Google account email",
                            )
                        }
                    }
            }
        }

        fun onRevocationStarted() {
            _uiState.update { state ->
                state.copy(
                    authorizationState = TelemetryAuthorizationState.REVOKING,
                    isBusy = true,
                    statusMessage = "Disconnecting Google account...",
                    errorMessage = null,
                )
            }
        }

        fun onRevocationFailed(message: String) {
            _uiState.update { state ->
                state.copy(
                    authorizationState = TelemetryAuthorizationState.AUTHORIZED,
                    isBusy = false,
                    statusMessage = "Disconnect failed",
                    errorMessage = message,
                )
            }
        }

        fun onRevoked() {
            accessToken = null
            preferences.edit().remove(KEY_ACCOUNT_EMAIL).apply()
            _uiState.value = TelemetryBridgeUiState(statusMessage = "Google access revoked")
        }

        private fun runBridgeTest(
            token: String,
            email: String,
        ) {
            _uiState.update { state ->
                state.copy(
                    isBusy = true,
                    statusMessage = "Creating Drive test artifact and Gmail trigger...",
                    errorMessage = null,
                )
            }

            viewModelScope.launch {
                bridgeApi.runBridgeTest(token, email)
                    .onSuccess { result ->
                        _uiState.update { state ->
                            state.copy(
                                isBusy = false,
                                statusMessage = "T0 API test sent successfully",
                                errorMessage = null,
                                lastDriveFileId = result.driveFileId,
                                lastDriveFileName = result.driveFileName,
                                lastGmailMessageId = result.gmailMessageId,
                            )
                        }
                    }.onFailure { error ->
                        _uiState.update { state ->
                            state.copy(
                                isBusy = false,
                                statusMessage = "T0 API test failed",
                                errorMessage = error.message ?: "Unknown Google API failure",
                            )
                        }
                    }
            }
        }

        private companion object {
            const val PREFERENCES_NAME = "telemetry_bridge"
            const val KEY_ACCOUNT_EMAIL = "google_account_email"
        }
    }
