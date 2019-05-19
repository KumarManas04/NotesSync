package com.infinitysolutions.notessync.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Model.NoteDisplayItem
import com.infinitysolutions.notessync.Model.NotesDao
import com.infinitysolutions.notessync.Model.NotesRoomDatabase
import com.infinitysolutions.notessync.Repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val notesDisplayList: LiveData<List<NoteDisplayItem>>
    private val repository: NotesRepository
    private val notesDao: NotesDao = NotesRoomDatabase.getDatabase(application).notesDao()

    init {
        repository = NotesRepository(notesDao)
        notesDisplayList = repository.notesDisplayList
    }

    fun insert(note: Note) = viewModelScope.launch(Dispatchers.IO){
        repository.insert(note)
    }

//    fun getNote(nId: Long){
//        notesDao.getNote(nId)
//    }
}