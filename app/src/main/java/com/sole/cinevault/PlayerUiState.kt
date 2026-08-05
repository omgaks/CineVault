package com.sole.cinevault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * PlayerUiState.kt
 *
 * State holders extracted out of VideoPlayerScreen.kt's top-of-composable
 * "wall" of ~100 `by remember { mutableStateOf(...) }` declarations. Each
 * holder groups one cohesive slice of state into its own small class —
 * see conversation history for why this is being done incrementally
 * (smallest/most self-contained cluster first) rather than all at once:
 * every read and write of every one of these variables throughout the
 * file needs updating to go through the holder, which is a much larger
 * and more error-prone surface than the earlier package-move and
 * overlay-wiring work, where the underlying code never changed at all.
 *
 * Usage in VideoPlayerScreen.kt: `val x = remember { XState() }`, then
 * every former `someVar` read/write becomes `x.someVar`.
 */

// First slice: the auto-subtitle-fetch flow (searching for and applying a
// subtitle automatically when a video with no existing match is opened).
// Genuinely self-contained — used from two LaunchedEffects and one status
// banner, nothing else in the file touches it.
class AutoSubtitleFetchState {
    var attemptedForPath by mutableStateOf<String?>(null)
    var status by mutableStateOf("")
    var downloadInProgress by mutableStateOf(false)
}
