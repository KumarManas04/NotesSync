package com.infinitysolutions.notessync.encrypt

import android.util.Base64
import android.util.Base64InputStream
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.util.IOUtils
import com.google.gson.Gson
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.contracts.Contract.Companion.CREDENTIALS_FILENAME
import com.infinitysolutions.notessync.contracts.Contract.Companion.FILE_SYSTEM_FILENAME
import com.infinitysolutions.notessync.contracts.Contract.Companion.FILE_TYPE_TEXT
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_FILE_SYSTEM_FILENAME
import com.infinitysolutions.notessync.model.ImageData
import com.infinitysolutions.notessync.model.NoteFile
import com.infinitysolutions.notessync.util.AES256Helper
import com.infinitysolutions.notessync.util.DropboxHelper
import com.infinitysolutions.notessync.util.GoogleDriveHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom

class EncryptionViewModel: ViewModel() {
    private val TAG = "EncryptionViewModel"
    private val loadingMessage = MutableLiveData<String>()
    private val secureDataResult = MutableLiveData<Boolean>()
    var googleDriveHelper: GoogleDriveHelper? = null
    var dropboxHelper: DropboxHelper? = null
    private var exitBlocked: Boolean = false
    private lateinit var localStoragePath: String

    fun setLocalStoragePath(path: String){
        localStoragePath = path
    }

    fun prepareDriveHelpers(){
        loadingMessage.value = "Preparing..."
        GlobalScope.launch(Dispatchers.IO) {
            val driveHelper = googleDriveHelper
            if(driveHelper != null) {
                val appFolderId = getAppFolderId(driveHelper)
                driveHelper.appFolderId = appFolderId
                driveHelper.fileSystemId = getFileSystemId(appFolderId, driveHelper)
                driveHelper.imageFileSystemId = getImageFileSystemId(appFolderId, driveHelper)
            }

            withContext(Dispatchers.Main) {
                loadingMessage.value = null
//                encryptionViewModel.checkCloudEncryption(cloudType)
            }
        }
    }

    fun secureCloudData(userId: String, cloudType: Int, password: String) {
        exitBlocked = true
        loadingMessage.value = "Securing your data..."
        viewModelScope.launch(Dispatchers.IO) {
            val secureRandom = SecureRandom()
            val key = ByteArray(32)
            secureRandom.nextBytes(key)

            //Encrypt all user data in cloud storage
            val encryptionResult = encryptAllCloudData(cloudType, userId, key.toString())

            //Encrypt the encryption key
            val aesHelper = AES256Helper()
            aesHelper.generateKey(password, userId)
            val encryptedKey = aesHelper.encrypt(key.toString())
            val result = uploadKey(encryptedKey, cloudType)
            withContext(Dispatchers.Main) {
                exitBlocked = false
                loadingMessage.value = null
                secureDataResult.value = result && encryptionResult
            }
        }
    }

    private fun uploadKey(encryptedKey: String, cloudType: Int): Boolean {
        return try {
            if (cloudType == CLOUD_GOOGLE_DRIVE) {
                val driveHelper = googleDriveHelper ?: return false
                val credentialFileId = driveHelper.searchFile(
                    CREDENTIALS_FILENAME,
                    FILE_TYPE_TEXT
                )
                if (credentialFileId != null) {
                    driveHelper.updateFile(credentialFileId, encryptedKey)
                } else {
                    driveHelper.createFile(
                        driveHelper.appFolderId,
                        CREDENTIALS_FILENAME,
                        FILE_TYPE_TEXT,
                        encryptedKey
                    )
                }
            } else {
                val driveHelper = dropboxHelper ?: return false
                driveHelper.writeFile(CREDENTIALS_FILENAME, encryptedKey)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun encryptAllCloudData(cloudType: Int, userId: String, password: String): Boolean {
        return try {
            val aesHelper = AES256Helper()
            aesHelper.generateKey(password, userId)
            val filesList = if (cloudType == CLOUD_GOOGLE_DRIVE)
                getFilesListGD()
            else
                getFilesListDB()

            encryptNoteFiles(filesList, aesHelper, cloudType)
            val imageFilesList = if(cloudType == CLOUD_GOOGLE_DRIVE)
                getImagesListGD()
            else
                getImagesListDB()
            Log.d(TAG, "Encrypted file system")

            encryptImageFiles(imageFilesList, aesHelper, cloudType)
            Log.d(TAG, "Encrypted image file system")
            true
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun encryptImageFiles(filesList: List<ImageData>, aesHelper: AES256Helper, cloudType: Int){
        val tempFile = File(localStoragePath, "temp.txt")
        val tempFile1 = File(localStoragePath, "temp1.txt")
        for (imageFile in filesList) {
            Log.d(TAG, "ImageId: ${imageFile.imageId}")
            // Get the file and write it to 'temp.txt'
            if (cloudType == CLOUD_GOOGLE_DRIVE) {
                val driveHelper = googleDriveHelper ?: return
                driveHelper.getFileContentStream(imageFile.gDriveId, localStoragePath)
            }else {
                val driveHelper = dropboxHelper ?: return
                driveHelper.getFileContentStream(
                    "image_${imageFile.imageId}.txt",
                    localStoragePath
                )
            }

            // Read from 'temp.txt' and write Base64Decoded content to 'temp1.txt'
            if(tempFile1.exists())
                tempFile1.delete()
            val tempFis = FileInputStream(tempFile)
            val tempFos = FileOutputStream(tempFile1)
            val base64Stream = Base64InputStream(tempFis, Base64.DEFAULT)
            IOUtils.copy(base64Stream, tempFos)
            base64Stream.close()
            tempFos.flush()
            tempFos.close()

            // Send 'temp1.txt' content to encrypt and save to 'temp.txt'
            val fis = FileInputStream(tempFile1)
            aesHelper.encryptStream(fis, tempFile.absolutePath)

            // Upload 'temp.txt' content to cloud
            if(cloudType == CLOUD_GOOGLE_DRIVE){
                val driveHelper = googleDriveHelper ?: return
                driveHelper.updateFileStream(imageFile.gDriveId!!, tempFile.absolutePath)
            }else{
                val inputStream = FileInputStream(tempFile)
                val driveHelper = dropboxHelper ?: return
                driveHelper.writeFileStream("image_${imageFile.imageId}.txt", inputStream)
            }
        }

        val imageFileSystemString = Gson().toJson(filesList)
        val imageFileSystemStringEncrypted = aesHelper.encrypt(imageFileSystemString)
        if(cloudType == CLOUD_GOOGLE_DRIVE) {
            val driveHelper = googleDriveHelper ?: return
            driveHelper.updateFile(driveHelper.imageFileSystemId, imageFileSystemStringEncrypted)
        }else {
            val driveHelper = dropboxHelper ?: return
            driveHelper.writeFile(IMAGE_FILE_SYSTEM_FILENAME, imageFileSystemStringEncrypted)
        }
    }

    private fun encryptNoteFiles(filesList: List<NoteFile>, aesHelper: AES256Helper, cloudType: Int) {
        for (file in filesList) {
            if (cloudType == CLOUD_GOOGLE_DRIVE) {
                val driveHelper = googleDriveHelper ?: return
                val fileContent = driveHelper.getFileContent(file.gDriveId) as String
                val fileContentEncrypted = aesHelper.encrypt(fileContent)
                driveHelper.updateFile(file.gDriveId!!, fileContentEncrypted)
            } else {
                val driveHelper = dropboxHelper ?: return
                val fileContent = driveHelper.getFileContent("${file.nId}.txt")
                val fileContentEncrypted = aesHelper.encrypt(fileContent)
                driveHelper.writeFile("${file.nId}.txt", fileContentEncrypted)
            }
        }

        val fileSystemJson = Gson().toJson(filesList)
        val fileSystemJsonEncrypted = aesHelper.encrypt(fileSystemJson)
        if (cloudType == CLOUD_GOOGLE_DRIVE) {
            val driveHelper = googleDriveHelper ?: return
            driveHelper.updateFile(driveHelper.fileSystemId, fileSystemJsonEncrypted)
        } else {
            val driveHelper = dropboxHelper ?: return
            driveHelper.writeFile(FILE_SYSTEM_FILENAME, fileSystemJsonEncrypted)
        }
    }

    private fun getImagesListGD(): List<ImageData> {
        val driveHelper = googleDriveHelper ?: return ArrayList()
        val imageFileSystemId = driveHelper.imageFileSystemId
        val fileContentString = driveHelper.getFileContent(imageFileSystemId) as String
        return Gson().fromJson(fileContentString, Array<ImageData>::class.java).asList()
    }

    private fun getImagesListDB(): List<ImageData> {
        val driveHelper = dropboxHelper ?: return ArrayList()
        return if (driveHelper.checkIfFileExists(IMAGE_FILE_SYSTEM_FILENAME)) {
            val fileContentString = driveHelper.getFileContent(IMAGE_FILE_SYSTEM_FILENAME)
            Gson().fromJson(fileContentString, Array<ImageData>::class.java).asList()
        } else {
            ArrayList()
        }
    }

    private fun getFilesListGD(): List<NoteFile> {
        val driveHelper = googleDriveHelper ?: return ArrayList()
        val fileSystemId = driveHelper.fileSystemId
        val fileContent = driveHelper.getFileContent(fileSystemId)
        return Gson().fromJson(fileContent, Array<NoteFile>::class.java).asList()
    }

    private fun getFilesListDB(): List<NoteFile> {
        val driveHelper = dropboxHelper ?: return ArrayList()
        return if (driveHelper.checkIfFileExists(FILE_SYSTEM_FILENAME)) {
            val fileContent = driveHelper.getFileContent(FILE_SYSTEM_FILENAME)
            Gson().fromJson(fileContent, Array<NoteFile>::class.java).asList()
        } else {
            val filesList = ArrayList<NoteFile>()
            val fileContent = Gson().toJson(filesList)
            driveHelper.writeFile(FILE_SYSTEM_FILENAME, fileContent)
            filesList
        }
    }

    private fun getAppFolderId(driveHelper: GoogleDriveHelper): String {
        var folderId =
            driveHelper.searchFile("notes_sync_data_folder_19268", Contract.FILE_TYPE_FOLDER)
        if (folderId == null)
            folderId = driveHelper.createFile(
                null, "notes_sync_data_folder_19268",
                Contract.FILE_TYPE_FOLDER, null
            )
        return folderId
    }

    private fun getFileSystemId(parentFolderId: String, driveHelper: GoogleDriveHelper): String {
        var fileSystemId: String? =
            driveHelper.searchFile(FILE_SYSTEM_FILENAME, FILE_TYPE_TEXT)
        if (fileSystemId == null) {
            val filesList = ArrayList<NoteFile>()
            val fileContent = Gson().toJson(filesList)

            fileSystemId = driveHelper.createFile(parentFolderId, FILE_SYSTEM_FILENAME, FILE_TYPE_TEXT, fileContent)
        }
        return fileSystemId
    }

    private fun getImageFileSystemId(parentFolderId: String, driveHelper: GoogleDriveHelper): String {
        var imageFileSystemId: String? = driveHelper.searchFile(
            IMAGE_FILE_SYSTEM_FILENAME,
            FILE_TYPE_TEXT
        )
        if (imageFileSystemId == null) {
            Log.d(TAG, "Image File system not found")
            val imagesList = ArrayList<ImageData>()
            val fileContent = Gson().toJson(imagesList)

            imageFileSystemId = driveHelper.createFile(
                parentFolderId,
                IMAGE_FILE_SYSTEM_FILENAME,
                FILE_TYPE_TEXT,
                fileContent
            )
        } else {
            Log.d(TAG, "Image file system found")
        }
        return imageFileSystemId
    }

    fun getSecureDataResult(): LiveData<Boolean> = secureDataResult
    fun getLoadingMessage(): LiveData<String> = loadingMessage
    fun getExitBlocked(): Boolean = exitBlocked
}