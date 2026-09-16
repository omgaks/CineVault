package com.sole.cinevault.subtitles

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Preview(name = "Quick HUD · Compact portrait", widthDp = 360, heightDp = 800, showBackground = true)
@Preview(name = "Quick HUD · Compact landscape", widthDp = 640, heightDp = 360, showBackground = true)
@Preview(name = "Quick HUD · Medium portrait", widthDp = 700, heightDp = 1000, showBackground = true)
@Preview(name = "Quick HUD · Expanded landscape", widthDp = 1200, heightDp = 800, showBackground = true)
@Preview(name = "Quick HUD · Large font", widthDp = 360, heightDp = 800, fontScale = 1.5f, showBackground = true)
@Composable
private fun QuickHudAdaptivePreview() {
    val density = LocalDensity.current
    val size = with(density) { IntSize(360.dp.roundToPx(), 800.dp.roundToPx()) }
    QuickHud(
        subtitleFileName = "A very long subtitle filename for adaptive preview.srt",
        delaySeconds = 0.2f,
        onDelayChange = {},
        speechTimeline = null,
        fontSizeSp = 24f,
        onFontSizeChange = {},
        bottomPadding = 0.12f,
        onBottomPaddingChange = {},
        onReset = {},
        containerSize = size,
        initialOffset = Offset.Zero,
        windowWidth = 320.dp,
    )
}
