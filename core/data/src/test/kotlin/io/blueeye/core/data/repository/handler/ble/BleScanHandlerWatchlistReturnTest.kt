package io.blueeye.core.data.repository.handler.ble

import io.blueeye.core.alert.TacticalAlertService
import io.blueeye.core.alert.TrackerAlertService
import io.blueeye.core.connectivity.manager.AutoActiveProbeCoordinator
import io.blueeye.core.connectivity.manager.AutoActiveProbeScanCandidate
import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.diagnostics.ScoreDiagnosticLogger
import io.blueeye.core.data.evidence.AlertEvidenceEventRecorder
import io.blueeye.core.data.tracker.FollowMeScoreCalculator
import io.blueeye.core.data.tracker.alert.AlertDecisionEngine
import io.blueeye.core.data.tracker.session.FollowMeSessionManager
import io.blueeye.core.data.watchlist.WatchlistReturnAlertPolicy
import io.blueeye.core.location.LocationProvider
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.scanner.model.BleScanResultData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BleScanHandlerWatchlistReturnTest {
    private val deviceDao: DeviceDao = mock()
    private val macAddressResolver: MacAddressResolver = mock()
    private val deviceEnricher: DeviceEnricher = mock()
    private val classifier: ScanResultClassifier = mock()
    private val persister: DevicePersister = mock()
    private val tacticalAlertService: TacticalAlertService = mock()
    private val followMeScoreCalculator: FollowMeScoreCalculator = mock()
    private val trackerAlertService: TrackerAlertService = mock()
    private val diagnosticLogger: ScoreDiagnosticLogger = mock()
    private val locationProvider: LocationProvider = mock()
    private val sessionManager: FollowMeSessionManager = mock()
    private val alertDecisionEngine: AlertDecisionEngine = mock()
    private val autoActiveProbeCoordinator: AutoActiveProbeCoordinator = mock()
    private val alertEvidenceEventRecorder: AlertEvidenceEventRecorder = mock()

    private val handler =
        BleScanHandler(
            deviceDao = deviceDao,
            macAddressResolver = macAddressResolver,
            deviceEnricher = deviceEnricher,
            classifier = classifier,
            persister = persister,
            tacticalAlertService = tacticalAlertService,
            followMeScoreCalculator = followMeScoreCalculator,
            trackerAlertService = trackerAlertService,
            diagnosticLogger = diagnosticLogger,
            locationProvider = locationProvider,
            sessionManager = sessionManager,
            alertDecisionEngine = alertDecisionEngine,
            autoActiveProbeCoordinator = autoActiveProbeCoordinator,
            alertEvidenceEventRecorder = alertEvidenceEventRecorder,
        )

    @Test
    fun `handle emits watchlist return alert with evidence after offline gap`() =
        runTest {
            val existing =
                watchlistedDevice(
                    lastSeenAt = NOW - WatchlistReturnAlertPolicy.OFFLINE_THRESHOLD_MS - 1L,
                )
            stubPipeline(existing)

            handler.handle(scan(timestamp = NOW))

            val evidence = captureWatchlistEvidence()
            assertEquals(EvidenceSource.WATCHLIST, evidence.source)
            assertEquals(DetectionConfidence.CRITICAL, evidence.confidence)
            assertTrue(evidence.reasonText.contains("returned after 60s offline"))
            assertEquals(MAC, evidence.rawValue)
            assertEquals(ALIAS, evidence.parsedValue)
            assertTrue(evidence.isPassive)
            assertEquals(EvidenceProvenance.BLE_ADVERTISEMENT, evidence.provenance)
            verify(deviceDao).recordWatchlistReturnAlert(
                fingerprint = MAC,
                timestamp = NOW,
                offlineDurationMs = WatchlistReturnAlertPolicy.OFFLINE_THRESHOLD_MS + 1L,
            )
            verify(alertEvidenceEventRecorder).recordWatchlistReturn(
                deviceFingerprint = MAC,
                observedMac = MAC,
                evidence = evidence,
            )
        }

    @Test
    fun `handle suppresses watchlist return alert during continuous presence`() =
        runTest {
            val existing =
                watchlistedDevice(
                    lastSeenAt = NOW - PRESENT_GAP_MS,
                )
            stubPipeline(existing)

            handler.handle(scan(timestamp = NOW))

            verify(tacticalAlertService, never()).onWatchlistDeviceReturned(
                mac = any(),
                rssi = any(),
                evidence = any(),
            )
        }

    @Test
    fun `handle applies cooldown after a watchlist return alert`() =
        runTest {
            val existing =
                watchlistedDevice(
                    lastSeenAt = NOW - WatchlistReturnAlertPolicy.OFFLINE_THRESHOLD_MS - 1L,
                )
            stubPipeline(existing)

            handler.handle(scan(timestamp = NOW))
            handler.handle(scan(timestamp = NOW + COOLDOWN_BLOCKED_GAP_MS))

            verify(tacticalAlertService, times(1)).onWatchlistDeviceReturned(
                mac = eq(MAC),
                rssi = eq(RSSI),
                evidence = any(),
            )
        }

    @Test
    fun `handle applies persisted cooldown after process restart`() =
        runTest {
            val existing =
                watchlistedDevice(
                    lastSeenAt = NOW - WatchlistReturnAlertPolicy.OFFLINE_THRESHOLD_MS - 1L,
                    lastWatchlistReturnAlertAt = NOW - COOLDOWN_BLOCKED_GAP_MS,
                )
            stubPipeline(existing)

            handler.handle(scan(timestamp = NOW))

            verify(tacticalAlertService, never()).onWatchlistDeviceReturned(
                mac = any(),
                rssi = any(),
                evidence = any(),
            )
            verify(deviceDao, never()).recordWatchlistReturnAlert(
                fingerprint = any(),
                timestamp = any(),
                offlineDurationMs = any(),
            )
        }

    @Test
    fun `handle queues auto active probe candidate after persistence`() =
        runTest {
            val existing = watchlistedDevice(lastSeenAt = NOW)
            stubPipeline(existing)

            handler.handle(scan(timestamp = NOW, isConnectable = true))

            verify(autoActiveProbeCoordinator).enqueueCandidate(
                AutoActiveProbeScanCandidate(
                    fingerprint = MAC,
                    mac = MAC,
                    isConnectable = true,
                    connectionStatus = existing.connectionStatus,
                    lastProbeTimestamp = existing.lastProbeTimestamp,
                    now = NOW,
                )
            )
        }

    @Test
    fun `handle queues auto active probe candidate for new connectable device`() =
        runTest {
            stubPipeline(existing = null)

            handler.handle(scan(timestamp = NOW, isConnectable = true))

            verify(autoActiveProbeCoordinator).enqueueCandidate(
                AutoActiveProbeScanCandidate(
                    fingerprint = MAC,
                    mac = MAC,
                    isConnectable = true,
                    connectionStatus = null,
                    lastProbeTimestamp = 0L,
                    now = NOW,
                )
            )
        }

    @Test
    fun `handle keeps retention cleanup outside scan processing`() =
        runTest {
            stubPipeline(existing = null)

            handler.handle(scan(timestamp = NOW))

            verify(deviceDao, never()).deleteOldDevices(any())
        }

    @Test
    fun `handle records public safety evidence event after persistence`() =
        runTest {
            val evidence =
                DetectionEvidence(
                    source = EvidenceSource.OUI,
                    confidence = DetectionConfidence.HIGH,
                    reasonText =
                        "MAC OUI is consistent with Axon Enterprise, Inc.: " +
                            "Body camera and connected safety sensor equipment.",
                    timestamp = NOW,
                    rawValue = "0025DF",
                    parsedValue = "BODY_CAMERA",
                    isPassive = true,
                )
            stubPipeline(existing = watchlistedDevice(lastSeenAt = NOW))
            whenever(deviceEnricher.enrich(any())).thenAnswer { invocation ->
                invocation.getArgument<ScanDataContext>(0).apply {
                    isTactical = true
                    tacticalEvidence = listOf(evidence)
                }
            }

            handler.handle(scan(timestamp = NOW))

            verify(persister).persist(any(), eq(classifier))
            verify(alertEvidenceEventRecorder).recordPublicSafetySignal(
                deviceFingerprint = MAC,
                observedMac = MAC,
                evidence = evidence,
            )
        }

    @Test
    fun `handle records follow me alert evidence after persistence`() =
        runTest {
            stubPipeline(existing = watchlistedDevice(lastSeenAt = NOW))
            whenever(sessionManager.recordDeviceSighting(MAC)).thenReturn(NOW - 900_000L)
            whenever(sessionManager.hasUserMoved()).thenReturn(true)
            whenever(sessionManager.isDeviceZastane(MAC)).thenReturn(false)
            whenever(
                followMeScoreCalculator.calculateScore(
                    metrics = any(),
                    currentTimeMs = any(),
                ),
            ).thenReturn(
                FollowMeScoreCalculator.ScoreResult(
                    totalScore = 64,
                    status = TrackingStatus.SUSPICIOUS,
                    durationScore = 20,
                    rssiStabilityScore = 10,
                    deviceTypeScore = 0,
                    macBehaviorScore = 15,
                    encounterScore = 19,
                    explanation = "Seen while moving with repeated encounters",
                ),
            )
            whenever(
                alertDecisionEngine.shouldAlert(
                    isIgnored = false,
                    userHasMoved = true,
                    isZastane = false,
                    trackingStatus = TrackingStatus.SUSPICIOUS,
                ),
            ).thenReturn(true)
            whenever(
                alertDecisionEngine.getDecisionExplanation(
                    isIgnored = false,
                    isKnownTracker = false,
                    userHasMoved = true,
                    isZastane = false,
                    trackingStatus = TrackingStatus.SUSPICIOUS,
                ),
            ).thenReturn("Possible follow-me pattern needs evidence review")

            handler.handle(scan(timestamp = NOW))

            verify(persister).persist(any(), eq(classifier))
            val evidenceCaptor = argumentCaptor<DetectionEvidence>()
            verify(alertEvidenceEventRecorder).recordFollowMeAlert(
                deviceFingerprint = eq(MAC),
                observedMac = eq(MAC),
                evidence = evidenceCaptor.capture(),
            )
            val evidence = evidenceCaptor.firstValue
            assertEquals(EvidenceSource.FOLLOW_ME_SCORE, evidence.source)
            assertEquals(DetectionConfidence.MEDIUM, evidence.confidence)
            assertEquals(EvidenceProvenance.FOLLOW_ME_ANALYSIS, evidence.provenance)
            assertEquals(TrackingStatus.SUSPICIOUS.name, evidence.parsedValue)
            assertTrue(evidence.reasonText.contains("Follow-Me alert decision"))
            assertTrue(evidence.reasonText.contains("Score 64/100"))
            assertTrue(evidence.reasonText.contains("status possible follow-me pattern"))
            assertFalse(evidence.reasonText.contains("status SUSPICIOUS"))
            assertTrue(evidence.rawValue.orEmpty().contains("score=64"))
            assertTrue(evidence.rawValue.orEmpty().contains("mac=15"))
            verify(trackerAlertService).onDeviceAnalyzed(
                mac = MAC,
                score = 64,
                status = TrackingStatus.SUSPICIOUS,
                evidenceReason = "Seen while moving with repeated encounters",
                isKnownTracker = false,
            )
        }

    @Test
    fun `handle passes accumulated mac rotation evidence for known alias to follow me scoring`() =
        runTest {
            stubPipeline(existing = watchlistedDevice(lastSeenAt = NOW))
            whenever(macAddressResolver.resolve(any())).thenAnswer { invocation ->
                invocation.getArgument<ScanDataContext>(0).apply {
                    isCarryover = false
                    macChangeCount = 4
                }
            }
            whenever(sessionManager.recordDeviceSighting(MAC)).thenReturn(NOW - 900_000L)
            whenever(sessionManager.hasUserMoved()).thenReturn(true)
            whenever(sessionManager.isDeviceZastane(MAC)).thenReturn(false)

            handler.handle(
                scan(
                    timestamp = NOW,
                    rawData = byteArrayOf(0x02, 0x01, 0x06),
                ),
            )

            val metricsCaptor = argumentCaptor<FollowMeScoreCalculator.DeviceMetrics>()
            verify(followMeScoreCalculator).calculateScore(
                metrics = metricsCaptor.capture(),
                currentTimeMs = any(),
            )
            assertEquals(4, metricsCaptor.firstValue.macChangeCount)
            assertTrue(metricsCaptor.firstValue.hasStablePayload)
        }

    @Test
    fun `handle suppresses follow me scoring when tracking is disabled`() =
        runTest {
            stubPipeline(
                existing =
                    watchlistedDevice(lastSeenAt = NOW).copy(
                        isTrackingEnabled = false,
                        isIgnoredForTracking = false,
                    ),
            )

            handler.handle(scan(timestamp = NOW))

            verifyFollowMeSuppressed()
        }

    @Test
    fun `handle suppresses follow me scoring for known safe calibration`() =
        runTest {
            stubPipeline(
                existing =
                    watchlistedDevice(lastSeenAt = NOW).copy(
                        isSafeBeacon = false,
                        isIgnoredForTracking = false,
                        calibrationLabel = DeviceCalibrationLabel.KNOWN_SAFE,
                    ),
            )

            handler.handle(scan(timestamp = NOW))

            verifyFollowMeSuppressed()
        }

    @Test
    fun `handle suppresses follow me scoring for false positive calibration`() =
        runTest {
            stubPipeline(
                existing =
                    watchlistedDevice(lastSeenAt = NOW).copy(
                        isSafeBeacon = false,
                        isIgnoredForTracking = false,
                        calibrationLabel = DeviceCalibrationLabel.FALSE_POSITIVE,
                    ),
            )

            handler.handle(scan(timestamp = NOW))

            verifyFollowMeSuppressed()
        }

    private suspend fun stubPipeline(existing: DeviceEntity?) {
        whenever(deviceDao.getByFingerprint(MAC)).thenReturn(existing)
        whenever(deviceDao.deleteOldDevices(any())).thenReturn(0)
        whenever(classifier.resolveType(any())).thenReturn(DeviceType.HEADPHONES)
        whenever(
            followMeScoreCalculator.calculateScore(
                metrics = any(),
                currentTimeMs = any(),
            ),
        ).thenReturn(
            FollowMeScoreCalculator.ScoreResult(
                totalScore = 0,
                status = TrackingStatus.SAFE,
                durationScore = 0,
                rssiStabilityScore = 0,
                deviceTypeScore = 0,
                macBehaviorScore = 0,
                encounterScore = 0,
                explanation = "Low risk - casual encounter",
            ),
        )
    }

    private fun captureWatchlistEvidence(): DetectionEvidence {
        val evidenceCaptor = argumentCaptor<DetectionEvidence>()
        verify(tacticalAlertService).onWatchlistDeviceReturned(
            mac = eq(MAC),
            rssi = eq(RSSI),
            evidence = evidenceCaptor.capture(),
        )
        return evidenceCaptor.firstValue
    }

    private suspend fun verifyFollowMeSuppressed() {
        verify(followMeScoreCalculator, never()).calculateScore(
            metrics = any(),
            currentTimeMs = any(),
        )
        verify(alertDecisionEngine, never()).shouldAlert(
            isIgnored = any(),
            userHasMoved = any(),
            isZastane = any(),
            trackingStatus = any(),
        )

        val contextCaptor = argumentCaptor<ScanDataContext>()
        verify(persister).persist(contextCaptor.capture(), eq(classifier))
        assertEquals(0f, contextCaptor.firstValue.followingScore)
        assertEquals(TrackingStatus.SAFE, contextCaptor.firstValue.trackingStatus)
        assertEquals(
            "User calibration marks this device safe or disables Follow-Me scoring.",
            contextCaptor.firstValue.followMeExplanation,
        )
    }

    private fun scan(
        timestamp: Long,
        isConnectable: Boolean = false,
        rawData: ByteArray? = null,
    ): BleScanResultData =
        BleScanResultData(
            mac = MAC,
            rssi = RSSI,
            timestamp = timestamp,
            technology = "BLE",
            name = "Sony WH-1000XM5",
            isConnectable = isConnectable,
            rawData = rawData,
        )

    private fun watchlistedDevice(
        lastSeenAt: Long,
        lastWatchlistReturnAlertAt: Long = 0L,
    ): DeviceEntity =
        DeviceEntity(
            fingerprint = MAC,
            lastMacAddress = MAC,
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            lastDeviceName = "Sony WH-1000XM5",
            deviceType = DeviceType.HEADPHONES,
            isInWatchlist = true,
            userAlias = ALIAS,
            isTrackingEnabled = true,
            firstSeenAt = NOW - 600_000L,
            lastSeenAt = lastSeenAt,
            lastRssi = RSSI,
            encounterCount = 3,
            lastWatchlistReturnAlertAt = lastWatchlistReturnAlertAt,
        )

    private companion object {
        private const val MAC = "AA:BB:CC:11:22:33"
        private const val ALIAS = "Desk headphones"
        private const val RSSI = -52
        private const val NOW = 1_789_000_000_000L
        private const val PRESENT_GAP_MS = 30_000L
        private const val COOLDOWN_BLOCKED_GAP_MS = 1_000L
    }
}
