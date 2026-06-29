package io.blueeye.feature.radar.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.blueeye.core.ui.theme.BlueEyeTheme
import io.blueeye.core.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarSectionTabs(
    options: List<RadarSectionViewOption>,
    selectedView: RadarSectionViewType,
    onSelected: (RadarSectionViewType) -> Unit,
) {
    if (options.size <= 1) return

    val selectedTabIndex = options.indexOfFirst { it.type == selectedView }.coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        edgePadding = Dimens.PaddingSmall,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        options.forEach { option ->
            Tab(
                selected = option.type == selectedView,
                onClick = { onSelected(option.type) },
                text = {
                    Text(
                        text = option.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RadarSectionTabsPreview() {
    val previewOptions =
        listOf(
            previewOption(RadarSectionViewType.ALL, "All", countText = "12"),
            previewOption(RadarSectionViewType.SUSPICIOUS, "Suspicious", countText = "2"),
            previewOption(RadarSectionViewType.PUBLIC_SAFETY, "Public Safety", countText = "1"),
            previewOption(RadarSectionViewType.UNKNOWN_NOISE, "Noise", countText = "6"),
        )

    BlueEyeTheme {
        RadarSectionTabs(
            options = previewOptions,
            selectedView = RadarSectionViewType.SUSPICIOUS,
            onSelected = {},
        )
    }
}

private fun previewOption(
    type: RadarSectionViewType,
    title: String,
    countText: String,
): RadarSectionViewOption =
    RadarSectionViewOption(
        type = type,
        label = "$title $countText",
        count = countText.toInt(),
    )
