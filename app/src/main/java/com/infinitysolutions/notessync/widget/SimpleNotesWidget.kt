package com.infinitysolutions.notessync.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.widget.RemoteViews
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.home.MainActivity

class SimpleNotesWidget: AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
    }

    override fun onDisabled(context: Context) {
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (intent != null && intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)) {
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            if (context != null) {
                onUpdate(context, AppWidgetManager.getInstance(context), ids)
            }
        }
    }

    companion object {

        internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
            val layout = when(prefs.getInt(Contract.PREF_THEME, Contract.THEME_DEFAULT)){
                Contract.THEME_DEFAULT -> R.layout.simple_widget
                Contract.THEME_DARK -> R.layout.simple_widget_dark
                Contract.THEME_AMOLED -> R.layout.simple_widget_amoled
                else -> R.layout.simple_widget
            }

            val remoteViews = RemoteViews(context.packageName, layout)

            val newNoteIntent = Intent(context, MainActivity::class.java)
            newNoteIntent.putExtra(Contract.WIDGET_BUTTON_EXTRA, Contract.WIDGET_NEW_NOTE)
            val newNotePendingIntent = PendingIntent.getActivity(context, 1, newNoteIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            remoteViews.setOnClickPendingIntent(R.id.new_note_button, newNotePendingIntent)

            val newListIntent = Intent(context, MainActivity::class.java)
            newListIntent.putExtra(Contract.WIDGET_BUTTON_EXTRA, Contract.WIDGET_NEW_LIST)
            val newListPendingIntent = PendingIntent.getActivity(context, 2, newListIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            remoteViews.setOnClickPendingIntent(R.id.new_list_button, newListPendingIntent)

            val newImageIntent = Intent(context, MainActivity::class.java)
            newImageIntent.putExtra(Contract.WIDGET_BUTTON_EXTRA, Contract.WIDGET_NEW_IMAGE)
            val newImagePendingIntent = PendingIntent.getActivity(context, 50, newImageIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            remoteViews.setOnClickPendingIntent(R.id.new_image_button, newImagePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }
}