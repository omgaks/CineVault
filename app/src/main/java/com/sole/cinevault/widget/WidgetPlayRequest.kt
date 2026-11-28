package com.sole.cinevault.widget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A film tapped on the home-screen widget, waiting for the app to open it. */
object WidgetPlayRequest {
    const val ACTION = "com.sole.cinevault.WIDGET_PLAY"
    const val EXTRA_PATH = "widget_video_path"

    const val ACTION_TAB = "com.sole.cinevault.WIDGET_TAB"
    const val EXTRA_TAB = "widget_tab"

    var pendingPath: String? by mutableStateOf(null)
    var pendingTab: Int? by mutableStateOf(null)
}
