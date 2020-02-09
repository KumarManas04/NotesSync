package com.infinitysolutions.notessync.Fragments


import android.app.AlertDialog
import android.app.Service
import android.content.Context.MODE_PRIVATE
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.http.OkHttp3Requestor
import com.dropbox.core.v2.DbxClientV2
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.gson.Gson
import com.infinitysolutions.notessync.Contracts.Contract
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.ENCRYPTED_CHECK_ERROR
import com.infinitysolutions.notessync.Contracts.Contract.Companion.ENCRYPTED_NO
import com.infinitysolutions.notessync.Contracts.Contract.Companion.ENCRYPTED_YES
import com.infinitysolutions.notessync.Contracts.Contract.Companion.FILE_SYSTEM_FILENAME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.FILE_TYPE_TEXT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.MODE_CHANGE_PASSWORD
import com.infinitysolutions.notessync.Contracts.Contract.Companion.MODE_NEW_PASSWORD
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_CHANGE_NETWORK_ERROR
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_CHANGE_OLD_INVALID
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_CHANGE_SUCCESS
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_MODE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_VERIFY_CORRECT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_VERIFY_ERROR
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_VERIFY_INVALID
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ACCESS_TOKEN
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_CODE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ENCRYPTED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ID
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.Model.ImageData
import com.infinitysolutions.notessync.Model.NoteFile
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.Util.DropboxHelper
import com.infinitysolutions.notessync.Util.GoogleDriveHelper
import com.infinitysolutions.notessync.Util.WorkSchedulerHelper
import com.infinitysolutions.notessync.ViewModel.LoginViewModel
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.fragment_password.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PasswordFragment : Fragment() {
    private val TAG = "PasswordFragment"
    private lateinit var loginViewModel: LoginViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_password, container, false)
        val userId = arguments?.getString(PREF_ID) as String
        val cloudType = arguments?.getInt(PREF_CLOUD_TYPE) as Int
        val passwordMode = arguments?.getInt(PASSWORD_MODE) as Int
        initDataBinding(rootView, cloudType, userId, passwordMode)
        if (passwordMode == MODE_CHANGE_PASSWORD || passwordMode == MODE_NEW_PASSWORD) {
            loginViewModel.isLoginSuccess = true
        }

        rootView.skip_button.setOnClickListener {
            val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
            val editor = prefs?.edit()
            editor?.putString(PREF_CODE, null)
            editor?.putBoolean(PREF_ENCRYPTED, false)
            editor?.putString(PREF_ID, userId)
            editor?.putInt(PREF_CLOUD_TYPE, cloudType)
            editor?.commit()
            Toast.makeText(activity, getString(R.string.toast_login_successful), LENGTH_SHORT).show()
            loginViewModel.isLoginSuccess = true
            activity?.onBackPressed()
        }

        rootView.submit_button.setOnClickListener {
            if (loginViewModel.encryptionDetected) {
                if (passwordMode == MODE_CHANGE_PASSWORD) {
                    if (rootView.password_edit_text.text.isNotEmpty() && rootView.again_password_edit_text.text.isNotEmpty()) {
                        loginViewModel.changePassword(
                            userId,
                            cloudType,
                            rootView.password_edit_text.text.toString(),
                            rootView.again_password_edit_text.text.toString()
                        )
                    } else {
                        Toast.makeText(activity, getString(R.string.enter_password), LENGTH_SHORT).show()
                    }
                } else {
                    if (rootView.password_edit_text.text.isNotEmpty())
                        loginViewModel.runVerification(
                            userId,
                            cloudType,
                            rootView.password_edit_text.text.toString()
                        )
                    else
                        Toast.makeText(activity, getString(R.string.enter_password), LENGTH_SHORT).show()
                }
            } else {
                if (rootView.password_edit_text.text.isNotEmpty() && rootView.password_edit_text.text.toString() == rootView.again_password_edit_text.text.toString()) {
                    AlertDialog.Builder(context)
                        .setTitle(getString(R.string.warning))
                        .setMessage(getString(R.string.encryption_warning))
                        .setPositiveButton(getString(R.string.yes)) { _: DialogInterface, _: Int ->
                            val mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)
                            mainViewModel.setExitBlocked(true)
                            loginViewModel.secureCloudData(
                                userId,
                                cloudType,
                                rootView.password_edit_text.text.toString()
                            )
                        }
                        .setNegativeButton(getString(R.string.no), null)
                        .setCancelable(true)
                        .show()
                } else {
                    Toast.makeText(activity, getString(R.string.toast_passwords_dont_match), LENGTH_SHORT).show()
                }
            }
        }
        return rootView
    }

    private fun initDataBinding(rootView: View, cloudType: Int, userId: String, passwordMode: Int) {
        loginViewModel = ViewModelProviders.of(activity!!).get(LoginViewModel::class.java)
        loginViewModel.setLocalStoragePath(context!!.filesDir.toString())
        if (!loginViewModel.isLoginInitialized)
            initializeLogin(cloudType)

        loginViewModel.getLoadingMessage()
            .observe(this, androidx.lifecycle.Observer { loadingMessage ->
                if (loadingMessage != null) {
                    rootView.loading_panel.visibility = VISIBLE
                    rootView.input_bar.visibility = GONE
                    rootView.loading_message.text = loadingMessage
                    rootView.skip_button.visibility = GONE
                } else {
                    rootView.loading_panel.visibility = GONE
                    rootView.input_bar.visibility = VISIBLE
                }
            })

        loginViewModel.getEncryptionCheckLoading()
            .observe(this, androidx.lifecycle.Observer { showLoading ->
                if (showLoading != null) {
                    if (showLoading) {
                        rootView.loading_panel.visibility = VISIBLE
                        rootView.input_bar.visibility = GONE
                        rootView.warning_text_view.visibility = GONE
                        rootView.loading_message.text =
                            getString(R.string.check_encryption_loading_text)
                        rootView.skip_button.visibility = GONE
                    } else {
                        rootView.loading_panel.visibility = GONE
                        rootView.input_bar.visibility = VISIBLE
                    }
                }
            })

        loginViewModel.getEncryptionCheckResult()
            .observe(this, androidx.lifecycle.Observer { encryptionResult ->
                if (encryptionResult != null) {
                    when (encryptionResult) {
                        ENCRYPTED_NO -> {
                            rootView.info_text_view.text =
                                getString(R.string.will_user_encrypt_message)
                            rootView.password_edit_text.hint = getString(R.string.enter_new_password)
                            rootView.warning_text_view.visibility = VISIBLE
                            rootView.again_password_edit_text.visibility = VISIBLE
                            rootView.skip_button.visibility = VISIBLE
                        }
                        ENCRYPTED_YES -> {
                            if (passwordMode == MODE_CHANGE_PASSWORD) {
                                rootView.info_text_view.text = getString(R.string.change_password)
                                rootView.password_edit_text.hint = getString(R.string.enter_old_password)
                                rootView.again_password_edit_text.visibility = VISIBLE
                                rootView.again_password_edit_text.hint = getString(R.string.enter_new_password)
                            } else {
                                rootView.info_text_view.text =
                                    getString(R.string.enter_password_to_decrypt_message)
                                rootView.password_edit_text.hint = getString(R.string.enter_password)
                                rootView.again_password_edit_text.visibility = GONE
                            }
                            rootView.warning_text_view.visibility = GONE
                            rootView.skip_button.visibility = GONE
                        }
                        ENCRYPTED_CHECK_ERROR -> {
                            AlertDialog.Builder(context)
                                .setTitle("Network error")
                                .setMessage("Please connect to the internet and press retry.")
                                .setPositiveButton("Retry") { _: DialogInterface, _: Int ->
                                    initializeLogin(cloudType)
                                }
                                .setNegativeButton("Cancel") { _: DialogInterface, _: Int ->
                                    activity?.onBackPressed()
                                }
                                .setCancelable(false)
                                .show()
                        }
                    }
                }
            })

        loginViewModel.getVerifyPasswordResult()
            .observe(this, androidx.lifecycle.Observer { result ->
                if (result != null) {
                    when (result) {
                        PASSWORD_VERIFY_INVALID -> {
                            Toast.makeText(activity, "Incorrect password", LENGTH_SHORT).show()
                            rootView.loading_panel.visibility = GONE
                            rootView.input_bar.visibility = VISIBLE
                        }
                        PASSWORD_VERIFY_CORRECT -> finishLogin(
                            rootView.password_edit_text.text.toString(),
                            userId,
                            cloudType
                        )

                        PASSWORD_VERIFY_ERROR -> {
                            Toast.makeText(activity, "Error occurred", LENGTH_SHORT).show()
                            rootView.loading_panel.visibility = GONE
                            rootView.input_bar.visibility = VISIBLE
                        }
                    }
                }
            })

        loginViewModel.getSecureDataResult().observe(this, androidx.lifecycle.Observer { result ->
            if (result != null) {
                val mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)
                mainViewModel.setExitBlocked(false)
                if (result) {
                    Toast.makeText(activity, "Success", LENGTH_SHORT).show()
                    finishLogin(rootView.password_edit_text.text.toString(), userId, cloudType)
                } else {
                    Toast.makeText(activity, "Error occurred", LENGTH_SHORT).show()
                    rootView.loading_panel.visibility = GONE
                    rootView.input_bar.visibility = VISIBLE
                }
            }
        })

        loginViewModel.getChangePasswordResult()
            .observe(this, androidx.lifecycle.Observer { result ->
                if (result != null) {
                    when (result) {
                        PASSWORD_CHANGE_OLD_INVALID -> {
                            Toast.makeText(activity, "Old password is invalid!", LENGTH_SHORT)
                                .show()
                        }
                        PASSWORD_CHANGE_SUCCESS -> {
                            Toast.makeText(activity, "Password changed", LENGTH_SHORT).show()
                            finishLogin(
                                rootView.again_password_edit_text.text.toString(),
                                userId,
                                cloudType
                            )
                        }
                        PASSWORD_CHANGE_NETWORK_ERROR -> {
                            Toast.makeText(activity, "Network error", LENGTH_SHORT).show()
                        }
                    }
                }
            })
    }

    private fun initializeLogin(cloudType: Int) {
        GlobalScope.launch(Dispatchers.IO) {
            if (cloudType == CLOUD_GOOGLE_DRIVE) {
                val googleDriveService = getGoogleDriveService()
                if (googleDriveService != null) {
                    loginViewModel.googleDriveHelper = GoogleDriveHelper(googleDriveService)
                    val appFolderId = getAppFolderId()
                    loginViewModel.googleDriveHelper.appFolderId = appFolderId
                    loginViewModel.googleDriveHelper.fileSystemId = getFileSystemId(appFolderId)
                    loginViewModel.googleDriveHelper.imageFileSystemId =
                        getImageFileSystemId(appFolderId)
                }
            } else {
                val dropboxClient = getDropboxClient()
                if (dropboxClient != null) {
                    loginViewModel.dropboxHelper = DropboxHelper(dropboxClient)
                }
            }
            loginViewModel.isLoginInitialized = true
            withContext(Dispatchers.Main) {
                loginViewModel.checkCloudEncryption(cloudType)
            }
        }
    }

    private fun finishLogin(password: String, userId: String, cloudType: Int) {
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        val editor = prefs?.edit()
        editor?.putString(PREF_CODE, password)
        editor?.putBoolean(PREF_ENCRYPTED, true)
        editor?.putString(PREF_ID, userId)
        editor?.putInt(PREF_CLOUD_TYPE, cloudType)
        editor?.commit()
        Toast.makeText(activity, getString(R.string.toast_login_successful), LENGTH_SHORT).show()
        WorkSchedulerHelper().syncNotes(true, context!!)
        loginViewModel.isLoginSuccess = true
        activity?.onBackPressed()
    }

    private fun getImageFileSystemId(parentFolderId: String): String {
        var imageFileSystemId: String? = loginViewModel.googleDriveHelper.searchFile(
            Contract.IMAGE_FILE_SYSTEM_FILENAME,
            FILE_TYPE_TEXT
        )
        if (imageFileSystemId == null) {
            Log.d(TAG, "Image File system not found")
            val imagesList = ArrayList<ImageData>()
            val fileContent = Gson().toJson(imagesList)

            imageFileSystemId = loginViewModel.googleDriveHelper.createFile(
                parentFolderId,
                Contract.IMAGE_FILE_SYSTEM_FILENAME,
                FILE_TYPE_TEXT,
                fileContent
            )
        } else {
            Log.d(TAG, "Image file system found")
        }
        return imageFileSystemId
    }

    private fun getFileSystemId(parentFolderId: String): String {
        var fileSystemId: String? =
            loginViewModel.googleDriveHelper.searchFile(FILE_SYSTEM_FILENAME, FILE_TYPE_TEXT)
        if (fileSystemId == null) {
            val filesList = ArrayList<NoteFile>()
            val fileContent = Gson().toJson(filesList)

            fileSystemId = loginViewModel.googleDriveHelper.createFile(
                parentFolderId,
                FILE_SYSTEM_FILENAME,
                FILE_TYPE_TEXT,
                fileContent
            )
        }
        return fileSystemId
    }

    private fun getAppFolderId(): String {
        var folderId =
            loginViewModel.googleDriveHelper.searchFile(
                "notes_sync_data_folder_19268",
                Contract.FILE_TYPE_FOLDER
            )
        if (folderId == null)
            folderId = loginViewModel.googleDriveHelper.createFile(
                null, "notes_sync_data_folder_19268",
                Contract.FILE_TYPE_FOLDER, null
            )
        return folderId
    }

    private fun getGoogleDriveService(): Drive? {
        val googleAccount = GoogleSignIn.getLastSignedInAccount(activity)
        return if (googleAccount != null) {
            val credential =
                GoogleAccountCredential.usingOAuth2(activity, listOf(DriveScopes.DRIVE_FILE))
            credential.selectedAccount = googleAccount.account
            Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                JacksonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName(getString(R.string.app_name))
                .build()
        } else {
            null
        }
    }

    private fun getDropboxClient(): DbxClientV2? {
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, Service.MODE_PRIVATE)
        val accessToken = prefs?.getString(PREF_ACCESS_TOKEN, null)
        return if (accessToken != null) {
            val requestConfig = DbxRequestConfig.newBuilder("Notes-Sync")
                .withHttpRequestor(OkHttp3Requestor(OkHttp3Requestor.defaultOkHttpClient()))
                .build()
            DbxClientV2(requestConfig, accessToken)
        } else {
            null
        }
    }

    override fun onDestroy() {
        if (!loginViewModel.isLoginSuccess) {
            val cloudType = arguments?.getInt(PREF_CLOUD_TYPE) as Int
            val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
            val editor = prefs?.edit()
            if (cloudType == CLOUD_GOOGLE_DRIVE) {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                val googleSignInClient = GoogleSignIn.getClient(context!!, gso)
                googleSignInClient.signOut()
            } else {
                editor?.remove(PREF_ACCESS_TOKEN)
            }
            editor?.remove(PREF_ID)
            editor?.remove(PREF_CLOUD_TYPE)
            editor?.commit()
        }
        loginViewModel.resetViewModel()
        super.onDestroy()
    }
}
