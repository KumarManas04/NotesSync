package com.infinitysolutions.notessync.Adapters

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.infinitysolutions.notessync.Contracts.Contract
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_ID_EXTRA
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Model.NotesRoomDatabase
import com.infinitysolutions.notessync.R

class WidgetRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private lateinit var notesList: List<Note>
    private var selectedLayout = R.layout.widget_notes_item

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, selectedLayout)
        rv.setTextViewText(R.id.title_text, notesList[position].noteTitle)
        rv.setTextViewText(R.id.content_preview_text, notesList[position].noteContent)

        val fillInIntent = Intent()
        fillInIntent.putExtra(NOTE_ID_EXTRA, notesList[position].nId)
        rv.setOnClickFillInIntent(R.id.list_item_container, fillInIntent)
        return rv
    }

    override fun getCount(): Int {
        return notesList.size
    }

    override fun onCreate() {
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onDataSetChanged() {
        val prefs = context.getSharedPreferences(Contract.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(Contract.PREF_THEME)){
            selectedLayout = if (prefs.getInt(Contract.PREF_THEME, 0) == 1)
                    R.layout.widget_notes_item_dark
                else
                    R.layout.widget_notes_item
        }
        val notesDao = NotesRoomDatabase.getDatabase(context).notesDao()
        val identityToken = Binder.clearCallingIdentity()
        notesList = notesDao.getAllPresent()
        Binder.restoreCallingIdentity(identityToken)
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun getViewTypeCount(): Int {
        return 2
    }

    override fun onDestroy() {
    }
}