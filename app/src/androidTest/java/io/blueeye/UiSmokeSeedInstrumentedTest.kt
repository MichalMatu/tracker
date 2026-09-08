package io.blueeye

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.blueeye.core.data.db.TrackerDatabase
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiSmokeSeedInstrumentedTest {
    @Test
    fun seedUiSmokeDevices() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.deleteDatabase(TrackerDatabase.DATABASE_NAME)
            val database =
                Room.databaseBuilder(
                    context,
                    TrackerDatabase::class.java,
                    TrackerDatabase.DATABASE_NAME,
                ).fallbackToDestructiveMigration()
                    .build()

            try {
                val devices = smokeDevices()
                database.deviceDao().upsertAll(devices)
                assertEquals(devices.size, database.deviceDao().getAllDevices().size)
            } finally {
                database.close()
            }
        }

    private fun smokeDevices(): List<DeviceEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            baseDevice(1, "UI Smoke Watch", now).copy(
                isInWatchlist = true,
                userAlias = "Walk bag tag",
                deviceType = DeviceType.TRACKER,
                lastRssi = -54,
            ),
            baseDevice(2, "UI Smoke Watch Two", now).copy(
                isInWatchlist = true,
                deviceType = DeviceType.BEACON,
                lastRssi = -67,
            ),
            baseDevice(3, "UI Smoke Suspicious", now).copy(
                trackingStatus = TrackingStatus.SUSPICIOUS,
                followingScore = 78f,
                deviceType = DeviceType.TRACKER,
                lastRssi = -72,
            ),
            baseDevice(4, "UI Smoke Phone", now).copy(
                deviceType = DeviceType.PHONE,
                vendorName = "Smoke Mobile",
                lastRssi = -42,
            ),
            baseDevice(5, "UI Smoke Headphones", now).copy(
                deviceType = DeviceType.HEADPHONES,
                vendorName = "Smoke Audio",
                lastRssi = -61,
            ),
            baseDevice(6, "UI Smoke Sensor", now).copy(
                deviceType = DeviceType.SENSOR,
                sensorData = "Temp: 21.5C, Battery: 91%",
                lastRssi = -83,
            ),
            baseDevice(7, "UI Smoke Nearby", now).copy(
                deviceType = DeviceType.BEACON,
                vendorName = "Smoke Labs",
                lastRssi = -88,
            ),
            baseDevice(8, "UI Smoke Unknown", now).copy(
                deviceType = DeviceType.UNKNOWN,
                lastRssi = -96,
            ),
        )
    }

    private fun baseDevice(
        index: Int,
        name: String,
        now: Long,
    ): DeviceEntity {
        val observedAt = now - index * MINUTE_MS
        return DeviceEntity(
            fingerprint = "ui-smoke-$index",
            lastMacAddress = "02:00:00:00:00:${index.toString().padStart(2, '0')}",
            macAddressType = MacAddressType.RANDOM,
            technology = "BLE",
            lastDeviceName = name,
            firstSeenAt = observedAt - HOUR_MS,
            lastSeenAt = observedAt,
            lastRssi = -70,
            encounterCount = index + 1,
            isConnectable = true,
            lastRawData = "0201060303AAFE-smoke-$index",
        )
    }

    private companion object {
        private const val MINUTE_MS = 60_000L
        private const val HOUR_MS = 3_600_000L
    }
}
