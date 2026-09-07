package io.blueeye.service

import android.content.Context
import android.content.Intent
import android.util.Log
import io.blueeye.core.domain.scanner.ScannerRuntimeState
import io.blueeye.core.permission.PermissionManager

object ScannerServiceController {
    private const val TAG = "ScannerServiceController"

    fun start(context: Context) {
        val appContext = context.applicationContext
        val missingPermissions = PermissionManager.getMissingScannerStartupPermissions(appContext)

        if (missingPermissions.isNotEmpty()) {
            reportMissingPermissions(missingPermissions)
            return
        }

        val intent =
            Intent(appContext, ScannerService::class.java).apply {
                action = ScannerService.ACTION_START
            }

        startServiceCommand(
            context = appContext,
            intent = intent,
            foreground = true,
            failurePrefix = "Foreground scanner service failed to start",
        )
    }

    fun stop(context: Context) {
        when (ScannerService.scannerState.value) {
            ScannerRuntimeState.Idle,
            is ScannerRuntimeState.Error,
            -> return
            ScannerRuntimeState.Starting,
            ScannerRuntimeState.Running,
            -> Unit
        }

        val appContext = context.applicationContext
        val intent =
            Intent(appContext, ScannerService::class.java).apply {
                action = ScannerService.ACTION_STOP
            }

        startServiceCommand(
            context = appContext,
            intent = intent,
            foreground = false,
            failurePrefix = "Foreground scanner service failed to stop",
        )
    }

    fun startFocusedScan(
        context: Context,
        macAddress: String,
    ): Result<Unit> =
        runCatching {
            check(ScannerService.scannerState.value is ScannerRuntimeState.Running) {
                "Scanner service must be running before focused scan"
            }
            val appContext = context.applicationContext
            val intent =
                Intent(appContext, ScannerService::class.java).apply {
                    action = ScannerService.ACTION_FOCUSED_SCAN
                    putExtra(ScannerService.EXTRA_FOCUSED_MAC, macAddress)
                }
            appContext.startService(intent)
        }

    fun resumePassiveScan(context: Context): Result<Unit> =
        runCatching {
            if (ScannerService.scannerState.value !is ScannerRuntimeState.Running) return@runCatching
            val appContext = context.applicationContext
            val intent =
                Intent(appContext, ScannerService::class.java).apply {
                    action = ScannerService.ACTION_RESUME_PASSIVE
                }
            appContext.startService(intent)
        }

    fun reportMissingPermissions(context: Context) {
        reportMissingPermissions(
            missingPermissions = PermissionManager.getMissingScannerStartupPermissions(context),
        )
    }

    private fun startServiceCommand(
        context: Context,
        intent: Intent,
        foreground: Boolean,
        failurePrefix: String,
    ) {
        try {
            if (foreground) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val message = "$failurePrefix: ${e.message ?: e.javaClass.simpleName}"
            Log.e(TAG, message, e)
            ScannerService.publishError(message)
        }
    }

    private fun reportMissingPermissions(missingPermissions: List<String>) {
        val message = PermissionManager.missingPermissionsMessage(missingPermissions)
        Log.e(TAG, message)
        ScannerService.publishError(message)
    }
}
