package io.blueeye.feature.watchlist

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.blueeye.core.ui.theme.BlueEyeTheme
import io.blueeye.core.ui.theme.extendedColors

@Composable
fun WatchlistReturnEvidenceSummary(
    evidence: WatchlistReturnEvidenceUiInfo,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "${evidence.confidenceText} - ${evidence.sourceText}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extendedColors.warning,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = evidence.reasonText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        evidence.valueText?.let { valueText ->
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WatchlistReturnEvidenceSummaryPreview() {
    BlueEyeTheme {
        WatchlistReturnEvidenceSummary(
            evidence =
                WatchlistReturnEvidenceUiInfo(
                    confidenceText = "High confidence",
                    sourceText = "Source: Watchlist - passive",
                    reasonText = "User-selected watchlist device returned after 120s offline.",
                    valueText = "Value: AA:BB:CC:11:22:33 -> Desk headphones",
                ),
        )
    }
}
