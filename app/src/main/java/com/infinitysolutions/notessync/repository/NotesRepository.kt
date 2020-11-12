package com.infinitysolutions.notessync.repository

import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.sqlite.db.SimpleSQLiteQuery
import com.infinitysolutions.notessync.model.Note
import com.infinitysolutions.notessync.model.NotesDao

class NotesRepository(private val notesDao: NotesDao){

    fun getNotesList(orderBy: String, order: String): LiveData<List<Note>>{
        val query = SimpleSQLiteQuery("SELECT * FROM notes_table WHERE type = 1 OR type = 3 OR type = 7 OR type = 11 ORDER BY $orderBy $order")
        return notesDao.getNotes(query)
    }

    fun getArchiveList(orderBy: String, order: String): LiveData<List<Note>>{
        val query = SimpleSQLiteQuery("SELECT * FROM notes_table WHERE type = 2 OR type = 4 OR type = 8 OR type = 12 ORDER BY $orderBy $order")
        return notesDao.getArchived(query)
    }

    fun getTrashList(orderBy: String, order: String): LiveData<List<Note>>{
        val query = SimpleSQLiteQuery("SELECT * FROM notes_table WHERE type = 5 OR type = 6 OR type = 9 OR type = 13 ORDER BY $orderBy $order")
        return notesDao.getTrash(query)
    }

    @WorkerThread
    suspend fun insert(note: Note): Long{
        return notesDao.insert(note)
    }
}