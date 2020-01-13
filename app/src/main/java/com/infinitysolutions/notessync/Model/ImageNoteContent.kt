package com.infinitysolutions.notessync.Model

data class ImageNoteContent(
    var noteContent: String?,
    var idList: ArrayList<Long> = ArrayList()
)