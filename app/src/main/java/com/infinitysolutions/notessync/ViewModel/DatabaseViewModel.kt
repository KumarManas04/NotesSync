package com.infinitysolutions.notessync.ViewModel

import android.app.Application
import androidx.lifecycle.*
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Model.NotesDao
import com.infinitysolutions.notessync.Model.NotesRoomDatabase
import com.infinitysolutions.notessync.Repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DatabaseViewModel(application: Application) : AndroidViewModel(application) {
    private val query = MutableLiveData<String>()
    private val viewMode = MutableLiveData<Int>()
    private val notesDao: NotesDao = NotesRoomDatabase.getDatabase(application).notesDao()
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
}