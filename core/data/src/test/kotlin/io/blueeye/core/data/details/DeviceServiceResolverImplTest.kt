package io.blueeye.core.data.details

import io.blueeye.core.connectivity.resolver.BluetoothNamesResolver
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceServiceResolverImplTest {
    private val resolver =
        DeviceServiceResolverImpl(
            FakeBluetoothNamesResolver,
        )

    @Test
    fun `resolves persisted classic uuid list as services`() {
        val services =
            resolver.resolvePersistedServices(
                device(gattServices = "0000110b-0000-1000-8000-00805f9b34fb, 0000110e-0000-1000-8000-00805f9b34fb"),
            ).getOrThrow()

        assertEquals(2, services.size)
        assertEquals("0000110b-0000-1000-8000-00805f9b34fb", services[0].uuid)
        assertEquals("Unknown Service", services[0].name)
        assertEquals(0, services[0].characteristics.size)
        assertEquals("0000110e-0000-1000-8000-00805f9b34fb", services[1].uuid)
    }

    @Test
    fun `resolves structured probe services with characteristic values`() {
        val services =
            resolver.resolvePersistedServices(
                device(
                    gattServices = "180a:[2a24,2a29]",
                    characteristicData = "2A24=4D6F64656C|2A29=56656E646F72",
                ),
            ).getOrThrow()

        assertEquals(1, services.size)
        assertEquals("0000180a-0000-1000-8000-00805f9b34fb", services[0].uuid)
        assertEquals(2, services[0].characteristics.size)
        assertEquals("00002a24-0000-1000-8000-00805f9b34fb", services[0].characteristics[0].uuid)
        assertEquals("4D6F64656C", services[0].characteristics[0].value)
        assertEquals("00002a29-0000-1000-8000-00805f9b34fb", services[0].characteristics[1].uuid)
        assertEquals("56656E646F72", services[0].characteristics[1].value)
    }

    private fun device(
        gattServices: String?,
        characteristicData: String? = null,
    ): Device =
        Device(
            fingerprint = FINGERPRINT,
            macAddress = FINGERPRINT,
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = "Test device",
            deviceType = DeviceType.UNKNOWN,
            vendorName = null,
            predictedModel = null,
            trackingStatus = TrackingStatus.SAFE,
            followingScore = 0f,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            firstSeenAt = NOW,
            lastSeenAt = NOW,
            encounterCount = 1,
            gattServices = gattServices,
            characteristicData = characteristicData,
        )

    private companion object {
        private const val FINGERPRINT = "AA:BB:CC:11:22:33"
        private const val NOW = 1_789_000_000_000L
    }
}

private object FakeBluetoothNamesResolver : BluetoothNamesResolver {
    override fun resolveServiceName(uuid: String): String = "Unknown Service"

    override fun resolveCharName(uuid: String): String = "Unknown Char"
}
