package com.infinitysolutions.notessync.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.contracts.Contract.Companion.ENCRYPTED_CHECK_ERROR
import com.infinitysolutions.notessync.contracts.Contract.Companion.ENCRYPTED_NO
import com.infinitysolutions.notessync.contracts.Contract.Companion.ENCRYPTED_YES
import com.infinitysolutions.notessync.util.AES256Helper
import com.infinitysolutions.notessync.util.DropboxHelper
import com.infinitysolutions.notessync.util.GoogleDriveHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

class PasswordViewModel : ViewModel(){
    private val loadingMessage = MutableLiveData<String?>()
    private val verifyPasswordResult = MutableLiveData<Int>()
    private val passwordSetResult = MutableLiveData<Boolean>()
    private val encryptionCheckResult = MutableLiveData<Int>()
    var googleDriveHelper: GoogleDriveHelper? = null
    var dropboxHelper: DropboxHelper? = null

    fun setLoadingMessage(message: String?){
        loadingMessage.value = message
    }

    fun checkCloudEncryption(cloudType: Int) {
        setLoadingMessage("Checking...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encryptionFound = checkForEncryption(cloudType)
                withContext(Dispatchers.Main) {
                    setLoadingMessage(null)
                    if (encryptionFound)
                        encryptionCheckResult.value = ENCRYPTED_YES
                    else
                        encryptionCheckResult.value = ENCRYPTED_NO
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoadingMessage(null)
                    encryptionCheckResult.value = ENCRYPTED_CHECK_ERROR
                }
            }
        }
    }

    private fun checkForEncryption(cloudType: Int): Boolean {
        if (cloudType == Contract.CLOUD_GOOGLE_DRIVE) {
            val driveHelper = googleDriveHelper ?: return false
            val credentialFileId = driveHelper.searchFile(
                Contract.CREDENTIALS_FILENAME,
                Contract.FILE_TYPE_TEXT
            ) ?: return false

            val content = driveHelper.getFileContent(credentialFileId)
            return content != null
        } else {
            val driveHelper = dropboxHelper ?: return false
            return driveHelper.checkIfFileExists(Contract.CREDENTIALS_FILENAME)
        }
    }

    fun runVerification(userId: String, cloudType: Int, password: String) {
        setLoadingMessage("Verifying...")

        viewModelScope.launch(Dispatchers.IO) {
            val result = verifyPassword(password, userId, cloudType)
            withContext(Dispatchers.Main) {
                setLoadingMessage(null)
                if (result != null) {
                    if (result)
                        verifyPasswordResult.value = Contract.PASSWORD_VERIFY_CORRECT
                    else
                        verifyPasswordResult.value = Contract.PASSWORD_VERIFY_INVALID
                } else {
                    verifyPasswordResult.value = Contract.PASSWORD_VERIFY_ERROR
                }
            }
        }
    }

    private fun verifyPassword(password: String, userId: String, cloudType: Int): Boolean? {
        val aesHelper = AES256Helper()
        aesHelper.generateKey(password, userId)

        if (cloudType == Contract.CLOUD_GOOGLE_DRIVE) {
            val driveHelper: GoogleDriveHelper = googleDriveHelper ?: return null
            val credentialFileId =
                driveHelper.searchFile(Contract.CREDENTIALS_FILENAME, Contract.FILE_TYPE_TEXT)
            return if (credentialFileId != null) {
                val content = driveHelper.getFileContent(credentialFileId)
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
            val driveHelper: DropboxHelper = dropboxHelper ?: return null
            return if (driveHelper.checkIfFileExists(Contract.CREDENTIALS_FILENAME)) {
                val content = driveHelper.getFileContent(Contract.CREDENTIALS_FILENAME)
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

    fun changePassword(userId: String, cloudType: Int, oldPassword: String, newPassword: String) {
        setLoadingMessage("Updating password...")
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
                        setLoadingMessage(null)
                        passwordSetResult.value = result
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        setLoadingMessage(null)
                        passwordSetResult.value = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingMessage.value = null
                    passwordSetResult.value = false
                }
            }
        }
    }

    private fun getPresentCredential(aesHelper: AES256Helper, cloudType: Int): String? {
        var content: String? = if (cloudType == Contract.CLOUD_GOOGLE_DRIVE) {
            val driveHelper: GoogleDriveHelper = googleDriveHelper ?: return null
            val credentialFileId = driveHelper.searchFile(
                Contract.CREDENTIALS_FILENAME,
                Contract.FILE_TYPE_TEXT
            )
            if (credentialFileId != null)
                driveHelper.getFileContent(credentialFileId)
            else
                null
        } else {
            val driveHelper: DropboxHelper = dropboxHelper ?: return null
            if (driveHelper.checkIfFileExists(Contract.CREDENTIALS_FILENAME))
                driveHelper.getFileContent(Contract.CREDENTIALS_FILENAME)
            else
                null
        }

        try {
            if (content != null)
                content = aesHelper.decrypt(content)
        } catch (e: AEADBadTagException) {
            return null
        }
        return content
    }

    fun setupPassword(userId: String, cloudType: Int, password: String){
        setLoadingMessage("Securing your data...")
        viewModelScope.launch(Dispatchers.IO) {
            // Generating random 32 bytes key
            val secureRandom = SecureRandom()
            val key = ByteArray(32)
            secureRandom.nextBytes(key)

            // Encrypting the random key with password
            val aesHelper = AES256Helper()
            aesHelper.generateKey(password, userId)
            val encryptedKey = aesHelper.encrypt(key.toString())
            val result = uploadKey(encryptedKey, cloudType)
            withContext(Dispatchers.Main){
                setLoadingMessage(null)
                passwordSetResult.value = result
            }
        }
    }

    private fun uploadKey(encryptedKey: String, cloudType: Int): Boolean {
        return try {
            if (cloudType == Contract.CLOUD_GOOGLE_DRIVE) {
                val driveHelper = googleDriveHelper ?: return false
                val credentialFileId = driveHelper.searchFile(
                    Contract.CREDENTIALS_FILENAME,
                    Contract.FILE_TYPE_TEXT
                )
                if (credentialFileId != null) {
                    driveHelper.updateFile(credentialFileId, encryptedKey)
                } else {
                    driveHelper.createFile(
                        driveHelper.appFolderId,
                        Contract.CREDENTIALS_FILENAME,
                        Contract.FILE_TYPE_TEXT,
                        encryptedKey
                    )
                }
            } else {
                val driveHelper = dropboxHelper ?: return false
                driveHelper.writeFile(Contract.CREDENTIALS_FILENAME, encryptedKey)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getEncryptionCheckResult(): LiveData<Int> = encryptionCheckResult
    fun getVerifyPasswordResult(): LiveData<Int> = verifyPasswordResult
    fun getPasswordSetResult(): LiveData<Boolean> = passwordSetResult
    fun getLoadingMessage(): LiveData<String?> = loadingMessage
}