package com.sole.cinevault.audiofx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.sole.cinevault.ui.theme.SpaceBlack

@Preview(name = "Audio FX · Compact portrait", widthDp = 360, heightDp = 800, showBackground = true)
@Preview(name = "Audio FX · Compact landscape", widthDp = 640, heightDp = 360, showBackground = true)
@Preview(name = "Audio FX · Medium portrait", widthDp = 700, heightDp = 1000, showBackground = true)
@Preview(name = "Audio FX · Expanded landscape", widthDp = 1200, heightDp = 800, showBackground = true)
@Preview(name = "Audio FX · Large font", widthDp = 360, heightDp = 800, fontScale = 1.5f, showBackground = true)
@Composable
private fun AudioFxDashboardAdaptivePreview() {
    val context = LocalContext.current
    val controller = remember { AudioFxController(context) }
    Box(Modifier.fillMaxSize().background(SpaceBlack), contentAlignment = Alignment.Center) {
        AudioFxDashboard(controller = controller, onDismiss = {})
    }
}
