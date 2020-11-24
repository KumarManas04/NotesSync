package com.infinitysolutions.notessync.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.TaskStackBuilder
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_THEME
import com.infinitysolutions.notessync.contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.contracts.Contract.Companion.WIDGET_BUTTON_EXTRA
import com.infinitysolutions.notessync.contracts.Contract.Companion.WIDGET_NEW_IMAGE
import com.infinitysolutions.notessync.contracts.Contract.Companion.WIDGET_NEW_LIST
import com.infinitysolutions.notessync.contracts.Contract.Companion.WIDGET_NEW_NOTE
import com.infinitysolutions.notessync.home.MainActivity
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.noteedit.NoteEditActivity

class NotesWidget : AppWidgetProvider() {

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
        if (intent != null && intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)){
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            if (context != null) {
                this.onUpdate(context, AppWidgetManager.getInstance(context), ids)
            }
        }else {
            super.onReceive(context, intent)
        }
    }

    companion object {

        internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
            val layout = when(prefs.getInt(PREF_THEME, Contract.THEME_DEFAULT)){
                Contract.THEME_DEFAULT -> R.layout.notes_widget
                Contract.THEME_DARK -> R.layout.notes_widget_dark
                Contract.THEME_AMOLED -> R.layout.notes_widget_amoled
                else -> R.layout.notes_widget
            }
            val remoteViews = RemoteViews(context.packageName, layout)

            val newNoteIntent = Intent(context, MainActivity::class.java)
            newNoteIntent.putExtra(WIDGET_BUTTON_EXTRA, WIDGET_NEW_NOTE)
            val newNotePendingIntent = PendingIntent.getActivity(context, 1, newNoteIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            remoteViews.setOnClickPendingIntent(R.id.new_note_button, newNotePendingIntent)

            val newListIntent = Intent(context, MainActivity::class.java)
            newListIntent.putExtra(WIDGET_BUTTON_EXTRA, WIDGET_NEW_LIST)
            val newListPendingIntent = PendingIntent.getActivity(context, 2, newListIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            remoteViews.setOnClickPendingIntent(R.id.new_list_button, newListPendingIntent)

            val newImageIntent = Intent(context, MainActivity::class.java)
            newImageIntent.putExtra(WIDGET_BUTTON_EXTRA, WIDGET_NEW_IMAGE)
            val newImagePendingIntent = PendingIntent.getActivity(context, 50, newImageIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            remoteViews.setOnClickPendingIntent(R.id.new_image_button, newImagePendingIntent)

            val adapterIntent = Intent(context, WidgetRemoteViewsService::class.java)
            remoteViews.setRemoteAdapter(R.id.notes_list_view, adapterIntent)
            val intentTemplate = Intent(context, NoteEditActivity::class.java)   // The intent for click on a note in widget's list
            intentTemplate.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            val pendingIntentTemplate = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intentTemplate)
                .getPendingIntent(3, PendingIntent.FLAG_UPDATE_CURRENT)
            remoteViews.setPendingIntentTemplate(R.id.notes_list_view, pendingIntentTemplate)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.notes_list_view)

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }
}

