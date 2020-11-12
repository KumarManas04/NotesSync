package com.infinitysolutions.notessync.model

data class NoteContent(
    var noteTitle: String?,
    var noteContent: String?,
    var noteColor: Int?,
    var noteType: Int?,
    var reminderTime: Long
)