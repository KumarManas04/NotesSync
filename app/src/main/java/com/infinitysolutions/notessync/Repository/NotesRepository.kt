package com.infinitysolutions.notessync.Repository

import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Model.NotesDao

class NotesRepository(private val notesDao: NotesDao){

    fun getAllList(): LiveData<List<Note>>{
        return notesDao.getAll()
    }

    fun getNotesList(): LiveData<List<Note>>{
        return notesDao.getNotesOnly()
    }

    fun getTodoList(): LiveData<List<Note>>{
        return notesDao.getListsOnly()
    }

    fun getArchiveList(): LiveData<List<Note>>{
        return notesDao.getArchived()
    }

    fun getTrashList(): LiveData<List<Note>>{
        return notesDao.getTrash()
    }

    fun getImageNotesList(): LiveData<List<Note>>{
        return notesDao.getImageNotesOnly()
    }

    @WorkerThread
    suspend fun insert(note: Note){
        notesDao.insert(note)
    }
}