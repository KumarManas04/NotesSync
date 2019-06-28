package com.infinitysolutions.notessync.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Model.NotesDao
import com.infinitysolutions.notessync.Model.NotesRoomDatabase
import com.infinitysolutions.notessync.Repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DatabaseViewModel(application: Application) : AndroidViewModel(application) {
    var notesList: LiveData<List<Note>>
    var archivesList: LiveData<List<Note>>
    private val repository: NotesRepository
    private val notesDao: NotesDao = NotesRoomDatabase.getDatabase(application).notesDao()

    init {
        repository = NotesRepository(notesDao)
        notesList = repository.notesList
        archivesList = repository.archiveList
    }

    fun insert(note: Note) = viewModelScope.launch(Dispatchers.IO){
        repository.insert(note)
    }
}