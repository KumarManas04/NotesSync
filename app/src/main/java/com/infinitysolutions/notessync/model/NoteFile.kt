package com.infinitysolutions.notessync.model

data class NoteFile(
    var nId: Long? = null,
    var dateCreated: Long,
    var dateModified: Long,
    var gDriveId: String?
)