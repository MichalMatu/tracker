package io.blueeye.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseExportActionsTest {
    @Test
    fun `small export remains eligible for clipboard`() {
        assertTrue(isClipboardPayloadSafe("{\"ok\":true}"))
    }

    @Test
    fun `real crash sized export is rejected before binder clipboard call`() {
        val crashSizedPayload = "x".repeat(2_377_352)

        assertFalse(isClipboardPayloadSafe(crashSizedPayload))
    }

    @Test
    fun `clipboard guard measures utf8 bytes rather than characters`() {
        val multiBytePayload = "€".repeat(180_000)

        assertFalse(isClipboardPayloadSafe(multiBytePayload))
    }
}
