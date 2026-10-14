package io.blueeye.feature.settings

import io.blueeye.core.domain.alert.AlertCategory
import io.blueeye.core.domain.alert.AlertDeliveryDiagnostics
import io.blueeye.core.domain.alert.AlertDeliveryResult
import io.blueeye.core.domain.alert.AlertDeliveryStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertDeliveryDiagnosticsExportTest {
    @Test
    fun `untouched alert diagnostics are not misleading policy block`() {
        val root = Json.parseToJsonElement(
            """{"schemaVersion":20}""".withFieldMvpDiagnostics(SettingsUiState()),
        ).jsonObject
        val alerts = root.getValue("fieldMvpDiagnostics").jsonObject.getValue("alerts").jsonObject
        assertEquals("NOT_ATTEMPTED", alerts.getValue("lastStatus").jsonPrimitive.content)
        assertEquals(0, alerts.getValue("recentDecisionCount").jsonPrimitive.int)
        assertEquals(0, alerts.getValue("recentDecisions").jsonArray.size)
    }

    @Test
    fun `recent delivery decisions omit private alert keys`() {
        val result = AlertDeliveryResult(
            category = AlertCategory.WATCHLIST_RETURN,
            key = "PRIVATE_DEVICE_FINGERPRINT",
            status = AlertDeliveryStatus.BLOCKED_BY_PERMISSION,
            timestamp = 123456L,
        )
        val ui = SettingsUiState(
            alertDeliveryDiagnostics = AlertDeliveryDiagnostics(
                lastResult = result,
                recentResults = listOf(result),
            ),
        )
        val exported = """{"schemaVersion":20}""".withFieldMvpDiagnostics(ui)
        val alerts = Json.parseToJsonElement(exported).jsonObject
            .getValue("fieldMvpDiagnostics").jsonObject.getValue("alerts").jsonObject
        assertEquals(1, alerts.getValue("recentDecisions").jsonArray.size)
        assertEquals("BLOCKED_BY_PERMISSION", alerts.getValue("lastStatus").jsonPrimitive.content)
        assertEquals(false, exported.contains("PRIVATE_DEVICE_FINGERPRINT"))
    }
}
