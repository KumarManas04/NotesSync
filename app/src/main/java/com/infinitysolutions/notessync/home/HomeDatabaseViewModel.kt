package com.infinitysolutions.notessync.home

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.util.Log
import androidx.lifecycle.*
import androidx.sqlite.db.SimpleSQLiteQuery
import com.google.gson.Gson
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.contracts.Contract.Companion.ORDER_BY_UPDATED
import com.infinitysolutions.notessync.contracts.Contract.Companion.ORDER_DESC
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ORDER
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ORDER_BY
import com.infinitysolutions.notessync.contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.model.*
import com.infinitysolutions.notessync.repository.NotesRepository
import com.infinitysolutions.notessync.util.WorkSchedulerHelper
import com.infinitysolutions.notessync.widget.NotesWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class HomeDatabaseViewModel(application: Application) : AndroidViewModel(application){
    private val query = MutableLiveData<SimpleSQLiteQuery>()
    private val viewMode = MutableLiveData<Int>()
    private var orderBy = ORDER_BY_UPDATED
    private var order = ORDER_DESC
    private val notesDao: NotesDao = NotesRoomDatabase.getDatabase(application).notesDao()
    private val imagesDao: ImagesDao = NotesRoomDatabase.getDatabase(application).imagesDao()
    private val repository: NotesRepository = NotesRepository(notesDao)

    val searchResultList: LiveData<List<Note>> = Transformations.switchMap(query) { searchQuery ->
        notesDao.getSearchResult(searchQuery)
    }

    val viewList: LiveData<List<Note>> = Transformations.switchMap(viewMode) { mode ->
        when (mode) {
            1 -> repository.getNotesList(orderBy, order)
            2 -> repository.getArchiveList(orderBy, order)
            3 -> repository.getTrashList(orderBy, order)
            else -> null
        }
    }

    init {
        query.value = SimpleSQLiteQuery("SELECT * FROM notes_table WHERE type != 0 AND type != 5 AND type != 6 AND type != 9 AND type != 10 AND type != 13")
        viewMode.value = 1
        val context = getApplication<Application>().applicationContext
        val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        orderBy = prefs.getString(
            PREF_ORDER_BY,
            ORDER_BY_UPDATED
        )?: ORDER_BY_UPDATED
        order = prefs.getString(PREF_ORDER, ORDER_DESC)?: ORDER_DESC
    }

    fun setSearchQuery(searchQuery: String) {
        val tempList = searchQuery.split("\\s+".toRegex())
        val list = ArrayList<String>()
        for(str in tempList){
            if(str.trim().isNotEmpty())
                list.add(str)
        }

        if(list.isEmpty()) {
            query.value = SimpleSQLiteQuery("SELECT * FROM notes_table WHERE type != 0 AND type != 5 AND type != 6 AND type != 9 AND type != 10 AND type != 13")
            return
        }
        val sB = StringBuilder("SELECT * FROM notes_table WHERE type != 0 AND type != 5 AND type != 6 AND type != 9 AND type != 10 AND type != 13 AND (")
        list.forEachIndexed { index, str ->
            if (index != 0)
                sB.append(" OR ")
            sB.append("title LIKE '%$str%' OR content LIKE '%$str%'")
        }

        sB.append(")")
        val queryItem = SimpleSQLiteQuery(sB.toString())
        query.value = queryItem
    }

    fun deleteNote(note: Note) {
        val prefs = getApplication<Application>().applicationContext.getSharedPreferences(
            SHARED_PREFS_NAME,
            MODE_PRIVATE
        )
        val isLoggedIn = prefs != null && prefs.contains(Contract.PREF_CLOUD_TYPE)
        val noteType = if (note.noteType == Contract.IMAGE_TRASH || note.noteType == Contract.IMAGE_LIST_TRASH) {
            val imageNoteContent = Gson().fromJson(note.noteContent, ImageNoteContent::class.java)
            deleteImagesByIds(imageNoteContent.idList)
            Contract.IMAGE_DELETED
        } else {
            Contract.NOTE_DELETED
        }
        if(isLoggedIn)
            changeNoteType(note, noteType)
        else {
            GlobalScope.launch(Dispatchers.IO) {
                notesDao.deleteNoteById(note.nId!!)
            }
        }
    }

    private fun deleteImagesByIds(idList: ArrayList<Long>) {
        GlobalScope.launch(Dispatchers.IO) {
            val images = getImagesByIds(idList)
            for (image in images)
                deleteImage(image.imageId!!, image.imagePath)
        }
    }

    private fun deleteImage(id: Long, path: String) {
        val file = File(path)
        if (file.exists())
            file.delete()
        imagesDao.deleteImageById(id)
    }

    private fun changeNoteType(note: Note, noteType: Int) {
        insert(
            Note(
                note.nId,
                note.noteTitle,
                note.noteContent,
                note.dateCreated,
                Calendar.getInstance().timeInMillis,
                note.gDriveId,
                noteType,
                note.synced,
                note.noteColor,
                note.reminderTime
            )
        )
    }

    fun insert(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            val noteId = repository.insert(note)
            withContext(Dispatchers.Main) {
                val context = getApplication<Application>().applicationContext
                updateWidgets(context)
                val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
                var set = prefs.getStringSet(Contract.PREF_SYNC_QUEUE, null)
                if(set == null)
                    set = hashSetOf(noteId.toString())
                else
                    set.add(noteId.toString())

                val editor = prefs.edit()
                editor.putStringSet(Contract.PREF_SYNC_QUEUE, set)
                editor.commit()
                WorkSchedulerHelper().syncNotes(false, context)
            }
        }
    }

    fun setOrder(order: String, orderBy: String){
        this.order = order
        this.orderBy = orderBy
        val vM = viewMode.value
        viewMode.value = vM
    }

    fun setViewMode(mode: Int) {
        viewMode.value = mode
    }

    private fun updateWidgets(context: Context) {
        val intent = Intent(context, NotesWidget::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, NotesWidget::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }

    private fun getImagesByIds(idList: ArrayList<Long>): ArrayList<ImageData> = ArrayList(imagesDao.getImagesByIds(idList))
    fun getImagePathById(id: Long): String = imagesDao.getImagePathById(id)
}