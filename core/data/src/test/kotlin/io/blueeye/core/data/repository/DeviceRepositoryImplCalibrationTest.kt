package io.blueeye.core.data.repository

import io.blueeye.core.data.db.dao.DeviceCalibrationUpdate
import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.repository.handler.ble.BleScanHandler
import io.blueeye.core.data.repository.handler.classic.ClassicScanHandler
import io.blueeye.core.data.repository.handler.paired.ProbeResultHandler
import io.blueeye.core.domain.repository.DeviceConfig
import io.blueeye.core.model.DeviceCalibrationLabel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DeviceRepositoryImplCalibrationTest {
    private val deviceDao: DeviceDao = mock()
    private val repository =
        DeviceRepositoryImpl(
            deviceDao = deviceDao,
            deviceHistoryDataSource = mock(),
            bleScanHandler = mock<BleScanHandler>(),
            classicScanHandler = mock<ClassicScanHandler>(),
            probeResultHandler = mock<ProbeResultHandler>(),
            probeStateManager = ProbeStateManager(),
        )

    @Test
    fun `calibration update is persisted as one atomic DAO operation`() =
        runTest {
            whenever(deviceDao.updateCalibrationState(any())).thenReturn(1)
            val config =
                DeviceConfig(
                    alias = "known sensor",
                    notes = "desk",
                    isSafe = true,
                    alertSound = false,
                    alertVibration = true,
                    isTrackingEnabled = false,
                )

            val result =
                repository.updateDeviceCalibration(
                    fingerprint = FINGERPRINT,
                    config = config,
                    label = DeviceCalibrationLabel.KNOWN_SAFE,
                )

            assertTrue(result.isSuccess)
            val update = argumentCaptor<DeviceCalibrationUpdate>()
            verify(deviceDao).updateCalibrationState(update.capture())
            assertEquals(FINGERPRINT, update.firstValue.fingerprint)
            assertEquals("known sensor", update.firstValue.userAlias)
            assertEquals("desk", update.firstValue.userNotes)
            assertTrue(update.firstValue.isSafeBeacon)
            assertEquals(false, update.firstValue.alertSound)
            assertTrue(update.firstValue.alertVibration)
            assertEquals(false, update.firstValue.isTrackingEnabled)
            assertTrue(update.firstValue.isIgnoredForTracking)
            assertEquals(DeviceCalibrationLabel.KNOWN_SAFE, update.firstValue.calibrationLabel)
        }

    @Test
    fun `calibration update fails when no device row was updated`() =
        runTest {
            whenever(deviceDao.updateCalibrationState(any())).thenReturn(0)

            val result =
                repository.updateDeviceCalibration(
                    fingerprint = FINGERPRINT,
                    config =
                        DeviceConfig(
                            alias = null,
                            notes = null,
                            isSafe = false,
                            alertSound = false,
                            alertVibration = false,
                        ),
                    label = DeviceCalibrationLabel.UNKNOWN,
                )

            assertTrue(result.isFailure)
        }

    private companion object {
        const val FINGERPRINT = "device-fingerprint"
    }
}
