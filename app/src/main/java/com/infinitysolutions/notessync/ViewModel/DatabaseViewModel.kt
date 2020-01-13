package com.infinitysolutions.notessync.ViewModel

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.*
import com.google.gson.Gson
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_DELETED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_LIST_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_LIST_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_LIST_TRASH
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_TRASH
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_TRASH
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DELETED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_TRASH
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_SYNC_QUEUE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.Fragments.NotesWidget
import com.infinitysolutions.notessync.Model.*
import com.infinitysolutions.notessync.Repository.NotesRepository
import com.infinitysolutions.notessync.Util.WorkSchedulerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class DatabaseViewModel(application: Application) : AndroidViewModel(application) {
    private val query = MutableLiveData<String>()
    private val viewMode = MutableLiveData<Int>()
    private val notesDao: NotesDao = NotesRoomDatabase.getDatabase(application).notesDao()
    private val imagesDao: ImagesDao = NotesRoomDatabase.getDatabase(application).imagesDao()
    private val repository: NotesRepository = NotesRepository(notesDao)
    val searchResultList: LiveData<List<Note>> = Transformations.switchMap(query) { searchQuery ->
        notesDao.getSearchResult(searchQuery)
    }
    val viewList: LiveData<List<Note>> = Transformations.switchMap(viewMode) { mode ->
        when (mode) {
            1 -> repository.getAllList()
            2 -> repository.getArchiveList()
            3 -> repository.getTrashList()
            else -> null
        }
    }

    init {
        query.value = "%%"
        viewMode.value = 1
    }

    fun setSearchQuery(searchQuery: String) {
        query.value = searchQuery
    }

    fun setViewMode(mode: Int) {
        viewMode.value = mode
    }

    fun insert(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(note)
            withContext(Dispatchers.Main) {
                val context = getApplication<Application>().applicationContext
                updateWidgets(context)
                val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
                var set = prefs.getStringSet(PREF_SYNC_QUEUE, null)
                if(set == null)
                   set = hashSetOf(note.nId.toString())
                else
                    set.add(note.nId.toString())

                val editor = prefs.edit()
                editor.putStringSet(PREF_SYNC_QUEUE, set)
                editor.commit()
                WorkSchedulerHelper().syncNotes(false)
            }
        }
    }

    fun getNoteById(nId: Long): Note {
        return notesDao.getNoteById(nId)
    }

    fun makeCopy(note: Note?, noteType: Int?, noteTitle: String, noteContent: String) {
        if(noteType != null && note != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val currentTime = Calendar.getInstance().timeInMillis
                when (noteType) {
                    IMAGE_DEFAULT, IMAGE_ARCHIVED, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED -> {
                        val imageNoteContent = Gson().fromJson(noteContent, ImageNoteContent::class.java)
                        val idsList = imageNoteContent.idList
                        val imageDataList = getImagesByIds(idsList)
                        var bitmap: Bitmap
                        var file: File
                        val newIdList = ArrayList<Long>()
                        for (imageData in imageDataList) {
                            file = File(imageData.imagePath)
                            bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            newIdList.add(insertImage(bitmap).imageId!!)
                        }
                        val newNoteContent = Gson().toJson(ImageNoteContent(imageNoteContent.noteContent, newIdList))
                        val newNote = Note(
                            null,
                            noteTitle,
                            newNoteContent,
                            currentTime,
                            currentTime,
                            "-1",
                            noteType,
                            false,
                            note.noteColor,
                            -1L
                        )
                        insert(newNote)
                    }
                    else -> {
                        val newNote = Note(
                            null,
                            noteTitle,
                            noteContent,
                            currentTime,
                            currentTime,
                            "-1",
                            noteType,
                            false,
                            note.noteColor,
                            -1L
                        )
                        insert(newNote)
                    }
                }
            }
        }
    }

    fun insertImage(image: Bitmap): ImageData {
        val path = getApplication<Application>().applicationContext.filesDir.toString()

        var imageBitmap: Bitmap? = null
        if(image.width > 1000 || image.height > 1000){
            val ratio = (maxOf(image.width, image.height)).toFloat() / 1000.0f
            val newWidth = (image.width * ratio).toInt()
            val newHeight = (image.height * ratio).toInt()
            imageBitmap = Bitmap.createScaledBitmap(image, newWidth, newHeight, false)
            image.recycle()
        }

        val time = Calendar.getInstance().timeInMillis
        val file = File(path, "$time.png")
        try {
            val fos = FileOutputStream(file)
            if(imageBitmap == null)
                image.compress(Bitmap.CompressFormat.PNG, 100, fos)
            else
                imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val imageData = ImageData(null, file.absolutePath, time, time, null)
        imageData.imageId = imagesDao.insert(imageData)
        return imageData
    }

    fun getImagesByIds(idList: ArrayList<Long>): ArrayList<ImageData> {
        return ArrayList(imagesDao.getImagesByIds(idList))
    }

    fun getImagePathById(id: Long): String {
        return imagesDao.getImagePathById(id)
    }

    fun deleteImage(id: Long, path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(path)
            if (file.exists())
                file.delete()
            imagesDao.deleteImageById(id)
        }
    }

    private fun deleteImagesByIds(idList: ArrayList<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            val images = getImagesByIds(idList)
            for (image in images)
                deleteImage(image.imageId!!, image.imagePath)
        }
    }

    fun deleteNote(note: Note) {
        if (note.noteType == IMAGE_TRASH || note.noteType == IMAGE_LIST_TRASH) {
            val imageNoteContent = Gson().fromJson(note.noteContent, ImageNoteContent::class.java)
            deleteImagesByIds(imageNoteContent.idList)
            changeNoteType(note, IMAGE_DELETED)
        } else if (note.noteType == NOTE_TRASH || note.noteType == LIST_TRASH) {
            changeNoteType(note, NOTE_DELETED)
        }
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

    private fun updateWidgets(context: Context) {
        val intent = Intent(context, NotesWidget::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, NotesWidget::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }
}