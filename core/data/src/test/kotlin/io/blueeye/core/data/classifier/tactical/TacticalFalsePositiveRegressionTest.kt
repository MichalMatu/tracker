package io.blueeye.core.data.classifier.tactical

import io.blueeye.core.data.classifier.vendor.TacticalOuiRegistry
import io.blueeye.core.data.classifier.vendor.tactical.TacticalCategory
import io.blueeye.core.data.classifier.vendor.tactical.TacticalNameMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TacticalFalsePositiveRegressionTest {
    @Test
    fun `huawei watch suffix is not reveal media body camera`() {
        assertNull(TacticalNameMatcher.match("HUAWEI WATCH FIT 4-D50"))
    }

    @Test
    fun `consumer names containing d4 or d5 fragments are not reveal media`() {
        assertNull(TacticalNameMatcher.match("ZD411-DFJ233300037"))
        assertNull(TacticalNameMatcher.match("Govee_H80C4_6D43"))
    }

    @Test
    fun `bare model code is not enough but reveal branded model matches`() {
        assertNull(TacticalNameMatcher.match("D5"))
        assertEquals(TacticalCategory.BODY_CAMERA, TacticalNameMatcher.match("Reveal D5")?.category)
    }

    @Test
    fun `generic ffe0 service is not kestrel evidence by itself`() {
        assertNull(TacticalOuiRegistry.matchByServiceUuid(listOf("0000ffe0-0000-1000-8000-00805f9b34fb")))
    }
}
