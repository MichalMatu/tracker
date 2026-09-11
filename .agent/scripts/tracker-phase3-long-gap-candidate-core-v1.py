from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one match in {path}: {text.count(old)}")
    path.write_text(text.replace(old, new, 1))


models = Path("core/data/src/main/java/io/blueeye/core/data/tracker/model/CorrelationResult.kt")
replace_once(
    models,
    '''/** Target match with the matcher evidence that justified it. */
data class CarryoverMatch(
    val targetId: String,
    val evidence: CarryoverMatchEvidence,
)

/** Result of correlation attempt. */
data class CorrelationResult(
''',
    '''/** Target match with the matcher evidence that justified it. */
data class CarryoverMatch(
    val targetId: String,
    val evidence: CarryoverMatchEvidence,
)

/** Non-destructive identity-continuity candidate for later persistence and review. */
data class IdentityCandidateMatch(
    val candidateFingerprint: String,
    val evidence: CarryoverMatchEvidence,
)

/** Result of correlation attempt. */
data class CorrelationResult(
''',
)
replace_once(
    models,
    '''    val correlatedMac: String?,
    val macChangeCount: Int = 0,
    val matchEvidence: CarryoverMatchEvidence? = null,
)
''',
    '''    val correlatedMac: String?,
    val macChangeCount: Int = 0,
    val matchEvidence: CarryoverMatchEvidence? = null,
    val identityCandidate: IdentityCandidateMatch? = null,
)
''',
)

strategy = Path("core/data/src/main/java/io/blueeye/core/data/tracker/strategy/DeviceCorrelationStrategy.kt")
replace_once(
    strategy,
    '''import io.blueeye.core.data.tracker.model.CarryoverMatchReason
import io.blueeye.core.data.tracker.model.TrackedTarget
''',
    '''import io.blueeye.core.data.tracker.model.CarryoverMatchReason
import io.blueeye.core.data.tracker.model.IdentityCandidateMatch
import io.blueeye.core.data.tracker.model.TrackedTarget
''',
)
replace_once(
    strategy,
    '''        /** Maximum time to consider for carryover (30 seconds) */
        const val CARRYOVER_WINDOW_MS = 30_000L

        /** Maximum RSSI difference for correlation */
''',
    '''        /** Maximum time to consider for destructive carryover (30 seconds). */
        const val CARRYOVER_WINDOW_MS = 30_000L

        /** Maximum age for a non-destructive long-gap identity candidate. */
        const val LONG_GAP_CANDIDATE_WINDOW_MS = 300_000L

        /** Maximum RSSI difference for correlation */
''',
)
replace_once(
    strategy,
    '''    ): CarryoverMatch? {
        val input =
            MatchInput(
                data = data,
                deviceName = deviceName,
                advertisingInterval = advertisingInterval,
                serviceUuids = data.serviceUuids.toSet(),
                rawData = data.rawData ?: data.manufacturerData,
            )

        // Select best target based on score
''',
    '''    ): CarryoverMatch? {
        val input = buildMatchInput(data, deviceName, advertisingInterval)

        // Select best target based on score
''',
)
replace_once(
    strategy,
    '''        return bestMatch
    }

    private fun weightedMatchForTarget(
''',
    '''        return bestMatch
    }

    /**
     * Finds review-only continuity evidence outside the destructive 30-second carryover window.
     * The caller must not use this result to rewrite the current fingerprint or merge records.
     */
    fun findLongGapCandidate(
        data: BleScanResultData,
        deviceName: String?,
        advertisingInterval: Long?,
        targets: Collection<TrackedTarget>,
    ): IdentityCandidateMatch? {
        val input = buildMatchInput(data, deviceName, advertisingInterval)
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
    }

    private fun buildMatchInput(
        data: BleScanResultData,
        deviceName: String?,
        advertisingInterval: Long?,
    ): MatchInput =
        MatchInput(
            data = data,
            deviceName = deviceName,
            advertisingInterval = advertisingInterval,
            serviceUuids = data.serviceUuids.toSet(),
            rawData = data.rawData ?: data.manufacturerData,
        )

    private fun longGapSameNameEvidence(
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

    private fun weightedMatchForTarget(
''',
)

tracker = Path("core/data/src/main/java/io/blueeye/core/data/tracker/AddressCarryoverTracker.kt")
replace_once(
    tracker,
    '''        val carryoverMatch = correlationStrategy.findMatch(
            data,
            deviceName,
            advertisingInterval,
            targets.values
        )

        val result = if (carryoverMatch != null) {
''',
    '''        val carryoverMatch = correlationStrategy.findMatch(
            data,
            deviceName,
            advertisingInterval,
            targets.values
        )
        val identityCandidate =
            if (carryoverMatch == null) {
                correlationStrategy.findLongGapCandidate(
                    data = data,
                    deviceName = deviceName,
                    advertisingInterval = advertisingInterval,
                    targets = targets.values,
                )
            } else {
                null
            }

        val result = if (carryoverMatch != null) {
''',
)
replace_once(
    tracker,
    '''        } else {
            handleNewTarget(
                data.mac,
                data.rssi,
                data.timestamp,
                payloadHash,
                uuidSet,
                deviceName,
                data.rawData ?: data.manufacturerData,
                advertisingInterval
            )
        }
''',
    '''        } else {
            handleNewTarget(
                data.mac,
                data.rssi,
                data.timestamp,
                payloadHash,
                uuidSet,
                deviceName,
                data.rawData ?: data.manufacturerData,
                advertisingInterval
            ).copy(identityCandidate = identityCandidate)
        }
''',
)

test = Path("core/data/src/test/kotlin/io/blueeye/core/data/tracker/AddressCarryoverTrackerTest.kt")
anchor = '''    @Test
    fun `known alias should keep reporting primary mac for persistence`() {
'''
insert = '''    @Test
    fun `long gap corroborated JBL name becomes candidate without carryover`() {
        val now = System.currentTimeMillis()
        val serviceUuids = listOf("0000fe2c-0000-1000-8000-00805f9b34fb")
        val first =
            createScanData("41:11:11:11:11:11", "JBL Tune 520BT-LE", null, null).copy(
                timestamp = now,
                serviceUuids = serviceUuids,
            )
        val second =
            createScanData("42:22:22:22:22:22", "JBL Tune 520BT-LE", null, null).copy(
                timestamp = now + 108_000L,
                serviceUuids = serviceUuids,
            )

        val firstResult = tracker.processScan(first, first.name)
        val secondResult = tracker.processScan(second, second.name)

        assertTrue(firstResult.isNewTarget)
        assertTrue(secondResult.isNewTarget)
        assertFalse(secondResult.isCarryover)
        assertNotEquals(firstResult.targetId, secondResult.targetId)
        assertEquals(first.mac, secondResult.identityCandidate?.candidateFingerprint)
        assertEquals(
            CarryoverMatchReason.SAME_NAME_PROXIMITY,
            secondResult.identityCandidate?.evidence?.reasonCode,
        )
        assertTrue(secondResult.identityCandidate?.evidence?.featureSummary.orEmpty().contains("candidateOnly=true"))
        assertTrue(secondResult.identityCandidate?.evidence?.featureSummary.orEmpty().contains("timeDeltaMs=108000"))
    }

    @Test
    fun `long gap OPPO name without corroboration stays separate without candidate`() {
        val now = System.currentTimeMillis()
        val first =
            createScanData("51:11:11:11:11:11", "OPPO Enco Buds3 Pro", null, null).copy(timestamp = now)
        val second =
            createScanData("52:22:22:22:22:22", "OPPO Enco Buds3 Pro", null, null).copy(timestamp = now + 72_000L)

        val firstResult = tracker.processScan(first, first.name)
        val secondResult = tracker.processScan(second, second.name)

        assertTrue(firstResult.isNewTarget)
        assertTrue(secondResult.isNewTarget)
        assertFalse(secondResult.isCarryover)
        assertNotEquals(firstResult.targetId, secondResult.targetId)
        assertNull(secondResult.identityCandidate)
    }

'''
replace_once(test, anchor, insert + anchor)
