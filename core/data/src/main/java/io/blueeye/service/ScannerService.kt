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
import io.blueeye.core.data.scanner.ScannerRuntimeDiagnosticsStore
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.domain.scanner.ScannerLifecycleTransition
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
        private const val TAG = "ScannerService"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_FOCUSED_SCAN = "ACTION_FOCUSED_SCAN"
        const val ACTION_RESUME_PASSIVE = "ACTION_RESUME_PASSIVE"
        const val EXTRA_FOCUSED_MAC = "EXTRA_FOCUSED_MAC"
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
            ScannerRuntimeDiagnosticsStore.recordScanError(message)
        }
    }

    @Inject lateinit var bleScanner: BleScanner

    @Inject lateinit var deviceRepository: DeviceRepository

    @Inject lateinit var carryoverTracker: io.blueeye.core.data.tracker.AddressCarryoverTracker

    @Inject lateinit var deviceDao: io.blueeye.core.data.db.dao.DeviceDao

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val startupOwnership = ScannerStartupOwnership()
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
                    recordLifecycleTransition(ScannerLifecycleTransition.BLUETOOTH_OFF)
                    failScanner("Bluetooth is off or unavailable.")
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        recordLifecycleTransition(ScannerLifecycleTransition.SERVICE_CREATED)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startScannerService()
            ACTION_STOP -> stopScannerService(startId)
            ACTION_FOCUSED_SCAN -> startFocusedScan(intent.getStringExtra(EXTRA_FOCUSED_MAC))
            ACTION_RESUME_PASSIVE -> resumePassiveScan()
            null -> Log.i(TAG, "Ignoring null restart intent; scanner restart is explicit")
        }
        // Stability policy: the OS must not recreate scanning after process/service teardown.
        // A fresh user/runtime Start command is required.
        return START_NOT_STICKY
    }

    private fun startScannerService() {
        if (!ScannerServiceLifecyclePolicy.shouldStart(_scannerState.value, startupJob?.isActive == true)) {
            recordLifecycleTransition(ScannerLifecycleTransition.START_IGNORED_ALREADY_ACTIVE)
            return
        }

        recordLifecycleTransition(ScannerLifecycleTransition.START_REQUESTED)
        publishStarting()
        ScannerRuntimeDiagnosticsStore.recordState(ScannerRuntimeState.Starting)

        if (!prepareScannerStartup()) return

        val startupToken = startupOwnership.beginStartup()
        startupJob =
            serviceScope.launch {
                try {
                    val rehydrated =
                        runCatching {
                            val existingDevices = deviceDao.getAllDevices()
                            carryoverTracker.rehydrateFromDatabase(existingDevices)
                        }.onFailure { error ->
                            Log.e(TAG, "Failed to rehydrate carryover tracker", error)
                        }.isSuccess

                    if (!rehydrated) {
                        failScanner("Scanner startup failed: tracking memory could not be restored.")
                        return@launch
                    }

                    val startupContext = kotlinx.coroutines.currentCoroutineContext()
                    try {
                        val started =
                            startupOwnership.runIfCurrent(startupToken) {
                                if (!startupContext.isActive) return@runIfCurrent
                                bleScanner.startScanning()
                                observeScannerState()
                                startCleanupJob()
                                acquireWakeLock()
                            }
                        if (!started || !startupContext.isActive) return@launch
                    } catch (e: Exception) {
                        failScanner("Scanner failed to start: ${e.message ?: e.javaClass.simpleName}", e)
                        return@launch
                    }
                } finally {
                    if (startupOwnership.isCurrent(startupToken)) {
                        startupJob = null
                    }
                }
            }
    }

    private fun prepareScannerStartup(): Boolean {
        val missingPermissions = PermissionManager.getMissingScannerStartupPermissions(this)
        return when {
            missingPermissions.isNotEmpty() -> {
                failStartup(PermissionManager.missingPermissionsMessage(missingPermissions))
                false
            }
            !promoteToForeground() -> false
            !bleScanner.isBluetoothEnabled -> {
                failStartup("Bluetooth is off or unavailable.")
                false
            }
            else -> {
                registerBluetoothStateReceiver()
                true
            }
        }
    }

    private fun promoteToForeground(): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
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

    private fun stopScannerService(startId: Int) {
        if (!ScannerServiceLifecyclePolicy.shouldStop(_scannerState.value, startupJob?.isActive == true)) {
            recordLifecycleTransition(ScannerLifecycleTransition.STOP_IGNORED_ALREADY_IDLE)
            stopSelfResult(startId)
            return
        }

        recordLifecycleTransition(ScannerLifecycleTransition.STOP_REQUESTED)
        teardownScannerRuntime(removeForeground = true)
        _scannerState.value = ScannerRuntimeState.Idle
        ScannerRuntimeDiagnosticsStore.recordState(ScannerRuntimeState.Idle)
        stopSelfResult(startId)
    }

    private fun startFocusedScan(macAddress: String?) {
        if (!ScannerServiceLifecyclePolicy.canSwitchScanMode(_scannerState.value)) return
        val mac = macAddress?.takeIf { it.isNotBlank() } ?: return
        recordLifecycleTransition(ScannerLifecycleTransition.FOCUSED_SCAN_REQUESTED)
        bleScanner.startFocusedScan(mac)
    }

    private fun resumePassiveScan() {
        if (!ScannerServiceLifecyclePolicy.canSwitchScanMode(_scannerState.value)) return
        recordLifecycleTransition(ScannerLifecycleTransition.PASSIVE_SCAN_RESUMED)
        bleScanner.startScanning()
    }

    private fun startCleanupJob() {
        cleanupJob?.cancel()
        cleanupJob =
            serviceScope.launch {
                delay(CLEANUP_INITIAL_DELAY_MS)
                while (isActive) {
                    try {
                        val maxAge = DEVICE_MAX_AGE_HOURS * HOUR_TO_MS
                        deviceRepository.deleteOldDevices(maxAge)
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        Log.e(TAG, "Cleanup error", e)
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
        recordLifecycleTransition(ScannerLifecycleTransition.DESTROYED)
        teardownScannerRuntime(removeForeground = true)
        if (_scannerState.value !is ScannerRuntimeState.Error) {
            _scannerState.value = ScannerRuntimeState.Idle
            ScannerRuntimeDiagnosticsStore.recordState(ScannerRuntimeState.Idle)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeScannerState() {
        scannerStateJob?.cancel()
        scannerStateJob =
            serviceScope.launch {
                bleScanner.state.collect { state ->
                    when (state) {
                        ScannerState.Idle -> Unit
                        ScannerState.Starting -> {
                            if (_scannerState.value !is ScannerRuntimeState.Running) {
                                publishStarting()
                                ScannerRuntimeDiagnosticsStore.recordState(ScannerRuntimeState.Starting)
                            }
                        }
                        ScannerState.Scanning,
                        is ScannerState.Focused -> {
                            val wasRunning = _scannerState.value is ScannerRuntimeState.Running
                            _scannerState.value = ScannerRuntimeState.Running
                            ScannerRuntimeDiagnosticsStore.recordState(ScannerRuntimeState.Running)
                            if (!wasRunning) {
                                recordLifecycleTransition(ScannerLifecycleTransition.RUNNING)
                            }
                        }
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
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, throwable)
        }
        recordLifecycleTransition(ScannerLifecycleTransition.START_FAILED)
        cleanupAfterFailure()
        _scannerState.value = ScannerRuntimeState.Error(message)
        ScannerRuntimeDiagnosticsStore.recordScanError(message)
        stopSelf()
    }

    private fun failScanner(
        message: String,
        throwable: Throwable? = null,
    ) {
        if (throwable == null) {
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, throwable)
        }
        recordLifecycleTransition(ScannerLifecycleTransition.SCANNER_FAILED)
        cleanupAfterFailure()
        _scannerState.value = ScannerRuntimeState.Error(message)
        ScannerRuntimeDiagnosticsStore.recordScanError(message)
        stopSelf()
    }

    private fun cleanupAfterFailure() {
        teardownScannerRuntime(removeForeground = true)
    }

    private fun teardownScannerRuntime(removeForeground: Boolean) {
        startupJob?.cancel()
        startupJob = null
        scannerStateJob?.cancel()
        scannerStateJob = null
        cleanupJob?.cancel()
        cleanupJob = null
        unregisterBluetoothStateReceiver()
        startupOwnership.invalidate {
            runCatching { bleScanner.stopScanning() }
                .onFailure { error -> Log.w(TAG, "Scanner teardown failed", error) }
        }
        if (removeForeground) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        }
        releaseWakeLock()
    }

    private fun recordLifecycleTransition(transition: ScannerLifecycleTransition) {
        Log.i(TAG, "lifecycle=$transition")
        ScannerRuntimeDiagnosticsStore.recordLifecycleTransition(transition)
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
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val stopIntent = Intent(this, ScannerService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
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
