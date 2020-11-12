package com.infinitysolutions.notessync.login

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.app.Service
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.NavHostFragment.findNavController
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.http.OkHttp3Requestor
import com.dropbox.core.v2.DbxClientV2
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.contracts.Contract.Companion.ENCRYPTED_CHECK_ERROR
import com.infinitysolutions.notessync.contracts.Contract.Companion.ENCRYPTED_NO
import com.infinitysolutions.notessync.contracts.Contract.Companion.MODE_CHANGE_PASSWORD
import com.infinitysolutions.notessync.contracts.Contract.Companion.MODE_LOGIN_TIME_PASSWORD
import com.infinitysolutions.notessync.contracts.Contract.Companion.PASSWORD_MODE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ENCRYPTED
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ID
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.util.DropboxHelper
import com.infinitysolutions.notessync.util.GoogleDriveHelper
import kotlinx.android.synthetic.main.fragment_password_check.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PasswordCheckFragment : Fragment() {
    private val TAG = "PasswordCheckFragment"
    private lateinit var passwordViewModel: PasswordViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_password_check, container, false)
        var userId = ""
        var cloudType = -1
        var passwordCheckMode = -1
        try {
            userId = arguments?.getString(PREF_ID) as String
            cloudType = arguments?.getInt(PREF_CLOUD_TYPE) as Int
            passwordCheckMode = arguments?.getInt(PASSWORD_MODE) as Int
        } catch (ex: TypeCastException) {
            activity?.setResult(RESULT_CANCELED)
            activity?.finish()
        }

        initializeLogin(cloudType)
        initDataBinding(rootView, userId, cloudType, passwordCheckMode)
        rootView.submit_button.setOnClickListener {
            if (rootView.password_edit_text.text.isNotEmpty())
                passwordViewModel.runVerification(userId, cloudType, rootView.password_edit_text.text.toString())
            else
                Toast.makeText(activity, getString(R.string.enter_password), LENGTH_SHORT).show()
        }
        return rootView
    }

    private fun initDataBinding(
        rootView: View,
        userId: String,
        cloudType: Int,
        passwordCheckMode: Int
    ) {
        passwordViewModel =
            ViewModelProviders.of(activity!!)[PasswordViewModel::class.java]
        passwordViewModel.getLoadingMessage().observe(this, Observer { loading ->
            if (loading != null) {
                rootView.loading_panel.visibility = VISIBLE
                rootView.input_bar.visibility = GONE
            } else {
                rootView.loading_panel.visibility = GONE
                rootView.input_bar.visibility = VISIBLE
            }
        })

        passwordViewModel.getEncryptionCheckResult().observe(this, Observer {result ->
            if(result != null){
                when(result){
                    ENCRYPTED_NO ->{
                        if(passwordCheckMode == MODE_LOGIN_TIME_PASSWORD){
                            val bundle = Bundle()
                            bundle.putString(PREF_ID, userId)
                            bundle.putInt(PREF_CLOUD_TYPE, cloudType)
                            bundle.putInt(PASSWORD_MODE, passwordCheckMode)
                            findNavController(this).navigate(R.id.action_passwordCheckFragment_to_passwordSetFragment)
                        }else if(passwordCheckMode == MODE_CHANGE_PASSWORD){
                            val prefs = activity?.getSharedPreferences(Contract.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
                            prefs?.edit()?.putBoolean(PREF_ENCRYPTED, false)?.commit()
                            activity?.setResult(RESULT_CANCELED)
                            activity?.finish()
                        }
                    }
                    ENCRYPTED_CHECK_ERROR ->{
                        AlertDialog.Builder(context)
                            .setTitle("Network error")
                            .setMessage("Please connect to the internet and press retry.")
                            .setPositiveButton("Retry") { _: DialogInterface, _: Int ->
                                initializeLogin(cloudType)
                            }
                            .setNegativeButton("Cancel") { _: DialogInterface, _: Int ->
                                activity?.setResult(RESULT_CANCELED)
                                activity?.finish()
                            }
                            .setCancelable(false)
                            .show()
                    }
                }
            }
        })

        passwordViewModel.getVerifyPasswordResult().observe(this, Observer { result ->
            if (result != null) {
                when (result) {
                    Contract.PASSWORD_VERIFY_INVALID -> {
                        Toast.makeText(
                            activity, getString(R.string.toast_password_invalid),
                            LENGTH_SHORT
                        ).show()
                    }
                    Contract.PASSWORD_VERIFY_CORRECT -> {
                        if (passwordCheckMode == MODE_LOGIN_TIME_PASSWORD)
                            finishLogin(
                                rootView.password_edit_text.text.toString(),
                                userId,
                                cloudType
                            )
                        else if (passwordCheckMode == MODE_CHANGE_PASSWORD) {
                            val bundle = Bundle()
                            bundle.putString(PREF_ID, userId)
                            bundle.putInt(PREF_CLOUD_TYPE, cloudType)
                            bundle.putInt(PASSWORD_MODE, passwordCheckMode)
                            findNavController(this).navigate(R.id.action_passwordCheckFragment_to_passwordSetFragment, bundle)
                        }
                    }
                    Contract.PASSWORD_VERIFY_ERROR -> {
                        Toast.makeText(
                            activity, getString(R.string.toast_error),
                            LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun initializeLogin(cloudType: Int) {
        passwordViewModel.setLoadingMessage("Preparing...")
        GlobalScope.launch(Dispatchers.IO) {
            if (cloudType == Contract.CLOUD_GOOGLE_DRIVE) {
                if(passwordViewModel.googleDriveHelper == null) {
                    val googleDriveService = getGoogleDriveService()
                    if (googleDriveService != null)
                        passwordViewModel.googleDriveHelper = GoogleDriveHelper(googleDriveService)
                }
            } else {
                if(passwordViewModel.dropboxHelper == null) {
                    val dropboxClient = getDropboxClient()
                    if (dropboxClient != null)
                        passwordViewModel.dropboxHelper = DropboxHelper(dropboxClient)
                }
            }
            withContext(Dispatchers.Main) {
                passwordViewModel.checkCloudEncryption(cloudType)
            }
        }
    }

    private fun finishLogin(password: String, userId: String, cloudType: Int) {
        // Login successful. Return to previous activity with result ok
        val prefs = activity?.getSharedPreferences(Contract.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs?.edit()
        editor?.putString(Contract.PREF_CODE, password)
        editor?.putBoolean(Contract.PREF_ENCRYPTED, true)
        editor?.putString(PREF_ID, userId)
        editor?.putInt(PREF_CLOUD_TYPE, cloudType)
        editor?.commit()
        Toast.makeText(activity, getString(R.string.toast_login_successful), LENGTH_SHORT)
            .show()
        activity?.setResult(RESULT_OK)
        activity?.finish()
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
        val prefs = activity?.getSharedPreferences(Contract.SHARED_PREFS_NAME, Service.MODE_PRIVATE)
        val accessToken = prefs?.getString(Contract.PREF_ACCESS_TOKEN, null)
        return if (accessToken != null) {
            val requestConfig = DbxRequestConfig.newBuilder("Notes-Sync")
                .withHttpRequestor(OkHttp3Requestor(OkHttp3Requestor.defaultOkHttpClient()))
                .build()
            DbxClientV2(requestConfig, accessToken)
        } else {
            null
        }
    }
}