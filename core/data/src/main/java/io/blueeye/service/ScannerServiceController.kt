package io.blueeye.service

import android.content.Context
import android.content.Intent
import android.util.Log
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

        try {
            ScannerService.publishStarting()
            appContext.startForegroundService(intent)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val message = "Foreground scanner service failed to start: ${e.message ?: e.javaClass.simpleName}"
            Log.e(TAG, message, e)
            ScannerService.publishError(message)
        }
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        val intent =
            Intent(appContext, ScannerService::class.java).apply {
                action = ScannerService.ACTION_STOP
            }

        try {
            appContext.startService(intent)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val message = "Foreground scanner service failed to stop: ${e.message ?: e.javaClass.simpleName}"
            Log.e(TAG, message, e)
            ScannerService.publishError(message)
        }
    }

    fun reportMissingPermissions(context: Context) {
        reportMissingPermissions(
            missingPermissions = PermissionManager.getMissingScannerStartupPermissions(context),
        )
    }

    private fun reportMissingPermissions(missingPermissions: List<String>) {
        val message = PermissionManager.missingPermissionsMessage(missingPermissions)
        Log.e(TAG, message)
        ScannerService.publishError(message)
    }
}
