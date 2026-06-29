package io.blueeye.core.data.repository.handler.ble

import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.db.dao.FollowMeObservationDao
import io.blueeye.core.data.db.dao.SignalSampleDao
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.repository.handler.common.DeviceTypePriorityHelper
import io.blueeye.core.location.LocationProvider
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.scanner.throttle.ScanThrottler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DevicePersisterDebounceTest {

    private val deviceDao: DeviceDao = mock()
    private val signalSampleDao: SignalSampleDao = mock()
    private val followMeObservationDao: FollowMeObservationDao = mock()
    private val followMeObservationRecorder = FollowMeObservationRecorder(followMeObservationDao)
    private val scanThrottler: ScanThrottler = mock()
    private val locationProvider: LocationProvider = mock()
    private val priorityHelper: DeviceTypePriorityHelper = mock()

    private val persister = DevicePersister(
        deviceDao,
        signalSampleDao,
        followMeObservationRecorder,
        scanThrottler,
        locationProvider,
        priorityHelper
    )

    @Test
    fun `persist SHOULD update status if Status changed`() = runTest {
        // Arrange
        val fingerprint = "FINGERPRINT_1"
        val existing = createDeviceEntity(fingerprint, TrackingStatus.SAFE, 10.0f)
        val ctx = createScanContext(fingerprint, existing)
        
        // Scan has NEW status
        ctx.trackingStatus = TrackingStatus.SUSPICIOUS
        ctx.followingScore = 15.0f // Small change, but status changed
        ctx.followMeExplanation = "Seen for 6min while moving"

        whenever(priorityHelper.resolveBetterType(any(), any())).thenReturn(DeviceType.UNKNOWN)
        whenever(scanThrottler.shouldUpdateDevice(any())).thenReturn(true) // Force update

        // Act
        persister.persist(ctx, mock { whenever(it.resolveType(any())).thenReturn(DeviceType.UNKNOWN) })

        // Assert
        verify(deviceDao).setTrackingStatus(
            fingerprint = eq(fingerprint),
            status = eq(TrackingStatus.SUSPICIOUS),
            score = eq(15.0f),
            explanation = eq("Seen for 6min while moving"),
            durationScore = eq(0),
            rssiStabilityScore = eq(0),
            deviceTypeScore = eq(0),
            macBehaviorScore = eq(0),
            encounterScore = eq(0),
            userMoved = anyOrNull(),
            baselineDevice = anyOrNull(),
        )
    }

    @Test
    fun `persist SHOULD update status if Score changed significantly`() = runTest {
        // Arrange
        val fingerprint = "FINGERPRINT_1"
        val existing = createDeviceEntity(fingerprint, TrackingStatus.SAFE, 10.0f)
        val ctx = createScanContext(fingerprint, existing)
        
        // Status same, but Score Changed > 2.0
        ctx.trackingStatus = TrackingStatus.SAFE
        ctx.followingScore = 13.0f // Delta = 3.0
        ctx.followMeExplanation = "Known tracker type"

        whenever(priorityHelper.resolveBetterType(any(), any())).thenReturn(DeviceType.UNKNOWN)
        whenever(scanThrottler.shouldUpdateDevice(any())).thenReturn(true)

        // Act
        persister.persist(ctx, mock { whenever(it.resolveType(any())).thenReturn(DeviceType.UNKNOWN) })

        // Assert
        verify(deviceDao).setTrackingStatus(
            fingerprint = eq(fingerprint),
            status = eq(TrackingStatus.SAFE),
            score = eq(13.0f),
            explanation = eq("Known tracker type"),
            durationScore = eq(0),
            rssiStabilityScore = eq(0),
            deviceTypeScore = eq(0),
            macBehaviorScore = eq(0),
            encounterScore = eq(0),
            userMoved = anyOrNull(),
            baselineDevice = anyOrNull(),
        )
    }

    @Test
    fun `persist SHOULD update explanation even when scan update is throttled`() = runTest {
        val fingerprint = "FINGERPRINT_1"
        val existing = createDeviceEntity(fingerprint, TrackingStatus.SAFE, 10.0f)
            .copy(followMeExplanation = "Old explanation")
        val ctx = createScanContext(fingerprint, existing)

        ctx.trackingStatus = TrackingStatus.SAFE
        ctx.followingScore = 10.0f
        ctx.followMeExplanation = "Movement not detected - follow-me score suppressed"

        whenever(priorityHelper.resolveBetterType(any(), any())).thenReturn(DeviceType.UNKNOWN)
        whenever(scanThrottler.shouldUpdateDevice(any())).thenReturn(false)

        persister.persist(ctx, mock { whenever(it.resolveType(any())).thenReturn(DeviceType.UNKNOWN) })

        verify(deviceDao).setTrackingStatus(
            fingerprint = eq(fingerprint),
            status = eq(TrackingStatus.SAFE),
            score = eq(10.0f),
            explanation = eq("Movement not detected - follow-me score suppressed"),
            durationScore = eq(0),
            rssiStabilityScore = eq(0),
            deviceTypeScore = eq(0),
            macBehaviorScore = eq(0),
            encounterScore = eq(0),
            userMoved = anyOrNull(),
            baselineDevice = anyOrNull(),
        )
    }

    @Test
    fun `persist SHOULD update follow me components with stable status`() = runTest {
        val fingerprint = "FINGERPRINT_1"
        val existing = createDeviceEntity(fingerprint, TrackingStatus.SAFE, 10.0f)
            .copy(followMeExplanation = "Stable explanation")
        val ctx = createScanContext(fingerprint, existing)

        ctx.trackingStatus = TrackingStatus.SAFE
        ctx.followingScore = 10.0f
        ctx.followMeExplanation = "Stable explanation"
        ctx.followMeDurationScore = 15
        ctx.followMeRssiStabilityScore = 18
        ctx.followMeEncounterScore = 4
        ctx.followMeUserMoved = true
        ctx.followMeBaselineDevice = false

        whenever(priorityHelper.resolveBetterType(any(), any())).thenReturn(DeviceType.UNKNOWN)
        whenever(scanThrottler.shouldUpdateDevice(any())).thenReturn(false)

        persister.persist(ctx, mock { whenever(it.resolveType(any())).thenReturn(DeviceType.UNKNOWN) })

        verify(deviceDao).setTrackingStatus(
            fingerprint = eq(fingerprint),
            status = eq(TrackingStatus.SAFE),
            score = eq(10.0f),
            explanation = eq("Stable explanation"),
            durationScore = eq(15),
            rssiStabilityScore = eq(18),
            deviceTypeScore = eq(0),
            macBehaviorScore = eq(0),
            encounterScore = eq(4),
            userMoved = eq(true),
            baselineDevice = eq(false),
        )
    }

    @Test
    fun `persist SHOULD record follow me history when components change`() = runTest {
        val fingerprint = "FINGERPRINT_1"
        val existing = createDeviceEntity(fingerprint, TrackingStatus.SAFE, 10.0f)
            .copy(followMeExplanation = "Stable explanation")
        val ctx = createScanContext(fingerprint, existing)

        ctx.trackingStatus = TrackingStatus.SAFE
        ctx.followingScore = 10.0f
        ctx.followMeExplanation = "Stable explanation"
        ctx.followMeDurationScore = 15
        ctx.followMeRssiStabilityScore = 18
        ctx.followMeEncounterScore = 4
        ctx.followMeUserMoved = true
        ctx.followMeBaselineDevice = false

        whenever(priorityHelper.resolveBetterType(any(), any())).thenReturn(DeviceType.UNKNOWN)
        whenever(scanThrottler.shouldUpdateDevice(any())).thenReturn(false)

        persister.persist(ctx, mock { whenever(it.resolveType(any())).thenReturn(DeviceType.UNKNOWN) })

        val captor = argumentCaptor<io.blueeye.core.data.db.entity.FollowMeObservationEntity>()
        verify(followMeObservationDao).insert(captor.capture())
        val observation = captor.firstValue

        assertEquals(fingerprint, observation.deviceFingerprint)
        assertEquals("MAC", observation.observedMac)
        assertEquals(TrackingStatus.SAFE, observation.trackingStatus)
        assertEquals(10.0f, observation.score)
        assertEquals("Stable explanation", observation.explanation)
        assertEquals(-50, observation.rssi)
        assertEquals(15, observation.durationScore)
        assertEquals(18, observation.rssiStabilityScore)
        assertEquals(4, observation.encounterScore)
        assertEquals(true, observation.userMoved)
        assertEquals(false, observation.baselineDevice)
        verify(followMeObservationDao).trimDeviceHistory(eq(fingerprint), eq(100))
        verify(followMeObservationDao).deleteOldObservations(any())
    }

    @Test
    fun `persist SHOULD record BLE scan snapshot with fresh location`() = runTest {
        val fingerprint = "FINGERPRINT_1"
        val existing = createDeviceEntity(fingerprint, TrackingStatus.SAFE, 0f)
        val ctx = createScanContext(fingerprint, existing).copy(
            name = "Tracker Tag",
            manufacturerId = 0x004C,
            manufacturerData = byteArrayOf(0x01, 0x02),
            manufacturerDataById = mapOf(0x004C to byteArrayOf(0x01, 0x02)),
            serviceUuids = listOf("0000feed-0000-1000-8000-00805f9b34fb"),
            serviceDataByUuid = mapOf("0000feed-0000-1000-8000-00805f9b34fb" to byteArrayOf(0x0A, 0x0B)),
            appearance = 512,
            txPower = -8,
            isConnectable = true,
            primaryPhy = 1,
            secondaryPhy = 2,
            rawData = byteArrayOf(0x02, 0x01, 0x06),
        ).apply {
            this.fingerprint = fingerprint
            existingDevice = existing
            rawDataHex = "020106"
            vendorName = "Apple"
            beaconType = "FindMy"
            advertisingInterval = 1_000L
            trackingStatus = TrackingStatus.SUSPICIOUS
            followingScore = 42f
        }

        whenever(locationProvider.getFreshCoordinates()).thenReturn(Triple(51.0879, 17.0395, 6.5f))
        whenever(priorityHelper.resolveBetterType(any(), any())).thenReturn(DeviceType.TRACKER)
        whenever(scanThrottler.shouldUpdateDevice(any())).thenReturn(true)
        whenever(scanThrottler.shouldWriteSample(eq("MAC"), eq(false), any())).thenReturn(true)

        persister.persist(ctx, mock { whenever(it.resolveType(any())).thenReturn(DeviceType.TRACKER) })

        val captor = argumentCaptor<io.blueeye.core.data.db.entity.SignalSampleEntity>()
        verify(signalSampleDao).insert(captor.capture())
        val sample = captor.firstValue

        assertEquals(fingerprint, sample.deviceFingerprint)
        assertEquals("MAC", sample.observedMac)
        assertEquals("BLE", sample.technology)
        assertEquals("Tracker Tag", sample.deviceName)
        assertEquals(DeviceType.TRACKER.name, sample.deviceType)
        assertEquals("Apple", sample.vendorName)
        assertEquals(-50, sample.rssi)
        assertEquals(51.0879, sample.latitude)
        assertEquals(17.0395, sample.longitude)
        assertEquals(6.5f, sample.locationAccuracy)
        assertEquals(0x004C, sample.manufacturerId)
        assertEquals("0102", sample.manufacturerDataHex)
        assertEquals("0x004C=0102", sample.manufacturerDataByIdHex)
        assertEquals("0000feed-0000-1000-8000-00805f9b34fb", sample.serviceUuids)
        assertEquals("0000feed-0000-1000-8000-00805f9b34fb=0A0B", sample.serviceDataByUuidHex)
        assertEquals(512, sample.appearance)
        assertEquals(-8, sample.txPower)
        assertEquals(true, sample.isConnectable)
        assertEquals(1, sample.primaryPhy)
        assertEquals(2, sample.secondaryPhy)
        assertEquals(1_000L, sample.advertisingIntervalMs)
        assertEquals("FindMy", sample.beaconType)
        assertEquals("020106", sample.rawDataHex)
        assertEquals(TrackingStatus.SUSPICIOUS.name, sample.trackingStatus)
        assertEquals(42f, sample.followingScore)
    }

    @Test
    fun `persist SHOULD NOT update status if change is minor`() = runTest {
        // Arrange
        val fingerprint = "FINGERPRINT_1"
        val existing = createDeviceEntity(fingerprint, TrackingStatus.SAFE, 10.0f)
        val ctx = createScanContext(fingerprint, existing)
        
        // Status same, Score Delta = 1.0 (under threshold 2.0)
        ctx.trackingStatus = TrackingStatus.SAFE
        ctx.followingScore = 11.0f 

        whenever(priorityHelper.resolveBetterType(any(), any())).thenReturn(DeviceType.UNKNOWN)
        whenever(scanThrottler.shouldUpdateDevice(any())).thenReturn(true)

        // Act
        persister.persist(ctx, mock { whenever(it.resolveType(any())).thenReturn(DeviceType.UNKNOWN) })

        // Assert
        verify(deviceDao, never()).setTrackingStatus(
            any(),
            any(),
            any(),
            anyOrNull(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
        )
        verify(followMeObservationDao, never()).insert(any())
    }

    // Helpers
    private fun createDeviceEntity(fingerprint: String, status: TrackingStatus, score: Float): DeviceEntity {
        return DeviceEntity(
            fingerprint = fingerprint,
            lastMacAddress = "MAC",
            lastDeviceName = null,
            firstSeenAt = 0,
            lastSeenAt = 0,
            trackingStatus = status,
            followingScore = score
        )
    }

    private fun createScanContext(fingerprint: String, existing: DeviceEntity): ScanDataContext {
        return ScanDataContext(
            mac = "MAC",
            rssi = -50,
            timestamp = System.currentTimeMillis(),
            technology = "BLE",
            name = null,
            manufacturerId = null,
            manufacturerData = null,
            serviceUuids = emptyList(),
            appearance = null,
            txPower = null,
            isConnectable = false,
            primaryPhy = null,
            secondaryPhy = null,
            rawData = null
        ).apply {
            this.fingerprint = fingerprint
            this.existingDevice = existing
        }
    }
}
