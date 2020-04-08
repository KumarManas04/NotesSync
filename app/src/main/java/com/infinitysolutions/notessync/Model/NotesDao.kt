package com.infinitysolutions.notessync.Model

import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery

@Dao
interface NotesDao {

    @RawQuery(observedEntities = [Note::class])
    fun getNotes(query: SimpleSQLiteQuery): LiveData<List<Note>>

    @RawQuery(observedEntities = [Note::class])
    fun getArchived(query: SimpleSQLiteQuery): LiveData<List<Note>>

    @RawQuery(observedEntities = [Note::class])
    fun getTrash(query: SimpleSQLiteQuery): LiveData<List<Note>>

    @Query("SELECT * FROM notes_table WHERE type = 1 OR type = 3 OR type = 7 OR type = 11 ORDER BY date_modified DESC")
    fun getAllPresent(): List<Note>

    @Query("SELECT * FROM notes_table WHERE type = 5 OR type = 6 OR type = 9 OR type = 13 ORDER BY date_modified DESC")
    fun getTrashPresent(): List<Note>

    @Query("SELECT * FROM notes_table WHERE note_id = :nId LIMIT 1")
    fun getNoteById(nId: Long): Note

    @Query("SELECT * FROM notes_table WHERE note_id in (:idList)")
    fun getNotesByIds(idList: List<Long>): List<Note>

    @RawQuery(observedEntities = [Note::class])
    fun getSearchResult(query: SimpleSQLiteQuery): LiveData<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun simpleInsert(note: Note): Long

    @Query("DELETE FROM notes_table WHERE note_id = :noteId")
    fun deleteNoteById(noteId: Long)

    @Query("SELECT date_modified FROM notes_table ORDER BY date_modified DESC LIMIT 1")
    fun getLastModifiedTime(): Long

    @Query("UPDATE  notes_table SET g_drive_id = :gDriveId , synced = :synced WHERE note_id = :noteId")
    fun updateSyncedState(noteId: Long, gDriveId: String, synced: Boolean)

    @Query("UPDATE notes_table SET content = :noteContent WHERE note_id = :noteId")
    fun updateNoteContent(noteId: Long, noteContent: String)

    @Query("SELECT note_id FROM notes_table")
    fun getAllIds(): List<Long>
}