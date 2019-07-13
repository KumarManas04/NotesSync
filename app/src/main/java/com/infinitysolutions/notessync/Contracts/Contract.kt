package com.infinitysolutions.notessync.Contracts

class Contract{
    companion object {
        const val NOTE_DELETED = 0
        const val NOTE_DEFAULT = 1
        const val NOTE_ARCHIVED = 2
        const val LIST_DEFAULT = 3
        const val LIST_ARCHIVED = 4
        const val FILE_TYPE_FOLDER = "application/vnd.google-apps.folder"
        const val FILE_TYPE_TEXT = "text/plain"
        const val SHARED_PREFS_NAME = "com.infinitySolutions.notesSync.sharedPrefs"
        const val PREF_THEME = "sharedPrefs.currentTheme"
    }
}