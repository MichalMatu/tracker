package io.blueeye.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.blueeye.core.ui.theme.Dimens
import io.blueeye.core.ui.theme.extendedColors

@Composable
fun DetailsDecisionSummaryText(summary: DetailsDecisionSummary) {
    val toneColor = summary.tone.resolveColor()
    Column(
        modifier = Modifier.padding(top = Dimens.PaddingExtraSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingExtraSmall),
    ) {
        Text(
            text = summary.headline,
            style = MaterialTheme.typography.labelLarge,
            color = toneColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = summary.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = summary.actionText,
            style = MaterialTheme.typography.labelSmall,
            color = toneColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailsDecisionTone.resolveColor(): Color =
    when (this) {
        DetailsDecisionTone.SAFE -> MaterialTheme.extendedColors.safe
        DetailsDecisionTone.WARNING -> MaterialTheme.extendedColors.warning
        DetailsDecisionTone.SUSPICIOUS -> MaterialTheme.extendedColors.suspicious
    }
