package io.blueeye.core.data.mapper

import io.blueeye.core.data.db.entity.FollowMeObservationEntity
import io.blueeye.core.model.FollowMeHistorySample

fun FollowMeObservationEntity.toDomain(): FollowMeHistorySample =
    FollowMeHistorySample(
        timestamp = timestamp,
        observedMac = observedMac,
        trackingStatus = trackingStatus,
        score = score,
        explanation = explanation,
        rssi = rssi,
        encounterCount = encounterCount,
        durationScore = durationScore,
        rssiStabilityScore = rssiStabilityScore,
        deviceTypeScore = deviceTypeScore,
        macBehaviorScore = macBehaviorScore,
        encounterScore = encounterScore,
        userMoved = userMoved,
        baselineDevice = baselineDevice,
    )

fun List<FollowMeObservationEntity>.toFollowMeHistoryDomain(): List<FollowMeHistorySample> =
    map { it.toDomain() }
