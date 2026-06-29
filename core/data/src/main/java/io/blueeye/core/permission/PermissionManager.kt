package io.blueeye.core.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionManager {
    fun hasBlePermissions(context: Context): Boolean {
        return getMissingBlePermissions(context).isEmpty() &&
            getMissingLocationPermissions(context).isEmpty()
    }

    fun hasScannerStartupPermissions(context: Context): Boolean {
        return getMissingScannerStartupPermissions(context).isEmpty()
    }

    fun getMissingScannerStartupPermissions(context: Context): List<String> {
        return getMissingBlePermissions(context) +
            getMissingLocationPermissions(context) +
            getMissingNotificationPermissions(context)
    }

    fun missingPermissionsMessage(missingPermissions: List<String>): String {
        val labels =
            missingPermissions
                .map(::permissionLabel)
                .distinct()
                .joinToString()

        return if (labels.isBlank()) {
            "Scanner permissions are granted."
        } else {
            "Missing scanner permissions: $labels"
        }
    }

    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        permissions += Manifest.permission.ACCESS_COARSE_LOCATION
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        return permissions.distinct().toTypedArray()
    }

    private fun getMissingBlePermissions(context: Context): List<String> {
        val permissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            } else {
                emptyList()
            }

        return permissions.filterNot { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getMissingLocationPermissions(context: Context): List<String> {
        return listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ).filterNot { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getMissingNotificationPermissions(context: Context): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return emptyList()

        return listOf(Manifest.permission.POST_NOTIFICATIONS)
            .filterNot { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
    }

    private fun permissionLabel(permission: String): String {
        return when (permission) {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT -> "Nearby devices"
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION -> "Location"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            else -> permission.substringAfterLast('.')
        }
    }
}
