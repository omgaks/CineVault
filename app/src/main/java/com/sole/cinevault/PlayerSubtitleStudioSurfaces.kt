package com.sole.cinevault

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sole.cinevault.subtitles.*

/**
 * Slice 56: presentation host for the compact Subtitle Studio surfaces.
 *
 * This owns no player state. It only renders and wires:
 * - Quick HUD
 * - Studio pill + Download/Power Tools lists
 * - Behaviour window
 * - Dual Subs window
 */
@Composable
internal fun PlayerSubtitleStudioSurfaces(
    context: Context,
    containerWidth: Dp,
    containerHeight: Dp,
    externalDisplayActive: Boolean,
    bottomDockPadding: Dp,
    playButton: Dp,
    subtitleIconCenterX: Float,
    quickHudFileName: String?,
    showSubtitleDock: Boolean,
    showSubtitleBloom: Boolean,
    studioCategory: StudioCategory?,
    showSubtitleBehaviourWindow: Boolean,
    showDualSubsWindow: Boolean,
    coreUi: SubtitleCoreUiState,
    trackUi: SubtitleTrackSelectionState,
    searchUi: SubtitleAcquisitionUiState,
    studioUi: SubtitleStudioUiState,
    dualUi: DualSubtitleState,
    appearanceUi: SubtitleAppearanceUiState,
    driftUi: DriftCorrectionState,
    autoSyncSpeechTimeline: FloatArray?,
    dualSecondaryColorHex: String,
    pendingDualAiLanguage: String?,
    subtitleStudioNavigation: SubtitleStudioNavigationCoordinator,
    subtitleResetCoordinator: SubtitleResetCoordinator,
    subtitleSyncTools: SubtitleSyncToolsCoordinator,
    autoSyncCoordinator: AutoSyncCoordinator,
    onStudioCategoryChanged: (StudioCategory?) -> Unit,
    onTrackSelectorManageModeChanged: (Boolean) -> Unit,
    onShowSubtitleBloomChanged: (Boolean) -> Unit,
    onShowSubtitleBehaviourWindowChanged: (Boolean) -> Unit,
    onShowDualSubsWindowChanged: (Boolean) -> Unit,
    onShowSpeechSubtitlePanelChanged: (Boolean) -> Unit,
    onShowSubtitleTranslationPanelChanged: (Boolean) -> Unit,
    onPendingDualAiLanguageChanged: (String?) -> Unit,
    onDualSecondaryColorHexChanged: (String) -> Unit,
) {
    val blockedByPlayerMode =
        CineVaultPlayerHolder.isInPipMode || externalDisplayActive

    if (showSubtitleDock && !blockedByPlayerMode) {
        val density = LocalDensity.current
        val containerPx = with(density) {
            androidx.compose.ui.unit.IntSize(
                containerWidth.roundToPx(),
                containerHeight.roundToPx(),
            )
        }
        val quickHudWidth =
            (containerWidth * 0.33f).coerceIn(211.dp, 264.dp)
        val quickHudOffsetX =
            calculatePlayerPopupOffsetX(
                iconCenterX = subtitleIconCenterX,
                popupWidth = quickHudWidth,
                screenWidthPx = with(density) { containerWidth.toPx() },
                density = density,
            ).toFloat()
        val quickHudOffsetY = with(density) {
            (containerHeight - bottomDockPadding - playButton - 158.dp)
                .coerceAtLeast(8.dp)
                .toPx()
        }

        QuickHud(
            subtitleFileName = quickHudFileName,
            delaySeconds = coreUi.syncOffset,
            onDelayChange = {
                coreUi.syncOffset = it
                studioUi.menuTouchKey++
            },
            speechTimeline = autoSyncSpeechTimeline,
            fontSizeSp = appearanceUi.textSizeSp,
            onFontSizeChange = {
                appearanceUi.textSizeSp = it
                studioUi.menuTouchKey++
            },
            bottomPadding = appearanceUi.bottomPadding,
            onBottomPaddingChange = {
                appearanceUi.bottomPadding = it
                studioUi.menuTouchKey++
            },
            onReset = { subtitleResetCoordinator.reset() },
            containerSize = containerPx,
            initialOffset = Offset(
                quickHudOffsetX,
                quickHudOffsetY,
            ),
            windowWidth = quickHudWidth,
        )
    }

    if (showSubtitleBloom && !blockedByPlayerMode) {
        val density = LocalDensity.current
        val containerPx = with(density) {
            androidx.compose.ui.unit.IntSize(
                containerWidth.roundToPx(),
                containerHeight.roundToPx(),
            )
        }
        val sideMargin =
            if (containerWidth < 700.dp) 10.dp else 18.dp
        val topMargin =
            if (containerHeight < 420.dp) 10.dp else 16.dp
        val pillWidth = 242.dp
        val listWindowWidth =
            if (containerWidth < 700.dp) 242.dp else 275.dp

        val pillOffset = with(density) {
            val xDp =
                (containerWidth - pillWidth - sideMargin)
                    .coerceAtLeast(sideMargin)
            val yDp =
                (containerHeight * 0.5f - 28.dp)
                    .coerceIn(
                        topMargin,
                        (containerHeight - 62.dp)
                            .coerceAtLeast(topMargin),
                    )
            Offset(xDp.toPx(), yDp.toPx())
        }

        val windowOffset = with(density) {
            val xDp =
                (containerWidth - listWindowWidth - sideMargin)
                    .coerceAtLeast(sideMargin)
            val estimatedWindowHeight =
                if (containerHeight < 420.dp) 250.dp else 290.dp
            val yDp =
                (containerHeight * 0.5f - estimatedWindowHeight * 0.5f)
                    .coerceAtLeast(topMargin)
            Offset(xDp.toPx(), yDp.toPx())
        }

        when (studioCategory) {
            null ->
                SubtitleStudioPill(
                    activeCategory = null,
                    onCategorySelected = {
                        subtitleStudioNavigation.onStudioCategoryTapped(it)
                    },
                    containerSize = containerPx,
                    initialOffset = pillOffset,
                )

            StudioCategory.DOWNLOAD ->
                StudioListWindow(
                    title = "Download",
                    onBack = { onStudioCategoryChanged(null) },
                    containerSize = containerPx,
                    initialOffset = windowOffset,
                    items =
                        listOf(
                            StudioListItem(
                                icon = StudioRowIcons.Manage,
                                label = "Manage",
                                onClick = {
                                    onTrackSelectorManageModeChanged(true)
                                    trackUi.showSelector = true
                                    onShowSubtitleBloomChanged(false)
                                    onStudioCategoryChanged(null)
                                },
                            ),
                            StudioListItem(
                                icon = StudioRowIcons.Tracks,
                                label = "Tracks",
                                onClick = {
                                    onTrackSelectorManageModeChanged(false)
                                    trackUi.showSelector = true
                                    onShowSubtitleBloomChanged(false)
                                    onStudioCategoryChanged(null)
                                },
                            ),
                            StudioListItem(
                                icon = StudioRowIcons.Web,
                                label = "Web",
                                onClick = {
                                    searchUi.showFallback = true
                                    onShowSubtitleBloomChanged(false)
                                    onStudioCategoryChanged(null)
                                },
                            ),
                            StudioListItem(
                                icon = StudioRowIcons.SmartSearch,
                                label = "Smart search",
                                onClick = {
                                    searchUi.showSearch = true
                                    onShowSubtitleBloomChanged(false)
                                    onStudioCategoryChanged(null)
                                },
                            ),
                            StudioListItem(
                                icon = StudioRowIcons.AutoDownload,
                                label = "Auto download",
                                toggledOn =
                                    coreUi.behaviorPrefs.autoDownloadWhenMissing,
                                onClick = {
                                    coreUi.behaviorPrefs =
                                        coreUi.behaviorPrefs.copy(
                                            autoDownloadWhenMissing =
                                                !coreUi.behaviorPrefs
                                                    .autoDownloadWhenMissing
                                        )
                                    saveSubtitleBehaviorPrefs(
                                        context,
                                        coreUi.behaviorPrefs,
                                    )
                                },
                            ),
                        ),
                )

            StudioCategory.POWER_TOOLS ->
                StudioListWindow(
                    title = "Power tools",
                    onBack = { onStudioCategoryChanged(null) },
                    containerSize = containerPx,
                    initialOffset = windowOffset,
                    items =
                        listOf(
                            StudioListItem(
                                icon = StudioRowIcons.SpeechToSubs,
                                label = "Speech to subs",
                                onClick = {
                                    onShowSpeechSubtitlePanelChanged(true)
                                    onShowSubtitleBloomChanged(false)
                                    onStudioCategoryChanged(null)
                                },
                            ),
                            StudioListItem(
                                icon = StudioRowIcons.AiTranslate,
                                label = "AI translate",
                                onClick = {
                                    onShowSubtitleTranslationPanelChanged(true)
                                    onShowSubtitleBloomChanged(false)
                                    onStudioCategoryChanged(null)
                                },
                            ),
                            StudioListItem(
                                icon = StudioRowIcons.AutoSync,
                                label = "Auto sync",
                                onClick = {
                                    autoSyncCoordinator.runAutoSync()
                                    onShowSubtitleBloomChanged(false)
                                    onStudioCategoryChanged(null)
                                },
                            ),
                            StudioListItem(
                                icon = StudioRowIcons.DialogueSync,
                                label = "Dialogue sync",
                                onClick = {
                                    subtitleSyncTools.armDialogueSync()
                                    onShowSubtitleBloomChanged(false)
                                    onStudioCategoryChanged(null)
                                },
                            ),
                            StudioListItem(
                                icon = StudioRowIcons.DriftSync,
                                label = "Drift sync",
                                onClick = {
                                    driftUi.showDialog = true
                                    onShowSubtitleBloomChanged(false)
                                    onStudioCategoryChanged(null)
                                },
                            ),
                            StudioListItem(
                                icon = StudioRowIcons.DualSubs,
                                label = "Dual subs",
                                onClick = {
                                    onShowDualSubsWindowChanged(true)
                                    onShowSubtitleBloomChanged(false)
                                    onStudioCategoryChanged(null)
                                },
                            ),
                        ),
                )

            else -> Unit
        }
    }

    if (showSubtitleBehaviourWindow && !blockedByPlayerMode) {
        val density = LocalDensity.current
        val containerPx = with(density) {
            androidx.compose.ui.unit.IntSize(
                containerWidth.roundToPx(),
                containerHeight.roundToPx(),
            )
        }

        SubtitleBehaviourWindow(
            prefs = coreUi.behaviorPrefs,
            onChange = {
                coreUi.behaviorPrefs = it
                saveSubtitleBehaviorPrefs(context, it)
                studioUi.menuTouchKey++
            },
            cleaningOptions = coreUi.cleaningOptions,
            onCleaningOptionsChange = {
                coreUi.cleaningOptions = it
                saveSubtitleCleaningOptions(context, it)
                studioUi.menuTouchKey++
            },
            onBack = {
                onShowSubtitleBehaviourWindowChanged(false)
                onShowSubtitleBloomChanged(true)
                onStudioCategoryChanged(null)
            },
            containerSize = containerPx,
            initialOffset = with(density) {
                val margin =
                    if (containerWidth < 700.dp) 10.dp else 18.dp
                val width =
                    if (containerWidth < 700.dp) 290.dp else 330.dp
                val top =
                    if (containerHeight < 420.dp) 10.dp else 16.dp
                Offset(
                    (containerWidth - width - margin)
                        .coerceAtLeast(margin)
                        .toPx(),
                    top.toPx(),
                )
            },
            onUserInteraction = {
                studioUi.menuTouchKey++
            },
        )
    }

    if (showDualSubsWindow && !blockedByPlayerMode) {
        val density = LocalDensity.current
        val containerPx = with(density) {
            androidx.compose.ui.unit.IntSize(
                containerWidth.roundToPx(),
                containerHeight.roundToPx(),
            )
        }
        val languages = SubtitleLanguageRegistry.allLanguages()

        DualSubsWindow(
            enabled = dualUi.enabled,
            onEnabledChange = { enabled ->
                dualUi.enabled = enabled
                if (enabled) {
                    subtitleSyncTools.fetchAndApplyDualSecondary()
                } else {
                    subtitleSyncTools.disableDualSubtitles()
                }
                studioUi.menuTouchKey++
            },
            canEnable = trackUi.primaryUri != null,
            primaryLabel = quickHudFileName ?: "None",
            secondaryLanguage = dualUi.secondaryLanguage,
            secondaryLanguageLabel =
                languages
                    .firstOrNull {
                        it.first == dualUi.secondaryLanguage
                    }
                    ?.second
                    ?: dualUi.secondaryLanguage.uppercase(),
            onSecondaryLanguageChange = { lang ->
                onPendingDualAiLanguageChanged(null)
                dualUi.secondaryLanguage = lang
                coreUi.behaviorPrefs =
                    coreUi.behaviorPrefs.copy(
                        dualSecondaryLanguage = lang
                    )
                saveSubtitleBehaviorPrefs(
                    context,
                    coreUi.behaviorPrefs,
                )
                if (dualUi.enabled) {
                    subtitleSyncTools.fetchAndApplyDualSecondary()
                }
                studioUi.menuTouchKey++
            },
            availableLanguages = languages,
            gapLines = dualUi.gapLines,
            onGapLinesChange = { gap ->
                dualUi.gapLines = gap
                if (dualUi.enabled) {
                    subtitleSyncTools.fetchAndApplyDualSecondary()
                }
                studioUi.menuTouchKey++
            },
            secondaryColorHex = dualSecondaryColorHex,
            onSecondaryColorChange = { color ->
                onDualSecondaryColorHexChanged(color)
                if (dualUi.enabled) {
                    subtitleSyncTools.fetchAndApplyDualSecondary()
                }
                studioUi.menuTouchKey++
            },
            onUserInteraction = {
                studioUi.menuTouchKey++
            },
            statusText = dualUi.statusText,
            secondarySourceLabel = dualUi.secondarySourceLabel,
            onBack = {
                onShowDualSubsWindowChanged(false)
                onShowSubtitleBloomChanged(true)
            },
            containerSize = containerPx,
            initialOffset = with(density) {
                val margin =
                    if (containerWidth < 700.dp) 10.dp else 18.dp
                val width =
                    if (containerWidth < 700.dp) 240.dp else 270.dp
                val top =
                    if (containerHeight < 420.dp) 10.dp else 16.dp
                Offset(
                    (containerWidth - width - margin)
                        .coerceAtLeast(margin)
                        .toPx(),
                    top.toPx(),
                )
            },
        )
    }
}
