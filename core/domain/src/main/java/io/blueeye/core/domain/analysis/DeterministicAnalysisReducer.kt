package io.blueeye.core.domain.analysis

import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.model.analysis.AnalysisCandidateV1
import io.blueeye.core.model.analysis.AnalysisContradictionV1
import io.blueeye.core.model.analysis.AnalysisDiagnosticsV1
import io.blueeye.core.model.analysis.AnalysisHistoryCompletenessV1
import io.blueeye.core.model.analysis.AnalysisInputV1
import io.blueeye.core.model.analysis.AnalysisLocalVerdictV1
import io.blueeye.core.model.analysis.AnalysisObservationSummaryV1
import io.blueeye.core.model.analysis.AnalysisQualityFlagV1
import io.blueeye.core.model.analysis.AnalysisTimeWindowV1

object DeterministicAnalysisReducer {
    fun reduce(input: AnalysisInputV1): AnalysisCandidateV1 {
        val candidateKey = input.device.fingerprint
        val scopedSignals =
            input.signalSamples.filter { sample ->
                sample.deviceFingerprint.isBlank() || sample.deviceFingerprint == candidateKey
            }
        val scopedAlerts =
            input.alertEvents.filter { event ->
                event.deviceFingerprint == candidateKey
            }
        val scopedIdentity =
            input.identityCandidates.filter { candidate ->
                candidate.deviceFingerprint == candidateKey ||
                    candidate.candidateFingerprint == candidateKey
            }

        val observations =
            AnalysisObservationSummaryV1(
                signal = AnalysisObservationReducer.signal(scopedSignals),
                movement = AnalysisObservationReducer.movement(input.followMeHistory),
                alerts = AnalysisObservationReducer.alerts(scopedAlerts),
            )
        val evidence =
            AnalysisEvidenceReducer.reduce(
                device = input.device,
                alertEvents = scopedAlerts,
                identityCandidates = scopedIdentity,
            )
        val foreignInputDropped =
            scopedSignals.size != input.signalSamples.size ||
                scopedAlerts.size != input.alertEvents.size ||
                scopedIdentity.size != input.identityCandidates.size

        return AnalysisCandidateV1(
            candidateKey = candidateKey,
            window =
                analysisWindow(
                    input = input,
                    scopedSignals = scopedSignals,
                    scopedAlerts = scopedAlerts,
                    scopedIdentityCount = scopedIdentity.size,
                ),
            verdict =
                AnalysisLocalVerdictV1(
                    trackingStatus = input.device.trackingStatus,
                    followingScore = input.device.followingScore,
                    calibrationLabel = input.device.calibrationLabel,
                    userSuppressed = input.device.isUserSuppressed(),
                ),
            observations = observations,
            evidence = evidence,
            diagnostics =
                AnalysisDiagnosticsV1(
                    contradictions =
                        contradictions(
                            input = input,
                            maxFollowMeScore = observations.movement.maxScore,
                            rejectedHighConfidenceIdentity =
                                evidence.identityRelations.hasRejectedHighConfidenceRelation(),
                        ),
                    qualityFlags =
                        qualityFlags(
                            input = input,
                            observations = observations,
                            foreignInputDropped = foreignInputDropped,
                        ),
                ),
        )
    }

    private fun analysisWindow(
        input: AnalysisInputV1,
        scopedSignals: List<io.blueeye.core.model.SignalSample>,
        scopedAlerts: List<io.blueeye.core.model.AlertEvidenceEvent>,
        scopedIdentityCount: Int,
    ): AnalysisTimeWindowV1 {
        val timestamps =
            buildList {
                add(input.device.firstSeenAt)
                add(input.device.lastSeenAt)
                addAll(scopedSignals.map { it.timestamp })
                addAll(input.followMeHistory.map { it.timestamp })
                addAll(scopedAlerts.map { it.timestamp })
                if (scopedIdentityCount > 0) {
                    addAll(
                        input.identityCandidates
                            .filter { candidate ->
                                candidate.deviceFingerprint == input.device.fingerprint ||
                                    candidate.candidateFingerprint == input.device.fingerprint
                            }
                            .map { it.timestamp },
                    )
                }
            }

        return AnalysisTimeWindowV1(
            startedAt = timestamps.minOrNull() ?: input.device.firstSeenAt,
            endedAt = timestamps.maxOrNull() ?: input.device.lastSeenAt,
        )
    }

    private fun contradictions(
        input: AnalysisInputV1,
        maxFollowMeScore: Float?,
        rejectedHighConfidenceIdentity: Boolean,
    ): List<AnalysisContradictionV1> =
        buildList {
            val localAttention =
                input.device.trackingStatus != TrackingStatus.SAFE ||
                    input.device.followingScore >= ATTENTION_SCORE_THRESHOLD ||
                    (maxFollowMeScore ?: 0f) >= ATTENTION_SCORE_THRESHOLD
            if (input.device.isUserSuppressed() && localAttention) {
                add(AnalysisContradictionV1.USER_SUPPRESSION_CONFLICTS_WITH_LOCAL_ATTENTION)
            }
            if (rejectedHighConfidenceIdentity) {
                add(AnalysisContradictionV1.REVIEW_REJECTS_HIGH_CONFIDENCE_IDENTITY_RELATION)
            }
        }.sortedBy { it.name }

    private fun qualityFlags(
        input: AnalysisInputV1,
        observations: AnalysisObservationSummaryV1,
        foreignInputDropped: Boolean,
    ): List<AnalysisQualityFlagV1> =
        buildList {
            if (observations.signal.uniqueSampleCount == 0) {
                add(AnalysisQualityFlagV1.NO_SIGNAL_SAMPLES)
            }
            if (observations.movement.sourceSampleCount == 0) {
                add(AnalysisQualityFlagV1.NO_FOLLOW_ME_HISTORY)
            }
            if (observations.signal.location.samplesWithCoordinates == 0) {
                add(AnalysisQualityFlagV1.NO_LOCATION_DATA)
            } else if (observations.signal.location.poorSamples > 0) {
                add(AnalysisQualityFlagV1.POOR_LOCATION_ACCURACY)
            }
            addCompletenessFlags(input.completeness)
            if (foreignInputDropped) {
                add(AnalysisQualityFlagV1.FOREIGN_DEVICE_INPUT_DROPPED)
            }
        }.sortedBy { it.name }

    private fun MutableList<AnalysisQualityFlagV1>.addCompletenessFlags(
        completeness: AnalysisHistoryCompletenessV1,
    ) {
        if (!completeness.signalHistoryComplete) {
            add(AnalysisQualityFlagV1.SIGNAL_HISTORY_INCOMPLETE)
        }
        if (!completeness.followMeHistoryComplete) {
            add(AnalysisQualityFlagV1.FOLLOW_ME_HISTORY_INCOMPLETE)
        }
        if (!completeness.alertHistoryComplete) {
            add(AnalysisQualityFlagV1.ALERT_HISTORY_INCOMPLETE)
        }
        if (!completeness.identityHistoryComplete) {
            add(AnalysisQualityFlagV1.IDENTITY_HISTORY_INCOMPLETE)
        }
        if (!completeness.deviceEvidenceHistoryComplete) {
            add(AnalysisQualityFlagV1.DEVICE_EVIDENCE_HISTORY_INCOMPLETE)
        }
    }

    private fun io.blueeye.core.model.Device.isUserSuppressed(): Boolean =
        isSafeBeacon ||
            isIgnoredForTracking ||
            !isTrackingEnabled ||
            calibrationLabel == DeviceCalibrationLabel.FALSE_POSITIVE ||
            calibrationLabel == DeviceCalibrationLabel.KNOWN_SAFE

    private const val ATTENTION_SCORE_THRESHOLD = 51f
}
