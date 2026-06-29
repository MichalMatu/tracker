package io.blueeye.feature.settings

internal object SessionReviewMixFormatter {
    fun format(counts: SessionReviewCategoryCounts): String {
        val parts =
            listOfNotNull(
                counts.watchlist.countPart("watchlist"),
                counts.suspicious.countPart("suspicious"),
                counts.publicSafety.countPart("public safety signals"),
                counts.nearby.countPart("nearby"),
                counts.unknownNoise.countPart("noise"),
            )
        return parts
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "Review mix: ", separator = " / ")
            .orEmpty()
    }

    private fun Int.countPart(label: String): String? = takeIf { it > 0 }?.let { count -> "$count $label" }
}
