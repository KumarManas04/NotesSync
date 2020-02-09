package com.infinitysolutions.notessync.Fragments


import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
import android.widget.Toast.LENGTH_SHORT
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.infinitysolutions.notessync.Contracts.Contract
import com.infinitysolutions.notessync.Contracts.Contract.Companion.APP_LOCK_STATE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_DROPBOX
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.MODE_CHANGE_PASSWORD
import com.infinitysolutions.notessync.Contracts.Contract.Companion.MODE_NEW_PASSWORD
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_MODE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ACCESS_TOKEN
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_APP_LOCK_CODE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ENCRYPTED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_THEME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.STATE_CHANGE_PIN
import com.infinitysolutions.notessync.Contracts.Contract.Companion.STATE_NEW_PIN
import com.infinitysolutions.notessync.Contracts.Contract.Companion.THEME_AMOLED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.THEME_DARK
import com.infinitysolutions.notessync.Contracts.Contract.Companion.THEME_DEFAULT
import com.infinitysolutions.notessync.R
import kotlinx.android.synthetic.main.fragment_settings.view.*

class SettingsFragment : Fragment() {
    private val TAG = "SettingsFragment"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_settings, container, false)
        setupViews(rootView)
        return rootView
    }

    private fun setupViews(rootView: View) {
        val toolbar = rootView.toolbar
        toolbar.title = getString(R.string.menu_settings)
        toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
        }

        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs!!.contains(PREF_THEME)) {
            when(prefs.getInt(PREF_THEME, THEME_DEFAULT)){
                THEME_DEFAULT-> rootView.pref_theme_text.text = getString(R.string.light)
                THEME_DARK -> rootView.pref_theme_text.text = getString(R.string.dark)
                THEME_AMOLED -> rootView.pref_theme_text.text = getString(R.string.amoled)
            }
        }else{
            rootView.pref_theme_text.text = getString(R.string.light)
        }

        rootView.night_mode_button.setOnClickListener {
            val content = arrayOf(getString(R.string.light), getString(R.string.dark), getString(R.string.amoled))
            val optionsDialog = AlertDialog.Builder(activity)
            optionsDialog.setTitle(getString(R.string.pick_theme))
            optionsDialog.setItems(content) { dialog, which ->
                val editor = prefs.edit()
                when(which){
                    0 -> editor.putInt(PREF_THEME, THEME_DEFAULT)
                    1 -> editor.putInt(PREF_THEME, THEME_DARK)
                    2 -> editor.putInt(PREF_THEME, THEME_AMOLED)
                }
                editor.commit()
                updateWidgets()
                activity?.recreate()
            }
            optionsDialog.show()
        }

        configureAppLockButtons(rootView, prefs)

        rootView.about_button.setOnClickListener {
            findNavController(this).navigate(R.id.action_settingsFragment_to_aboutFragment)
        }

        rootView.resources_button.setOnClickListener {
            findNavController(this).navigate(R.id.action_settingsFragment_to_resourcesFragment)
        }

        rootView.open_source_button.setOnClickListener {
            openLink("https://github.com/KumarManas04/NotesSync")
        }

        val loginStatus = getLoginStatus(prefs)
        if (loginStatus != -1) {
            if (loginStatus == CLOUD_DROPBOX) {
                rootView.logout_text.text = getString(R.string.dropbox_logout_text)
                rootView.logout_button.setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle(getString(R.string.logout))
                        .setMessage(getString(R.string.dropbox_logout_question))
                        .setPositiveButton(getString(R.string.yes)) { _: DialogInterface, _: Int ->
                            val editor = prefs.edit()
                            editor.putString(PREF_ACCESS_TOKEN, null)
                            editor.remove(PREF_CLOUD_TYPE)
                            editor.commit()
                            resetLoginButton(rootView)
                        }
                        .setNegativeButton(getString(R.string.no), null)
                        .show()
                }
            } else {
                rootView.logout_text.text = getString(R.string.gdrive_logout_text)
                rootView.logout_button.setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle(getString(R.string.logout))
                        .setMessage(getString(R.string.gdrive_logout_question))
                        .setPositiveButton(getString(R.string.yes)) { _: DialogInterface, _: Int ->
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                            val googleSignInClient = GoogleSignIn.getClient(activity!!, gso)
                            googleSignInClient.signOut()
                            resetLoginButton(rootView)
                        }
                        .setNegativeButton(getString(R.string.no), null)
                        .show()
                }
            }
            configureChangePassButton(rootView)
        } else {
            resetLoginButton(rootView)
        }
    }

    private fun configureAppLockButtons(rootView: View, prefs: SharedPreferences){
        if (prefs.contains(PREF_APP_LOCK_CODE)){
            rootView.app_lock_toggle.isChecked = true
            rootView.change_pin_button.setOnClickListener {
                val bundle = Bundle()
                bundle.putInt(APP_LOCK_STATE, STATE_CHANGE_PIN)
                findNavController(this).navigate(R.id.action_settingsFragment_to_appLockFragment, bundle)
            }
        }else{
            rootView.app_lock_toggle.isChecked = false
            rootView.change_pin_button.setOnClickListener {
                Toast.makeText(activity, getString(R.string.toast_enable_app_lock_first), LENGTH_SHORT).show()
            }
        }

        rootView.app_lock_toggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked){
                val bundle = Bundle()
                bundle.putInt(APP_LOCK_STATE, STATE_NEW_PIN)
                try {
                    findNavController(this).navigate(R.id.action_settingsFragment_to_appLockFragment, bundle)
                }catch (e: Exception){
                    rootView.app_lock_toggle.isChecked = false
                }
            }else{
                prefs.edit().remove(PREF_APP_LOCK_CODE).commit()
            }
        }

        rootView.app_lock_button.setOnClickListener {
            rootView.app_lock_toggle.toggle()
        }
    }

    private fun configureChangePassButton(rootView: View){
        //This will only be reached when user is logged in
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs != null){
            val passwordMode = if (prefs.contains(PREF_ENCRYPTED) && prefs.getBoolean(PREF_ENCRYPTED, false)){
                rootView.change_pass_title.text = getString(R.string.change_password)
                rootView.change_pass_text.text = getString(R.string.change_password_summary)
                MODE_CHANGE_PASSWORD
            }else{
                rootView.change_pass_title.text = getString(R.string.enable_encrypted_sync)
                rootView.change_pass_text.text = getString(R.string.encrypted_sync_summary)
                MODE_NEW_PASSWORD
            }

            rootView.change_pass_button.setOnClickListener {
                val bundle = Bundle()
                bundle.putInt(PREF_CLOUD_TYPE, prefs.getInt(PREF_CLOUD_TYPE, CLOUD_GOOGLE_DRIVE))
                bundle.putString(Contract.PREF_ID, prefs.getString(Contract.PREF_ID, null))
                bundle.putInt(PASSWORD_MODE, passwordMode)
                findNavController(this).navigate(R.id.action_settingsFragment_to_passwordFragment, bundle)
            }
        }
    }

    private fun resetLoginButton(rootView: View) {
        rootView.logout_title.text = getString(R.string.login)
        rootView.logout_text.text = getString(R.string.login_pref_summary)
        rootView.logout_icon.setImageResource(R.drawable.pref_login_icon)
        rootView.logout_button.setOnClickListener {
            findNavController(this).navigate(R.id.action_settingsFragment_to_cloudPickerFragment)
        }

        rootView.change_pass_title.text = getString(R.string.enable_encrypted_sync)
        rootView.change_pass_text.text = getString(R.string.encrypted_sync_summary)
        rootView.change_pass_button.setOnClickListener {
            Toast.makeText(activity, getString(R.string.toast_please_login_first), LENGTH_SHORT).show()
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
            Toast.makeText(activity, getString(R.string.toast_no_browser), Toast.LENGTH_SHORT).show()
    }

    private fun updateWidgets() {
        val intent = Intent(activity, NotesWidget::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids =
            AppWidgetManager.getInstance(activity).getAppWidgetIds(ComponentName(activity!!, NotesWidget::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        activity?.sendBroadcast(intent)
    }
}