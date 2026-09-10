package io.blueeye.core.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity

/**
 * Keeps live-updating content from repeatedly shrinking and expanding its parent.
 *
 * The greatest measured height is retained for as long as this modifier remains in composition.
 * Values may continue to update normally, but disappearing optional rows no longer pull every
 * element below the card up and down on each refresh.
 */
@Composable
fun Modifier.stableLiveHeight(): Modifier {
    var retainedHeightPx by remember { mutableIntStateOf(0) }
    val retainedHeight = with(LocalDensity.current) { retainedHeightPx.toDp() }

    return heightIn(min = retainedHeight)
        .onSizeChanged { size ->
            if (size.height > retainedHeightPx) {
                retainedHeightPx = size.height
            }
        }
}
