package com.infinitysolutions.notessync.Model

data class ImageNoteContent(
    var noteContent: String?,
    val idList: ArrayList<String> = ArrayList()
)