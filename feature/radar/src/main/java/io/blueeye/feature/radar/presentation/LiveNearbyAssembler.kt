package io.blueeye.feature.radar.presentation

object LiveNearbyAssembler {
    fun assemble(
        devices: List<LiveNearbyDevice>,
        pinnedFingerprint: String?,
    ): LiveNearbySnapshot {
        val pinned = devices.firstOrNull { it.item.fingerprint == pinnedFingerprint }
        val nonPinned = devices.filterNot { it.item.fingerprint == pinnedFingerprint }
        val groupableByFamily =
            nonPinned
                .filter { ProtocolNoiseClassifier.canGroup(it.item) }
                .groupBy { ProtocolNoiseClassifier.family(it.item)!! }
                .filterValues { it.size >= MIN_GROUP_SIZE }

        val groupedFingerprints =
            groupableByFamily.values
                .flatten()
                .mapTo(mutableSetOf()) { it.item.fingerprint }

        val standaloneEntries =
            nonPinned
                .filterNot { it.item.fingerprint in groupedFingerprints }
                .filter { it.freshness != LiveNearbyFreshness.STALE }
                .map { LiveNearbyEntry.Device(it) }

        val groupEntries =
            groupableByFamily.mapNotNull { (family, allMembers) ->
                val visibleMembers =
                    allMembers
                        .filter { it.freshness != LiveNearbyFreshness.STALE }
                        .sortedWith(
                            compareBy<LiveNearbyDevice> { it.freshness.sortOrder }
                                .thenByDescending { it.smoothedRssi }
                                .thenByDescending { it.item.lastSeenAt },
                        )
                if (visibleMembers.isEmpty()) return@mapNotNull null

                val active = visibleMembers.filter { it.freshness == LiveNearbyFreshness.ACTIVE }
                val recent = visibleMembers.filter { it.freshness == LiveNearbyFreshness.RECENT }
                LiveNearbyEntry.ProtocolGroup(
                    family = family,
                    members = visibleMembers,
                    activeCount = active.size,
                    recentCount = recent.size,
                    totalIdentities = allMembers.size,
                    sortRssi =
                        active.maxOfOrNull { it.smoothedRssi }
                            ?: recent.maxOf { it.smoothedRssi },
                    latestSeenAt = visibleMembers.maxOf { it.item.lastSeenAt },
                )
            }

        val allEntries = standaloneEntries + groupEntries
        val activeEntries =
            allEntries
                .filter { it.isActiveEntry() }
                .sortedWith(
                    compareByDescending<LiveNearbyEntry> { it.sortRssi }
                        .thenByDescending { it.latestSeenAt }
                        .thenBy { it.key },
                )
        val recentEntries =
            allEntries
                .filterNot { it.isActiveEntry() }
                .sortedWith(
                    compareByDescending<LiveNearbyEntry> { it.latestSeenAt }
                        .thenByDescending { it.sortRssi }
                        .thenBy { it.key },
                )

        return LiveNearbySnapshot(
            pinned = pinned,
            active = activeEntries,
            recent = recentEntries,
            activeIdentityCount = devices.count { it.freshness == LiveNearbyFreshness.ACTIVE },
            recentIdentityCount = devices.count { it.freshness == LiveNearbyFreshness.RECENT },
        )
    }

    private fun LiveNearbyEntry.isActiveEntry(): Boolean =
        when (this) {
            is LiveNearbyEntry.Device -> device.freshness == LiveNearbyFreshness.ACTIVE
            is LiveNearbyEntry.ProtocolGroup -> activeCount > 0
        }

    private val LiveNearbyFreshness.sortOrder: Int
        get() =
            when (this) {
                LiveNearbyFreshness.ACTIVE -> 0
                LiveNearbyFreshness.RECENT -> 1
                LiveNearbyFreshness.STALE -> 2
            }

    private const val MIN_GROUP_SIZE = 2
}

internal class LiveNearbyOrderController {
    private var lastReorderAt: Long? = null
    private var activeKeys: List<String> = emptyList()
    private var recentKeys: List<String> = emptyList()

    fun apply(
        snapshot: LiveNearbySnapshot,
        nowMs: Long,
        frozen: Boolean,
    ): LiveNearbySnapshot {
        val due = lastReorderAt?.let { nowMs - it >= REORDER_INTERVAL_MS } ?: true
        if (!frozen && due) {
            activeKeys = snapshot.active.map { it.key }
            recentKeys = snapshot.recent.map { it.key }
            lastReorderAt = nowMs
        }
        return snapshot.copy(
            active = applyStableOrder(snapshot.active, activeKeys),
            recent = applyStableOrder(snapshot.recent, recentKeys),
        )
    }

    private fun applyStableOrder(
        entries: List<LiveNearbyEntry>,
        preferredKeys: List<String>,
    ): List<LiveNearbyEntry> {
        val byKey = entries.associateBy { it.key }
        val ordered = preferredKeys.mapNotNull(byKey::get).toMutableList()
        val known = ordered.mapTo(mutableSetOf()) { it.key }
        ordered += entries.filterNot { it.key in known }
        return ordered
    }

    private companion object {
        const val REORDER_INTERVAL_MS = 5_000L
    }
}
