package com.infinitysolutions.notessync.adapters

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import android.util.SparseArray
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_LIST_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.LIST_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_ID_EXTRA
import com.infinitysolutions.notessync.model.ImageNoteContent
import com.infinitysolutions.notessync.model.Note
import com.infinitysolutions.notessync.model.NotesRoomDatabase
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.util.ChecklistConverter
import com.infinitysolutions.notessync.util.ColorsUtil
import java.io.File


class WidgetRemoteViewsFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {
    private lateinit var notesList: List<Note>
    private val colorsUtil = ColorsUtil()
    private val pathsMap = SparseArray<String>()

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_notes_item)
        val noteTitle = notesList[position].noteTitle
        if (noteTitle != null && noteTitle.isNotEmpty()){
            rv.setViewVisibility(R.id.title_text, VISIBLE)
            rv.setTextViewText(R.id.title_text, notesList[position].noteTitle)
        }else
            rv.setViewVisibility(R.id.title_text, GONE)

        if(notesList[position].noteType == IMAGE_DEFAULT || notesList[position].noteType == IMAGE_LIST_DEFAULT){
            rv.setViewVisibility(R.id.image_view, VISIBLE)
            val bitmap = Glide.with(context)
                .asBitmap()
                .load(File(pathsMap[position]))
                .submit(400, 400)
                .get()
            rv.setImageViewBitmap(R.id.image_view, bitmap)
        }else{
            rv.setViewVisibility(R.id.image_view, GONE)
        }
        var noteContent = notesList[position].noteContent
        if (noteContent != null) {
            if (notesList[position].noteType == LIST_DEFAULT && (noteContent.contains("[ ]") || noteContent.contains("[x]")))
                noteContent = ChecklistConverter.convertList(noteContent)
            if (notesList[position].noteType == IMAGE_DEFAULT || notesList[position].noteType == IMAGE_LIST_DEFAULT){
                val imageContent = Gson().fromJson(noteContent, ImageNoteContent::class.java)
                noteContent = imageContent.noteContent
            }
            if(noteContent != null && noteContent.isNotEmpty()) {
                rv.setViewVisibility(R.id.content_preview_text, VISIBLE)
                rv.setTextViewText(R.id.content_preview_text, noteContent)
            }else{
                rv.setViewVisibility(R.id.content_preview_text, GONE)
            }
        }

        rv.setInt(R.id.list_item_container, "setBackgroundColor", Color.parseColor(colorsUtil.getColor(notesList[position].noteColor)))
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
        val notesDao = NotesRoomDatabase.getDatabase(context).notesDao()
        val imagesDao = NotesRoomDatabase.getDatabase(context).imagesDao()
        val identityToken = Binder.clearCallingIdentity()
        notesList = notesDao.getAllPresent()
        var imageContent: ImageNoteContent
        var path: String
        for(i in notesList.indices){
            if(notesList[i].noteType == IMAGE_DEFAULT || notesList[i].noteType == IMAGE_LIST_DEFAULT){
                imageContent = Gson().fromJson(notesList[i].noteContent, ImageNoteContent::class.java)
                path = imagesDao.getImagePathById(imageContent.idList[0])
                pathsMap.put(i, path)
            }
        }
        Binder.restoreCallingIdentity(identityToken)
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun onDestroy() {}
}