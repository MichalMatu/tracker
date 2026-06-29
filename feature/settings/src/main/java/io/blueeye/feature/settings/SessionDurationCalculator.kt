package io.blueeye.feature.settings

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.Device
import io.blueeye.core.model.SignalSample

internal object SessionDurationCalculator {
    fun observedDurationMs(
        startedAt: Long,
        devices: List<Device>,
        samples: List<SignalSample>,
        followMeObservations: List<SessionFollowMeObservation> = emptyList(),
        alertEvidenceEvents: List<AlertEvidenceEvent> = emptyList(),
    ): Long {
        if (startedAt <= 0L) return 0L

        val latestObservedAt =
            listOfNotNull(
                devices.maxOfOrNull { device -> device.lastSeenAt },
                samples.maxOfOrNull { sample -> sample.timestamp },
                followMeObservations.maxOfOrNull { observation -> observation.observation.timestamp },
                alertEvidenceEvents.maxOfOrNull { event -> event.timestamp },
            ).maxOrNull()
        return latestObservedAt
            ?.minus(startedAt)
            ?.coerceAtLeast(0L)
            ?: 0L
    }
}
