package com.infinitysolutions.notessync.ViewModel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Log
import androidx.lifecycle.*
import com.infinitysolutions.notessync.Model.*
import com.infinitysolutions.notessync.Repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val searchResultList: LiveData<List<Note>> = Transformations.switchMap(query){searchQuery->
        notesDao.getSearchResult(searchQuery)
    }
    val viewList: LiveData<List<Note>> = Transformations.switchMap(viewMode){mode->
        when (mode) {
            1 -> repository.getAllList()
            2 -> repository.getNotesList()
            3 -> repository.getTodoList()
            4 -> repository.getArchiveList()
            5 -> repository.getTrashList()
            6 -> repository.getImageNotesList()
            else -> null
        }
    }

    init {
        query.value = "%%"
        viewMode.value = 1
    }

    fun setSearchQuery(searchQuery: String){
        query.value = searchQuery
    }

    fun setViewMode(mode: Int){
        viewMode.value = mode
    }

    fun insert(note: Note){
        viewModelScope.launch(Dispatchers.IO){
            repository.insert(note)
        }
    }

    fun getNoteById(nId: Long): Note{
        return notesDao.getNoteById(nId)
    }

    fun insertImage(path: String, image: Bitmap): ImageData{
        val time = Calendar.getInstance().timeInMillis
        val file = File(path, "$time.png")
        try {
            val fos = FileOutputStream(file)
            image.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
        }catch (e: Exception){
            e.printStackTrace()
        }
        val imageData = ImageData(null, file.absolutePath, time, time, null)
        imageData.imageId = imagesDao.insert(imageData)
        return imageData
    }

    fun getImagesByIds(idList: ArrayList<Long>): ArrayList<ImageData>{
        return ArrayList(imagesDao.getImagesByIds(idList))
    }

    fun getImagePathById(id: Long): String{
        return imagesDao.getImagePathById(id)
    }

    fun deleteImage(id: Long, path: String){
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(path)
            if (file.exists())
                file.delete()
            imagesDao.deleteImageById(id)
        }
    }

    fun deleteImagesByIds(idList: ArrayList<Long>){
        viewModelScope.launch(Dispatchers.IO) {
            val images = getImagesByIds(idList)
            for(image in images)
                deleteImage(image.imageId!!, image.imagePath)
        }
    }
}