package io.blueeye.feature.radar.presentation

enum class RadarSectionViewType(
    val title: String,
    val sectionType: RadarUiSectionType?,
) {
    ALL(title = "All", sectionType = null),
    NEARBY(title = "Nearby", sectionType = RadarUiSectionType.NEARBY),
    WATCHLIST(title = "Watchlist", sectionType = RadarUiSectionType.WATCHLIST),
    SUSPICIOUS(title = "Suspicious", sectionType = RadarUiSectionType.SUSPICIOUS),
    PUBLIC_SAFETY(title = "Public Safety Signals", sectionType = RadarUiSectionType.PUBLIC_SAFETY),
    UNKNOWN_NOISE(title = "Unknown / Noise", sectionType = RadarUiSectionType.UNKNOWN_NOISE),
}

data class RadarSectionViewOption(
    val type: RadarSectionViewType,
    val label: String,
    val count: Int,
)

object RadarSectionViewMapper {
    fun options(sections: List<RadarUiSection>): List<RadarSectionViewOption> {
        if (sections.isEmpty()) return emptyList()

        val totalCount = sections.sumOf { it.items.size }
        val sectionCounts = sections.associate { section -> section.type to section.items.size }

        return buildList {
            add(RadarSectionViewType.ALL.toOption(totalCount))
            DECISION_VIEW_ORDER
                .forEach { type ->
                    val count = sectionCounts[type.sectionType].orZero()
                    add(type.toOption(count))
                }
        }
    }

    fun resolveSelectedView(
        requested: RadarSectionViewType,
        options: List<RadarSectionViewOption>,
    ): RadarSectionViewType {
        val availableTypes = options.map { it.type }.toSet()
        return if (requested in availableTypes) requested else RadarSectionViewType.ALL
    }

    fun visibleSections(
        sections: List<RadarUiSection>,
        selectedView: RadarSectionViewType,
    ): List<RadarUiSection> {
        val sectionType = selectedView.sectionType ?: return sections
        return sections.filter { it.type == sectionType }
    }

    fun emptyText(selectedView: RadarSectionViewType): String =
        when (selectedView) {
            RadarSectionViewType.ALL -> "No devices visible."
            RadarSectionViewType.NEARBY -> "No ordinary nearby devices are visible right now."
            RadarSectionViewType.WATCHLIST -> "No watched devices are visible right now."
            RadarSectionViewType.SUSPICIOUS -> "No suspicious movement evidence is visible right now."
            RadarSectionViewType.PUBLIC_SAFETY ->
                "No public-safety-like signals are visible right now."
            RadarSectionViewType.UNKNOWN_NOISE ->
                "No unknown or low-value noise is visible right now."
        }

    private fun RadarSectionViewType.toOption(count: Int): RadarSectionViewOption =
        RadarSectionViewOption(
            type = this,
            label = "$title $count",
            count = count,
        )

    private fun Int?.orZero(): Int = this ?: 0

    private val DECISION_VIEW_ORDER =
        listOf(
            RadarSectionViewType.WATCHLIST,
            RadarSectionViewType.SUSPICIOUS,
            RadarSectionViewType.PUBLIC_SAFETY,
            RadarSectionViewType.NEARBY,
            RadarSectionViewType.UNKNOWN_NOISE,
        )
}
