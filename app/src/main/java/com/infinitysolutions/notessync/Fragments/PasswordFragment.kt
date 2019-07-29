package com.infinitysolutions.notessync.Fragments


import android.app.Service
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
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
import com.infinitysolutions.notessync.Contracts.Contract.Companion.AUTO_SYNC_WORK_ID
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.FILE_SYSTEM_FILENAME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.FILE_TYPE_TEXT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ACCESS_TOKEN
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_CODE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ENCRYPTED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ID
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_SCHEDULE_TIME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.Model.NoteFile
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.Util.DropboxHelper
import com.infinitysolutions.notessync.Util.GoogleDriveHelper
import com.infinitysolutions.notessync.Util.WorkSchedulerHelper
import com.infinitysolutions.notessync.ViewModel.LoginViewModel
import kotlinx.android.synthetic.main.fragment_password.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.ArrayList

class PasswordFragment : Fragment() {
    private val TAG = "PasswordFragment"
    private var encryptionFound: Boolean = false
    private lateinit var loginViewModel: LoginViewModel
    private var isLoginSuccess = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_password, container, false)
        val userId = arguments?.getString(PREF_ID) as String
        val cloudType = arguments?.getInt(PREF_CLOUD_TYPE) as Int
        initDataBinding(rootView, cloudType, userId)

        rootView.skip_button.setOnClickListener {
            val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
            val editor = prefs?.edit()
            editor?.putString(PREF_CODE, null)
            editor?.putBoolean(PREF_ENCRYPTED, false)
            editor?.putString(PREF_ID, userId)
            editor?.putInt(PREF_CLOUD_TYPE, cloudType)
            setAutoSync(editor)
            editor?.commit()
            loginViewModel.resetViewModel()
            isLoginSuccess = true
            activity?.onBackPressed()
        }

        rootView.submit_button.setOnClickListener {
            if (encryptionFound)
                loginViewModel.runVerification(userId, cloudType, rootView.password_edit_text.text.toString())
            else{
                if (rootView.password_edit_text.text.toString() == rootView.again_password_edit_text.text.toString())
                    loginViewModel.secureCloudData(userId, cloudType, rootView.password_edit_text.text.toString())
                else
                    Toast.makeText(activity, "Passwords do not match", LENGTH_SHORT).show()
            }
        }
        return rootView
    }

    private fun initDataBinding(rootView: View, cloudType: Int, userId: String){
        loginViewModel = ViewModelProviders.of(activity!!).get(LoginViewModel::class.java)
        initializeLogin(cloudType)

        loginViewModel.getEncryptionCheckLoading().observe(this, androidx.lifecycle.Observer {showLoading->
            if (showLoading != null) {
                if (showLoading) {
                    rootView.loading_panel.visibility = VISIBLE
                    rootView.input_bar.visibility = GONE
                    rootView.warning_text_view.visibility = GONE
                    rootView.loading_message.text = getString(R.string.check_encryption_loading_text)
                    rootView.skip_button.visibility = GONE
                } else {
                    rootView.loading_panel.visibility = GONE
                    rootView.input_bar.visibility = VISIBLE
                }
            }
        })

        loginViewModel.getEncryptionCheckResult().observe(this, androidx.lifecycle.Observer {encryptionFound->
            if (encryptionFound != null) {
                if (encryptionFound) {
                    rootView.info_text_view.text = getString(R.string.enter_password_to_decrypt_message)
                    rootView.password_edit_text.hint = "Enter password"
                    rootView.warning_text_view.visibility = GONE
                    rootView.again_password_edit_text.visibility = GONE
                    rootView.skip_button.visibility = GONE
                } else {
                    rootView.info_text_view.text = getString(R.string.will_user_encrypt_message)
                    rootView.password_edit_text.hint = "Enter new password"
                    rootView.warning_text_view.visibility = VISIBLE
                    rootView.again_password_edit_text.visibility = VISIBLE
                    rootView.skip_button.visibility = VISIBLE
                }
                this.encryptionFound = encryptionFound
            }
        })

        loginViewModel.getVerifyPasswordLoading().observe(this, androidx.lifecycle.Observer {showLoading->
            if (showLoading != null) {
                if (showLoading) {
                    rootView.loading_panel.visibility = VISIBLE
                    rootView.input_bar.visibility = GONE
                    rootView.loading_message.text = getString(R.string.verify_password_loading_text)
                }
            }
        })

        loginViewModel.getVerifyPasswordResult().observe(this, androidx.lifecycle.Observer {result->
            if (result != null){
                when (result) {
                    1 -> finishLogin(rootView.password_edit_text.text.toString(), userId, cloudType)
                    0 -> {
                        Toast.makeText(activity, "Incorrect password", LENGTH_SHORT).show()
                        rootView.loading_panel.visibility = GONE
                        rootView.input_bar.visibility = VISIBLE
                    }
                    else -> {
                        Toast.makeText(activity, "Error occurred", LENGTH_SHORT).show()
                        rootView.loading_panel.visibility = GONE
                        rootView.input_bar.visibility = VISIBLE
                    }
                }
            }
        })

        loginViewModel.getSecureDataLoading().observe(this, androidx.lifecycle.Observer { showLoading->
            if (showLoading != null) {
                if (showLoading) {
                    rootView.loading_panel.visibility = VISIBLE
                    rootView.input_bar.visibility = GONE
                    rootView.loading_message.text = getString(R.string.securing_data_loading_text)
                }
            }
        })

        loginViewModel.getSecureDataResult().observe(this, androidx.lifecycle.Observer {result->
            if (result != null) {
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
    }

    private fun initializeLogin(cloudType: Int) {
        GlobalScope.launch {
            if (cloudType == CLOUD_GOOGLE_DRIVE) {
                val googleDriveService = getGoogleDriveService()
                if (googleDriveService != null) {
                    loginViewModel.googleDriveHelper = GoogleDriveHelper(googleDriveService)
                    val appFolderId = getAppFolderId()
                    val fileSystemId = getFileSystemId(appFolderId)
                    loginViewModel.googleDriveHelper.appFolderId = appFolderId
                    loginViewModel.googleDriveHelper.fileSystemId = fileSystemId
                }
            } else {
                val dropboxClient = getDropboxClient()
                if (dropboxClient != null) {
                    loginViewModel.dropboxHelper = DropboxHelper(dropboxClient)
                }
            }
            withContext(Dispatchers.Main) {
                loginViewModel.checkCloudEncryption(cloudType)
            }
        }
    }

    private fun finishLogin(password: String, userId: String, cloudType: Int){
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        val editor = prefs?.edit()
        editor?.putString(PREF_CODE, password)
        editor?.putBoolean(PREF_ENCRYPTED, true)
        editor?.putString(PREF_ID, userId)
        editor?.putInt(PREF_CLOUD_TYPE, cloudType)
        setAutoSync(editor)
        editor?.commit()
        loginViewModel.resetViewModel()
        isLoginSuccess = true
        activity?.onBackPressed()
    }

    private fun getFileSystemId(parentFolderId: String): String {
        var fileSystemId: String? = loginViewModel.googleDriveHelper.searchFile(FILE_SYSTEM_FILENAME, FILE_TYPE_TEXT)
        if (fileSystemId == null) {
            Log.d(TAG, "File system not found")
            val filesList = ArrayList<NoteFile>()
            val fileContent = Gson().toJson(filesList)

            fileSystemId = loginViewModel.googleDriveHelper.createFile(parentFolderId, FILE_SYSTEM_FILENAME, FILE_TYPE_TEXT, fileContent)
        }
        return fileSystemId
    }

    private fun getAppFolderId(): String {
        var folderId = loginViewModel.googleDriveHelper.searchFile("notes_sync_data_folder_19268", Contract.FILE_TYPE_FOLDER)
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
            val credential = GoogleAccountCredential.usingOAuth2(activity, listOf(DriveScopes.DRIVE_FILE))
            credential.selectedAccount = googleAccount.account
            Drive.Builder(AndroidHttp.newCompatibleTransport(), JacksonFactory.getDefaultInstance(), credential)
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
        if (!isLoginSuccess) {
            val cloudType = arguments?.getInt(PREF_CLOUD_TYPE) as Int
            val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
            val editor = prefs?.edit()
            if (cloudType == CLOUD_GOOGLE_DRIVE) {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                val googleSignInClient= GoogleSignIn.getClient(context!!, gso)
                googleSignInClient.signOut()
            } else {
                editor?.remove(PREF_ACCESS_TOKEN)
            }
            editor?.remove(PREF_ID)
            editor?.remove(PREF_CLOUD_TYPE)
            editor?.commit()
        }
        super.onDestroy()
    }

    private fun setAutoSync(editor: SharedPreferences.Editor?) {
        val c = Calendar.getInstance()
        c.set(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DATE),
            10,
            0,
            0
        )
        WorkSchedulerHelper().setAutoSync(AUTO_SYNC_WORK_ID, c.timeInMillis)
        editor?.putLong(PREF_SCHEDULE_TIME, c.timeInMillis)
    }
}
