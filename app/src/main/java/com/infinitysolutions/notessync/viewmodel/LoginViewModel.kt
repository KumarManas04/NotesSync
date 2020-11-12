package com.infinitysolutions.notessync.viewmodel

import android.util.Base64
import android.util.Base64InputStream
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.util.IOUtils
import com.google.gson.Gson
import com.infinitysolutions.notessync.contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.contracts.Contract.Companion.CREDENTIALS_FILENAME
import com.infinitysolutions.notessync.contracts.Contract.Companion.ENCRYPTED_CHECK_ERROR
import com.infinitysolutions.notessync.contracts.Contract.Companion.ENCRYPTED_NO
import com.infinitysolutions.notessync.contracts.Contract.Companion.ENCRYPTED_YES
import com.infinitysolutions.notessync.contracts.Contract.Companion.FILE_SYSTEM_FILENAME
import com.infinitysolutions.notessync.contracts.Contract.Companion.FILE_TYPE_TEXT
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_FILE_SYSTEM_FILENAME
import com.infinitysolutions.notessync.contracts.Contract.Companion.PASSWORD_CHANGE_NETWORK_ERROR
import com.infinitysolutions.notessync.contracts.Contract.Companion.PASSWORD_CHANGE_OLD_INVALID
import com.infinitysolutions.notessync.contracts.Contract.Companion.PASSWORD_CHANGE_SUCCESS
import com.infinitysolutions.notessync.contracts.Contract.Companion.PASSWORD_VERIFY_CORRECT
import com.infinitysolutions.notessync.contracts.Contract.Companion.PASSWORD_VERIFY_ERROR
import com.infinitysolutions.notessync.contracts.Contract.Companion.PASSWORD_VERIFY_INVALID
import com.infinitysolutions.notessync.model.ImageData
import com.infinitysolutions.notessync.model.NoteFile
import com.infinitysolutions.notessync.util.AES256Helper
import com.infinitysolutions.notessync.util.DropboxHelper
import com.infinitysolutions.notessync.util.GoogleDriveHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

class LoginViewModel : ViewModel() {

    private val encryptionCheckLoading = MutableLiveData<Boolean>()
    private val encryptionCheckResult = MutableLiveData<Int>()
    private val loadingMessage = MutableLiveData<String>()
    private val verifyPasswordResult = MutableLiveData<Int>()
    private val secureDataResult = MutableLiveData<Boolean>()
    private val changePasswordResult = MutableLiveData<Int>()
    var isLoginInitialized = false
    var encryptionDetected = false
    var isLoginSuccess = false
    lateinit var googleDriveHelper: GoogleDriveHelper
    lateinit var dropboxHelper: DropboxHelper
    private lateinit var localStoragePath: String
    private val TAG = "LoginViewModel"

    fun setLocalStoragePath(path: String){
        localStoragePath = path
    }

    fun changePassword(userId: String, cloudType: Int, oldPassword: String, newPassword: String) {
        loadingMessage.value = "Updating password..."
        viewModelScope.launch(Dispatchers.IO) {
            val aesHelper = AES256Helper()
            aesHelper.generateKey(oldPassword, userId)
            try {
                val key = getPresentCredential(aesHelper, cloudType)
                if (key != null) {
                    aesHelper.generateKey(newPassword, userId)
                    val encryptedKey = aesHelper.encrypt(key)
                    val result = uploadKey(encryptedKey, cloudType)
                    withContext(Dispatchers.Main) {
                        loadingMessage.value = null
                        changePasswordResult.value =
                            if (result) PASSWORD_CHANGE_SUCCESS else PASSWORD_CHANGE_NETWORK_ERROR
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loadingMessage.value = null
                        changePasswordResult.value = PASSWORD_CHANGE_OLD_INVALID
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingMessage.value = null
                    changePasswordResult.value = PASSWORD_CHANGE_NETWORK_ERROR
                }
            }
        }
    }

    private fun getPresentCredential(aesHelper: AES256Helper, cloudType: Int): String? {
        var content: String? = if (cloudType == CLOUD_GOOGLE_DRIVE) {
            val credentialFileId = googleDriveHelper.searchFile(CREDENTIALS_FILENAME, FILE_TYPE_TEXT)
            if (credentialFileId != null)
                googleDriveHelper.getFileContent(credentialFileId)
            else
                null
        } else {
            if (dropboxHelper.checkIfFileExists(CREDENTIALS_FILENAME))
                dropboxHelper.getFileContent(CREDENTIALS_FILENAME)
            else
                null
        }

        try {
            if (content != null) {
                content = aesHelper.decrypt(content)
            }
        } catch (e: AEADBadTagException) {
            return null
        }
        return content
    }

    fun checkCloudEncryption(cloudType: Int) {
        encryptionCheckLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encryptionFound = checkForEncryption(cloudType)
                withContext(Dispatchers.Main) {
                    encryptionCheckLoading.value = false
                    if (encryptionFound)
                        encryptionCheckResult.value = ENCRYPTED_YES
                    else
                        encryptionCheckResult.value = ENCRYPTED_NO
                    encryptionDetected = encryptionFound
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    encryptionCheckLoading.value = false
                    encryptionCheckResult.value = ENCRYPTED_CHECK_ERROR
                    encryptionDetected = false
                }
            }
        }
    }

    private fun checkForEncryption(cloudType: Int): Boolean {
        if (cloudType == CLOUD_GOOGLE_DRIVE) {
            val credentialFileId = googleDriveHelper.searchFile(CREDENTIALS_FILENAME, FILE_TYPE_TEXT) ?: return false

            val content = googleDriveHelper.getFileContent(credentialFileId)
            return content != null
        } else {
            return dropboxHelper.checkIfFileExists(CREDENTIALS_FILENAME)
        }
    }

    fun runVerification(userId: String, cloudType: Int, password: String) {
        loadingMessage.value = "Verifying password..."
        viewModelScope.launch(Dispatchers.IO) {
            val result = verifyPassword(password, userId, cloudType)
            withContext(Dispatchers.Main) {
                loadingMessage.value = null
                if (result != null) {
                    if (result)
                        verifyPasswordResult.value = PASSWORD_VERIFY_CORRECT
                    else
                        verifyPasswordResult.value = PASSWORD_VERIFY_INVALID
                } else {
                    verifyPasswordResult.value = PASSWORD_VERIFY_ERROR
                }
            }
        }
    }

    private fun verifyPassword(password: String, userId: String, cloudType: Int): Boolean? {
        val aesHelper = AES256Helper()
        aesHelper.generateKey(password, userId)

        if (cloudType == CLOUD_GOOGLE_DRIVE) {
            val credentialFileId =
                googleDriveHelper.searchFile(CREDENTIALS_FILENAME, FILE_TYPE_TEXT)
            return if (credentialFileId != null) {
                val content = googleDriveHelper.getFileContent(credentialFileId)
                if (content != null) {
                    try {
                        aesHelper.decrypt(content)
                        true
                    } catch (e: AEADBadTagException) {
                        false
                    }
                } else {
                    null
                }
            } else {
                null
            }
        } else {
            return if (dropboxHelper.checkIfFileExists(CREDENTIALS_FILENAME)) {
                val content = dropboxHelper.getFileContent(CREDENTIALS_FILENAME)
                try {
                    aesHelper.decrypt(content)
                    true
                } catch (e: AEADBadTagException) {
                    false
                }
            } else {
                null
            }
        }
    }

    fun secureCloudData(userId: String, cloudType: Int, password: String) {
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
                loadingMessage.value = null
                secureDataResult.value = result && encryptionResult
            }
        }
    }

    private fun encryptAllCloudData(cloudType: Int, userId: String, password: String): Boolean {
        return try {
            val aesHelper = AES256Helper()
            aesHelper.generateKey(password, userId)
            val filesList = if (cloudType == CLOUD_GOOGLE_DRIVE)
                getFilesListGD(googleDriveHelper.fileSystemId)
            else
                getFilesListDB()

            encryptNoteFiles(filesList, aesHelper, cloudType)
            val imageFilesList = if(cloudType == CLOUD_GOOGLE_DRIVE)
                getImagesListGD(googleDriveHelper.imageFileSystemId)
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
        var tempFis: FileInputStream
        var tempFos: FileOutputStream
        var base64Stream: Base64InputStream
        var fis: FileInputStream
        var inputStream: FileInputStream
        for (imageFile in filesList) {
            Log.d(TAG, "ImageId: ${imageFile.imageId}")
            // Get the file and write it to 'temp.txt'
            if (cloudType == CLOUD_GOOGLE_DRIVE)
                googleDriveHelper.getFileContentStream(imageFile.gDriveId, localStoragePath)
            else
                dropboxHelper.getFileContentStream("image_${imageFile.imageId}.txt", localStoragePath)

            // Read from 'temp.txt' and write Base64Decoded content to 'temp1.txt'
            if(tempFile1.exists())
                tempFile1.delete()
            tempFis = FileInputStream(tempFile)
            tempFos = FileOutputStream(tempFile1)
            base64Stream = Base64InputStream(tempFis, Base64.DEFAULT)
            IOUtils.copy(base64Stream, tempFos)
            base64Stream.close()
            tempFos.flush()
            tempFos.close()

            // Send 'temp1.txt' content to encrypt and save to 'temp.txt'
            fis = FileInputStream(tempFile1)
            aesHelper.encryptStream(fis, tempFile.absolutePath)

            // Upload 'temp.txt' content to cloud
            if(cloudType == CLOUD_GOOGLE_DRIVE){
                googleDriveHelper.updateFileStream(imageFile.gDriveId!!, tempFile.absolutePath)
            }else{
                inputStream = FileInputStream(tempFile)
                dropboxHelper.writeFileStream("image_${imageFile.imageId}.txt", inputStream)
            }
        }

        val imageFileSystemString = Gson().toJson(filesList)
        val imageFileSystemStringEncrypted = aesHelper.encrypt(imageFileSystemString)
        if(cloudType == CLOUD_GOOGLE_DRIVE)
            googleDriveHelper.updateFile(googleDriveHelper.imageFileSystemId, imageFileSystemStringEncrypted)
        else
            dropboxHelper.writeFile(IMAGE_FILE_SYSTEM_FILENAME, imageFileSystemStringEncrypted)
    }

    private fun encryptNoteFiles(filesList: List<NoteFile>, aesHelper: AES256Helper, cloudType: Int) {
        var fileContent: String
        var fileContentEncrypted: String
        for (file in filesList) {
            if (cloudType == CLOUD_GOOGLE_DRIVE) {
                fileContent = googleDriveHelper.getFileContent(file.gDriveId) as String
                fileContentEncrypted = aesHelper.encrypt(fileContent)
                googleDriveHelper.updateFile(file.gDriveId!!, fileContentEncrypted)
            } else {
                fileContent = dropboxHelper.getFileContent("${file.nId}.txt")
                fileContentEncrypted = aesHelper.encrypt(fileContent)
                dropboxHelper.writeFile("${file.nId}.txt", fileContentEncrypted)
            }
        }

        val fileSystemJson = Gson().toJson(filesList)
        val fileSystemJsonEncrypted = aesHelper.encrypt(fileSystemJson)
        if (cloudType == CLOUD_GOOGLE_DRIVE) {
            googleDriveHelper.updateFile(googleDriveHelper.fileSystemId, fileSystemJsonEncrypted)
        } else {
            dropboxHelper.writeFile(FILE_SYSTEM_FILENAME, fileSystemJsonEncrypted)
        }
    }

    private fun uploadKey(encryptedKey: String, cloudType: Int): Boolean {
        return try {
            if (cloudType == CLOUD_GOOGLE_DRIVE) {
                val credentialFileId = googleDriveHelper.searchFile(
                    CREDENTIALS_FILENAME,
                    FILE_TYPE_TEXT
                )
                if (credentialFileId != null) {
                    googleDriveHelper.updateFile(credentialFileId, encryptedKey)
                } else {
                    googleDriveHelper.createFile(
                        googleDriveHelper.appFolderId,
                        CREDENTIALS_FILENAME,
                        FILE_TYPE_TEXT,
                        encryptedKey
                    )
                }
            } else {
                dropboxHelper.writeFile(CREDENTIALS_FILENAME, encryptedKey)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getImagesListGD(imageFileSystemId: String): List<ImageData> {
        val fileContentString = googleDriveHelper.getFileContent(imageFileSystemId) as String
        return Gson().fromJson(fileContentString, Array<ImageData>::class.java).asList()
    }

    private fun getImagesListDB(): List<ImageData> {
        return if (dropboxHelper.checkIfFileExists(IMAGE_FILE_SYSTEM_FILENAME)) {
            val fileContentString = dropboxHelper.getFileContent(IMAGE_FILE_SYSTEM_FILENAME)
            Gson().fromJson(fileContentString, Array<ImageData>::class.java).asList()
        } else {
            ArrayList()
        }
    }

    private fun getFilesListGD(fileSystemId: String): List<NoteFile> {
        val fileContent = googleDriveHelper.getFileContent(fileSystemId)
        return Gson().fromJson(fileContent, Array<NoteFile>::class.java).asList()
    }

    private fun getFilesListDB(): List<NoteFile> {
        return if (dropboxHelper.checkIfFileExists(FILE_SYSTEM_FILENAME)) {
            val fileContent = dropboxHelper.getFileContent(FILE_SYSTEM_FILENAME)
            Gson().fromJson(fileContent, Array<NoteFile>::class.java).asList()
        } else {
            val filesList = ArrayList<NoteFile>()
            val fileContent = Gson().toJson(filesList)
            dropboxHelper.writeFile(FILE_SYSTEM_FILENAME, fileContent)
            filesList
        }
    }

    fun resetViewModel() {
        encryptionCheckLoading.value = null
        encryptionCheckResult.value = null
        verifyPasswordResult.value = null
        loadingMessage.value = null
        secureDataResult.value = null
        isLoginInitialized = false
        encryptionDetected = false
        isLoginSuccess = false
    }

    fun getEncryptionCheckResult(): LiveData<Int> = encryptionCheckResult
    fun getEncryptionCheckLoading(): LiveData<Boolean> = encryptionCheckLoading
    fun getVerifyPasswordResult(): LiveData<Int> = verifyPasswordResult
    fun getSecureDataResult(): LiveData<Boolean> = secureDataResult
    fun getLoadingMessage(): LiveData<String> = loadingMessage
    fun getChangePasswordResult(): LiveData<Int> = changePasswordResult
}