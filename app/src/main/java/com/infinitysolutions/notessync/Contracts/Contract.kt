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
        const val DRIVE_EXTRA = "DriveExtra"
        const val NOTE_ID_EXTRA = "noteIdExtra"
        const val CLOUD_GOOGLE_DRIVE = 0
        const val CLOUD_DROPBOX = 1
        const val SHARED_PREFS_NAME = "com.infinitySolutions.notesSync.sharedPrefs"
        const val PREF_THEME = "sharedPrefs.currentTheme"
        const val PREF_CLOUD_TYPE = "sharedPrefs.cloudType"
        const val PREF_ACCESS_TOKEN = "sharedPrefs.accessToken"
        const val PREF_SCHEDULE_TIME = "sharedPrefs.scheduleTime"
        const val FILE_SYSTEM_FILENAME = "notes_files_system.txt"
        const val AUTO_SYNC_WORK_ID = "autoSync"
        const val WIDGET_BUTTON_EXTRA = "widgetButtonPressed"
        const val WIDGET_NEW_NOTE = "newNoteButtonPressed"
        const val WIDGET_NEW_LIST = "newListButtonPressed"
    }
}