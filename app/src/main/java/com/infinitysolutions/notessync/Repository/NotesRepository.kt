package com.infinitysolutions.notessync.Repository

import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Model.NoteDisplayItem
import com.infinitysolutions.notessync.Model.NotesDao

class NotesRepository(private val notesDao: NotesDao){
    val notesDisplayList: LiveData<List<NoteDisplayItem>> = notesDao.getDisplayList()

    @WorkerThread
    suspend fun insert(note: Note){
        notesDao.insert(note)
    }
}