package com.sole.cinevault

/** What a TV-remote / keyboard key should do while a film is playing. */
internal enum class RemoteKeyAction { TOGGLE_PLAY, PLAY, PAUSE, SEEK_BACK, SEEK_FORWARD, SHOW_CONTROLS, NONE }

// Android key codes, kept as plain numbers so this stays testable without Android.
private const val KEY_DPAD_UP = 19
private const val KEY_DPAD_DOWN = 20
private const val KEY_DPAD_LEFT = 21
private const val KEY_DPAD_RIGHT = 22
private const val KEY_DPAD_CENTER = 23
private const val KEY_ENTER = 66
private const val KEY_SPACE = 62
private const val KEY_MEDIA_PLAY_PAUSE = 85
private const val KEY_MEDIA_NEXT = 87
private const val KEY_MEDIA_PREVIOUS = 88
private const val KEY_MEDIA_REWIND = 89
private const val KEY_MEDIA_FAST_FORWARD = 90
private const val KEY_MEDIA_PLAY = 126
private const val KEY_MEDIA_PAUSE = 127

internal const val REMOTE_SEEK_STEP_MS = 10_000L
internal const val REMOTE_SEEK_BIG_STEP_MS = 30_000L

/**
 * Media keys always work. The arrow keys and OK only act as playback keys while the
 * on-screen controls are hidden and no menu is open; otherwise they belong to the
 * controls and menus, so focus navigation keeps working.
 */
internal fun remoteKeyAction(keyCode: Int, controlsVisible: Boolean, menuOpen: Boolean): RemoteKeyAction {
    when (keyCode) {
        KEY_MEDIA_PLAY_PAUSE -> return RemoteKeyAction.TOGGLE_PLAY
        KEY_MEDIA_PLAY -> return RemoteKeyAction.PLAY
        KEY_MEDIA_PAUSE -> return RemoteKeyAction.PAUSE
        KEY_MEDIA_REWIND, KEY_MEDIA_PREVIOUS -> return RemoteKeyAction.SEEK_BACK
        KEY_MEDIA_FAST_FORWARD, KEY_MEDIA_NEXT -> return RemoteKeyAction.SEEK_FORWARD
    }
    if (menuOpen || controlsVisible) return RemoteKeyAction.NONE
    return when (keyCode) {
        KEY_DPAD_CENTER, KEY_ENTER, KEY_SPACE -> RemoteKeyAction.TOGGLE_PLAY
        KEY_DPAD_LEFT -> RemoteKeyAction.SEEK_BACK
        KEY_DPAD_RIGHT -> RemoteKeyAction.SEEK_FORWARD
        KEY_DPAD_UP, KEY_DPAD_DOWN -> RemoteKeyAction.SHOW_CONTROLS
        else -> RemoteKeyAction.NONE
    }
}

/** Big skips for the dedicated rewind / fast-forward keys, small ones for the arrows. */
internal fun remoteSeekStepMs(keyCode: Int): Long =
    if (keyCode == KEY_MEDIA_REWIND || keyCode == KEY_MEDIA_FAST_FORWARD ||
        keyCode == KEY_MEDIA_PREVIOUS || keyCode == KEY_MEDIA_NEXT
    ) REMOTE_SEEK_BIG_STEP_MS else REMOTE_SEEK_STEP_MS
