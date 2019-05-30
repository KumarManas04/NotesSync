package com.infinitysolutions.notessync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.gson.Gson
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Model.NoteContent
import com.infinitysolutions.notessync.Model.NoteFile
import com.infinitysolutions.notessync.Model.NotesRoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.collections.ArrayList

class NotesSyncService : Service() {
    private val TAG = "NotesSyncService"
    private val notesSyncBinder = NotesSyncBinder()
    private lateinit var mNotesList: List<Note>
    private val FILE_TYPE_FOLDER = "application/vnd.google-apps.folder"
    private val FILE_TYPE_TEXT = "text/plain"

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "Service onBind")
        return notesSyncBinder
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

    fun test() {
        Log.d(TAG, "NotesSync Service binding tester")
    }

    private fun getLocalData() {
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
        val googleDriveService = getGoogleDriveService()

        if (googleDriveService != null) {
            GlobalScope.launch(Dispatchers.Default) {
                val folderId = getAppFolderId(googleDriveService)
                if (folderId != null) {
                    Log.d(TAG, "Folder id = $folderId")
                    val filesList = getFilesListGDrive(googleDriveService, folderId)
                    if (filesList != null) {
                        if (mNotesList.isEmpty())
                            restoreNotes(googleDriveService, filesList)
                        else{
                            compareAndSync(googleDriveService, folderId, filesList)
                        }
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

    private fun compareAndSync(googleDriveService: Drive, parentFolderId: String, cloudFilesList: List<NoteFile>){
        var noteExists: Boolean
        val noteData = NoteContent(null, null)
        var jsonData: String
        var fileId: String
        val filesToAddToCloud = ArrayList<NoteFile>()
        filesToAddToCloud.addAll(cloudFilesList)
        val gson = Gson()
        for (i in 0 until mNotesList.size){
            noteExists = false
            for(file in cloudFilesList){
                if (mNotesList[i].nId == file.nId) {
                    noteExists = true
                    break
                }
            }
            if (!noteExists){
                noteData.noteTitle = mNotesList[i].noteTitle
                noteData.noteContent = mNotesList[i].noteContent
                jsonData = gson.toJson(noteData)
                fileId = createFile(googleDriveService, parentFolderId, "${mNotesList[i].nId}.txt", FILE_TYPE_TEXT, jsonData)
                mNotesList[i].gDriveId = fileId
                filesToAddToCloud.add(NoteFile(mNotesList[i].nId, mNotesList[i].dateCreated, mNotesList[i].dateModified, fileId))
            }
        }

        val fileSystemJson = gson.toJson(filesToAddToCloud)
        val fileSystemId: String? = searchFile(googleDriveService, "notes_files_system.txt", FILE_TYPE_TEXT)
        if(fileSystemId != null){
            updateFile(googleDriveService, fileSystemId, fileSystemJson)
        }
    }

    private fun restoreNotes(googleDriveService: Drive, filesList: List<NoteFile>){
        var fileContent: String?
        var noteData: NoteContent
        val notesList = ArrayList<Note>()
        val gson = Gson()
        for (file in filesList){
            fileContent = getFileContent(googleDriveService, file.gDriveId)
            noteData = gson.fromJson(fileContent, NoteContent::class.java)
            notesList.add(Note(file.nId, noteData.noteTitle, noteData.noteContent, file.dateCreated, file.dateModified, file.gDriveId))
        }
        mNotesList = notesList
        updateLocalDatabase()
    }

    private suspend fun getFilesListGDrive(googleDriveService: Drive, parentFolderId: String): List<NoteFile>?{
        val fileId: String? = searchFile(googleDriveService, "notes_files_system.txt", FILE_TYPE_TEXT)
        var filesList: List<NoteFile>? = null
        if (fileId == null) {
            Log.d(TAG, "File system not found")
            if (mNotesList.isNotEmpty()) {
                filesList = writeFileSystemGDrive(googleDriveService, parentFolderId)
                updateLocalDatabase()
            }else{
                withContext(Dispatchers.Main){
                    Toast.makeText(this@NotesSyncService, "No notes to sync", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.d(TAG, "File system found!")
            val fileContent = getFileContent(googleDriveService, fileId)
            val gson = Gson()
            filesList = gson.fromJson(fileContent, Array<NoteFile>::class.java).asList()
        }

        return filesList
    }

    private fun getFileContent(googleDriveService: Drive, fileId: String?): String?{
        val outputStream = ByteArrayOutputStream()
        googleDriveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        return outputStream.toString()
    }

    private fun updateLocalDatabase(){
        if (mNotesList.isEmpty()) {
            return
        }
        GlobalScope.launch(Dispatchers.IO) {
            val notesDao = NotesRoomDatabase.getDatabase(application).notesDao()
            for(note in mNotesList){
                notesDao.insert(note)
            }
        }
    }

    private fun writeFileSystemGDrive(googleDriveService: Drive, parentFolderId: String): List<NoteFile> {
        val gson = Gson()
        val noteContent = NoteContent(null, null)
        var jsonData: String?
        var fileId: String?
        for (i in 0 until mNotesList.size) {
            noteContent.noteTitle = mNotesList[i].noteTitle
            noteContent.noteContent = mNotesList[i].noteContent
            jsonData = gson.toJson(noteContent)
            fileId = createFile(googleDriveService, parentFolderId, "${mNotesList[i].nId}.txt", FILE_TYPE_TEXT, jsonData)
            mNotesList[i].gDriveId = fileId
        }

        val filesList = prepareFilesList()
        val fileSystemJson = gson.toJson(filesList)

        createFile(googleDriveService, parentFolderId, "notes_files_system.txt", FILE_TYPE_TEXT, fileSystemJson)
        return filesList
    }

    private fun prepareFilesList(): List<NoteFile> {
        val files: MutableList<NoteFile> = ArrayList()
        for (note in mNotesList) {
            files.add(NoteFile(note.nId, note.dateCreated, note.dateModified, note.gDriveId))
        }
        return files
    }

    private fun getAppFolderId(googleDriveService: Drive): String? {
        var folderId = searchFile(googleDriveService, "notes_sync_data_folder_19612", FILE_TYPE_FOLDER)
        if (folderId == null)
            folderId = createFile(googleDriveService, null, "notes_sync_data_folder_19612", FILE_TYPE_FOLDER, null)
        return folderId
    }

    private fun updateFile(googleDriveService: Drive, fileId: String, fileContent: String): String? {
        val mediaStream = InputStreamContent("text/plain", fileContent.byteInputStream())
        val contentFile = File()
        val file = googleDriveService.files().update(fileId, contentFile, mediaStream)
            .execute()
        if (file != null)
            Log.d(TAG, "FileId = ${file.id}")
        else
            Log.d(TAG, "Failed to update file")

        return file.id
    }

    private fun searchFile(googleDriveService: Drive, fileName: String, fileMimeType: String): String? {
        var fileId: String? = null
        val result = googleDriveService.files().list().apply {
            q = "name='$fileName' and mimeType='$fileMimeType'"
            spaces = "drive"
            fields = "files(id)"
        }.execute()
        if (result.files.size > 0) {
            Log.d(TAG, "File found!")
            fileId = result.files[0].id
        } else {
            Log.d(TAG, "File not found")
        }
        return fileId
    }

    private fun createFile(
        googleDriveService: Drive,
        parentFolderId: String?,
        fileName: String,
        fileMimeType: String,
        content: String?
    ): String {
        val fileMetadata = File()
        fileMetadata.name = fileName
        fileMetadata.mimeType = fileMimeType
        if (parentFolderId != null)
            fileMetadata.parents = Collections.singletonList(parentFolderId)

        val file = if (content != null) {
            val contentStream = ByteArrayContent.fromString("text/plain", content)
            googleDriveService.files().create(fileMetadata, contentStream)
                .setFields("id")
                .execute()
        } else {
            googleDriveService.files().create(fileMetadata)
                .setFields("id")
                .execute()
        }

        return file.id
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(false)
    }

    inner class NotesSyncBinder : Binder() {
        fun getService(): NotesSyncService {
            return this@NotesSyncService
        }
    }
}
