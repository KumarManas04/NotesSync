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

    @Query("Select note_id, title, date_created,date_modified FROM notes_table ORDER BY date_modified DESC")
    fun getDisplayList(): LiveData<List<NoteDisplayItem>>

    @Query("SELECT * FROM notes_table where note_id LIKE :nId LIMIT 1")
    fun getNote(nId: Long): Note

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)
}