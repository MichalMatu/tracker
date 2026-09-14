#!/bin/bash
set -eu

mkdir -p feature/radar/src/main/java/io/blueeye/feature/radar/presentation
mkdir -p feature/radar/src/test/java/io/blueeye/feature/radar/presentation

cat > feature/radar/src/main/java/io/blueeye/feature/radar/presentation/LiveNearbyModels.kt <<'EOF'
package io.blueeye.feature.radar.presentation

enum class LiveNearbyFreshness {
    ACTIVE,
    RECENT,
    STALE,
}

enum class LiveSignalTrend {
    STRONGER,
    STEADY,
    WEAKER,
    UNKNOWN,
}

data class LiveNearbyDevice(
    val item: RadarUiItem,
    val smoothedRssi: Int,
    val trend: LiveSignalTrend,
    val freshness: LiveNearbyFreshness,
    val ageMs: Long,
)

sealed interface LiveNearbyEntry {
    val key: String
    val sortRssi: Int
    val latestSeenAt: Long

    data class Device(val device: LiveNearbyDevice) : LiveNearbyEntry {
        override val key: String = "device:${device.item.fingerprint}"
        override val sortRssi: Int = device.smoothedRssi
        override val latestSeenAt: Long = device.item.lastSeenAt
    }

    data class ProtocolGroup(
        val family: ProtocolNoiseFamily,
        val members: List<LiveNearbyDevice>,
        val activeCount: Int,
        val recentCount: Int,
        val totalIdentities: Int,
        override val sortRssi: Int,
        override val latestSeenAt: Long,
    ) : LiveNearbyEntry {
        override val key: String = "protocol:${family.name}"
    }
}

data class LiveNearbySnapshot(
    val pinned: LiveNearbyDevice?,
    val active: List<LiveNearbyEntry>,
    val recent: List<LiveNearbyEntry>,
    val activeIdentityCount: Int,
    val recentIdentityCount: Int,
)

sealed interface LiveNearbyUiState {
    data object Loading : LiveNearbyUiState

    data class Error(val message: String) : LiveNearbyUiState

    data class Success(val snapshot: LiveNearbySnapshot) : LiveNearbyUiState
}
EOF

cat > feature/radar/src/main/java/io/blueeye/feature/radar/presentation/LiveNearbySignalHistory.kt <<'EOF'
package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.RadarDeviceSummary

internal class LiveNearbySignalHistory {
    private data class Sample(val timestamp: Long, val rssi: Int)

    private val samplesByFingerprint = mutableMapOf<String, MutableList<Sample>>()

    fun observe(devices: List<RadarDeviceSummary>, nowMs: Long) {
        devices.forEach { device ->
            val samples = samplesByFingerprint.getOrPut(device.fingerprint) { mutableListOf() }
            if (samples.lastOrNull()?.timestamp != device.lastSeenAt) {
                samples += Sample(device.lastSeenAt, device.rssi)
            }
        }
        val cutoff = nowMs - HISTORY_WINDOW_MS
        samplesByFingerprint.entries.removeAll { (_, samples) ->
            samples.removeAll { sample -> sample.timestamp < cutoff }
            samples.isEmpty()
        }
    }

    fun smoothedRssi(device: RadarDeviceSummary): Int {
        val values = samplesByFingerprint[device.fingerprint]?.map { it.rssi }.orEmpty()
        return median(values).takeIf { values.isNotEmpty() } ?: device.rssi
    }

    fun trend(fingerprint: String, nowMs: Long): LiveSignalTrend {
        val cutoff = nowMs - TREND_WINDOW_MS
        val values =
            samplesByFingerprint[fingerprint]
                .orEmpty()
                .filter { it.timestamp >= cutoff }
                .map { it.rssi }
        if (values.size < MIN_TREND_SAMPLES) return LiveSignalTrend.UNKNOWN

        val midpoint = values.size / 2
        val first = median(values.take(midpoint))
        val second = median(values.drop(midpoint))
        val delta = second - first
        return when {
            delta >= TREND_THRESHOLD_DB -> LiveSignalTrend.STRONGER
            delta <= -TREND_THRESHOLD_DB -> LiveSignalTrend.WEAKER
            else -> LiveSignalTrend.STEADY
        }
    }

    private fun median(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private companion object {
        const val HISTORY_WINDOW_MS = 30_000L
        const val TREND_WINDOW_MS = 15_000L
        const val MIN_TREND_SAMPLES = 4
        const val TREND_THRESHOLD_DB = 6
    }
}
EOF

cat > feature/radar/src/main/java/io/blueeye/feature/radar/presentation/LiveNearbyAssembler.kt <<'EOF'
package io.blueeye.feature.radar.presentation

object LiveNearbyAssembler {
    fun assemble(
        devices: List<LiveNearbyDevice>,
        pinnedFingerprint: String?,
    ): LiveNearbySnapshot {
        val pinned = devices.firstOrNull { it.item.fingerprint == pinnedFingerprint }
        val nonPinned = devices.filterNot { it.item.fingerprint == pinnedFingerprint }
        val groupableByFamily =
            nonPinned
                .filter { ProtocolNoiseClassifier.canGroup(it.item) }
                .groupBy { ProtocolNoiseClassifier.family(it.item)!! }
                .filterValues { it.size >= MIN_GROUP_SIZE }

        val groupedFingerprints =
            groupableByFamily.values
                .flatten()
                .mapTo(mutableSetOf()) { it.item.fingerprint }

        val standaloneEntries =
            nonPinned
                .filterNot { it.item.fingerprint in groupedFingerprints }
                .filter { it.freshness != LiveNearbyFreshness.STALE }
                .map { LiveNearbyEntry.Device(it) }

        val groupEntries =
            groupableByFamily.mapNotNull { (family, allMembers) ->
                val visibleMembers =
                    allMembers
                        .filter { it.freshness != LiveNearbyFreshness.STALE }
                        .sortedWith(
                            compareBy<LiveNearbyDevice> { it.freshness.sortOrder }
                                .thenByDescending { it.smoothedRssi }
                                .thenByDescending { it.item.lastSeenAt },
                        )
                if (visibleMembers.isEmpty()) return@mapNotNull null

                val active = visibleMembers.filter { it.freshness == LiveNearbyFreshness.ACTIVE }
                val recent = visibleMembers.filter { it.freshness == LiveNearbyFreshness.RECENT }
                LiveNearbyEntry.ProtocolGroup(
                    family = family,
                    members = visibleMembers,
                    activeCount = active.size,
                    recentCount = recent.size,
                    totalIdentities = allMembers.size,
                    sortRssi =
                        active.maxOfOrNull { it.smoothedRssi }
                            ?: recent.maxOf { it.smoothedRssi },
                    latestSeenAt = visibleMembers.maxOf { it.item.lastSeenAt },
                )
            }

        val allEntries = standaloneEntries + groupEntries
        val activeEntries =
            allEntries
                .filter { it.isActiveEntry() }
                .sortedWith(
                    compareByDescending<LiveNearbyEntry> { it.sortRssi }
                        .thenByDescending { it.latestSeenAt }
                        .thenBy { it.key },
                )
        val recentEntries =
            allEntries
                .filterNot { it.isActiveEntry() }
                .sortedWith(
                    compareByDescending<LiveNearbyEntry> { it.latestSeenAt }
                        .thenByDescending { it.sortRssi }
                        .thenBy { it.key },
                )

        return LiveNearbySnapshot(
            pinned = pinned,
            active = activeEntries,
            recent = recentEntries,
            activeIdentityCount = devices.count { it.freshness == LiveNearbyFreshness.ACTIVE },
            recentIdentityCount = devices.count { it.freshness == LiveNearbyFreshness.RECENT },
        )
    }

    private fun LiveNearbyEntry.isActiveEntry(): Boolean =
        when (this) {
            is LiveNearbyEntry.Device -> device.freshness == LiveNearbyFreshness.ACTIVE
            is LiveNearbyEntry.ProtocolGroup -> activeCount > 0
        }

    private val LiveNearbyFreshness.sortOrder: Int
        get() =
            when (this) {
                LiveNearbyFreshness.ACTIVE -> 0
                LiveNearbyFreshness.RECENT -> 1
                LiveNearbyFreshness.STALE -> 2
            }

    private const val MIN_GROUP_SIZE = 2
}

internal class LiveNearbyOrderController {
    private var lastReorderAt: Long? = null
    private var activeKeys: List<String> = emptyList()
    private var recentKeys: List<String> = emptyList()

    fun apply(
        snapshot: LiveNearbySnapshot,
        nowMs: Long,
        frozen: Boolean,
    ): LiveNearbySnapshot {
        val due = lastReorderAt?.let { nowMs - it >= REORDER_INTERVAL_MS } ?: true
        if (!frozen && due) {
            activeKeys = snapshot.active.map { it.key }
            recentKeys = snapshot.recent.map { it.key }
            lastReorderAt = nowMs
        }
        return snapshot.copy(
            active = applyStableOrder(snapshot.active, activeKeys),
            recent = applyStableOrder(snapshot.recent, recentKeys),
        )
    }

    private fun applyStableOrder(
        entries: List<LiveNearbyEntry>,
        preferredKeys: List<String>,
    ): List<LiveNearbyEntry> {
        val byKey = entries.associateBy { it.key }
        val ordered = preferredKeys.mapNotNull(byKey::get).toMutableList()
        val known = ordered.mapTo(mutableSetOf()) { it.key }
        ordered += entries.filterNot { it.key in known }
        return ordered
    }

    private companion object {
        const val REORDER_INTERVAL_MS = 5_000L
    }
}
EOF

cat > feature/radar/src/main/java/io/blueeye/feature/radar/presentation/LiveNearbyViewModel.kt <<'EOF'
package io.blueeye.feature.radar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.blueeye.core.domain.scanner.ScannerRuntimeController
import io.blueeye.core.domain.scanner.ScannerRuntimeState
import io.blueeye.core.domain.usecase.GetRadarDevicesUseCase
import io.blueeye.core.model.RadarDeviceSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LiveNearbyViewModel
    @Inject
    constructor(
        getRadarDevicesUseCase: GetRadarDevicesUseCase,
        private val scannerRuntimeController: ScannerRuntimeController,
    ) : ViewModel() {
        val scannerState: StateFlow<ScannerRuntimeState> = scannerRuntimeController.scannerState

        private val pinnedFingerprint = MutableStateFlow<String?>(null)
        private val orderFrozen = MutableStateFlow(false)
        private val signalHistory = LiveNearbySignalHistory()
        private val orderController = LiveNearbyOrderController()

        private val clock =
            flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    delay(UI_TICK_MS)
                }
            }

        val uiState: StateFlow<LiveNearbyUiState> =
            combine(
                getRadarDevicesUseCase(sinceSecondsAgo = SOURCE_WINDOW_SECONDS),
                clock,
                pinnedFingerprint,
                orderFrozen,
            ) { result, nowMs, pinned, frozen ->
                result.fold(
                    onSuccess = { summaries ->
                        signalHistory.observe(summaries, nowMs)
                        val devices = summaries.map { it.toLiveNearbyDevice(nowMs) }
                        val assembled = LiveNearbyAssembler.assemble(devices, pinned)
                        LiveNearbyUiState.Success(orderController.apply(assembled, nowMs, frozen))
                    },
                    onFailure = { error ->
                        LiveNearbyUiState.Error(error.message ?: "Unable to load nearby signals")
                    },
                )
            }
                .flowOn(Dispatchers.Default)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = LiveNearbyUiState.Loading,
                )

        fun toggleScanning() {
            when (scannerState.value) {
                ScannerRuntimeState.Running,
                ScannerRuntimeState.Starting,
                -> scannerRuntimeController.stopScanning()
                ScannerRuntimeState.Idle,
                is ScannerRuntimeState.Error,
                -> scannerRuntimeController.startScanning()
            }
        }

        fun togglePin(fingerprint: String) {
            pinnedFingerprint.value =
                if (pinnedFingerprint.value == fingerprint) null else fingerprint
        }

        fun setOrderFrozen(frozen: Boolean) {
            orderFrozen.value = frozen
        }

        private fun RadarDeviceSummary.toLiveNearbyDevice(nowMs: Long): LiveNearbyDevice {
            val ageMs = (nowMs - lastSeenAt).coerceAtLeast(0L)
            val freshness =
                when {
                    ageMs <= ACTIVE_WINDOW_MS -> LiveNearbyFreshness.ACTIVE
                    ageMs <= RECENT_WINDOW_MS -> LiveNearbyFreshness.RECENT
                    else -> LiveNearbyFreshness.STALE
                }
            return LiveNearbyDevice(
                item = RadarUiMapper.mapToUi(this, isNew = false, activeProbeMac = null),
                smoothedRssi = signalHistory.smoothedRssi(this),
                trend = signalHistory.trend(fingerprint, nowMs),
                freshness = freshness,
                ageMs = ageMs,
            )
        }

        private companion object {
            const val SOURCE_WINDOW_SECONDS = 180L
            const val UI_TICK_MS = 1_000L
            const val ACTIVE_WINDOW_MS = 15_000L
            const val RECENT_WINDOW_MS = 60_000L
        }
    }
EOF

cat > feature/radar/src/main/java/io/blueeye/feature/radar/presentation/LiveNearbyScreen.kt <<'EOF'
package io.blueeye.feature.radar.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.blueeye.core.domain.scanner.ScannerRuntimeState
import io.blueeye.core.ui.theme.Dimens
import kotlinx.coroutines.flow.distinctUntilChanged

@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveNearbyScreen(
    onDeviceClick: (String) -> Unit,
    onMenuClick: () -> Unit,
    viewModel: LiveNearbyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scannerState by viewModel.scannerState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect(viewModel::setOrderFrozen)
    }

    val scannerActive =
        scannerState == ScannerRuntimeState.Running || scannerState == ScannerRuntimeState.Starting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Nearby") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Open menu")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::toggleScanning) {
                        Text(if (scannerActive) "Pause" else "Scan")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            LiveNearbyUiState.Loading ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            is LiveNearbyUiState.Error ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.PaddingMedium),
                ) {
                    Text("Live Nearby unavailable", style = MaterialTheme.typography.titleMedium)
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            is LiveNearbyUiState.Success -> {
                val snapshot = state.snapshot
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    state = listState,
                    contentPadding = PaddingValues(vertical = Dimens.PaddingSmall),
                ) {
                    item(key = "summary") {
                        LiveNearbySummary(
                            snapshot = snapshot,
                            scannerState = scannerState,
                        )
                    }
                    snapshot.pinned?.let { pinned ->
                        item(key = "pinned:${pinned.item.fingerprint}") {
                            LiveNearbySectionHeader("Pinned")
                            LiveNearbyDeviceCard(
                                device = pinned,
                                pinned = true,
                                onClick = onDeviceClick,
                                onPin = viewModel::togglePin,
                            )
                        }
                    }
                    liveNearbyEntries(
                        title = "Active now · ${snapshot.activeIdentityCount}",
                        entries = snapshot.active,
                        expandedGroups = expandedGroups,
                        onToggleGroup = { key ->
                            expandedGroups =
                                if (key in expandedGroups) expandedGroups - key else expandedGroups + key
                        },
                        onDeviceClick = onDeviceClick,
                        onPin = viewModel::togglePin,
                    )
                    liveNearbyEntries(
                        title = "Recently seen · ${snapshot.recentIdentityCount}",
                        entries = snapshot.recent,
                        expandedGroups = expandedGroups,
                        onToggleGroup = { key ->
                            expandedGroups =
                                if (key in expandedGroups) expandedGroups - key else expandedGroups + key
                        },
                        onDeviceClick = onDeviceClick,
                        onPin = viewModel::togglePin,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.liveNearbyEntries(
    title: String,
    entries: List<LiveNearbyEntry>,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    onDeviceClick: (String) -> Unit,
    onPin: (String) -> Unit,
) {
    item(key = "header:$title") { LiveNearbySectionHeader(title) }
    if (entries.isEmpty()) {
        item(key = "empty:$title") {
            Text(
                "No signals in this section.",
                modifier = Modifier.padding(horizontal = Dimens.PaddingMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    entries.forEach { entry ->
        when (entry) {
            is LiveNearbyEntry.Device ->
                item(key = "live:${entry.key}") {
                    LiveNearbyDeviceCard(
                        device = entry.device,
                        pinned = false,
                        onClick = onDeviceClick,
                        onPin = onPin,
                    )
                }
            is LiveNearbyEntry.ProtocolGroup -> {
                item(key = "live:${entry.key}") {
                    LiveNearbyProtocolGroupCard(
                        group = entry,
                        expanded = entry.key in expandedGroups,
                        onToggle = { onToggleGroup(entry.key) },
                    )
                }
                if (entry.key in expandedGroups) {
                    entry.members.forEach { member ->
                        item(key = "live:${entry.key}:${member.item.fingerprint}") {
                            LiveNearbyDeviceCard(
                                device = member,
                                pinned = false,
                                onClick = onDeviceClick,
                                onPin = onPin,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveNearbySummary(
    snapshot: LiveNearbySnapshot,
    scannerState: ScannerRuntimeState,
) {
    val scannerText =
        when (scannerState) {
            ScannerRuntimeState.Running -> "Scanning"
            ScannerRuntimeState.Starting -> "Starting scanner"
            ScannerRuntimeState.Idle -> "Scanner paused"
            is ScannerRuntimeState.Error -> "Scanner error"
        }
    Column(
        modifier = Modifier.fillMaxWidth().padding(Dimens.PaddingMedium),
        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingExtraSmall),
    ) {
        Text(
            "$scannerText · ${snapshot.activeIdentityCount} active now · " +
                "${snapshot.recentIdentityCount} recent",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "Active ≤15s · recent ≤60s · order refreshes every 5s. " +
                "Signal strength is not exact distance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveNearbySectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(
            horizontal = Dimens.PaddingMedium,
            vertical = Dimens.PaddingSmall,
        ),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun LiveNearbyDeviceCard(
    device: LiveNearbyDevice,
    pinned: Boolean,
    onClick: (String) -> Unit,
    onPin: (String) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PaddingMedium, vertical = Dimens.PaddingExtraSmall)
                .clickable { onClick(device.item.fingerprint) },
        shape = MaterialTheme.shapes.medium,
        tonalElevation = Dimens.CardElevation,
    ) {
        Row(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    device.item.vendorAndType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${device.smoothedRssi} dBm · ${device.trend.label} · ${device.ageMs.ageLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (ProtocolNoiseClassifier.mustStayStandalone(device.item)) {
                    Text(
                        "Attention evidence",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(
                onClick = {
                    onPin(device.item.fingerprint)
                },
            ) {
                Text(if (pinned) "Unpin" else "Pin")
            }
        }
    }
}

@Composable
private fun LiveNearbyProtocolGroupCard(
    group: LiveNearbyEntry.ProtocolGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PaddingMedium, vertical = Dimens.PaddingExtraSmall)
                .clickable(onClick = onToggle),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = Dimens.CardElevation,
    ) {
        Row(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(group.family.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${group.activeCount} active now · ${group.recentCount} recent · " +
                        "${group.totalIdentities} identities seen in last 3 min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Strongest ${group.sortRssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(if (expanded) "Hide" else "Show", color = MaterialTheme.colorScheme.primary)
        }
    }
}

private val LiveSignalTrend.label: String
    get() =
        when (this) {
            LiveSignalTrend.STRONGER -> "↑ stronger"
            LiveSignalTrend.STEADY -> "steady"
            LiveSignalTrend.WEAKER -> "↓ weaker"
            LiveSignalTrend.UNKNOWN -> "trend pending"
        }

private val Long.ageLabel: String
    get() =
        when {
            this < 1_000L -> "now"
            this < 60_000L -> "${this / 1_000L}s ago"
            else -> "${this / 60_000L}m ago"
        }
EOF

cat > feature/radar/src/test/java/io/blueeye/feature/radar/presentation/LiveNearbyAssemblerTest.kt <<'EOF'
package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.RadarEvidenceSignals
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveNearbyAssemblerTest {
    @Test
    fun `active devices sort by signal and recent stays below`() {
        val devices =
            listOf(
                device("weak", -80, LiveNearbyFreshness.ACTIVE, 10_000L),
                device("recent", -30, LiveNearbyFreshness.RECENT, 9_000L),
                device("strong", -45, LiveNearbyFreshness.ACTIVE, 8_000L),
            )

        val snapshot = LiveNearbyAssembler.assemble(devices, pinnedFingerprint = null)

        assertEquals(listOf("strong", "weak"), snapshot.active.deviceFingerprints())
        assertEquals(listOf("recent"), snapshot.recent.deviceFingerprints())
    }

    @Test
    fun `find hub identities group while suspicious identity escapes`() {
        val ordinaryA = device("a", -55, LiveNearbyFreshness.ACTIVE, 10_000L, "Google Find Hub")
        val ordinaryB = device("b", -65, LiveNearbyFreshness.ACTIVE, 9_000L, "Google Find Hub")
        val suspicious =
            device(
                fingerprint = "suspicious",
                rssi = -40,
                freshness = LiveNearbyFreshness.ACTIVE,
                lastSeenAt = 11_000L,
                beaconType = "Google Find Hub",
                status = TrackingStatus.SUSPICIOUS,
            )

        val snapshot = LiveNearbyAssembler.assemble(
            listOf(ordinaryA, ordinaryB, suspicious),
            pinnedFingerprint = null,
        )

        val group = snapshot.active.filterIsInstance<LiveNearbyEntry.ProtocolGroup>().single()
        assertEquals(2, group.activeCount)
        assertEquals(2, group.totalIdentities)
        assertTrue(
            snapshot.active.any {
                it is LiveNearbyEntry.Device && it.device.item.fingerprint == "suspicious"
            },
        )
    }

    @Test
    fun `pinned stale device remains visible and outside protocol group`() {
        val pinned = device("pinned", -70, LiveNearbyFreshness.STALE, 1_000L, "Apple Find My")
        val otherA = device("a", -60, LiveNearbyFreshness.ACTIVE, 10_000L, "Apple Find My")
        val otherB = device("b", -65, LiveNearbyFreshness.ACTIVE, 9_000L, "Apple Find My")

        val snapshot = LiveNearbyAssembler.assemble(
            listOf(pinned, otherA, otherB),
            pinnedFingerprint = "pinned",
        )

        assertEquals("pinned", snapshot.pinned?.item?.fingerprint)
        val group = snapshot.active.filterIsInstance<LiveNearbyEntry.ProtocolGroup>().single()
        assertEquals(2, group.totalIdentities)
    }

    @Test
    fun `order controller refreshes every five seconds and freezes while scrolling`() {
        val controller = LiveNearbyOrderController()
        val first = snapshot(device("a", -40), device("b", -70))
        val inverted = snapshot(device("b", -30), device("a", -80))

        assertEquals(listOf("a", "b"), controller.apply(first, 0L, frozen = false).active.deviceFingerprints())
        assertEquals(
            listOf("a", "b"),
            controller.apply(inverted, 4_000L, frozen = false).active.deviceFingerprints(),
        )
        assertEquals(
            listOf("b", "a"),
            controller.apply(inverted, 5_000L, frozen = false).active.deviceFingerprints(),
        )
        assertEquals(
            listOf("b", "a"),
            controller.apply(first, 11_000L, frozen = true).active.deviceFingerprints(),
        )
        assertEquals(
            listOf("a", "b"),
            controller.apply(first, 11_001L, frozen = false).active.deviceFingerprints(),
        )
    }

    private fun snapshot(vararg devices: LiveNearbyDevice): LiveNearbySnapshot =
        LiveNearbySnapshot(
            pinned = null,
            active = devices.map { LiveNearbyEntry.Device(it) },
            recent = emptyList(),
            activeIdentityCount = devices.size,
            recentIdentityCount = 0,
        )

    private fun List<LiveNearbyEntry>.deviceFingerprints(): List<String> =
        mapNotNull { (it as? LiveNearbyEntry.Device)?.device?.item?.fingerprint }

    private fun device(
        fingerprint: String,
        rssi: Int,
        freshness: LiveNearbyFreshness = LiveNearbyFreshness.ACTIVE,
        lastSeenAt: Long = 10_000L,
        beaconType: String? = null,
        status: TrackingStatus = TrackingStatus.SAFE,
    ): LiveNearbyDevice =
        LiveNearbyDevice(
            item =
                RadarUiItem(
                    fingerprint = fingerprint,
                    displayName = fingerprint,
                    vendorAndType = "BLE device",
                    signalInfo =
                        RadarUiSignalInfo(
                            rssi = rssi,
                            rssiText = "$rssi dBm",
                            rssiColor = RadarUiColorToken.PRIMARY,
                            signalIcon = 0,
                            signalDescription = "",
                            technologyText = "BLE",
                            technologyColor = RadarUiColorToken.PRIMARY,
                            technologyDescription = "",
                        ),
                    statusInfo =
                        RadarUiStatusInfo(
                            statusText = "",
                            statusColor = RadarUiColorToken.PRIMARY,
                            statusBackgroundColor = RadarUiColorToken.PRIMARY,
                            showWarningIcon = false,
                            warningIcon = null,
                        ),
                    icons = RadarUiIcons(deviceIcon = 0, showWatchlistIcon = false),
                    isNew = false,
                    isInWatchlist = false,
                    isIgnored = false,
                    nameColor = RadarUiColorToken.PRIMARY,
                    firstSeenAt = lastSeenAt,
                    lastSeenAt = lastSeenAt,
                    trackingStatus = status,
                    followingScore = 0f,
                    isSafeBeacon = false,
                    calibrationLabel = DeviceCalibrationLabel.UNKNOWN,
                    hasIdentitySignal = true,
                    evidenceSignals = RadarEvidenceSignals(false, true, false, false, false),
                    beaconType = beaconType,
                ),
            smoothedRssi = rssi,
            trend = LiveSignalTrend.STEADY,
            freshness = freshness,
            ageMs = 0L,
        )
}
EOF

python3 - <<'PY'
from pathlib import Path

p = Path('app/src/main/java/io/blueeye/navigation/Screen.kt')
s = p.read_text()
needle = '''    @Serializable
    data object Radar : Screen
'''
replacement = '''    @Serializable
    data object Radar : Screen

    @Serializable
    data object LiveNearby : Screen
'''
if needle not in s:
    raise SystemExit('Screen.Radar block not found')
p.write_text(s.replace(needle, replacement, 1))

p = Path('app/src/main/java/io/blueeye/navigation/AppNavigation.kt')
s = p.read_text()
s = s.replace(
    'import io.blueeye.feature.radar.presentation.RadarScreen\n',
    'import io.blueeye.feature.radar.presentation.LiveNearbyScreen\nimport io.blueeye.feature.radar.presentation.RadarScreen\n',
    1,
)
s = s.replace(
    '            "io.blueeye.navigation.Screen.Radar" -> Screen.Radar\n',
    '            "io.blueeye.navigation.Screen.Radar" -> Screen.Radar\n'
    '            "io.blueeye.navigation.Screen.LiveNearby" -> Screen.LiveNearby\n',
    1,
)
needle = '''            composable<Screen.Details> { backStackEntry ->
'''
block = '''            composable<Screen.LiveNearby> {
                LiveNearbyScreen(
                    onDeviceClick = { deviceId ->
                        navController.navigate(Screen.Details(deviceId = deviceId))
                    },
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                )
            }

            composable<Screen.Details> { backStackEntry ->
'''
if needle not in s:
    raise SystemExit('Details route not found')
p.write_text(s.replace(needle, block, 1))

p = Path('app/src/main/java/io/blueeye/ui/AppDrawer.kt')
s = p.read_text()
needle = '''        NavigationDrawerItem(
            label = { Text("Watchlist") },
'''
block = '''        NavigationDrawerItem(
            label = { Text("Live Nearby") },
            icon = { Icon(Icons.Default.List, contentDescription = null) },
            selected = currentScreen is Screen.LiveNearby,
            onClick = {
                scope.launch { drawerState.close() }
                navigateTo(Screen.LiveNearby)
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )

        NavigationDrawerItem(
            label = { Text("Watchlist") },
'''
if needle not in s:
    raise SystemExit('Watchlist drawer item not found')
p.write_text(s.replace(needle, block, 1))
PY

git diff --check
