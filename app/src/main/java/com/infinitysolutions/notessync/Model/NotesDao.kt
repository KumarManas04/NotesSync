package com.infinitysolutions.notessync.Model

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotesDao {

    @Query("SELECT * FROM notes_table WHERE type = 1 OR type = 3 ORDER BY date_modified DESC")
    fun getAll(): LiveData<List<Note>>

    @Query("SELECT * FROM notes_table WHERE type = 1 OR type = 3 ORDER BY date_modified DESC")
    fun getAllPresent(): List<Note>

    @Query("SELECT * FROM notes_table WHERE type = 1 ORDER BY date_modified DESC")
    fun getNotesOnly(): LiveData<List<Note>>

    @Query("SELECT * FROM notes_table WHERE type = 3 ORDER BY date_modified DESC")
    fun getListsOnly(): LiveData<List<Note>>

    @Query("SELECT * FROM notes_table WHERE type = 2 OR type = 4 ORDER BY date_modified DESC")
    fun getArchived(): LiveData<List<Note>>

    @Query("SELECT * FROM notes_table WHERE note_id = :nId LIMIT 1")
    fun getNoteById(nId: Long): Note

    @Query("SELECT * FROM notes_table WHERE type != 0 AND (title LIKE:query OR content LIKE:query)")
    fun getSearchResult(query: String): LiveData<List<Note>>

    @Query("SELECT * FROM notes_table ORDER BY note_id ASC")
    suspend fun getCurrentData(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)

    @Query("DELETE FROM notes_table WHERE note_id = :noteId")
    suspend fun deleteNoteById(noteId: Long)
}