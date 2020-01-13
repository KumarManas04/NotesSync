package com.infinitysolutions.notessync.Workers

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
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
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_DROPBOX
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CREDENTIALS_FILENAME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.FILE_SYSTEM_FILENAME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.FILE_TYPE_FOLDER
import com.infinitysolutions.notessync.Contracts.Contract.Companion.FILE_TYPE_TEXT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_DELETED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_FILE_SYSTEM_FILENAME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_LIST_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_LIST_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_LIST_TRASH
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_TRASH
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DELETED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ACCESS_TOKEN
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_SYNC_QUEUE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.Fragments.NotesWidget
import com.infinitysolutions.notessync.Model.*
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.Util.AES256Helper
import com.infinitysolutions.notessync.Util.DropboxHelper
import com.infinitysolutions.notessync.Util.GoogleDriveHelper
import com.infinitysolutions.notessync.Util.WorkSchedulerHelper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class SyncWorker(private val context: Context, params: WorkerParameters) : Worker(context, params) {
    private lateinit var googleDriveHelper: GoogleDriveHelper
    private lateinit var dropboxHelper: DropboxHelper
    private var imageFileSystem: MutableList<ImageData>? = null
    private var mDriveType: Int = CLOUD_GOOGLE_DRIVE
    private var isEncrypted: Boolean = false
    private lateinit var notesDao: NotesDao
    private lateinit var imagesDao: ImagesDao
    private val aesHelper = AES256Helper()
    private val TAG = "SyncWorker"

    override fun doWork(): Result {
        val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs.contains(PREF_SYNC_QUEUE)) {
            var set = prefs.getStringSet(PREF_SYNC_QUEUE, null)
            if (set != null && set.isNotEmpty()) {
                when(val loginStatus = getLoginStatus(prefs)){
                    -1 ->{
                        val editor = prefs.edit()
                        set.clear()
                        editor.putStringSet(PREF_SYNC_QUEUE, set)
                        editor.apply()
                    }
                    else -> mDriveType = loginStatus
                }

                if (mDriveType == CLOUD_GOOGLE_DRIVE) {
                    val googleDriveService = getGoogleDriveService()
                    if (googleDriveService != null) {
                        googleDriveHelper = GoogleDriveHelper(googleDriveService)
                    }else{
                        return Result.failure()
                    }
                } else {
                    val dropboxClient = getDropboxClient()
                    if (dropboxClient != null) {
                        dropboxHelper = DropboxHelper(dropboxClient)
                    }else{
                        return Result.failure()
                    }
                }

                try {
                    checkAndPrepareEncryption()
                    var fileSystem = getFileSystem()

                    val database = NotesRoomDatabase.getDatabase(applicationContext)
                    notesDao = database.notesDao()
                    imagesDao = database.imagesDao()

                    if(inputData.getBoolean("syncAll", true)){
                        val cloudIds = mutableListOf<String>()
                        for(noteFile in fileSystem)
                            cloudIds.add(noteFile.nId.toString())
                        val localIds = mutableListOf<String>()
                        val localIdList = notesDao.getAllIds()
                        for(id in localIdList)
                            localIds.add(id.toString())
                        set = cloudIds.union(localIds)
                    }
                    val idsList = mutableListOf<Long>()
                    for(str in set)
                        idsList.add(str.toLong())

                    val availableNotesList = notesDao.getNotesByIds(idsList)

                    if (availableNotesList.isNotEmpty()) {
                        for (note in availableNotesList) {
                            fileSystem = syncNote(note, fileSystem)
                            set.remove(note.nId.toString())
                        }
                    }
                    if(set.isNotEmpty()){
                        val unavailableNotesIds = mutableListOf<Long>()
                        for(string in set)
                            unavailableNotesIds.add(string.toLong())
                        for(id in unavailableNotesIds) {
                            downloadCloudNote(id, fileSystem)
                            set.remove(id.toString())
                        }
                    }

                    writeFileSystemToCloud(fileSystem)
                    if(imageFileSystem != null){
                        writeImageFileSystemToCloud(imageFileSystem!!)
                    }
                }catch(e: Exception){
                    e.printStackTrace()
                    return Result.retry()
                }finally {
                    updateWidgets()
                    val editor = prefs.edit()
                    editor.putStringSet(PREF_SYNC_QUEUE, set)
                    editor.commit()
                }
            }
        }
        return Result.success()
    }

    private fun downloadCloudNote(id: Long, fileSystem: List<NoteFile>) {
        val index = fileSystem.binarySearchBy(id){it.nId}
        if(id == fileSystem[index].nId){
            val cloudNoteFile = fileSystem[index]
            val fileContentString = getFileContent(cloudNoteFile)
            val fileContent = Gson().fromJson(fileContentString, NoteContent::class.java)
            if(isImageType(fileContent.noteType!!)){
                val imageNoteContent = Gson().fromJson(fileContent.noteContent, ImageNoteContent::class.java)
                val newIdList = downloadCloudImages(imageNoteContent.idList)
                if(newIdList != imageNoteContent.idList) {
                    imageNoteContent.idList = newIdList
                    fileContent.noteContent = Gson().toJson(imageNoteContent)
                    updateFile(cloudNoteFile, Gson().toJson(fileContent))
                }
            }

            notesDao.simpleInsert(
                Note(
                    id,
                    fileContent.noteTitle,
                    fileContent.noteContent,
                    cloudNoteFile.dateCreated,
                    cloudNoteFile.dateModified,
                    cloudNoteFile.gDriveId,
                    fileContent.noteType!!,
                    true,
                    fileContent.noteColor,
                    fileContent.reminderTime
                )
            )

            if(fileContent.reminderTime != -1L)
                WorkSchedulerHelper().setReminder(cloudNoteFile.nId, fileContent.reminderTime)
            else
                WorkSchedulerHelper().cancelReminderByNoteId(cloudNoteFile.nId)
        }
    }

    private fun syncNote(localNote: Note, fileSystem: List<NoteFile>): List<NoteFile> {
        var tempFileSystem = fileSystem.toMutableList()
        val noteIndex = tempFileSystem.binarySearchBy(localNote.nId) { it.nId }
        val cloudNoteFile = tempFileSystem[noteIndex]
        if (localNote.nId == cloudNoteFile.nId) {
            // Notes with same Id
            if (localNote.dateCreated != cloudNoteFile.dateCreated) {
                // Notes created on different devices.
                // Method -
                // Convert the current note in database into cloud note
                // Insert the original localNote into database as new note
                // and upload this new note to the cloud
                downloadCloudNote(cloudNoteFile.nId!!, fileSystem)

                val time = Calendar.getInstance().timeInMillis
                val note = Note(
                    null,
                    localNote.noteTitle,
                    localNote.noteContent,
                    localNote.dateCreated,
                    time,
                    "-1",
                    localNote.noteType,
                    false,
                    localNote.noteColor,
                    localNote.reminderTime
                )

                val newId = notesDao.simpleInsert(note)
                note.nId = newId
                tempFileSystem = uploadNewNote(note, tempFileSystem)
                if(note.reminderTime != -1L)
                    WorkSchedulerHelper().setReminder(newId, note.reminderTime)
                else
                    WorkSchedulerHelper().cancelReminderByNoteId(newId)

            }else if (localNote.noteType == NOTE_DELETED || localNote.noteType == IMAGE_DELETED) {
                // If note deleted from device then delete from cloud too
                notesDao.deleteNoteById(localNote.nId!!)
                tempFileSystem.removeAt(noteIndex)
                deleteFile(localNote)
                WorkSchedulerHelper().cancelReminderByNoteId(localNote.nId)
            }else if (localNote.dateModified > cloudNoteFile.dateModified) {
                // Local note is more recent
                val noteData = NoteContent(
                    localNote.noteTitle,
                    localNote.noteContent,
                    localNote.noteColor,
                    localNote.noteType,
                    localNote.reminderTime
                )
                if(isImageType(localNote.noteType)){
                    val gson = Gson()
                    val fileContentString = getFileContent(cloudNoteFile)
                    val fileContent = gson.fromJson(fileContentString, NoteContent::class.java)
                    val localImageNoteContent = gson.fromJson(localNote.noteContent, ImageNoteContent::class.java)
                    val cloudImageNoteContent = gson.fromJson(fileContent.noteContent, ImageNoteContent::class.java)
                    val localIdList = localImageNoteContent.idList
                    val cloudIdList = cloudImageNoteContent.idList
                    val newIdList = compareIdListLocalPrefer(localIdList, cloudIdList)
                    localImageNoteContent.idList = newIdList
                    if(newIdList != cloudIdList)
                        noteData.noteContent = gson.toJson(localImageNoteContent)

                    if(newIdList != localIdList)
                        notesDao.updateNoteContent(localNote.nId!!, gson.toJson(localImageNoteContent))
                }

                val fileContent = Gson().toJson(noteData)
                updateFile(cloudNoteFile, fileContent)
                fileSystem[noteIndex].dateModified = localNote.dateModified
            } else if (localNote.dateModified < cloudNoteFile.dateModified) {
                //Cloud note is more recent
                val gson = Gson()
                val fileContentString = getFileContent(cloudNoteFile)
                val fileContent = gson.fromJson(fileContentString, NoteContent::class.java)
                val note = Note(
                    cloudNoteFile.nId,
                    fileContent.noteTitle,
                    fileContent.noteContent,
                    cloudNoteFile.dateCreated,
                    cloudNoteFile.dateModified,
                    cloudNoteFile.gDriveId,
                    fileContent.noteType!!,
                    true,
                    fileContent.noteColor,
                    fileContent.reminderTime
                )
                if(isImageType(note.noteType)){
                    val localImageNoteContent = gson.fromJson(localNote.noteContent, ImageNoteContent::class.java)
                    val cloudImageNoteContent = gson.fromJson(note.noteContent, ImageNoteContent::class.java)
                    val localIdList = localImageNoteContent.idList
                    val cloudIdList = cloudImageNoteContent.idList
                    val newIdList = compareIdListCloudPrefer(localIdList, cloudIdList)
                    cloudImageNoteContent.idList = newIdList

                    if(newIdList != cloudIdList)
                        updateFile(cloudNoteFile, gson.toJson(cloudImageNoteContent))

                    if(newIdList != localIdList)
                        note.noteContent = gson.toJson(cloudImageNoteContent)
                }
                notesDao.simpleInsert(note)
                if(note.reminderTime != -1L)
                    WorkSchedulerHelper().setReminder(note.nId, note.reminderTime)
                else
                    WorkSchedulerHelper().cancelReminderByNoteId(note.nId)
            }
        } else {
            // Note exists on the device but not online
            if (localNote.noteType == NOTE_DELETED || localNote.noteType == IMAGE_DELETED || localNote.synced) {
                // The note was deleted from another device or, it was never synced and was deleted from device
                notesDao.deleteNoteById(localNote.nId!!)
            } else {
                tempFileSystem = uploadNewNote(localNote, tempFileSystem)
            }
        }
        return tempFileSystem.toList()
    }

    private fun compareIdListCloudPrefer(localIdList: ArrayList<Long>, cloudIdList: ArrayList<Long>): ArrayList<Long>{
        val newIdList = ArrayList<Long>()
        val set = cloudIdList.toHashSet()
        val deletionList = ArrayList<Long>()
        for(id in localIdList){
            if(set.contains(id))
                newIdList.add(id)
            else
                deletionList.add(id)
        }
        deleteImagesByIdFromStorage(deletionList)

        val downloadList = ArrayList<Long>()
        val set1 = localIdList.intersect(cloudIdList)
        for(id in cloudIdList){
            if(!set1.contains(id))
                downloadList.add(id)
        }
        val updatedList = downloadCloudImages(downloadList)
        newIdList.addAll(updatedList)
        return newIdList
    }

    private fun deleteImagesByIdFromStorage(idList: ArrayList<Long>){
        val imagesList = imagesDao.getImagesByIds(idList)
        for(image in imagesList){
            val file = File(image.imagePath)
            if (file.exists())
                file.delete()
            imagesDao.deleteImageById(image.imageId!!)
        }
    }

    private fun compareIdListLocalPrefer(localIdList: ArrayList<Long>, cloudIdList: ArrayList<Long>): ArrayList<Long>{
        val newIdList = ArrayList<Long>()
        val deletedList = ArrayList<Long>()
        val set1 = localIdList.toHashSet()
        for(id in cloudIdList){
            if(set1.contains(id))
                newIdList.add(id)
            else
                deletedList.add(id)
        }
        deleteImagesFromCloud(deletedList)

        val set = localIdList.intersect(cloudIdList)
        val uploadList = ArrayList<Long>()
        for(id in localIdList){
            if(!set.contains(id))
                uploadList.add(id)
        }
        val updatedList = uploadImages(uploadList)
        newIdList.addAll(updatedList)
        return newIdList
    }

    private fun downloadCloudImages(idList: List<Long>): ArrayList<Long>{
        val tempImageFileSystem: MutableList<ImageData> = imageFileSystem ?: getImageFileSystem().toMutableList()
        val newIdList = ArrayList<Long>()
        var lastCloudId = tempImageFileSystem.last().imageId!! + 1
        var lastLocalId = imagesDao.getLastId() + 1
        var index: Int
        var imageBitmap: Bitmap?
        for(id in idList){
            index = tempImageFileSystem.binarySearchBy(id){it.imageId}
            if(tempImageFileSystem[index].imageId == id){
                imageBitmap = getImageFromCloud(tempImageFileSystem[index])
                if(imageBitmap != null){
                    val newId = if(id >= lastLocalId)
                        id
                    else{
                        if(lastLocalId > lastCloudId)
                            lastLocalId++
                        else
                            lastCloudId++
                    }

                    val path = saveBitmapToStorage(imageBitmap)
                    if(path != null) {
                        val imageData = ImageData(
                            newId,
                            path,
                            tempImageFileSystem[index].dateCreated,
                            tempImageFileSystem[index].dateModified,
                            tempImageFileSystem[index].gDriveId,
                            tempImageFileSystem[index].type
                        )
                        imagesDao.insert(imageData)
                        newIdList.add(newId)
                        if(newId != id){
                            tempImageFileSystem.removeAt(index)
                            index = tempImageFileSystem.binarySearchBy(newId){it.imageId}
                            tempImageFileSystem.add(index, imageData)
                        }
                    }
                    imageBitmap.recycle()
                }
            }
        }
        imageFileSystem = tempImageFileSystem
        return newIdList
    }

    private fun saveBitmapToStorage(image: Bitmap): String?{
        val path = context.applicationContext.filesDir.toString()
        val time = Calendar.getInstance().timeInMillis
        val file = File(path, "$time.png")
        try {
            val fos = FileOutputStream(file)
            image.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
            return path
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun uploadNewNote(note: Note, fileSystem: MutableList<NoteFile>): MutableList<NoteFile>{
        val noteContent = if (isImageType(note.noteType)) {
            val imageNoteContent = Gson().fromJson(note.noteContent, ImageNoteContent::class.java)
            val newIdsList = uploadImages(imageNoteContent.idList)
            imageNoteContent.idList = newIdsList
            val newNoteContent = Gson().toJson(imageNoteContent)
            notesDao.updateNoteContent(note.nId!!, newNoteContent)
            newNoteContent
        } else {
            note.noteContent
        }

        val newFileContent = NoteContent(
            note.noteTitle,
            noteContent,
            note.noteColor,
            note.noteType,
            note.reminderTime
        )
        val fileId = createFile(note.nId!!, newFileContent)
        notesDao.updateSyncedState(note.nId!!, fileId, true)
        val index = fileSystem.binarySearchBy(note.nId) { it.nId }
        fileSystem.add(
            index, NoteFile(
                note.nId,
                note.dateCreated,
                note.dateModified,
                fileId
            )
        )
        return fileSystem
    }

    private fun uploadImages(idsList: ArrayList<Long>): ArrayList<Long> {
        val tempImageFileSystem: MutableList<ImageData> = imageFileSystem ?: getImageFileSystem().toMutableList()

        val imagesList = imagesDao.getImagesByIds(idsList)
        val newIdsList = ArrayList<Long>()
        var lastCloudId = tempImageFileSystem.last().imageId!!
        var lastLocalId = imagesDao.getLastId()
        var index: Int

        for (imageData in imagesList) {
            index = tempImageFileSystem.binarySearchBy(imageData.imageId) { it.imageId }
            val imageId: Long = if (tempImageFileSystem[index].imageId == imageData.imageId) {
                if (lastCloudId + 1 > lastLocalId) {
                    imagesDao.updateImageId(imageData.imageId!!, lastCloudId + 1)
                    ++lastCloudId
                }else{
                    imagesDao.updateImageId(imageData.imageId!!, lastLocalId + 1)
                    ++lastLocalId
                }
            } else {
                imageData.imageId!!
            }

            val file = File(imageData.imagePath)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            val fileId = createImageFile(imageId, bitmap)
            tempImageFileSystem.add(
                index, ImageData(
                    imageId,
                    imageData.imagePath,
                    imageData.dateCreated,
                    imageData.dateModified,
                    fileId
                )
            )
            newIdsList.add(imageId)
        }
        imageFileSystem = tempImageFileSystem
        return newIdsList
    }

    private fun isImageType(noteType: Int): Boolean {
        return when (noteType) {
            IMAGE_DEFAULT -> true
            IMAGE_ARCHIVED -> true
            IMAGE_TRASH -> true
            IMAGE_LIST_DEFAULT -> true
            IMAGE_LIST_ARCHIVED -> true
            IMAGE_LIST_TRASH -> true
            else -> false
        }
    }

    private fun getImageFileSystem(): List<ImageData> {
        return if (mDriveType == CLOUD_GOOGLE_DRIVE) {
            val imageFileSystemId = getImageFileSystemId(googleDriveHelper.appFolderId)
            googleDriveHelper.imageFileSystemId = imageFileSystemId
            getImagesListGD(imageFileSystemId)
        }else{
            getImagesListDB()
        }
    }

    private fun getImageFileSystemId(parentFolderId: String): String{
        var imageFileSystemId: String? = googleDriveHelper.searchFile(IMAGE_FILE_SYSTEM_FILENAME, FILE_TYPE_TEXT)
        if (imageFileSystemId == null) {
            Log.d(TAG, "Image File system not found")
            val imagesList = ArrayList<ImageData>()
            val fileContent = if (isEncrypted)
                aesHelper.encrypt(Gson().toJson(imagesList))
            else
                Gson().toJson(imagesList)

            imageFileSystemId = googleDriveHelper.createFile(
                parentFolderId,
                IMAGE_FILE_SYSTEM_FILENAME,
                FILE_TYPE_TEXT,
                fileContent
            )
        }
        return imageFileSystemId
    }

    private fun getFileSystem(): List<NoteFile> {
        return if (mDriveType == CLOUD_GOOGLE_DRIVE) {
            val appFolderId = getAppFolderId()
            val fileSystemId = getFileSystemId(appFolderId)
            googleDriveHelper.appFolderId = appFolderId
            googleDriveHelper.fileSystemId = fileSystemId
            getFilesListGD(fileSystemId)
        } else {
            getFilesListDB()
        }
    }

    private fun getAppFolderId(): String {
        var folderId = googleDriveHelper.searchFile("notes_sync_data_folder_19268", FILE_TYPE_FOLDER)
        if (folderId == null)
            folderId = googleDriveHelper.createFile(
                null, "notes_sync_data_folder_19268",
                FILE_TYPE_FOLDER, null
            )
        return folderId
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

            fileSystemId = googleDriveHelper.createFile(
                parentFolderId,
                FILE_SYSTEM_FILENAME,
                FILE_TYPE_TEXT,
                fileContent
            )
        }
        return fileSystemId
    }

    private fun getImagesListGD(imageFileSystemId: String): List<ImageData>{
        val fileContentString = googleDriveHelper.getFileContent(imageFileSystemId) as String
        val fileContent = if (isEncrypted)
            aesHelper.decrypt(fileContentString)
        else
            fileContentString

        return try{
            Gson().fromJson(fileContent, Array<ImageData>::class.java).asList()
        }catch(e: Exception){
            ArrayList()
        }
    }

    private fun getImagesListDB(): List<ImageData>{
        return if (dropboxHelper.checkIfFileExists(IMAGE_FILE_SYSTEM_FILENAME)) {
            val fileContentString = dropboxHelper.getFileContent(IMAGE_FILE_SYSTEM_FILENAME)
            val fileContent = if (isEncrypted)
                aesHelper.decrypt(fileContentString)
            else
                fileContentString
            try {
                Gson().fromJson(fileContent, Array<ImageData>::class.java).asList()
            }catch (e: Exception){
                ArrayList<ImageData>()
            }
        } else {
            ArrayList()
        }
    }

    private fun getFilesListGD(fileSystemId: String): List<NoteFile> {
        val fileContentString = googleDriveHelper.getFileContent(fileSystemId) as String
        val fileContent = if (isEncrypted)
            aesHelper.decrypt(fileContentString)
        else
            fileContentString

        return try{
            Gson().fromJson(fileContent, Array<NoteFile>::class.java).asList()
        }catch(e: Exception){
            ArrayList()
        }
    }

    private fun getFilesListDB(): List<NoteFile> {
        return if (dropboxHelper.checkIfFileExists(FILE_SYSTEM_FILENAME)) {
            val fileContentString = dropboxHelper.getFileContent(FILE_SYSTEM_FILENAME)
            val fileContent = if (isEncrypted)
                aesHelper.decrypt(fileContentString)
            else
                fileContentString
            try {
                Gson().fromJson(fileContent, Array<NoteFile>::class.java).asList()
            }catch (e: Exception){
                ArrayList<NoteFile>()
            }
        } else {
            ArrayList()
        }
    }

    private fun getGoogleDriveService(): Drive? {
        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
        return if (googleAccount != null) {
            val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
            credential.selectedAccount = googleAccount.account
            Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                JacksonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName(context.getString(R.string.app_name))
                .build()
        } else {
            null
        }
    }

    private fun getDropboxClient(): DbxClientV2? {
        val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Service.MODE_PRIVATE)
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

    private fun checkAndPrepareEncryption() {
        val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(Contract.PREF_ENCRYPTED, false)) {
            val password = prefs.getString(Contract.PREF_CODE, null)
            val userId = prefs.getString(Contract.PREF_ID, null)
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
            val credentialFileId =
                googleDriveHelper.searchFile(CREDENTIALS_FILENAME, FILE_TYPE_TEXT)
            return if (credentialFileId != null)
                googleDriveHelper.getFileContent(credentialFileId)
            else
                null
        } else {
            return if (dropboxHelper.checkIfFileExists(CREDENTIALS_FILENAME))
                dropboxHelper.getFileContent(CREDENTIALS_FILENAME)
            else
                null
        }
    }

    private fun createImageFile(imageId: Long, imageBitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val byteArray = baos.toByteArray()
        val fileContentString = Base64.encodeToString(byteArray, Base64.DEFAULT)
        val fileContent = if (isEncrypted)
            aesHelper.encrypt(fileContentString)
        else
            fileContentString

        return if (mDriveType == CLOUD_GOOGLE_DRIVE) {
            googleDriveHelper.createFile(
                googleDriveHelper.appFolderId,
                "image_$imageId.txt",
                FILE_TYPE_TEXT,
                fileContent
            )
        } else {
            dropboxHelper.writeFile("image_$imageId.txt", fileContent)
            "-1"
        }
    }

    private fun createFile(noteId: Long, noteContent: NoteContent): String {
        val fileContentString = Gson().toJson(noteContent)
        val fileContent = if (isEncrypted)
            aesHelper.encrypt(fileContentString)
        else
            fileContentString

        return if (mDriveType == CLOUD_GOOGLE_DRIVE) {
            googleDriveHelper.createFile(
                googleDriveHelper.appFolderId,
                "$noteId.txt",
                FILE_TYPE_TEXT,
                fileContent
            )
        } else {
            dropboxHelper.writeFile("$noteId.txt", fileContent)
            "-1"
        }
    }

    private fun updateFile(file: NoteFile, fileContentString: String) {
        val fileContent = if (isEncrypted)
            aesHelper.encrypt(fileContentString)
        else
            fileContentString

        if (mDriveType == CLOUD_GOOGLE_DRIVE)
            googleDriveHelper.updateFile(file.gDriveId!!, fileContent)
        else
            dropboxHelper.writeFile("${file.nId}.txt", fileContent)
    }

    private fun deleteFile(note: Note) {
        if(isImageType(note.noteType)){
            val imageNoteContent = Gson().fromJson(note.noteContent, ImageNoteContent::class.java)
            deleteImagesFromCloud(imageNoteContent.idList)
        }
        if (mDriveType == CLOUD_GOOGLE_DRIVE)
            googleDriveHelper.deleteFile(note.gDriveId)
        else
            dropboxHelper.deleteFile("${note.nId}.txt")
    }

    private fun deleteImagesFromCloud(idList: List<Long>){
        val tempImageFileSystem: MutableList<ImageData> = imageFileSystem ?: getImageFileSystem().toMutableList()

        var index: Int
        for(id in idList) {
            index = tempImageFileSystem.binarySearchBy(id){it.imageId}
            if(id == tempImageFileSystem[index].imageId) {
                if (mDriveType == CLOUD_GOOGLE_DRIVE)
                    googleDriveHelper.deleteFile(tempImageFileSystem[index].gDriveId)
                else
                    dropboxHelper.deleteFile("image_${tempImageFileSystem[index].imageId}.txt")
                tempImageFileSystem.removeAt(index)
            }
        }
        imageFileSystem = tempImageFileSystem
    }

    private fun getFileContent(file: NoteFile): String? {
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

    private fun getImageFromCloud(imageData: ImageData): Bitmap? {
        val fileContent = if (mDriveType == CLOUD_GOOGLE_DRIVE) {
            googleDriveHelper.getFileContent(imageData.gDriveId)
        } else {
            dropboxHelper.getFileContent("image_${imageData.imageId}.txt")
        }

        return if (fileContent != null) {
            val fileContentString = if (isEncrypted)
                aesHelper.decrypt(fileContent)
            else
                fileContent
            val imageBytes = Base64.decode(fileContentString, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } else
            null
    }

    private fun writeFileSystemToCloud(filesList: List<NoteFile>) {
        val fileSystemJsonString = Gson().toJson(filesList)
        val fileSystemJson = if (isEncrypted)
            aesHelper.encrypt(fileSystemJsonString)
        else
            fileSystemJsonString

        if (mDriveType == CLOUD_GOOGLE_DRIVE)
            googleDriveHelper.updateFile(googleDriveHelper.fileSystemId, fileSystemJson)
        else
            dropboxHelper.writeFile(FILE_SYSTEM_FILENAME, fileSystemJson)
    }

    private fun writeImageFileSystemToCloud(filesList: List<ImageData>) {
        val imageFileSystemJsonString = Gson().toJson(filesList)
        val imageFileSystemJson = if (isEncrypted)
            aesHelper.encrypt(imageFileSystemJsonString)
        else
            imageFileSystemJsonString

        if (mDriveType == CLOUD_GOOGLE_DRIVE)
            googleDriveHelper.updateFile(googleDriveHelper.imageFileSystemId, imageFileSystemJson)
        else
            dropboxHelper.writeFile(IMAGE_FILE_SYSTEM_FILENAME, imageFileSystemJson)
    }

    private fun updateWidgets() {
        val intent = Intent(context, NotesWidget::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, NotesWidget::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }

    private fun getLoginStatus(prefs: SharedPreferences?): Int {
        if (prefs != null && prefs.contains(PREF_CLOUD_TYPE)) {
            if (prefs.getInt(PREF_CLOUD_TYPE, CLOUD_GOOGLE_DRIVE) == CLOUD_DROPBOX) {
                if (prefs.contains(PREF_ACCESS_TOKEN) && prefs.getString(PREF_ACCESS_TOKEN, null) != null)
                    return CLOUD_DROPBOX
            } else {
                if (GoogleSignIn.getLastSignedInAccount(context) != null)
                    return CLOUD_GOOGLE_DRIVE
            }
        }
        return -1
    }
}