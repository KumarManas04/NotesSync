package com.infinitysolutions.notessync.model

data class ImageNoteContent(
    var noteContent: String?,
    var idList: ArrayList<Long> = ArrayList()
)