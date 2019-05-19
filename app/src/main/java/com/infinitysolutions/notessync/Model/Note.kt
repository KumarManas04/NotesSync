package com.infinitysolutions.notessync.Model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes_table")
data class Note(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "note_id") var nId: Long? = null,
    @ColumnInfo(name = "title") val noteTitle: String?,
    @ColumnInfo(name = "content") val noteContent: String?,
    @ColumnInfo(name = "date_created") val dateCreated: Long,
    @ColumnInfo(name = "date_modified") val dateModified: Long?,
    @ColumnInfo(name = "g_drive_id") val gDriveId: String?,
    @ColumnInfo(name = "o_drive_id") val oDriveId: String?
)