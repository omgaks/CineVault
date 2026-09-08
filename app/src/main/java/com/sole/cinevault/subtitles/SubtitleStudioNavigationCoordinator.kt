package com.sole.cinevault.subtitles

/**
 * Slice 22: owns routing between CineVault's subtitle dock / Sub Studio
 * categories and their standalone destinations.
 *
 * It deliberately holds no Compose state itself. VideoPlayerScreen remains
 * the owner of visibility/state; this class only centralises the routing
 * decisions that were previously embedded as local functions in the giant
 * player composable.
 */
class SubtitleStudioNavigationCoordinator(
    private val setActiveDockItem: (SubtitleDockItem?) -> Unit,
    private val setShowSubtitleDock: (Boolean) -> Unit,
    private val setTrackSelectorManageMode: (Boolean) -> Unit,
    private val setShowTrackSelector: (Boolean) -> Unit,
    private val setShowSubtitleBloom: (Boolean) -> Unit,
    private val setStudioCategory: (StudioCategory?) -> Unit,
    private val setShowAppearanceStudio: (Boolean) -> Unit,
    private val setShowSubtitleSearch: (Boolean) -> Unit,
    private val setShowSubtitleBehaviourWindow: (Boolean) -> Unit,
) {
    fun onDockItemTapped(item: SubtitleDockItem) {
        setActiveDockItem(item)
        setShowSubtitleDock(false)

        when (item) {
            SubtitleDockItem.TRACK -> {
                setTrackSelectorManageMode(false)
                setShowTrackSelector(true)
            }

            SubtitleDockItem.SYNC -> {
                setShowSubtitleBloom(true)
                setStudioCategory(StudioCategory.POWER_TOOLS)
            }

            SubtitleDockItem.STYLE -> {
                setShowAppearanceStudio(true)
            }

            SubtitleDockItem.SUPER_SUBS -> {
                setShowSubtitleSearch(true)
            }
        }
    }

    fun onStudioCategoryTapped(category: StudioCategory) {
        setStudioCategory(category)

        when (category) {
            StudioCategory.STYLE -> {
                setShowAppearanceStudio(true)
                setShowSubtitleBloom(false)
                setStudioCategory(null)
            }

            StudioCategory.SETTINGS -> {
                setShowSubtitleBehaviourWindow(true)
                setShowSubtitleBloom(false)
                setStudioCategory(null)
            }

            StudioCategory.DOWNLOAD,
            StudioCategory.POWER_TOOLS -> Unit
        }
    }

    fun closeSubtitleSurfaces(
        clearPendingImportCandidates: () -> Unit,
        setShowFallback: (Boolean) -> Unit,
        setShowEmbeddedBrowser: (Boolean) -> Unit,
        setShowDualSubsWindow: (Boolean) -> Unit,
    ) {
        setShowTrackSelector(false)
        setShowSubtitleSearch(false)
        setShowAppearanceStudio(false)
        setShowFallback(false)
        setShowEmbeddedBrowser(false)
        clearPendingImportCandidates()
        setShowSubtitleDock(false)
        setShowSubtitleBloom(false)
        setStudioCategory(null)
        setShowDualSubsWindow(false)
        setShowSubtitleBehaviourWindow(false)
    }
}
