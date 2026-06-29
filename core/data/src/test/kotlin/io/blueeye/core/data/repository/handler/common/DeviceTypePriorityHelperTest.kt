package io.blueeye.core.data.repository.handler.common

import io.blueeye.core.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTypePriorityHelperTest {
    private val helper = DeviceTypePriorityHelper()

    @Test
    fun `keeps existing computer type over equal priority audio payload`() {
        val resolved =
            helper.resolveBetterType(
                current = DeviceType.LAPTOP,
                candidate = DeviceType.HEADPHONES,
            )

        assertEquals(DeviceType.LAPTOP, resolved)
    }

    @Test
    fun `still upgrades lower confidence tracker type to strong tv identity`() {
        val resolved =
            helper.resolveBetterType(
                current = DeviceType.SAMSUNG_TAG,
                candidate = DeviceType.TV,
            )

        assertEquals(DeviceType.TV, resolved)
    }
}
