package com.infinitysolutions.notessync.Fragments


import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context.MODE_PRIVATE
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.infinitysolutions.notessync.Contracts.Contract.Companion.AUTO_SYNC_WORK_ID
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_DROPBOX
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ACCESS_TOKEN
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_SCHEDULE_TIME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_THEME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.Util.WorkSchedulerHelper
import kotlinx.android.synthetic.main.fragment_settings.view.*
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {
    private val TAG = "SettingsFragment"

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

        nightModeToggle.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPrefs.edit()
            if (isChecked)
                editor.putInt(PREF_THEME, 1)
            else
                editor.putInt(PREF_THEME, 0)

            editor.commit()
            activity?.recreate()
        }

        if (sharedPrefs.contains(PREF_SCHEDULE_TIME)) {
            rootView.auto_sync_toggle.isChecked = true
            val syncTime = sharedPrefs.getLong(PREF_SCHEDULE_TIME, 0L)
            val sdf = SimpleDateFormat("h:mm a", Locale.ENGLISH)
            rootView.auto_sync_time.text = sdf.format(syncTime)
        } else {
            rootView.auto_sync_toggle.isChecked = false
            rootView.auto_sync_time.text = getString(R.string.off_text)
        }

        rootView.auto_sync_toggle.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                WorkSchedulerHelper().cancelUniqueWork(AUTO_SYNC_WORK_ID)
                rootView.auto_sync_time.text = getString(R.string.off_text)
                if (sharedPrefs.contains(PREF_SCHEDULE_TIME))
                    sharedPrefs.edit().remove(PREF_SCHEDULE_TIME).apply()
            } else {
                if (getLoginStatus(sharedPrefs) == -1) {
                    Toast.makeText(activity, "Please login first", Toast.LENGTH_SHORT).show()
                    rootView.auto_sync_toggle.isChecked = false
                } else {
                    val c = Calendar.getInstance()
                    val timePicker = TimePickerDialog(context, { _, hourOfDay, minute ->
                        c.set(
                            c.get(Calendar.YEAR),
                            c.get(Calendar.MONTH),
                            c.get(Calendar.DATE),
                            hourOfDay,
                            minute,
                            0
                        )
                        val sdf = SimpleDateFormat("h:mm a", Locale.ENGLISH)
                        rootView.auto_sync_time.text = sdf.format(c.timeInMillis)
                        WorkSchedulerHelper().setAutoSync(AUTO_SYNC_WORK_ID, c.timeInMillis)
                        sharedPrefs.edit().putLong(PREF_SCHEDULE_TIME, c.timeInMillis).apply()
                    }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false)
                    timePicker.setOnCancelListener{
                        rootView.auto_sync_toggle.isChecked = false
                    }
                    timePicker.show()
                }
            }
        }

        rootView.auto_sync_button.setOnClickListener {
            rootView.auto_sync_toggle.toggle()
        }

        rootView.night_mode_button.setOnClickListener {
            nightModeToggle.toggle()
        }

        rootView.about_button.setOnClickListener {
            Navigation.findNavController(rootView).navigate(R.id.action_settingsFragment_to_aboutFragment)
        }

        rootView.resources_button.setOnClickListener {
            Navigation.findNavController(rootView).navigate(R.id.action_settingsFragment_to_resourcesFragment)
        }

        rootView.open_source_button.setOnClickListener {
            openLink("https://github.com/KumarManas04/NotesSync")
        }

        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        val loginStatus = getLoginStatus(prefs)
        if (prefs != null) {
            if (loginStatus != -1) {
                if (loginStatus == CLOUD_DROPBOX) {
                    rootView.logout_text.text = getString(R.string.dropbox_logout_text)
                    rootView.logout_button.setOnClickListener {
                        AlertDialog.Builder(context)
                            .setTitle("Logout")
                            .setMessage("Are you sure you want to logout from your Dropbox account?")
                            .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                                prefs.edit().putString(PREF_ACCESS_TOKEN, null).commit()
                                resetLoginButton(rootView)
                            }
                            .setNegativeButton("No", null)
                            .show()
                    }
                } else {
                    rootView.logout_text.text = getString(R.string.gdrive_logout_text)
                    rootView.logout_button.setOnClickListener {
                        AlertDialog.Builder(context)
                            .setTitle("Logout")
                            .setMessage("Are you sure you want to logout from your Google Drive account?")
                            .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                                val googleSignInClient = GoogleSignIn.getClient(activity!!, gso)
                                googleSignInClient.signOut()
                                resetLoginButton(rootView)
                            }
                            .setNegativeButton("No", null)
                            .show()
                    }
                }
            } else {
                resetLoginButton(rootView)
            }
        }
    }

    private fun resetLoginButton(rootView: View) {
        rootView.logout_title.text = getString(R.string.login)
        rootView.logout_text.text = getString(R.string.login_pref_summary)
        rootView.logout_icon.setImageResource(R.drawable.lock_pref_icon)
        WorkSchedulerHelper().cancelUniqueWork(AUTO_SYNC_WORK_ID)
        val sharedPrefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        sharedPrefs?.edit()?.remove(PREF_SCHEDULE_TIME)?.commit()
        rootView.auto_sync_time.text = getString(R.string.off_text)
        rootView.auto_sync_toggle.isChecked = false
        rootView.logout_button.setOnClickListener {
            Navigation.findNavController(rootView).navigate(R.id.action_settingsFragment_to_cloudPickerFragment)
        }
    }

    private fun getLoginStatus(prefs: SharedPreferences?): Int {
        if (prefs != null && prefs.contains(PREF_CLOUD_TYPE)) {
            if (prefs.getInt(PREF_CLOUD_TYPE, CLOUD_GOOGLE_DRIVE) == CLOUD_DROPBOX) {
                if (prefs.contains(PREF_ACCESS_TOKEN) && prefs.getString(PREF_ACCESS_TOKEN, null) != null)
                    return CLOUD_DROPBOX
            } else {
                if (GoogleSignIn.getLastSignedInAccount(activity) != null)
                    return CLOUD_GOOGLE_DRIVE
            }
        }
        return -1
    }

    private fun openLink(link: String){
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        if (browserIntent.resolveActivity(activity!!.packageManager) != null)
            startActivity(browserIntent)
        else
            Toast.makeText(activity, "No browser found!", Toast.LENGTH_SHORT).show()
    }
}