package com.infinitysolutions.notessync.Contracts

class Contract{
    companion object {
        const val NOTE_DELETED = 0
        const val NOTE_DEFAULT = 1
        const val NOTE_ARCHIVED = 2
        const val LIST_DEFAULT = 3
        const val LIST_ARCHIVED = 4
        const val NOTE_TRASH = 5
        const val LIST_TRASH = 6
        const val IMAGE_DEFAULT = 7
        const val IMAGE_ARCHIVED = 8
        const val IMAGE_TRASH = 9
        const val IMAGE_DELETED = 10
        const val IMAGE_LIST_DEFAULT = 11
        const val IMAGE_LIST_ARCHIVED = 12
        const val IMAGE_LIST_TRASH = 13
        const val FILE_TYPE_FOLDER = "application/vnd.google-apps.folder"
        const val FILE_TYPE_TEXT = "text/plain"
        const val DRIVE_EXTRA = "DriveExtra"
        const val SYNC_INDICATOR_EXTRA = "syncIndicatorExtra"
        const val NOTE_ID_EXTRA = "noteIdExtra"
        const val CLOUD_GOOGLE_DRIVE = 0
        const val CLOUD_DROPBOX = 1
        const val SHARED_PREFS_NAME = "com.infinitySolutions.notesSync.sharedPrefs"
        const val PREF_THEME = "sharedPrefs.currentTheme"
        const val PREF_COMPACT_VIEW_MODE_ENABLED = "sharedPrefs.isCompactViewModeEnabled"
        const val THEME_DEFAULT = 0
        const val THEME_DARK = 1
        const val THEME_AMOLED = 2
        const val PREF_CLOUD_TYPE = "sharedPrefs.cloudType"
        const val PREF_ACCESS_TOKEN = "sharedPrefs.accessToken"
        const val PREF_ID = "sharedPrefs.userId"
        const val PREF_CODE = "sharedPrefs.code"
        const val PREF_APP_LOCK_CODE = "sharedPrefs.appLockCode"
        const val PREF_ENCRYPTED = "sharedPrefs.encryptedStatus"
        const val FILE_SYSTEM_FILENAME = "notes_files_system.txt"
        const val CREDENTIALS_FILENAME = "credentials.txt"
        const val AUTO_SYNC_WORK_ID = "autoSync"
        const val AUTO_DELETE_WORK_ID = "autoDelete"
        const val PREF_LAST_SYNCED_TIME = "lastSyncedTime"
        const val PREF_IS_AUTO_SYNC_ENABLED = "isAutoSyncEnabled"
        const val WIDGET_BUTTON_EXTRA = "widgetButtonPressed"
        const val WIDGET_NEW_NOTE = "newNoteButtonPressed"
        const val WIDGET_NEW_LIST = "newListButtonPressed"
        const val WIDGET_NEW_IMAGE = "newImageButtonPressed"
        const val PASSWORD_MODE = "changePasswordMode"
        const val MODE_CHANGE_PASSWORD = 0
        const val MODE_NEW_PASSWORD = 1
        const val MODE_LOGIN_TIME_PASSWORD = 2
        const val PASSWORD_CHANGE_OLD_INVALID = 0
        const val PASSWORD_CHANGE_SUCCESS = 1
        const val PASSWORD_CHANGE_NETWORK_ERROR = 2
        const val PASSWORD_VERIFY_INVALID = 0
        const val PASSWORD_VERIFY_CORRECT = 1
        const val PASSWORD_VERIFY_ERROR = 2
        const val ENCRYPTED_NO = 0
        const val ENCRYPTED_YES = 1
        const val ENCRYPTED_CHECK_ERROR = 2
        const val APP_LOCK_STATE = "appLockState"
        const val STATE_NEW_PIN = 0
        const val STATE_CHANGE_PIN = 1
        const val IMAGE_CAPTURE_REQUEST_CODE = 1010
        const val IMAGE_PICKER_REQUEST_CODE = 1020
        const val FILE_PROVIDER_AUTHORITY = "com.infinitysolutions.notessync.fileprovider"
    }
}