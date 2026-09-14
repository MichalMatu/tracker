package io.blueeye.feature.telemetry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
}

@HiltViewModel
class TelemetryBridgeViewModel
    @Inject
    constructor(
        private val bridgeApi: GoogleWorkspaceBridgeApi,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(TelemetryBridgeUiState())
        val uiState: StateFlow<TelemetryBridgeUiState> = _uiState.asStateFlow()

        private var accessToken: String? = null

        fun onAuthorizationStarted(forTest: Boolean) {
            _uiState.update { state ->
                state.copy(
                    authorizationState = TelemetryAuthorizationState.AUTHORIZING,
                    isBusy = true,
                    statusMessage = if (forTest) "Refreshing Google authorization..." else "Connecting Google account...",
                    errorMessage = null,
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
    }
