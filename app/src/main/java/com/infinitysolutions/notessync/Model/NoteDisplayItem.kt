package com.infinitysolutions.notessync.Model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class NoteDisplayItem(
    @PrimaryKey @ColumnInfo(name = "note_id") var nId: Long? = null,
    @ColumnInfo(name = "title") val noteTitle: String?,
    @ColumnInfo(name = "date_created") val dateCreated: Long,
    @ColumnInfo(name = "date_modified") val dateModified: Long
)