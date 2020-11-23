package com.infinitysolutions.notessync.noteedit

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.infinitysolutions.notessync.model.*
import com.infinitysolutions.notessync.repository.NotesRepository
import java.io.File
import java.util.*

class NoteEditDatabaseViewModel(application: Application) : AndroidViewModel(application) {
    private val notesDao: NotesDao = NotesRoomDatabase.getDatabase(application).notesDao()
    private val imagesDao: ImagesDao = NotesRoomDatabase.getDatabase(application).imagesDao()
    private val repository: NotesRepository = NotesRepository(notesDao)

    fun insertImage(): ImageData{
        Log.d("DBVM", "Insert Image called")
        val path = getApplication<Application>().applicationContext.filesDir.toString()

        val time = Calendar.getInstance().timeInMillis
        val file = File(path, "$time.jpg")
        val imageData = ImageData(null, file.absolutePath, time, time, null)
        imageData.imageId = imagesDao.insert(imageData)
        return imageData
    }

    fun deleteImage(id: Long, path: String) {
        val file = File(path)
        if (file.exists())
            file.delete()
        imagesDao.deleteImageById(id)
    }

    fun getNoteById(nId: Long): Note = notesDao.getNoteById(nId)
}