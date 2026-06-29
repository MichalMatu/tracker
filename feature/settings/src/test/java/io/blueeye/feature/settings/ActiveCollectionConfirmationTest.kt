package io.blueeye.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveCollectionConfirmationTest {
    @Test
    fun `setting change requests confirmation before enabling active collection`() {
        var disableCount = 0
        var enableRequestCount = 0

        handleActiveCollectionSettingChange(
            enabled = false,
            requestedEnabled = true,
            onDisable = { disableCount++ },
            onEnableRequested = { enableRequestCount++ },
        )

        assertEquals(0, disableCount)
        assertEquals(1, enableRequestCount)
    }

    @Test
    fun `setting change disables active collection immediately`() {
        var disableCount = 0
        var enableRequestCount = 0

        handleActiveCollectionSettingChange(
            enabled = true,
            requestedEnabled = false,
            onDisable = { disableCount++ },
            onEnableRequested = { enableRequestCount++ },
        )

        assertEquals(1, disableCount)
        assertEquals(0, enableRequestCount)
    }

    @Test
    fun `setting change ignores unchanged active collection state`() {
        var disableCount = 0
        var enableRequestCount = 0

        handleActiveCollectionSettingChange(
            enabled = true,
            requestedEnabled = true,
            onDisable = { disableCount++ },
            onEnableRequested = { enableRequestCount++ },
        )

        assertEquals(0, disableCount)
        assertEquals(0, enableRequestCount)
    }
}
