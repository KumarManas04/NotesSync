package com.infinitysolutions.notessync.Repository

import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Model.NotesDao

class NotesRepository(private val notesDao: NotesDao){
    val allList: LiveData<List<Note>> = notesDao.getAll()
    val notesList: LiveData<List<Note>> = notesDao.getNotesOnly()
    val todoList: LiveData<List<Note>> = notesDao.getListsOnly()
    val archiveList : LiveData<List<Note>> = notesDao.getArchived()

    @WorkerThread
    suspend fun insert(note: Note){
        notesDao.insert(note)
    }
}