package com.infinitysolutions.notessync.Services

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.http.OkHttp3Requestor
import com.dropbox.core.v2.DbxClientV2
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.gson.Gson
import com.infinitysolutions.notessync.Contracts.Contract
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.DRIVE_EXTRA
import com.infinitysolutions.notessync.Contracts.Contract.Companion.FILE_SYSTEM_FILENAME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.FILE_TYPE_FOLDER
import com.infinitysolutions.notessync.Contracts.Contract.Companion.FILE_TYPE_TEXT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DELETED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ACCESS_TOKEN
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_CODE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ENCRYPTED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ID
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_LAST_SYNCED_TIME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SYNC_INDICATOR_EXTRA
import com.infinitysolutions.notessync.Fragments.NotesWidget
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Model.NoteContent
import com.infinitysolutions.notessync.Model.NoteFile
import com.infinitysolutions.notessync.Model.NotesRoomDatabase
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.Util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.ArrayList

class NotesSyncService : Service() {
    private val TAG = "NotesSyncService"
    private lateinit var mNotesList: List<Note>
    private lateinit var googleDriveHelper: GoogleDriveHelper
    private lateinit var dropboxHelper: DropboxHelper
    private var mDriveType: Int = CLOUD_GOOGLE_DRIVE
    private var shouldIndicateSync = true
    private val aesHelper = AES256Helper()
    private var isEncrypted = false

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            mDriveType = intent.getIntExtra(DRIVE_EXTRA, CLOUD_GOOGLE_DRIVE)
            shouldIndicateSync = intent.getBooleanExtra(SYNC_INDICATOR_EXTRA, true)
        }
        startForegroundService()
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationHelper().getSyncNotification(this)
        startForeground(101, notification)
        getLocalData()
    }

    private fun getLocalData() {
        Log.d(TAG, "Initializing local fetch...")
        GlobalScope.launch(Dispatchers.IO) {
            val notesDao = NotesRoomDatabase.getDatabase(application).notesDao()
            val notesList = notesDao.getCurrentData()
            withContext(Dispatchers.Main) {
                mNotesList = notesList
                try {
                    getCloudData()
                } catch (e: Exception) {
                    if (shouldIndicateSync)
                        Toast.makeText(this@NotesSyncService, "Sync error", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Error message = ${e.message}")
                    stopSelf()
                }
            }
        }
    }

    private fun getGoogleDriveService(): Drive? {
        val googleAccount = GoogleSignIn.getLastSignedInAccount(this)
        return if (googleAccount != null) {
            val credential = GoogleAccountCredential.usingOAuth2(this, listOf(DriveScopes.DRIVE_FILE))
            credential.selectedAccount = googleAccount.account
            Drive.Builder(AndroidHttp.newCompatibleTransport(), JacksonFactory.getDefaultInstance(), credential)
                .setApplicationName(getString(R.string.app_name))
                .build()
        } else {
            null
        }
    }

    private fun getDropboxClient(): DbxClientV2? {
        val prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        val accessToken = prefs.getString(PREF_ACCESS_TOKEN, null)
        return if (accessToken != null) {
            val requestConfig = DbxRequestConfig.newBuilder("Notes-Sync")
                .withHttpRequestor(OkHttp3Requestor(OkHttp3Requestor.defaultOkHttpClient()))
                .build()
            DbxClientV2(requestConfig, accessToken)
        } else {
            null
        }
    }

    private fun getCloudData() {
        Log.d(TAG, "Initializing cloud fetch...")
        //New drive adding Point 1
        if (mDriveType == CLOUD_GOOGLE_DRIVE) {
            val googleDriveService = getGoogleDriveService()
            if (googleDriveService == null)
                throw NullPointerException("GoogleDriveService was null")
            else
                googleDriveHelper = GoogleDriveHelper(googleDriveService)
        } else {
            val dropboxClient = getDropboxClient()
            if (dropboxClient == null)
                throw NullPointerException("DropboxClient was null")
            else
                dropboxHelper = DropboxHelper(dropboxClient)
        }

        GlobalScope.launch(Dispatchers.Default) {
            try {
                checkAndPrepareEncryption()
                //New drive adding Point 2
                Log.d(TAG, "Getting files list")
                val filesList = if (mDriveType == CLOUD_GOOGLE_DRIVE) {
                    val appFolderId = getAppFolderId()
                    val fileSystemId = getFileSystemId(appFolderId)
                    googleDriveHelper.appFolderId = appFolderId
                    googleDriveHelper.fileSystemId = fileSystemId
                    getFilesListGD(fileSystemId)
                } else {
                    getFilesListDB()
                }

                compareAndSync(filesList)
                withContext(Dispatchers.Main) {
                    val cal = Calendar.getInstance()
                    val prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
                    prefs.edit().putLong(PREF_LAST_SYNCED_TIME, cal.timeInMillis).apply()
                    if (shouldIndicateSync)
                        Toast.makeText(this@NotesSyncService, "Sync successful", Toast.LENGTH_SHORT).show()
                    updateWidgets()
                    stopSelf()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (shouldIndicateSync)
                        Toast.makeText(this@NotesSyncService, "Sync error", Toast.LENGTH_SHORT).show()
                    stopSelf()
                }
            }
        }
    }

    private fun checkAndPrepareEncryption() {
        val prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_ENCRYPTED, false)) {
            val password = prefs.getString(PREF_CODE, null)
            val userId = prefs.getString(PREF_ID, null)
            if (password != null && userId != null) {
                aesHelper.generateKey(password, userId)
                val cloudKeyEncrypted = getCloudKey()
                if (cloudKeyEncrypted != null) {
                    val cloudKey = aesHelper.decrypt(cloudKeyEncrypted)
                    aesHelper.generateKey(cloudKey, userId)
                    isEncrypted = true
                }
            }
        }
    }

    private fun getCloudKey(): String? {
        if (mDriveType == CLOUD_GOOGLE_DRIVE) {
            val googleDriveService = getGoogleDriveService() ?: return null
            val googleDriveHelper = GoogleDriveHelper(googleDriveService)
            val credentialFileId = googleDriveHelper.searchFile(Contract.CREDENTIALS_FILENAME, FILE_TYPE_TEXT)
            return if (credentialFileId != null)
                googleDriveHelper.getFileContent(credentialFileId)
            else
                null
        } else {
            val dropboxClient = getDropboxClient() ?: return null
            val dropboxHelper = DropboxHelper(dropboxClient)
            return if (dropboxHelper.checkIfFileExists(Contract.CREDENTIALS_FILENAME))
                dropboxHelper.getFileContent(Contract.CREDENTIALS_FILENAME)
            else
                null
        }
    }

    private fun compareAndSync(cloudFilesList: List<NoteFile>) {
        Log.d(TAG, "Initializing compare and sync...")
        if (mNotesList.isEmpty()) {
            restoreNotes(cloudFilesList)
            return
        }
        if (cloudFilesList.isEmpty()) {
            writeFileSystem()
            return
        }

        val workScheduler = WorkSchedulerHelper()
        var isCloudDbChanged = false
        var isLocalDbChanged = false
        val localNotesList: MutableList<Note> = ArrayList()
        val extraNotesLocal: MutableList<Note> = ArrayList()
        localNotesList.addAll(mNotesList)
        var localCounter = 0
        var cloudCounter = 0
        val gson = Gson()
        var noteData: NoteContent
        var fileContent: String?
        val newFilesList: MutableList<NoteFile> = ArrayList()
        while (localCounter < localNotesList.size && cloudCounter < cloudFilesList.size) {
            if (localNotesList[localCounter].nId == cloudFilesList[cloudCounter].nId) {
                //Notes with same Id
                if (localNotesList[localCounter].dateCreated != cloudFilesList[cloudCounter].dateCreated) {
                    //Notes created on different devices
                    fileContent = getFileContent(cloudFilesList[cloudCounter])
                    noteData = gson.fromJson(fileContent, NoteContent::class.java)
                    if (localNotesList[localCounter].noteType != NOTE_DELETED)
                        extraNotesLocal.add(localNotesList[localCounter])
                    localNotesList[localCounter].noteTitle = noteData.noteTitle
                    localNotesList[localCounter].noteContent = noteData.noteContent
                    localNotesList[localCounter].noteColor = noteData.noteColor
                    localNotesList[localCounter].dateCreated = cloudFilesList[cloudCounter].dateCreated
                    localNotesList[localCounter].dateModified = cloudFilesList[cloudCounter].dateModified
                    localNotesList[localCounter].gDriveId = cloudFilesList[cloudCounter].gDriveId
                    localNotesList[localCounter].synced = true
                    localNotesList[localCounter].noteType = noteData.noteType!!
                    localNotesList[localCounter].reminderTime = noteData.reminderTime
                    if (localNotesList[localCounter].reminderTime != -1L)
                        workScheduler.setReminder(
                            localNotesList[localCounter].nId,
                            localNotesList[localCounter].reminderTime
                        )
                    else
                        workScheduler.cancelReminderByNoteId(localNotesList[localCounter].nId)
                    isLocalDbChanged = true
                    continue
                }

                if (localNotesList[localCounter].noteType == NOTE_DELETED) {
                    //If note deleted from device then delete from cloud too
                    deleteFile(localNotesList[localCounter])
                    isCloudDbChanged = true
                    localCounter++
                    cloudCounter++
                    continue
                }

                if (localNotesList[localCounter].dateModified > cloudFilesList[cloudCounter].dateModified) {
                    //Local note is more recent
                    noteData = NoteContent(
                        localNotesList[localCounter].noteTitle,
                        localNotesList[localCounter].noteContent,
                        localNotesList[localCounter].noteColor,
                        localNotesList[localCounter].noteType,
                        localNotesList[localCounter].reminderTime
                    )
                    fileContent = gson.toJson(noteData)
                    updateFile(cloudFilesList[cloudCounter], fileContent)
                    isCloudDbChanged = true
                } else if (localNotesList[localCounter].dateModified < cloudFilesList[cloudCounter].dateModified) {
                    //Cloud note is more recent
                    fileContent = getFileContent(cloudFilesList[cloudCounter])
                    noteData = gson.fromJson(fileContent, NoteContent::class.java)
                    localNotesList[localCounter].noteTitle = noteData.noteTitle
                    localNotesList[localCounter].noteContent = noteData.noteContent
                    localNotesList[localCounter].dateModified = cloudFilesList[cloudCounter].dateModified
                    localNotesList[localCounter].dateCreated = cloudFilesList[cloudCounter].dateCreated
                    localNotesList[localCounter].gDriveId = cloudFilesList[cloudCounter].gDriveId
                    //If reminder time is changed then set or cancel the reminder according to cloud
                    if (localNotesList[localCounter].reminderTime != noteData.reminderTime) {
                        localNotesList[localCounter].reminderTime = noteData.reminderTime
                        if (noteData.reminderTime != -1L)
                            workScheduler.setReminder(localNotesList[localCounter].nId, noteData.reminderTime)
                        else
                            workScheduler.cancelReminderByNoteId(localNotesList[localCounter].nId)
                    }
                    isLocalDbChanged = true
                }

                newFilesList.add(
                    NoteFile(
                        localNotesList[localCounter].nId,
                        localNotesList[localCounter].dateCreated,
                        localNotesList[localCounter].dateModified,
                        localNotesList[localCounter].gDriveId
                    )
                )
                localNotesList[localCounter].synced = true
                localCounter++
                cloudCounter++
            } else if (localNotesList[localCounter].nId!! < cloudFilesList[cloudCounter].nId!!) {
                //Note exists on the device but not online
                if ((localNotesList[localCounter].noteType == NOTE_DELETED) || localNotesList[localCounter].synced) {
                    //The note was deleted from another device or it was never synced and deleted from device
                    deleteByNoteId(localNotesList[localCounter].nId)
                    localNotesList.removeAt(localCounter)
                    continue
                }

                noteData = NoteContent(
                    localNotesList[localCounter].noteTitle,
                    localNotesList[localCounter].noteContent,
                    localNotesList[localCounter].noteColor,
                    localNotesList[localCounter].noteType,
                    localNotesList[localCounter].reminderTime
                )
                fileContent = gson.toJson(noteData)
                val fileId = createFile(localNotesList[localCounter], fileContent)

                newFilesList.add(
                    NoteFile(
                        localNotesList[localCounter].nId,
                        localNotesList[localCounter].dateCreated,
                        localNotesList[localCounter].dateModified,
                        fileId
                    )
                )

                isCloudDbChanged = true
                localNotesList[localCounter].synced = true
                localCounter++
            } else {
                //Note exists online but not on the device
                fileContent = getFileContent(cloudFilesList[cloudCounter])
                noteData = gson.fromJson(fileContent, NoteContent::class.java)
                localNotesList.add(
                    Note(
                        cloudFilesList[cloudCounter].nId,
                        noteData.noteTitle,
                        noteData.noteContent,
                        cloudFilesList[cloudCounter].dateCreated,
                        cloudFilesList[cloudCounter].dateModified,
                        cloudFilesList[cloudCounter].gDriveId,
                        NOTE_DEFAULT,
                        true,
                        noteData.noteColor,
                        noteData.reminderTime
                    )
                )
                //If there is a reminder then set it
                if (noteData.reminderTime != -1L)
                    workScheduler.setReminder(localNotesList[localCounter].nId, noteData.reminderTime)

                newFilesList.add(
                    NoteFile(
                        cloudFilesList[cloudCounter].nId,
                        cloudFilesList[cloudCounter].dateCreated,
                        cloudFilesList[cloudCounter].dateModified,
                        cloudFilesList[cloudCounter].gDriveId
                    )
                )

                isLocalDbChanged = true
                cloudCounter++
            }
        }

        if (localNotesList.size - localCounter <= 0 && cloudFilesList.size - cloudCounter > 0) {
            //Some notes exist online but not on device
            for (i in cloudCounter until cloudFilesList.size) {
                fileContent = getFileContent(cloudFilesList[i])
                noteData = gson.fromJson(fileContent, NoteContent::class.java)
                localNotesList.add(
                    Note(
                        cloudFilesList[i].nId,
                        noteData.noteTitle,
                        noteData.noteContent,
                        cloudFilesList[i].dateCreated,
                        cloudFilesList[i].dateModified,
                        cloudFilesList[i].gDriveId,
                        noteData.noteType!!,
                        true,
                        noteData.noteColor,
                        noteData.reminderTime
                    )
                )
                //If there is a reminder then set it
                if (noteData.reminderTime != -1L)
                    workScheduler.setReminder(localNotesList[localCounter].nId, noteData.reminderTime)

                newFilesList.add(
                    NoteFile(
                        cloudFilesList[i].nId,
                        cloudFilesList[i].dateCreated,
                        cloudFilesList[i].dateModified,
                        cloudFilesList[i].gDriveId
                    )
                )
            }
            isLocalDbChanged = true
        } else if (localNotesList.size - localCounter > 0 && cloudFilesList.size - cloudCounter <= 0) {
            //Some notes exists on the device but not online
            var i = localCounter
            while (i < localNotesList.size) {
                if ((localNotesList[i].noteType == NOTE_DELETED) || localNotesList[i].synced) {
                    deleteByNoteId(localNotesList[i].nId)
                    localNotesList.removeAt(i)
                    continue
                }
                noteData = NoteContent(
                    localNotesList[i].noteTitle,
                    localNotesList[i].noteContent,
                    localNotesList[i].noteColor,
                    localNotesList[i].noteType,
                    localNotesList[i].reminderTime
                )
                fileContent = gson.toJson(noteData)
                localNotesList[i].gDriveId = createFile(localNotesList[i], fileContent)

                newFilesList.add(
                    NoteFile(
                        localNotesList[i].nId,
                        localNotesList[i].dateCreated,
                        localNotesList[i].dateModified,
                        localNotesList[i].gDriveId
                    )
                )
                i++
            }
            isCloudDbChanged = true
        }

        if (extraNotesLocal.isNotEmpty()) {
            val lastId = localNotesList[localNotesList.size - 1].nId?.plus(1)
            for (i in 0 until extraNotesLocal.size) {
                extraNotesLocal[i].nId = lastId?.plus(i)
                noteData = NoteContent(
                    extraNotesLocal[i].noteTitle,
                    extraNotesLocal[i].noteContent,
                    extraNotesLocal[i].noteColor,
                    extraNotesLocal[i].noteType,
                    extraNotesLocal[i].reminderTime
                )
                fileContent = gson.toJson(noteData)
                extraNotesLocal[i].gDriveId = createFile(extraNotesLocal[i], fileContent)
                localNotesList.add(extraNotesLocal[i])

                if (extraNotesLocal[i].reminderTime != -1L)
                    workScheduler.setReminder(extraNotesLocal[i].nId, extraNotesLocal[i].reminderTime)

                newFilesList.add(
                    NoteFile(
                        extraNotesLocal[i].nId,
                        extraNotesLocal[i].dateCreated,
                        extraNotesLocal[i].dateModified,
                        extraNotesLocal[i].gDriveId
                    )
                )
            }
            isCloudDbChanged = true
            isLocalDbChanged = true
        }

        Log.d(TAG, "Comparing done")
        //Updating local database
        if (isLocalDbChanged) {
            mNotesList = localNotesList
            updateLocalDatabase()
        }
        //Updating cloud file system
        if (isCloudDbChanged) {
            writeFileSystemToCloud(newFilesList)
        }
    }

    private fun createFile(note: Note, fileContentString: String): String {
        val fileContent = if (isEncrypted)
            aesHelper.encrypt(fileContentString)
        else
            fileContentString

        //New drive adding Point 4
        return if (mDriveType == CLOUD_GOOGLE_DRIVE) {
            googleDriveHelper.createFile(googleDriveHelper.appFolderId, "${note.nId}.txt", FILE_TYPE_TEXT, fileContent)
        } else {
            dropboxHelper.writeFile("${note.nId}.txt", fileContent)
            "-1"
        }
    }

    private fun updateFile(file: NoteFile, fileContentString: String) {
        val fileContent = if (isEncrypted)
            aesHelper.encrypt(fileContentString)
        else
            fileContentString
        //New drive adding Point 5
        if (mDriveType == CLOUD_GOOGLE_DRIVE)
            googleDriveHelper.updateFile(file.gDriveId!!, fileContent)
        else
            dropboxHelper.writeFile("${file.nId}.txt", fileContent)
    }

    private fun deleteFile(note: Note) {
        //New drive adding Point 6
        if (mDriveType == CLOUD_GOOGLE_DRIVE)
            googleDriveHelper.deleteFile(note.gDriveId)
        else
            dropboxHelper.deleteFile("${note.nId}.txt")
    }

    private fun getFileContent(file: NoteFile): String? {
        //New drive adding Point 7
        val fileContent = if (mDriveType == CLOUD_GOOGLE_DRIVE) {
            googleDriveHelper.getFileContent(file.gDriveId)
        } else {
            dropboxHelper.getFileContent("${file.nId}.txt")
        }

        return if (fileContent != null) {
            if (isEncrypted)
                aesHelper.decrypt(fileContent)
            else
                fileContent
        } else
            null
    }

    private fun restoreNotes(filesList: List<NoteFile>) {
        var fileContent: String?
        var noteData: NoteContent
        val notesList = ArrayList<Note>()
        val gson = Gson()
        val workScheduler = WorkSchedulerHelper()
        for (file in filesList) {
            fileContent = getFileContent(file)
            noteData = gson.fromJson(fileContent, NoteContent::class.java)
            notesList.add(
                Note(
                    file.nId,
                    noteData.noteTitle,
                    noteData.noteContent,
                    file.dateCreated,
                    file.dateModified,
                    file.gDriveId,
                    noteData.noteType!!,
                    true,
                    noteData.noteColor,
                    noteData.reminderTime
                )
            )
            if (noteData.reminderTime != -1L)
                workScheduler.setReminder(file.nId, noteData.reminderTime)
        }
        mNotesList = notesList
        updateLocalDatabase()
    }

    private fun getFileSystemId(parentFolderId: String): String {
        var fileSystemId: String? = googleDriveHelper.searchFile(FILE_SYSTEM_FILENAME, FILE_TYPE_TEXT)
        if (fileSystemId == null) {
            Log.d(TAG, "File system not found")
            val filesList = ArrayList<NoteFile>()
            val fileContent = if (isEncrypted)
                aesHelper.encrypt(Gson().toJson(filesList))
            else
                Gson().toJson(filesList)

            fileSystemId = googleDriveHelper.createFile(parentFolderId, FILE_SYSTEM_FILENAME, FILE_TYPE_TEXT, fileContent)
        }
        return fileSystemId
    }

    private fun getFilesListGD(fileSystemId: String): List<NoteFile> {
        val fileContentString = googleDriveHelper.getFileContent(fileSystemId) as String
        val fileContent = if (isEncrypted)
            aesHelper.decrypt(fileContentString)
        else
            fileContentString

        return Gson().fromJson(fileContent, Array<NoteFile>::class.java).asList()
    }

    private fun getFilesListDB(): List<NoteFile> {
        return if (dropboxHelper.checkIfFileExists(FILE_SYSTEM_FILENAME)) {
            val fileContentString = dropboxHelper.getFileContent(FILE_SYSTEM_FILENAME) as String
            val fileContent = if (isEncrypted)
                aesHelper.decrypt(fileContentString)
            else
                fileContentString
            Gson().fromJson(fileContent, Array<NoteFile>::class.java).asList()
        } else {
            val filesList = ArrayList<NoteFile>()
            val fileContent = if (isEncrypted)
                aesHelper.encrypt(Gson().toJson(filesList))
            else
                Gson().toJson(filesList)
            dropboxHelper.writeFile(FILE_SYSTEM_FILENAME, fileContent)
            filesList
        }
    }

    private fun deleteByNoteId(nId: Long?) {
        if (nId == null)
            return
        GlobalScope.launch(Dispatchers.IO) {
            val notesDao = NotesRoomDatabase.getDatabase(application).notesDao()
            notesDao.deleteNoteById(nId)
        }
    }

    private fun updateLocalDatabase() {
        if (mNotesList.isEmpty())
            return

        GlobalScope.launch(Dispatchers.IO) {
            val notesDao = NotesRoomDatabase.getDatabase(application).notesDao()
            for (note in mNotesList)
                notesDao.insert(note)
        }
    }

    private fun writeFileSystem() {
        val gson = Gson()
        val noteContent = NoteContent(null, null, null, null, -1L)
        var jsonData: String?
        val filesList: MutableList<NoteFile> = ArrayList()
        val newNotesList: MutableList<Note> = ArrayList()
        newNotesList.addAll(mNotesList)
        var i = 0
        while (i < newNotesList.size) {
            if (newNotesList[i].noteType != NOTE_DELETED) {
                noteContent.noteTitle = newNotesList[i].noteTitle
                noteContent.noteContent = newNotesList[i].noteContent
                noteContent.noteColor = newNotesList[i].noteColor
                noteContent.noteType = newNotesList[i].noteType
                noteContent.reminderTime = newNotesList[i].reminderTime
                jsonData = gson.toJson(noteContent)
                newNotesList[i].gDriveId = createFile(newNotesList[i], jsonData)

                filesList.add(
                    NoteFile(
                        newNotesList[i].nId,
                        newNotesList[i].dateCreated,
                        newNotesList[i].dateModified,
                        newNotesList[i].gDriveId
                    )
                )
            } else {
                deleteByNoteId(newNotesList[i].nId)
                newNotesList.removeAt(i)
                i--
            }
            i++
        }

        mNotesList = newNotesList
        writeFileSystemToCloud(filesList)
        updateLocalDatabase()
    }

    private fun writeFileSystemToCloud(filesList: MutableList<NoteFile>) {
        val fileSystemJsonString = Gson().toJson(filesList)
        val fileSystemJson = if (isEncrypted)
            aesHelper.encrypt(fileSystemJsonString)
        else
            fileSystemJsonString
        //New drive adding Point 8
        if (mDriveType == CLOUD_GOOGLE_DRIVE)
            googleDriveHelper.updateFile(googleDriveHelper.fileSystemId, fileSystemJson)
        else
            dropboxHelper.writeFile(FILE_SYSTEM_FILENAME, fileSystemJson)
    }

    private fun getAppFolderId(): String {
        var folderId = googleDriveHelper.searchFile("notes_sync_data_folder_19268", FILE_TYPE_FOLDER)
        if (folderId == null)
            folderId = googleDriveHelper.createFile(null, "notes_sync_data_folder_19268", FILE_TYPE_FOLDER, null)
        return folderId
    }

    private fun updateWidgets() {
        val intent = Intent(this, NotesWidget::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, NotesWidget::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(false)
    }
}