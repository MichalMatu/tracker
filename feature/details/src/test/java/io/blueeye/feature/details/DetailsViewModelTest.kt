package io.blueeye.feature.details

import androidx.lifecycle.SavedStateHandle
import io.blueeye.core.domain.details.DeviceConnectionController
import io.blueeye.core.domain.details.DeviceFocusedScanController
import io.blueeye.core.domain.details.DeviceSensorDataDecoder
import io.blueeye.core.domain.details.DeviceServiceResolver
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.domain.repository.WatchlistRepository
import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceConnectionState
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.GattService
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.SensorData
import io.blueeye.core.model.SignalSample
import io.blueeye.core.model.TrackingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val deviceRepository: DeviceRepository = mock()
    private val watchlistRepository: WatchlistRepository = mock()
    private val deviceConnectionController: DeviceConnectionController = mock()
    private val focusedScanController: DeviceFocusedScanController = mock()
    private val sensorDataDecoder: DeviceSensorDataDecoder = mock()
    private val deviceServiceResolver: DeviceServiceResolver = mock()

    @Test
    fun `toggleWatchlist adds through watchlist repository`() =
        runTest {
            val device = device(isInWatchlist = false)
            val viewModel = viewModel(device)
            advanceUntilIdle()
            assertEquals(device, viewModel.device.value)

            viewModel.toggleWatchlist()
            advanceUntilIdle()

            verify(watchlistRepository).addToWatchlist(FINGERPRINT)
        }

    @Test
    fun `toggleWatchlist removes through watchlist repository`() =
        runTest {
            val device = device(isInWatchlist = true)
            val viewModel = viewModel(device)
            advanceUntilIdle()
            assertEquals(device, viewModel.device.value)

            viewModel.toggleWatchlist()
            advanceUntilIdle()

            verify(watchlistRepository).removeFromWatchlist(FINGERPRINT)
        }

    @Test
    fun `exportJson includes evidence provenance`() =
        runTest {
            val device =
                device(
                    isInWatchlist = false,
                    evidence =
                        listOf(
                            DetectionEvidence(
                                source = EvidenceSource.SERVICE_UUID,
                                confidence = DetectionConfidence.HIGH,
                                reasonText = "Service UUID is consistent with Axon Body Camera.",
                                timestamp = NOW,
                                rawValue = "0000fd8e-0000-1000-8000-00805f9b34fb",
                                parsedValue = "BODY_CAMERA",
                                isPassive = true,
                                provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
                            ),
                        ),
                )
            val viewModel = viewModel(device)
            advanceUntilIdle()

            var exportedJson: String? = null
            viewModel.exportJson { json -> exportedJson = json }
            advanceUntilIdle()

            val evidenceJson =
                Json.parseToJsonElement(requireNotNull(exportedJson))
                    .jsonObject
                    .getValue("evidence")
                    .jsonArray
                    .single()
                    .jsonObject

            assertEquals(
                EvidenceProvenance.BLE_ADVERTISEMENT.name,
                evidenceJson.getValue("provenance").jsonPrimitive.content,
            )
        }

    private suspend fun viewModel(device: Device): DetailsViewModel {
        whenever(deviceRepository.getDeviceByFingerprint(FINGERPRINT)).thenReturn(Result.success(device))
        whenever(deviceRepository.getDeviceFlow(FINGERPRINT)).thenReturn(flowOf(Result.success(device)))
        whenever(deviceRepository.getSignalSamples(FINGERPRINT)).thenReturn(
            flowOf(Result.success(emptyList<SignalSample>())),
        )
        whenever(deviceRepository.getFollowMeHistory(FINGERPRINT)).thenReturn(
            flowOf(Result.success(emptyList<FollowMeHistorySample>())),
        )
        whenever(deviceRepository.getAlertEvidenceEvents(FINGERPRINT)).thenReturn(
            flowOf(Result.success(emptyList<AlertEvidenceEvent>())),
        )
        whenever(deviceConnectionController.connectionState).thenReturn(
            MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.Disconnected),
        )
        whenever(deviceConnectionController.services).thenReturn(
            MutableStateFlow<List<GattService>>(emptyList()),
        )
        whenever(sensorDataDecoder.decode(device)).thenReturn(Result.success(null as SensorData?))
        whenever(deviceServiceResolver.resolvePersistedServices(device)).thenReturn(Result.success(emptyList()))
        whenever(watchlistRepository.addToWatchlist(FINGERPRINT)).thenReturn(Result.success(Unit))
        whenever(watchlistRepository.removeFromWatchlist(FINGERPRINT)).thenReturn(Result.success(Unit))

        return DetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("deviceId" to FINGERPRINT)),
            repository = deviceRepository,
            watchlistRepository = watchlistRepository,
            deviceConnectionController = deviceConnectionController,
            focusedScanController = focusedScanController,
            sensorDataDecoder = sensorDataDecoder,
            deviceServiceResolver = deviceServiceResolver,
        )
    }

    private fun device(
        isInWatchlist: Boolean,
        evidence: List<DetectionEvidence> = emptyList(),
    ): Device =
        Device(
            fingerprint = FINGERPRINT,
            macAddress = FINGERPRINT,
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = "Desk headphones",
            deviceType = DeviceType.HEADPHONES,
            vendorName = "Example",
            predictedModel = null,
            trackingStatus = TrackingStatus.SAFE,
            followingScore = 0f,
            isSafeBeacon = false,
            isInWatchlist = isInWatchlist,
            userAlias = "Desk headphones",
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            firstSeenAt = NOW,
            lastSeenAt = NOW,
            rssi = -52,
            encounterCount = 1,
            evidence = evidence,
        )

    private companion object {
        private const val FINGERPRINT = "AA:BB:CC:11:22:33"
        private const val NOW = 1_789_000_000_000L
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                Dispatchers.setMain(dispatcher)
                try {
                    base.evaluate()
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
}
