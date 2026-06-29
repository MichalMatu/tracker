package io.blueeye.feature.radar.presentation

object RadarTopBarTitleFormatter {
    fun countText(state: RadarTopBarState): String {
        val attentionCount = state.decisionSummary?.attentionCount ?: 0
        val reviewText = reviewText(attentionCount)

        return when {
            state.isFilterActive && attentionCount > 0 ->
                "$reviewText / ${state.deviceCount} of ${state.totalCount} shown"
            state.isFilterActive ->
                "${state.deviceCount} of ${state.totalCount} shown / none need review"
            attentionCount > 0 ->
                "$reviewText / ${state.deviceCount} seen"
            else ->
                "${state.deviceCount} seen / none need review"
        }
    }

    fun summaryText(summary: RadarDecisionSummary): String = "${summary.headline} - ${summary.detail}"

    private fun reviewText(count: Int): String =
        if (count == 1) {
            "1 needs review"
        } else {
            "$count need review"
        }
}
