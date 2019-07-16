package com.infinitysolutions.notessync.Fragments


import android.app.AlertDialog
import android.content.Context.MODE_PRIVATE
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_DROPBOX
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ACCESS_TOKEN
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_THEME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.R
import kotlinx.android.synthetic.main.fragment_settings.view.*

class SettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_settings, container, false)
        setupViews(rootView)
        return rootView
    }

    private fun setupViews(rootView: View) {
        val toolbar = rootView.toolbar
        toolbar.title = "Settings"
        toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
        }

        val nightModeToggle = rootView.night_mode_toggle
        val sharedPrefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (sharedPrefs!!.contains(PREF_THEME))
            nightModeToggle.isChecked = sharedPrefs.getInt(PREF_THEME, 0) == 1

        nightModeToggle.setOnCheckedChangeListener { buttonView, isChecked ->
            val editor = sharedPrefs.edit()
            if (isChecked)
                editor.putInt(PREF_THEME, 1)
            else
                editor.putInt(PREF_THEME, 0)

            editor.commit()
            activity?.recreate()
        }

        rootView.night_mode_button.setOnClickListener {
            nightModeToggle.toggle()
        }

        rootView.lock_button.setOnClickListener {
            Navigation.findNavController(rootView).navigate(R.id.action_settingsFragment_to_lockFragment)
        }

        rootView.about_button.setOnClickListener {
            Navigation.findNavController(rootView).navigate(R.id.action_settingsFragment_to_aboutFragment)
        }

        rootView.open_source_button.setOnClickListener {
            Navigation.findNavController(rootView).navigate(R.id.action_settingsFragment_to_openSourceFragment)
        }

        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs != null && prefs.contains(PREF_CLOUD_TYPE)){
            if (prefs.getInt(PREF_CLOUD_TYPE, CLOUD_GOOGLE_DRIVE) == CLOUD_DROPBOX){
                if (prefs.contains(PREF_ACCESS_TOKEN) && prefs.getString(PREF_ACCESS_TOKEN, null) != null){
                    rootView.logout_button.visibility = VISIBLE
                    rootView.logout_text.text = "Logout from your Dropbox account"
                    rootView.logout_button.setOnClickListener {
                        AlertDialog.Builder(context)
                            .setTitle("Logout")
                            .setMessage("Are you sure you want to logout from your Dropbox account?")
                            .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                                rootView.logout_button.visibility = GONE
                                prefs.edit().putString(PREF_ACCESS_TOKEN, null).apply()
                            }
                            .setNegativeButton("No", null)
                            .show()
                    }
                }
            }else{
                if (GoogleSignIn.getLastSignedInAccount(activity) != null){
                    rootView.logout_button.visibility = VISIBLE
                    rootView.logout_text.text = "Logout from your Google Drive account"
                    rootView.logout_button.setOnClickListener {
                        AlertDialog.Builder(context)
                            .setTitle("Logout")
                            .setMessage("Are you sure you want to logout from your Google Drive account?")
                            .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                                rootView.logout_button.visibility = GONE
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                                val googleSignInClient = GoogleSignIn.getClient(activity!!, gso)
                                googleSignInClient.signOut()
                            }
                            .setNegativeButton("No", null)
                            .show()
                    }
                }
            }
        }
    }
}