package io.blueeye.feature.radar.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveGattCollectionConfirmDialogTest {
    @Test
    fun `toggle requests confirmation when active collection is disabled`() {
        var disableCount = 0
        var enableRequestCount = 0

        handleAutoActiveProbeToggle(
            enabled = false,
            onDisable = { disableCount++ },
            onEnableRequested = { enableRequestCount++ },
        )

        assertEquals(0, disableCount)
        assertEquals(1, enableRequestCount)
    }

    @Test
    fun `toggle disables immediately when active collection is enabled`() {
        var disableCount = 0
        var enableRequestCount = 0

        handleAutoActiveProbeToggle(
            enabled = true,
            onDisable = { disableCount++ },
            onEnableRequested = { enableRequestCount++ },
        )

        assertEquals(1, disableCount)
        assertEquals(0, enableRequestCount)
    }
}
