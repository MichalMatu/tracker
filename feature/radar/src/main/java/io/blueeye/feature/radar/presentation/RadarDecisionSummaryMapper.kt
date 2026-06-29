package io.blueeye.feature.radar.presentation

object RadarDecisionSummaryMapper {
    fun summarize(sections: List<RadarUiSection>): RadarDecisionSummary {
        val counts = RadarDecisionSectionCounts.from(sections)
        val attentionCount = counts.watchlist + counts.suspicious + counts.publicSafety

        return when {
            counts.suspicious > 0 ->
                summary(
                    headline = "Review tracker-like evidence",
                    tone = RadarUiColorToken.SUSPICIOUS,
                    counts = counts,
                    attentionCount = attentionCount,
                )
            counts.watchlist > 0 ->
                summary(
                    headline = "Watchlist devices visible",
                    tone = RadarUiColorToken.WARNING,
                    counts = counts,
                    attentionCount = attentionCount,
                )
            counts.publicSafety > 0 ->
                summary(
                    headline = "Public-safety-like signals need evidence review",
                    tone = RadarUiColorToken.WARNING,
                    counts = counts,
                    attentionCount = attentionCount,
                )
            counts.nearby > 0 ->
                summary(
                    headline = "No attention signals",
                    tone = RadarUiColorToken.SAFE,
                    counts = counts,
                    attentionCount = attentionCount,
                )
            else ->
                summary(
                    headline = "Only unknown or low-value noise",
                    tone = RadarUiColorToken.OUTLINE,
                    counts = counts,
                    attentionCount = attentionCount,
                )
        }
    }

    private fun summary(
        headline: String,
        tone: RadarUiColorToken,
        counts: RadarDecisionSectionCounts,
        attentionCount: Int,
    ): RadarDecisionSummary =
        RadarDecisionSummary(
            headline = headline,
            detail = detailText(counts),
            tone = tone,
            attentionCount = attentionCount,
        )

    private fun detailText(counts: RadarDecisionSectionCounts): String {
        val parts =
            listOfNotNull(
                countPart(counts.watchlist, "watchlist"),
                countPart(counts.suspicious, "suspicious"),
                countPart(counts.publicSafety, "public safety"),
                countPart(counts.nearby, "nearby"),
                countPart(counts.noise, "noise"),
            )
        return parts.ifEmpty { listOf("no devices") }.joinToString(separator = " / ")
    }

    private fun countPart(
        count: Int,
        label: String,
    ): String? {
        if (count == 0) return null
        return "$count $label"
    }
}

private data class RadarDecisionSectionCounts(
    val watchlist: Int,
    val suspicious: Int,
    val publicSafety: Int,
    val nearby: Int,
    val noise: Int,
) {
    companion object {
        fun from(sections: List<RadarUiSection>): RadarDecisionSectionCounts {
            val counts = sections.associate { it.type to it.items.size }
            return RadarDecisionSectionCounts(
                watchlist = counts[RadarUiSectionType.WATCHLIST].orZero(),
                suspicious = counts[RadarUiSectionType.SUSPICIOUS].orZero(),
                publicSafety = counts[RadarUiSectionType.PUBLIC_SAFETY].orZero(),
                nearby = counts[RadarUiSectionType.NEARBY].orZero(),
                noise = counts[RadarUiSectionType.UNKNOWN_NOISE].orZero(),
            )
        }

        private fun Int?.orZero(): Int = this ?: 0
    }
}
