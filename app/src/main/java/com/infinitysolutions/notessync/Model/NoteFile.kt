package com.infinitysolutions.notessync.Model

data class NoteFile(
    var nId: Long? = null,
    var dateCreated: Long,
    var dateModified: Long,
    var gDriveId: String?
)