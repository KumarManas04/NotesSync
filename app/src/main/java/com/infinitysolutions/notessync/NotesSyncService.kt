package com.infinitysolutions.notessync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.gson.Gson
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Model.NoteContent
import com.infinitysolutions.notessync.Model.NoteFile
import com.infinitysolutions.notessync.Model.NotesRoomDatabase
import com.infinitysolutions.notessync.Util.GoogleDriveHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesSyncService : Service() {
    private val TAG = "NotesSyncService"
    private lateinit var mNotesList: List<Note>
    private val FILE_TYPE_FOLDER = "application/vnd.google-apps.folder"
    private val FILE_TYPE_TEXT = "text/plain"
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
        val CHANNEL_ID = "notes_sync"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notes Sync"
            val description = "Used to sync notes to the cloud"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description
            channel.setSound(null, null)
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
        notificationBuilder.setSmallIcon(R.drawable.sync_notes)
            .setContentTitle("Notes Sync")
            .setContentText("Syncing notes...")

        val notification = notificationBuilder.build()

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
        if (googleAccount != null) {
            val credential = GoogleAccountCredential.usingOAuth2(this, listOf(DriveScopes.DRIVE_FILE))
            credential.selectedAccount = googleAccount.account

            return Drive.Builder(AndroidHttp.newCompatibleTransport(), JacksonFactory.getDefaultInstance(), credential)
                .setApplicationName(getString(R.string.app_name))
                .build()
        } else
            return null
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

        var isCloudDbChanged = false
        var isLocalDbChanged = false
        val localNotesList: MutableList<Note> = ArrayList()
        localNotesList.addAll(mNotesList)
        var localCounter = 0
        var cloudCounter = 0
        val gson = Gson()
        var noteData: NoteContent
        var fileContent: String?
        val newFilesList: MutableList<NoteFile> = ArrayList()
        while (localCounter < localNotesList.size && cloudCounter < cloudFilesList.size) {
            if (localNotesList[localCounter].nId == cloudFilesList[cloudCounter].nId) {
                if (localNotesList[localCounter].deleted){
                    googleDriveHelper.deleteFile(localNotesList[localCounter].gDriveId)
                    isCloudDbChanged = true
                    localCounter++
                    cloudCounter++
                    continue
                }
                if (localNotesList[localCounter].dateModified > cloudFilesList[cloudCounter].dateModified) {
                    noteData = NoteContent(localNotesList[localCounter].noteTitle, localNotesList[localCounter].noteContent)
                    fileContent = gson.toJson(noteData)
                    googleDriveHelper.updateFile(cloudFilesList[cloudCounter].gDriveId!!, fileContent)

                    isCloudDbChanged = true
                } else if (localNotesList[localCounter].dateModified < cloudFilesList[cloudCounter].dateModified) {
                    fileContent = googleDriveHelper.getFileContent(cloudFilesList[cloudCounter].gDriveId)
                    noteData = gson.fromJson(fileContent, NoteContent::class.java)
                    localNotesList[localCounter].noteTitle = noteData.noteTitle
                    localNotesList[localCounter].noteContent = noteData.noteContent
                    localNotesList[localCounter].dateModified = cloudFilesList[cloudCounter].dateModified
                    localNotesList[localCounter].dateCreated = cloudFilesList[cloudCounter].dateCreated
                    localNotesList[localCounter].gDriveId = cloudFilesList[cloudCounter].gDriveId

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
                if (localNotesList[localCounter].deleted || localNotesList[localCounter].synced){
                    deleteByNoteId(localNotesList[localCounter].nId)
                    localNotesList.removeAt(localCounter)
                    continue
                }

                noteData = NoteContent(localNotesList[localCounter].noteTitle, localNotesList[localCounter].noteContent)
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
                        false,
                        true
                    )
                )

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
                        false,
                        true
                    )
                )
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
                if (localNotesList[i].deleted || localNotesList[i].synced){
                    deleteByNoteId(localNotesList[i].nId)
                    localNotesList.removeAt(i)
                    continue
                }
                noteData = NoteContent(localNotesList[i].noteTitle, localNotesList[i].noteContent)
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
                    false,
                    true
                )
            )
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
        val noteContent = NoteContent(null, null)
        var jsonData: String?
        var fileId: String?
        val filesList: MutableList<NoteFile> = ArrayList()
        for (i in 0 until mNotesList.size) {
            noteContent.noteTitle = mNotesList[i].noteTitle
            noteContent.noteContent = mNotesList[i].noteContent
            jsonData = gson.toJson(noteContent)
            fileId = googleDriveHelper.createFile(parentFolderId, "${mNotesList[i].nId}.txt", FILE_TYPE_TEXT, jsonData)
            mNotesList[i].gDriveId = fileId
            filesList.add(
                NoteFile(
                    mNotesList[i].nId,
                    mNotesList[i].dateCreated,
                    mNotesList[i].dateModified,
                    mNotesList[i].gDriveId
                )
            )
        }

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
