package io.blueeye.core.alert

import io.blueeye.core.domain.alert.AlertCategory
import io.blueeye.core.domain.repository.TrackerAlertSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertCancellationPolicyTest {
    @Test
    fun `detection off cancels every active alert effect`() {
        assertEquals(
            setOf(AlertCancellationAction.ALL),
            alertCancellationActions(settings(detection = false)),
        )
    }

    @Test
    fun `sound off stops only active sound`() {
        assertEquals(
            setOf(AlertCancellationAction.SOUND),
            alertCancellationActions(settings(sound = false)),
        )
    }

    @Test
    fun `vibration off cancels only active vibration`() {
        assertEquals(
            setOf(AlertCancellationAction.VIBRATION),
            alertCancellationActions(settings(vibration = false)),
        )
    }

    @Test
    fun `sound and vibration off cancel both without dismissing notification`() {
        assertEquals(
            setOf(AlertCancellationAction.SOUND, AlertCancellationAction.VIBRATION),
            alertCancellationActions(settings(sound = false, vibration = false)),
        )
    }

    @Test
    fun `enabled policy leaves active effects alone`() {
        assertEquals(emptySet<AlertCancellationAction>(), alertCancellationActions(settings()))
    }

    @Test
    fun `latest applied policy wins over stale sampled policy`() {
        val sampledPolicy = settings()
        val latestAppliedPolicy = settings(detection = false)

        assertEquals(
            latestAppliedPolicy,
            effectiveAlertPolicy(sampledPolicy, latestAppliedPolicy),
        )
    }

    @Test
    fun `sampled policy is used before collector has applied a policy`() {
        val sampledPolicy = settings(sound = false)

        assertEquals(sampledPolicy, effectiveAlertPolicy(sampledPolicy, null))
    }

    @Test
    fun `acknowledge target preserves exact alert identity`() {
        assertEquals(
            AlertAcknowledgeTarget(AlertCategory.TEST, "settings-test-alert"),
            alertAcknowledgeTarget(AlertCategory.TEST.name, "settings-test-alert"),
        )
    }

    @Test
    fun `acknowledge target rejects malformed identity`() {
        assertNull(alertAcknowledgeTarget("NOT_A_CATEGORY", "settings-test-alert"))
        assertNull(alertAcknowledgeTarget(AlertCategory.TEST.name, ""))
        assertNull(alertAcknowledgeTarget(null, "settings-test-alert"))
    }

    @Test
    fun `alert action request code includes category and key`() {
        val testCode = alertActionRequestCode(AlertCategory.TEST, "shared-key")
        val followMeCode = alertActionRequestCode(AlertCategory.FOLLOW_ME, "shared-key")

        assertEquals(testCode, alertActionRequestCode(AlertCategory.TEST, "shared-key"))
        check(testCode != followMeCode)
    }

    private fun settings(
        detection: Boolean = true,
        sound: Boolean = true,
        vibration: Boolean = true,
    ): TrackerAlertSettings =
        TrackerAlertSettings(
            detectionEnabled = detection,
            vibrationEnabled = vibration,
            soundEnabled = sound,
            headsUpEnabled = true,
        )
}
