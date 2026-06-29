package io.blueeye.feature.settings

import io.blueeye.core.model.Device
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.IdentityCarryoverVerdict

internal fun Device.hasIdentityCarryoverEvidence(): Boolean =
    evidence.any { item ->
        item.source == EvidenceSource.IDENTITY_CARRYOVER
    }

internal fun Device.hasActionableIdentityCarryoverEvidence(): Boolean =
    identityCarryoverVerdict == IdentityCarryoverVerdict.UNREVIEWED &&
        hasIdentityCarryoverEvidence()

internal fun Device.hasReviewedIdentityCarryoverEvidence(): Boolean =
    identityCarryoverVerdict != IdentityCarryoverVerdict.UNREVIEWED &&
        hasIdentityCarryoverEvidence()

internal val IdentityCarryoverVerdict.sessionReviewDisplayText: String
    get() =
        when (this) {
            IdentityCarryoverVerdict.UNREVIEWED -> "unreviewed"
            IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE -> "same device"
            IdentityCarryoverVerdict.FALSE_MATCH -> "false match"
            IdentityCarryoverVerdict.INCONCLUSIVE -> "inconclusive"
        }
