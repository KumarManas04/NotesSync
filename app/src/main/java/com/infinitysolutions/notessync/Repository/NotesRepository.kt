package com.infinitysolutions.notessync.Repository

import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Model.NotesDao

class NotesRepository(private val notesDao: NotesDao){
    val notesList: LiveData<List<Note>> = notesDao.getAll()
    val archiveList : LiveData<List<Note>> = notesDao.getArchived()

    @WorkerThread
    suspend fun insert(note: Note) {
        notesDao.insert(note)
    }
}