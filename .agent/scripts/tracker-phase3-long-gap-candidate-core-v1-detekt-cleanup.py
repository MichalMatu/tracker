from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one match in {path}: {text.count(old)}")
    path.write_text(text.replace(old, new, 1))


strategy = Path("core/data/src/main/java/io/blueeye/core/data/tracker/strategy/DeviceCorrelationStrategy.kt")

replace_once(
    strategy,
    '''        val input = buildMatchInput(data, deviceName, advertisingInterval)
        for (target in targets) {
            if (AppleIdentityConflictGuard.hasNameFamilyConflict(input.deviceName, target.lastDeviceName)) {
                continue
            }
            val timeSinceLastSeen = input.data.timestamp - target.lastSeenAt
            if (timeSinceLastSeen <= CARRYOVER_WINDOW_MS || timeSinceLastSeen > LONG_GAP_CANDIDATE_WINDOW_MS) {
                continue
            }
            val evidence = longGapSameNameEvidence(input, target) ?: continue
            return IdentityCandidateMatch(
                candidateFingerprint = target.primaryMac,
                evidence = evidence,
            )
        }
        return null
''',
    '''        val input = buildMatchInput(data, deviceName, advertisingInterval)
        for (target in targets) {
            val timeSinceLastSeen = input.data.timestamp - target.lastSeenAt
            val isWithinCandidateWindow =
                timeSinceLastSeen > CARRYOVER_WINDOW_MS &&
                    timeSinceLastSeen <= LONG_GAP_CANDIDATE_WINDOW_MS
            val hasNameFamilyConflict =
                AppleIdentityConflictGuard.hasNameFamilyConflict(input.deviceName, target.lastDeviceName)
            if (isWithinCandidateWindow && !hasNameFamilyConflict) {
                val evidence = longGapSameNameEvidence(input, target)
                if (evidence != null) {
                    return IdentityCandidateMatch(
                        candidateFingerprint = target.primaryMac,
                        evidence = evidence,
                    )
                }
            }
        }
        return null
''',
)

replace_once(
    strategy,
    '''    private fun longGapSameNameEvidence(
        input: MatchInput,
        target: TrackedTarget,
    ): CarryoverMatchEvidence? {
        val name = input.deviceName
        if (name.isNullOrBlank() || name != target.lastDeviceName || isGenericName(name)) return null
        if (!hasSameNameCorroboration(input, target)) return null
        val rssiDiff = abs(input.data.rssi - target.lastRssi)
        if (rssiDiff > MAX_RSSI_DIFF || input.data.rssi <= -95) return null
        return carryoverEvidence(
            reasonCode = CarryoverMatchReason.SAME_NAME_PROXIMITY,
            confidence = 1.0f,
            input = input,
            target = target,
            details = "candidateOnly=true;scorePct=100",
        )
    }
''',
    '''    private fun longGapSameNameEvidence(
        input: MatchInput,
        target: TrackedTarget,
    ): CarryoverMatchEvidence? {
        val name = input.deviceName
        val hasMatchingSpecificName =
            !name.isNullOrBlank() &&
                name == target.lastDeviceName &&
                !isGenericName(name)
        val rssiDiff = abs(input.data.rssi - target.lastRssi)
        return when {
            !hasMatchingSpecificName -> null
            !hasSameNameCorroboration(input, target) -> null
            rssiDiff > MAX_RSSI_DIFF || input.data.rssi <= -95 -> null
            else ->
                carryoverEvidence(
                    reasonCode = CarryoverMatchReason.SAME_NAME_PROXIMITY,
                    confidence = 1.0f,
                    input = input,
                    target = target,
                    details = "candidateOnly=true;scorePct=100",
                )
        }
    }
''',
)
