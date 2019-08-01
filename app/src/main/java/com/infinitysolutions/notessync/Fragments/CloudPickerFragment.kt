package com.infinitysolutions.notessync.Fragments


import android.app.Activity
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import com.dropbox.core.android.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_DROPBOX
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.MODE_LOGIN_TIME_PASSWORD
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_MODE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ACCESS_TOKEN
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ID
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.fragment_cloud_picker.view.*

class CloudPickerFragment : Fragment() {
    private val TAG = "CloudPickerFragment"
    private lateinit var mainViewModel: MainViewModel
    private lateinit var rootView: View
    private val REQUEST_SIGN_IN = 133

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.fragment_cloud_picker, container, false)

        mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)

        rootView.g_drive.setOnClickListener {
            val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
            if (prefs != null) {
                prefs.edit().putInt(PREF_CLOUD_TYPE, CLOUD_GOOGLE_DRIVE).apply()

                if (GoogleSignIn.getLastSignedInAccount(activity) == null) {
                    val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                        .requestEmail()
                        .build()
                    val client = GoogleSignIn.getClient(activity!!, signInOptions)
                    startActivityForResult(client.signInIntent, REQUEST_SIGN_IN)
                }else{
                    mainViewModel.setSyncNotes(CLOUD_GOOGLE_DRIVE)
                    activity?.onBackPressed()
                }
            }
        }

        rootView.dropbox.setOnClickListener {
            val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
            if (prefs != null) {
                prefs.edit().putInt(PREF_CLOUD_TYPE, CLOUD_DROPBOX).apply()
                val accessToken = prefs.getString(PREF_ACCESS_TOKEN, null)
                if (accessToken == null)
                    Auth.startOAuth2Authentication(activity, getString(R.string.app_key))
                else{
                    mainViewModel.setSyncNotes(CLOUD_DROPBOX)
                    activity?.onBackPressed()
                }
            }
        }

        return rootView
    }

    override fun onResume() {
        super.onResume()
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs != null && prefs.contains(PREF_CLOUD_TYPE) && prefs.getInt(PREF_CLOUD_TYPE, CLOUD_GOOGLE_DRIVE) == CLOUD_DROPBOX) {
            var accessToken = prefs.getString(PREF_ACCESS_TOKEN, null)
            if (accessToken == null) {
                accessToken = Auth.getOAuth2Token()
                if (accessToken != null) {
                    prefs.edit().putString(PREF_ACCESS_TOKEN, accessToken).commit()
                    loginSuccess(CLOUD_DROPBOX, Auth.getUid())
                    Log.d(TAG, "Dropbox login complete")
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode){
            REQUEST_SIGN_IN->{
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val googleAccount = GoogleSignIn.getLastSignedInAccount(activity)
                    if (googleAccount != null) {
                        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
                        prefs?.edit()?.putString(PREF_ID, googleAccount.id)?.commit()
                        val id = googleAccount.id
                        loginSuccess(CLOUD_GOOGLE_DRIVE, googleAccount.id!!)
                        Log.d(TAG, "GDrive login complete")
                    }
                }else
                    Log.d(TAG, "Sign in failed")
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun loginSuccess(cloudType: Int, id: String){
        val bundle = Bundle()
        bundle.putString(PREF_ID, id)
        bundle.putInt(PREF_CLOUD_TYPE, cloudType)
        bundle.putInt(PASSWORD_MODE, MODE_LOGIN_TIME_PASSWORD)
        Navigation.findNavController(rootView).navigate(R.id.action_cloudPickerFragment_to_passwordFragment, bundle)
    }
}
