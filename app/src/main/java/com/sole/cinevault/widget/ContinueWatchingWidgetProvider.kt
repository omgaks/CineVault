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
 * Home-screen widget: a stack of posters for the films you have started. Swipe to flip through,
 * tap to carry on where you stopped. Resizable; tall sizes also show a header.
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
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_stack)
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
            views.setRemoteAdapter(R.id.widget_stack, serviceIntent)
            views.setEmptyView(R.id.widget_stack, R.id.widget_empty)

            val openApp = Intent(context, MainActivity::class.java).apply {
                action = WidgetPlayRequest.ACTION
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            views.setPendingIntentTemplate(R.id.widget_stack, PendingIntent.getActivity(context, 0, openApp, flags))

            val launch = Intent(context, MainActivity::class.java)
            views.setOnClickPendingIntent(
                R.id.widget_empty,
                PendingIntent.getActivity(context, 1, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            manager.updateAppWidget(id, views)
        }
    }
}
