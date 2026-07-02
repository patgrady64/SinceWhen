package com.patgrady64.sincewhen

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class UpcomingWidgetProvider : AppWidgetProvider() {


    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_upcoming)

            // Connect the layout's ListView to our RemoteViewsService
            val intent = Intent(context, WidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widgetListView, intent)

            val appIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                appIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widgetListView, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)


    }

    companion object {
        // Helper function to force-refresh the data display inside the widget
        fun refreshWidget(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, UpcomingWidgetProvider::class.java)
            )

            // 1. Force dataset reload
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widgetListView)

            // 2. Force full widget redraw (IMPORTANT FIX)
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = ComponentName(context, UpcomingWidgetProvider::class.java)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }

            context.sendBroadcast(intent)
        }


    }
}