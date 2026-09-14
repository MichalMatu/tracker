package io.blueeye.feature.telemetry

import android.accounts.Account
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import io.blueeye.core.ui.theme.Dimens

private enum class AuthorizationUiAction {
    CONNECT,
    RUN_TEST,
    RESTORE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelemetryBridgeScreen(
    onBackClick: () -> Unit,
    viewModel: TelemetryBridgeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val authorizationClient = remember(context) { Identity.getAuthorizationClient(context) }
    var pendingAction by remember { mutableStateOf(AuthorizationUiAction.CONNECT) }

    val consumeAuthorizationResult: (AuthorizationResult) -> Unit = { result ->
        viewModel.onAuthorizationResult(
            token = result.accessToken,
            grantedScopes = result.grantedScopes.toSet(),
            runTestAfterAuthorization = pendingAction == AuthorizationUiAction.RUN_TEST,
        )
    }

    val authorizationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
            val resultData = activityResult.data
            if (resultData == null) {
                val message =
                    if (activityResult.resultCode == Activity.RESULT_CANCELED) {
                        "Google authorization was cancelled"
                    } else {
                        "Google authorization returned no result"
                    }
                viewModel.onAuthorizationFailed(message)
                return@rememberLauncherForActivityResult
            }
            try {
                consumeAuthorizationResult(
                    authorizationClient.getAuthorizationResultFromIntent(resultData),
                )
            } catch (error: ApiException) {
                viewModel.onAuthorizationFailed(error.message ?: "Google authorization failed")
            }
        }

    fun requestAuthorization(
        action: AuthorizationUiAction,
        forceAccountPicker: Boolean,
    ) {
        pendingAction = action
        if (action == AuthorizationUiAction.RESTORE) {
            viewModel.onAuthorizationRestoreStarted()
        } else {
            viewModel.onAuthorizationStarted(forTest = action == AuthorizationUiAction.RUN_TEST)
        }

        val requestBuilder =
            AuthorizationRequest.builder()
                .setRequestedScopes(GoogleBridgeScopes.requested)

        if (forceAccountPicker) {
            requestBuilder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        } else {
            uiState.selectedAccountEmail?.let { email ->
                requestBuilder.setAccount(googleAccount(email))
            }
        }

        authorizationClient.authorize(requestBuilder.build())
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    if (action == AuthorizationUiAction.RESTORE) {
                        viewModel.onAuthorizationRestoreRequiresInteraction()
                    } else {
                        val pendingIntent = result.pendingIntent
                        if (pendingIntent == null) {
                            viewModel.onAuthorizationFailed("Google authorization requires a missing resolution")
                        } else {
                            authorizationLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                            )
                        }
                    }
                } else {
                    consumeAuthorizationResult(result)
                }
            }.addOnFailureListener { error ->
                val message = error.message ?: "Google authorization failed"
                if (action == AuthorizationUiAction.RESTORE) {
                    viewModel.onAuthorizationRestoreFailed(message)
                } else {
                    viewModel.onAuthorizationFailed(message)
                }
            }
    }

    fun revokeAccess() {
        viewModel.onRevocationStarted()
        val requestBuilder = RevokeAccessRequest.builder().setScopes(GoogleBridgeScopes.requested)
        uiState.selectedAccountEmail?.let { email ->
            requestBuilder.setAccount(googleAccount(email))
        }
        authorizationClient.revokeAccess(requestBuilder.build())
            .addOnSuccessListener { viewModel.onRevoked() }
            .addOnFailureListener { error ->
                viewModel.onRevocationFailed(error.message ?: "Google access revocation failed")
            }
    }

    LaunchedEffect(Unit) {
        if (uiState.selectedAccountEmail != null && !uiState.isAuthorized) {
            requestAuthorization(
                action = AuthorizationUiAction.RESTORE,
                forceAccountPicker = false,
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Telemetry & AI Bridge", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        TelemetryBridgeContent(
            state = uiState,
            onConnect = { requestAuthorization(AuthorizationUiAction.CONNECT, forceAccountPicker = true) },
            onRunTest = { requestAuthorization(AuthorizationUiAction.RUN_TEST, forceAccountPicker = false) },
            onDisconnect = ::revokeAccess,
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun TelemetryBridgeContent(
    state: TelemetryBridgeUiState,
    onConnect: () -> Unit,
    onRunTest: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.PaddingMedium),
        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
    ) {
        BridgeIntroCard()
        BridgeConnectionCard(state)
        BridgeActions(state, onConnect, onRunTest, onDisconnect)
        if (state.lastDriveFileId != null || state.lastGmailMessageId != null) {
            LastBridgeTestCard(state)
        }
        Spacer(Modifier.height(Dimens.PaddingSmall))
    }
}

@Composable
private fun BridgeIntroCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
        ) {
            Text("Developer bridge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text =
                    "Use a dedicated secondary Google account. " +
                        "The prototype requests only email identity, Drive file access, and Gmail send access.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text =
                    "No telemetry is collected automatically in T0. " +
                        "This screen only validates Google authorization, one Drive JSON upload, " +
                        "and one self-addressed Gmail trigger.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BridgeConnectionCard(state: TelemetryBridgeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
        ) {
            Text("Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            StatusRow("Account", state.selectedAccountEmail ?: "Not selected")
            StatusRow("Drive file scope", if (state.hasDriveAccess) "Granted" else "Not granted")
            StatusRow("Gmail send scope", if (state.hasGmailSendAccess) "Granted" else "Not granted")
            Text(state.statusMessage, style = MaterialTheme.typography.bodyMedium)
            state.errorMessage?.let { errorMessage ->
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun BridgeActions(
    state: TelemetryBridgeUiState,
    onConnect: () -> Unit,
    onRunTest: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Button(
        onClick = onConnect,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.isAuthorized) "Change Google account" else "Connect Google account")
    }

    Button(
        onClick = onRunTest,
        enabled = state.canRunTest && !state.isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isBusy && state.isAuthorized) {
            CircularProgressIndicator(
                modifier = Modifier.size(Dimens.IconMedium),
                strokeWidth = Dimens.PaddingExtraSmall / 2f,
            )
        } else {
            Text("Run Drive + Gmail test")
        }
    }

    OutlinedButton(
        onClick = onDisconnect,
        enabled = state.isAuthorized && !state.isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Revoke Google access")
    }
}

@Composable
private fun LastBridgeTestCard(state: TelemetryBridgeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
        ) {
            Text("Last T0 test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            state.lastDriveFileName?.let { StatusRow("Drive file", it) }
            state.lastDriveFileId?.let { StatusRow("Drive file id", it) }
            state.lastGmailMessageId?.let { StatusRow("Gmail message id", it) }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun TelemetryBridgeContentPreview() {
    MaterialTheme {
        TelemetryBridgeContent(
            state =
                TelemetryBridgeUiState(
                    authorizationState = TelemetryAuthorizationState.AUTHORIZED,
                    selectedAccountEmail = "blueeye.telemetry@example.com",
                    grantedScopes = setOf(GoogleBridgeScopes.DRIVE_FILE, GoogleBridgeScopes.GMAIL_SEND),
                    statusMessage = "Connected",
                    lastDriveFileName = "bridge-test-1.json",
                    lastDriveFileId = "drive-file-id",
                    lastGmailMessageId = "gmail-message-id",
                ),
            onConnect = {},
            onRunTest = {},
            onDisconnect = {},
        )
    }
}

private fun googleAccount(email: String): Account = Account(email, "com.google")
