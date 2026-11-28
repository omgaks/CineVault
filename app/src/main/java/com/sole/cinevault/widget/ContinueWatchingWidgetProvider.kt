package com.sole.cinevault.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import com.sole.cinevault.MainActivity
import com.sole.cinevault.R

/**
 * Home-screen widget in the style of the Google TV one: CINEVAULT header with the logo, big cards for the
 * films you have started (scroll up and down, tap to carry on), and Home / Library buttons at the bottom.
 * Resizable; short sizes drop the buttons.
 */
class ContinueWatchingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        update(context, manager, id)
    }

    companion object {
        /** Asks every placed widget to reload its films. Cheap; safe to call often. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, ContinueWatchingWidgetProvider::class.java))
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        private fun tabIntent(context: Context, tab: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = WidgetPlayRequest.ACTION_TAB
                putExtra(WidgetPlayRequest.EXTRA_TAB, tab)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(context, 10 + tab, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val options = manager.getAppWidgetOptions(id)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val layout = if (heightDp >= 170) R.layout.widget_continue_watching_tall else R.layout.widget_continue_watching

            val views = RemoteViews(context.packageName, layout)
            val serviceIntent = Intent(context, ContinueWatchingWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            val openApp = Intent(context, MainActivity::class.java).apply {
                action = WidgetPlayRequest.ACTION
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            views.setPendingIntentTemplate(R.id.widget_list, PendingIntent.getActivity(context, 0, openApp, flags))

            val launch = Intent(context, MainActivity::class.java)
            val openAppIntent = PendingIntent.getActivity(context, 1, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_empty, openAppIntent)
            views.setOnClickPendingIntent(R.id.widget_logo, openAppIntent)
            if (layout == R.layout.widget_continue_watching_tall) {
                views.setOnClickPendingIntent(R.id.widget_btn_home, tabIntent(context, 0))
                views.setOnClickPendingIntent(R.id.widget_btn_library, tabIntent(context, 1))
            }
            manager.updateAppWidget(id, views)
        }
    }
}
