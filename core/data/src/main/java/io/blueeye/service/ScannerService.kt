package io.blueeye.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.domain.scanner.ScannerRuntimeState
import io.blueeye.core.permission.PermissionManager
import io.blueeye.core.scanner.manager.BleScanner
import io.blueeye.core.scanner.manager.ScannerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScannerService : Service() {
    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "scanner_channel"

        const val CLEANUP_INITIAL_DELAY_MS = 30_000L
        const val CLEANUP_INTERVAL_MS = 60 * 60 * 1000L
        const val DEVICE_MAX_AGE_HOURS = 24
        const val HOUR_TO_MS = 60 * 60 * 1000L

        private val _scannerState = MutableStateFlow<ScannerRuntimeState>(ScannerRuntimeState.Idle)
        val scannerState: StateFlow<ScannerRuntimeState> = _scannerState.asStateFlow()

        internal fun publishStarting() {
            _scannerState.value = ScannerRuntimeState.Starting
        }

        internal fun publishError(message: String) {
            _scannerState.value = ScannerRuntimeState.Error(message)
        }
    }

    @Inject lateinit var bleScanner: BleScanner

    @Inject lateinit var deviceRepository: DeviceRepository

    @Inject lateinit var bleScanHandler: io.blueeye.core.data.repository.handler.ble.BleScanHandler

    @Inject lateinit var carryoverTracker: io.blueeye.core.data.tracker.AddressCarryoverTracker

    @Inject lateinit var deviceDao: io.blueeye.core.data.db.dao.DeviceDao

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var startupJob: Job? = null
    private var cleanupJob: Job? = null
    private var scannerStateJob: Job? = null
    private var bluetoothStateReceiverRegistered = false
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private val bluetoothStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return

                val state =
                    intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR,
                    )

                if (state == BluetoothAdapter.STATE_TURNING_OFF ||
                    state == BluetoothAdapter.STATE_OFF
                ) {
                    failScanner("Bluetooth is off or unavailable.")
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopForegroundService()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        if (_scannerState.value is ScannerRuntimeState.Running) {
            return
        }

        publishStarting()

        val missingPermissions = PermissionManager.getMissingScannerStartupPermissions(this)
        if (missingPermissions.isNotEmpty()) {
            failStartup(PermissionManager.missingPermissionsMessage(missingPermissions))
            return
        }

        if (!promoteToForeground()) {
            return
        }

        if (!bleScanner.isBluetoothEnabled()) {
            failStartup("Bluetooth is off or unavailable.")
            return
        }

        registerBluetoothStateReceiver()

        startupJob =
            serviceScope.launch {
                try {
                    val rehydrated =
                        runCatching {
                            val existingDevices = deviceDao.getAllDevices()
                            carryoverTracker.rehydrateFromDatabase(existingDevices)
                        }.onFailure { error ->
                            Log.e("ScannerService", "Failed to rehydrate carryover tracker", error)
                        }.isSuccess

                    if (!rehydrated) {
                        failScanner("Scanner startup failed: tracking memory could not be restored.")
                        return@launch
                    }

                    // Reset Follow-Me tracking session state (session-based timing)
                    bleScanHandler.resetSession()

                    try {
                        bleScanner.startScanning()
                        observeScannerState()
                    } catch (e: Exception) {
                        failScanner("Scanner failed to start: ${e.message ?: e.javaClass.simpleName}", e)
                        return@launch
                    }
                    startCleanupJob()
                    acquireWakeLock()
                } finally {
                    startupJob = null
                }
            }
    }

    private fun promoteToForeground(): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            true
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            failStartup("Foreground scanner service failed: ${e.message ?: e.javaClass.simpleName}", e)
            false
        }
    }

    private fun stopForegroundService() {
        startupJob?.cancel()
        startupJob = null
        scannerStateJob?.cancel()
        scannerStateJob = null
        unregisterBluetoothStateReceiver()
        bleScanner.stopScanning()
        stopForeground(STOP_FOREGROUND_REMOVE)
        cleanupJob?.cancel()
        releaseWakeLock()
        _scannerState.value = ScannerRuntimeState.Idle
        stopSelf()
    }

    private fun startCleanupJob() {
        cleanupJob?.cancel()
        cleanupJob = serviceScope.launch {
            delay(CLEANUP_INITIAL_DELAY_MS)
            while (isActive) {
                try {
                    val maxAge = DEVICE_MAX_AGE_HOURS * HOUR_TO_MS
                    deviceRepository.deleteOldDevices(maxAge)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Log.e("ScannerService", "Cleanup error", e)
                }
                delay(CLEANUP_INTERVAL_MS)
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "BlueEye:ScannerWakeLock")
            wakeLock?.setReferenceCounted(false)
        }
        if (wakeLock?.isHeld == false) wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        startupJob?.cancel()
        startupJob = null
        scannerStateJob?.cancel()
        unregisterBluetoothStateReceiver()
        if (_scannerState.value !is ScannerRuntimeState.Error) {
            _scannerState.value = ScannerRuntimeState.Idle
        }
        serviceScope.cancel()
        releaseWakeLock()
    }

    private fun observeScannerState() {
        scannerStateJob?.cancel()
        scannerStateJob =
            serviceScope.launch {
                bleScanner.state.collect { state ->
                    when (state) {
                        ScannerState.Idle -> Unit
                        ScannerState.Starting -> publishStarting()
                        ScannerState.Scanning,
                        is ScannerState.Focused -> _scannerState.value = ScannerRuntimeState.Running
                        is ScannerState.Error -> failScanner(state.message)
                    }
                }
            }
    }

    private fun failStartup(
        message: String,
        throwable: Throwable? = null,
    ) {
        if (throwable == null) {
            Log.e("ScannerService", message)
        } else {
            Log.e("ScannerService", message, throwable)
        }
        cleanupAfterFailure()
        _scannerState.value = ScannerRuntimeState.Error(message)
        stopSelf()
    }

    private fun failScanner(
        message: String,
        throwable: Throwable? = null,
    ) {
        if (throwable == null) {
            Log.e("ScannerService", message)
        } else {
            Log.e("ScannerService", message, throwable)
        }
        cleanupAfterFailure()
        _scannerState.value = ScannerRuntimeState.Error(message)
        stopSelf()
    }

    private fun cleanupAfterFailure() {
        startupJob?.cancel()
        startupJob = null
        scannerStateJob?.cancel()
        scannerStateJob = null
        cleanupJob?.cancel()
        unregisterBluetoothStateReceiver()
        runCatching { bleScanner.stopScanning() }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        releaseWakeLock()
    }

    private fun registerBluetoothStateReceiver() {
        if (bluetoothStateReceiverRegistered) return

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(bluetoothStateReceiver, filter)
        }
        bluetoothStateReceiverRegistered = true
    }

    private fun unregisterBluetoothStateReceiver() {
        if (!bluetoothStateReceiverRegistered) return

        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            // Receiver was already gone.
        }
        bluetoothStateReceiverRegistered = false
    }

    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = intent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val stopIntent = Intent(this, ScannerService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BlueEye Active")
            .setContentText("Scanning for background signals...")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop", stopPending)
            .setOngoing(true)
            .build()
    }
}
