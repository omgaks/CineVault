package com.sole.cinevault

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sole.cinevault.subtitles.DisplayProfileType
import com.sole.cinevault.subtitles.SubtitleAppearance
import com.sole.cinevault.subtitles.SubtitleProfileSettings
import com.sole.cinevault.subtitles.displayProfileId
import com.sole.cinevault.subtitles.loadSubtitleProfileSettings
import com.sole.cinevault.subtitles.saveSubtitleProfileSettings
import kotlinx.coroutines.delay

internal data class PlayerDisplayLayout(
    val isLandscape: Boolean,
    val isSmallPhone: Boolean,
    val isCompactLandscape: Boolean,
    val scale: Float,
    val playButton: Dp,
    val smallButton: Dp,
    val hudSize: Dp,
    val sidePadding: Dp,
    val bottomDockPadding: Dp,
    val seekBottomPadding: Dp,
    val topClusterPaddingTop: Dp,
)

internal fun calculatePlayerDisplayLayout(maxWidth: Dp, maxHeight: Dp): PlayerDisplayLayout {
    val isLandscape = maxWidth > maxHeight
    val isSmallPhone = maxWidth < 430.dp || maxHeight < 760.dp
    val isCompactLandscape = isLandscape && maxHeight < 430.dp
    val layoutScale = when {
        isCompactLandscape -> 0.70f
        isSmallPhone && !isLandscape -> 0.78f
        isSmallPhone -> 0.82f
        isLandscape -> 0.90f
        else -> 1f
    }
    val deckNaturalWidth = 66f * 6 + 98f + 7f * 7 + 24f
    val fitScale = ((maxWidth.value - 32f) / deckNaturalWidth).coerceAtMost(1f)
    val scale = minOf(layoutScale, fitScale).coerceAtLeast(0.42f)

    return PlayerDisplayLayout(
        isLandscape = isLandscape,
        isSmallPhone = isSmallPhone,
        isCompactLandscape = isCompactLandscape,
        scale = scale,
        playButton = (98 * scale).dp,
        smallButton = (66 * scale).dp,
        hudSize = (72 * scale).dp,
        sidePadding = if (isCompactLandscape) 8.dp else 16.dp,
        bottomDockPadding = when {
            isCompactLandscape -> 76.dp
            isLandscape -> 90.dp
            else -> 152.dp
        },
        seekBottomPadding = when {
            isCompactLandscape -> 13.dp
            isLandscape -> 17.dp
            else -> 92.dp
        },
        topClusterPaddingTop = if (isLandscape) 10.dp else 18.dp,
    )
}

@Composable
internal fun RememberPlayerSubtitleDisplayProfile(
    context: Context,
    externalDisplayConnected: Boolean,
    isSmallPhone: Boolean,
    isLandscape: Boolean,
    appearanceUi: SubtitleAppearanceUiState,
): DisplayProfileType {
    val displayProfileType = when {
        externalDisplayConnected -> DisplayProfileType.EXTERNAL
        isSmallPhone -> DisplayProfileType.PHONE
        else -> DisplayProfileType.TABLET
    }
    val currentProfileId = remember(displayProfileType, isLandscape) {
        displayProfileId(displayProfileType, isLandscape)
    }
    var profileLoadedFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentProfileId) {
        val settings = loadSubtitleProfileSettings(context, displayProfileType, isLandscape)
        appearanceUi.textSizeSp = settings.fontSizeSp
        appearanceUi.bottomPadding = settings.bottomPadding
        appearanceUi.preset = settings.presetName
        appearanceUi.appearance = SubtitleAppearance(
            settings.foregroundColor,
            settings.edgeType,
            settings.edgeColor,
            settings.backgroundColor,
        )
        profileLoadedFor = currentProfileId
    }

    LaunchedEffect(
        currentProfileId,
        appearanceUi.textSizeSp,
        appearanceUi.bottomPadding,
        appearanceUi.preset,
        appearanceUi.appearance,
    ) {
        if (profileLoadedFor != currentProfileId) return@LaunchedEffect
        delay(400)
        saveSubtitleProfileSettings(
            context,
            displayProfileType,
            isLandscape,
            SubtitleProfileSettings(
                fontSizeSp = appearanceUi.textSizeSp,
                bottomPadding = appearanceUi.bottomPadding,
                presetName = appearanceUi.preset,
                foregroundColor = appearanceUi.appearance.foregroundColor,
                edgeType = appearanceUi.appearance.edgeType,
                edgeColor = appearanceUi.appearance.edgeColor,
                backgroundColor = appearanceUi.appearance.backgroundColor,
            ),
        )
    }

    return displayProfileType
}
