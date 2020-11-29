package com.infinitysolutions.notessync.encrypt

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.http.OkHttp3Requestor
import com.dropbox.core.v2.DbxClientV2
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.contracts.Contract.Companion.CLOUD_DROPBOX
import com.infinitysolutions.notessync.contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ACCESS_TOKEN
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_CODE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ENCRYPTED
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ID
import com.infinitysolutions.notessync.contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.util.DropboxHelper
import com.infinitysolutions.notessync.util.GoogleDriveHelper
import kotlinx.android.synthetic.main.activity_enable_encrypion.*

class EnableEncryptionActivity : AppCompatActivity() {
    private val TAG = "EnableEncryptionActivity"
    private lateinit var encryptionViewModel: EncryptionViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs.contains(Contract.PREF_THEME)) {
            when (prefs.getInt(Contract.PREF_THEME, Contract.THEME_DEFAULT)) {
                Contract.THEME_DEFAULT -> setTheme(R.style.AppTheme)
                Contract.THEME_DARK -> setTheme(R.style.AppThemeDark)
                Contract.THEME_AMOLED -> setTheme(R.style.AppThemeAmoled)
            }
        }

        setContentView(R.layout.activity_enable_encrypion)

        val cloudType = getCloudType(prefs)
        val userId = prefs.getString(PREF_ID, null) ?: "-1"

        initDataBinding(cloudType, userId)

        submit_button.setOnClickListener {
            if (password_edit_text.text.isNotEmpty() && password_edit_text.text.toString() == again_password_edit_text.text.toString()) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.warning))
                    .setMessage(getString(R.string.encryption_warning))
                    .setPositiveButton(getString(R.string.yes)) { _: DialogInterface, _: Int ->
                        encryptionViewModel.secureCloudData(
                            userId,
                            cloudType,
                            password_edit_text.text.toString()
                        )
                    }
                    .setNegativeButton(getString(R.string.no), null)
                    .setCancelable(true)
                    .show()
            } else {
                Toast.makeText(this, getString(R.string.toast_passwords_dont_match),
                    LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun initDataBinding(cloudType: Int, userId: String){
        encryptionViewModel = ViewModelProvider(this)[EncryptionViewModel::class.java]
        encryptionViewModel.setLocalStoragePath(filesDir.toString())
        initializeLogin(cloudType)

        encryptionViewModel.getLoadingMessage()
            .observe(this, { loadingMessage ->
                if (loadingMessage != null) {
                    loading_panel.visibility = View.VISIBLE
                    input_bar.visibility = View.GONE
                    loading_message.text = loadingMessage
                } else {
                    loading_panel.visibility = View.GONE
                    input_bar.visibility = View.VISIBLE
                }
            })

        encryptionViewModel.getSecureDataResult().observe(this, { result ->
            if (result != null) {
                if (result) {
                    Toast.makeText(this, getString(R.string.toast_success), LENGTH_SHORT).show()
                    finishLogin(password_edit_text.text.toString(), userId, cloudType)
                } else {
                    Toast.makeText(this, getString(R.string.toast_error), LENGTH_SHORT).show()
                    loading_panel.visibility = View.GONE
                    input_bar.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun initializeLogin(cloudType: Int) {
        if (cloudType == CLOUD_GOOGLE_DRIVE) {
            if(encryptionViewModel.googleDriveHelper == null) {
                val googleDriveService = getGoogleDriveService()
                if (googleDriveService != null) {
                    encryptionViewModel.googleDriveHelper = GoogleDriveHelper(googleDriveService)
                    encryptionViewModel.prepareDriveHelpers()
                }
            }
        } else if(cloudType == CLOUD_DROPBOX) {
            if(encryptionViewModel.dropboxHelper == null) {
                val dropboxClient = getDropboxClient()
                if (dropboxClient != null)
                    encryptionViewModel.dropboxHelper = DropboxHelper(dropboxClient)
            }
        }
    }

    private fun getGoogleDriveService(): Drive? {
        val googleAccount = GoogleSignIn.getLastSignedInAccount(this)
        return if (googleAccount != null) {
            val credential =
                GoogleAccountCredential.usingOAuth2(this, listOf(DriveScopes.DRIVE_FILE))
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
        val prefs = this.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
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

    private fun getCloudType(prefs: SharedPreferences): Int {
        if (prefs.contains(PREF_CLOUD_TYPE)) {
            if (prefs.getInt(PREF_CLOUD_TYPE, CLOUD_GOOGLE_DRIVE) == CLOUD_DROPBOX) {
                if (prefs.contains(PREF_ACCESS_TOKEN) && prefs.getString(PREF_ACCESS_TOKEN, null) != null)
                    return CLOUD_DROPBOX
            } else {
                if (GoogleSignIn.getLastSignedInAccount(this) != null)
                    return CLOUD_GOOGLE_DRIVE
            }
        }
        return -1
    }

    private fun finishLogin(password: String, userId: String, cloudType: Int) {
        val prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(PREF_CODE, password)
        editor.putBoolean(PREF_ENCRYPTED, true)
        editor.putString(PREF_ID, userId)
        editor.putInt(PREF_CLOUD_TYPE, cloudType)
        editor.commit()
        Toast.makeText(this, "Success", LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    override fun onBackPressed() {
        if (encryptionViewModel.getExitBlocked())
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.warning))
                .setMessage(getString(R.string.encryption_warning_exit))
                .setPositiveButton(getString(R.string.yes)) { _: DialogInterface, _: Int ->
                    super.onBackPressed()
                }
                .setNegativeButton(getString(R.string.no), null)
                .setCancelable(true)
                .show()
        else
            super.onBackPressed()
    }
}