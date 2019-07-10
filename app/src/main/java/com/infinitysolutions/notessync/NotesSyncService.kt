package com.infinitysolutions.notessync

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.gson.Gson
import com.infinitysolutions.notessync.Contracts.Contract.Companion.FILE_TYPE_FOLDER
import com.infinitysolutions.notessync.Contracts.Contract.Companion.FILE_TYPE_TEXT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DELETED
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Model.NoteContent
import com.infinitysolutions.notessync.Model.NoteFile
import com.infinitysolutions.notessync.Model.NotesRoomDatabase
import com.infinitysolutions.notessync.Util.GoogleDriveHelper
import com.infinitysolutions.notessync.Util.NotificationHelper
import com.infinitysolutions.notessync.Util.WorkSchedulerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesSyncService : Service() {
    private val TAG = "NotesSyncService"
    private lateinit var mNotesList: List<Note>
    private lateinit var googleDriveHelper: GoogleDriveHelper

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStart")
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
                getCloudData()
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

    private fun getCloudData() {
        Log.d(TAG, "Initializing cloud fetch...")
        val googleDriveService = getGoogleDriveService()

        if (googleDriveService != null) {
            googleDriveHelper = GoogleDriveHelper(googleDriveService)
            GlobalScope.launch(Dispatchers.Default) {
                Log.d(TAG, "Getting appFolderID")
                val folderId = getAppFolderId()
                if (folderId != null) {
                    val fileSystemId = getFileSystemId(folderId)
                    val filesList = getFilesListGDrive(fileSystemId)
                    if (filesList != null && fileSystemId != null) {
                        compareAndSync(fileSystemId, filesList, folderId)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NotesSyncService, "Sync successful", Toast.LENGTH_SHORT).show()
                    stopSelf()
                }
            }
        } else {
            Toast.makeText(this, "Couldn't connect to Google Drive", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun compareAndSync(fileSystemId: String, cloudFilesList: List<NoteFile>, parentFolderId: String) {
        Log.d(TAG, "Initializing compare and sync...")
        if (mNotesList.isEmpty()) {
            restoreNotes(cloudFilesList)
            return
        }
        if (cloudFilesList.isEmpty()) {
            writeFileSystemGDrive(parentFolderId)
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
                if(localNotesList[localCounter].dateCreated != cloudFilesList[cloudCounter].dateCreated){
                    //Notes created on different devices
                    fileContent = googleDriveHelper.getFileContent(cloudFilesList[cloudCounter].gDriveId)
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
                        workScheduler.setReminder(localNotesList[localCounter].nId, localNotesList[localCounter].reminderTime)
                    else
                        workScheduler.cancelReminder(localNotesList[localCounter].nId)
                    isLocalDbChanged = true
                    continue
                }

                if (localNotesList[localCounter].noteType == NOTE_DELETED){
                    //If note deleted from device then delete from cloud too
                    googleDriveHelper.deleteFile(localNotesList[localCounter].gDriveId)
                    isCloudDbChanged = true
                    localCounter++
                    cloudCounter++
                    continue
                }

                if (localNotesList[localCounter].dateModified > cloudFilesList[cloudCounter].dateModified) {
                    //Local note is more recent
                    noteData = NoteContent(localNotesList[localCounter].noteTitle, localNotesList[localCounter].noteContent, localNotesList[localCounter].noteColor, localNotesList[localCounter].noteType, localNotesList[localCounter].reminderTime)
                    fileContent = gson.toJson(noteData)
                    googleDriveHelper.updateFile(cloudFilesList[cloudCounter].gDriveId!!, fileContent)
                    isCloudDbChanged = true
                } else if (localNotesList[localCounter].dateModified < cloudFilesList[cloudCounter].dateModified) {
                    //Cloud note is more recent
                    fileContent = googleDriveHelper.getFileContent(cloudFilesList[cloudCounter].gDriveId)
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
                            workScheduler.cancelReminder(localNotesList[localCounter].nId)
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
                if ((localNotesList[localCounter].noteType == NOTE_DELETED) || localNotesList[localCounter].synced){
                    //The note was deleted from another device or it was never synced and deleted from device
                    deleteByNoteId(localNotesList[localCounter].nId)
                    localNotesList.removeAt(localCounter)
                    continue
                }

                noteData = NoteContent(localNotesList[localCounter].noteTitle, localNotesList[localCounter].noteContent, localNotesList[localCounter].noteColor, localNotesList[localCounter].noteType, localNotesList[localCounter].reminderTime)
                fileContent = gson.toJson(noteData)
                val fileId = googleDriveHelper.createFile(
                    parentFolderId,
                    "${localNotesList[localCounter].nId}.txt",
                    FILE_TYPE_TEXT,
                    fileContent
                )

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
                fileContent = googleDriveHelper.getFileContent(cloudFilesList[cloudCounter].gDriveId)
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
                if(noteData.reminderTime != -1L)
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
                fileContent = googleDriveHelper.getFileContent(cloudFilesList[i].gDriveId)
                noteData = gson.fromJson(fileContent, NoteContent::class.java)
                localNotesList.add(
                    Note(
                        cloudFilesList[i].nId,
                        noteData.noteTitle,
                        noteData.noteContent,
                        cloudFilesList[i].dateCreated,
                        cloudFilesList[i].dateModified,
                        cloudFilesList[i].gDriveId,
                        NOTE_DEFAULT,
                        true,
                        noteData.noteColor,
                        noteData.reminderTime
                    )
                )
                //If there is a reminder then set it
                if(noteData.reminderTime != -1L)
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
            while (i < localNotesList.size){
                if ((localNotesList[i].noteType == NOTE_DELETED) || localNotesList[i].synced){
                    deleteByNoteId(localNotesList[i].nId)
                    localNotesList.removeAt(i)
                    continue
                }
                noteData = NoteContent(localNotesList[i].noteTitle, localNotesList[i].noteContent, localNotesList[i].noteColor, localNotesList[i].noteType, localNotesList[i].reminderTime)
                fileContent = gson.toJson(noteData)
                val fileId = googleDriveHelper.createFile(
                    parentFolderId,
                    "${localNotesList[i].nId}.txt",
                    FILE_TYPE_TEXT,
                    fileContent
                )

                localNotesList[i].gDriveId = fileId
                newFilesList.add(
                    NoteFile(
                        localNotesList[i].nId,
                        localNotesList[i].dateCreated,
                        localNotesList[i].dateModified,
                        fileId
                    )
                )
                i++
            }
            isCloudDbChanged = true
        }

        if (extraNotesLocal.isNotEmpty()){
            val lastId = localNotesList[localNotesList.size - 1].nId?.plus(1)
            var fileId: String?
            for (i in 0 until extraNotesLocal.size){
                extraNotesLocal[i].nId = lastId?.plus(i)
                noteData = NoteContent(extraNotesLocal[i].noteTitle, extraNotesLocal[i].noteContent, extraNotesLocal[i].noteColor, extraNotesLocal[i].noteType, extraNotesLocal[i].reminderTime)
                fileContent = gson.toJson(noteData)
                fileId = googleDriveHelper.createFile(parentFolderId, "${extraNotesLocal[i].nId}.txt", FILE_TYPE_TEXT, fileContent)
                extraNotesLocal[i].gDriveId = fileId
                localNotesList.add(extraNotesLocal[i])
                if (extraNotesLocal[i].reminderTime != -1L)
                    workScheduler.setReminder(extraNotesLocal[i].nId, extraNotesLocal[i].reminderTime)
                newFilesList.add(NoteFile(extraNotesLocal[i].nId, extraNotesLocal[i].dateCreated, extraNotesLocal[i].dateModified, extraNotesLocal[i].gDriveId))
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
            val fileSystemJson = Gson().toJson(newFilesList)
            googleDriveHelper.updateFile(fileSystemId, fileSystemJson)
        }
    }

    private fun restoreNotes(filesList: List<NoteFile>) {
        var fileContent: String?
        var noteData: NoteContent
        val notesList = ArrayList<Note>()
        val gson = Gson()
        val workScheduler = WorkSchedulerHelper()
        for (file in filesList) {
            fileContent = googleDriveHelper.getFileContent(file.gDriveId)
            noteData = gson.fromJson(fileContent, NoteContent::class.java)
            notesList.add(
                Note(
                    file.nId,
                    noteData.noteTitle,
                    noteData.noteContent,
                    file.dateCreated,
                    file.dateModified,
                    file.gDriveId,
                    NOTE_DEFAULT,
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

    private fun getFileSystemId(parentFolderId: String): String? {
        var fileSystemId: String? = googleDriveHelper.searchFile("notes_files_system.txt", FILE_TYPE_TEXT)
        if (fileSystemId == null) {
            Log.d(TAG, "File system not found")
            val filesList = ArrayList<NoteFile>()
            val fileContent = Gson().toJson(filesList)
            fileSystemId =
                googleDriveHelper.createFile(parentFolderId, "notes_files_system.txt", FILE_TYPE_TEXT, fileContent)
        }
        return fileSystemId
    }

    private fun getFilesListGDrive(fileSystemId: String?): List<NoteFile>? {
        if (fileSystemId == null)
            return null
        val fileContent = googleDriveHelper.getFileContent(fileSystemId)
        return Gson().fromJson(fileContent, Array<NoteFile>::class.java).asList()
    }

    private fun deleteByNoteId(nId: Long?){
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
            for (note in mNotesList) {
                notesDao.insert(note)
            }
        }
    }

    private fun writeFileSystemGDrive(parentFolderId: String) {
        val gson = Gson()
        val noteContent = NoteContent(null, null, null, null, -1L)
        var jsonData: String?
        var fileId: String?
        val filesList: MutableList<NoteFile> = ArrayList()
        val newNotesList: MutableList<Note> = ArrayList()
        newNotesList.addAll(mNotesList)
        var i = 0
        while( i < newNotesList.size){
            if (newNotesList[i].noteType != NOTE_DELETED) {
                noteContent.noteTitle = newNotesList[i].noteTitle
                noteContent.noteContent = newNotesList[i].noteContent
                noteContent.noteColor = newNotesList[i].noteColor
                noteContent.noteType = newNotesList[i].noteType
                noteContent.reminderTime = newNotesList[i].reminderTime
                jsonData = gson.toJson(noteContent)
                fileId =
                    googleDriveHelper.createFile(parentFolderId, "${newNotesList[i].nId}.txt", FILE_TYPE_TEXT, jsonData)
                newNotesList[i].gDriveId = fileId
                filesList.add(
                    NoteFile(
                        newNotesList[i].nId,
                        newNotesList[i].dateCreated,
                        newNotesList[i].dateModified,
                        newNotesList[i].gDriveId
                    )
                )
            }else{
                deleteByNoteId(newNotesList[i].nId)
                newNotesList.removeAt(i)
                i--
            }
            i++
        }

        mNotesList = newNotesList
        val fileSystemJson = gson.toJson(filesList)
        googleDriveHelper.createFile(parentFolderId, "notes_files_system.txt", FILE_TYPE_TEXT, fileSystemJson)
        updateLocalDatabase()
    }

    private fun getAppFolderId(): String? {
        var folderId = googleDriveHelper.searchFile("notes_sync_data_folder_19612", FILE_TYPE_FOLDER)
        if (folderId == null)
            folderId = googleDriveHelper.createFile(null, "notes_sync_data_folder_19612", FILE_TYPE_FOLDER, null)
        return folderId
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(false)
    }
}
