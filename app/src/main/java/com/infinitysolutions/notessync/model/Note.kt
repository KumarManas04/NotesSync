package com.infinitysolutions.notessync.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes_table")
data class Note(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "note_id") var nId: Long? = null,
    @ColumnInfo(name = "title") var noteTitle: String?,
    @ColumnInfo(name = "content") var noteContent: String?,
    @ColumnInfo(name = "date_created") var dateCreated: Long,
    @ColumnInfo(name = "date_modified") var dateModified: Long,
    @ColumnInfo(name = "g_drive_id") var gDriveId: String?,
    @ColumnInfo(name = "type") var noteType: Int,
    @ColumnInfo(name = "synced") var synced: Boolean,
    @ColumnInfo(name = "color") var noteColor: Int?,
    @ColumnInfo(name = "reminder_time") var reminderTime: Long
)