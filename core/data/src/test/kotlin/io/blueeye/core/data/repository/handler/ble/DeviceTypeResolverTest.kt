package io.blueeye.core.data.repository.handler.ble

import io.blueeye.core.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTypeResolverTest {
    @Test
    fun `new device keeps strong scan identity over generic tracker vendor signal`() {
        val resolved =
            DeviceTypeResolver.resolveForNew(
                vendorType = DeviceType.TAG,
                scanType = DeviceType.TV,
                nameType = DeviceType.TV,
            )

        assertEquals(DeviceType.TV, resolved)
    }

    @Test
    fun `new device still uses vendor type when scan identity is unknown`() {
        val resolved =
            DeviceTypeResolver.resolveForNew(
                vendorType = DeviceType.SAMSUNG_TAG,
                scanType = DeviceType.UNKNOWN,
                nameType = DeviceType.UNKNOWN,
            )

        assertEquals(DeviceType.SAMSUNG_TAG, resolved)
    }

    @Test
    fun `explicit audio name wins over generic phone vendor signal`() {
        val resolved =
            DeviceTypeResolver.resolveForNew(
                vendorType = DeviceType.PHONE,
                scanType = DeviceType.HEADPHONES,
                nameType = DeviceType.HEADPHONES,
            )

        assertEquals(DeviceType.HEADPHONES, resolved)
    }
}
