package com.infinitysolutions.notessync.Model

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotesDao {

    @Query("SELECT * FROM notes_table ORDER BY date_modified DESC")
    fun getAll(): LiveData<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)

    @Query("DELETE FROM notes_table WHERE note_id = :noteId")
    suspend fun deleteNoteById(noteId: Long)
}