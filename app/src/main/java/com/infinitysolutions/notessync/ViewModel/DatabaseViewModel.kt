package com.infinitysolutions.notessync.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Model.NoteFile
import com.infinitysolutions.notessync.Model.NotesDao
import com.infinitysolutions.notessync.Model.NotesRoomDatabase
import com.infinitysolutions.notessync.Repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DatabaseViewModel(application: Application) : AndroidViewModel(application) {
    val notesList: LiveData<List<Note>>
    private val filesList = MutableLiveData<List<NoteFile>>()
    private val repository: NotesRepository
    private val notesDao: NotesDao = NotesRoomDatabase.getDatabase(application).notesDao()

    init {
        repository = NotesRepository(notesDao)
        notesList = repository.notesList
    }

    fun insert(note: Note) = viewModelScope.launch(Dispatchers.IO){
        repository.insert(note)
    }

    fun deleteNoteById(id: Long?){
        if (id != null) {
            viewModelScope.launch(Dispatchers.IO) { notesDao.deleteNoteById(id) }
        }
    }
}