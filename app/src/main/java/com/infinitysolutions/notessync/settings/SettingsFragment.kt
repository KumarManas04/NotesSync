package com.infinitysolutions.notessync.settings


import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context.MODE_PRIVATE
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.infinitysolutions.notessync.noteedit.ColorPickerAdapter
import com.infinitysolutions.notessync.login.LoginActivity
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.contracts.Contract.Companion.APP_LOCK_STATE
import com.infinitysolutions.notessync.contracts.Contract.Companion.CLOUD_DROPBOX
import com.infinitysolutions.notessync.contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.contracts.Contract.Companion.MODE_CHANGE_PASSWORD
import com.infinitysolutions.notessync.contracts.Contract.Companion.MODE_NEW_PASSWORD
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ACCESS_TOKEN
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_APP_LOCK_CODE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_DEFAULT_NOTE_COLOR
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ENCRYPTED
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_MAX_PREVIEW_LINES
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_MOVE_CHECKED_TO_BOTTOM
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_THEME
import com.infinitysolutions.notessync.contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.contracts.Contract.Companion.STATE_CHANGE_PIN
import com.infinitysolutions.notessync.contracts.Contract.Companion.STATE_NEW_PIN
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_AMOLED
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_DARK
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_DEFAULT
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.applock.AppLockActivity
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ID
import com.infinitysolutions.notessync.encrypt.EnableEncryptionActivity
import com.infinitysolutions.notessync.widget.NotesWidget
import com.infinitysolutions.notessync.util.ColorsUtil
import com.infinitysolutions.notessync.util.WorkSchedulerHelper
import com.infinitysolutions.notessync.login.ChangePasswordActivity
import kotlinx.android.synthetic.main.bottom_sheet.view.*
import kotlinx.android.synthetic.main.fragment_settings.view.*
import kotlinx.android.synthetic.main.preview_lines_dialog.view.*
import kotlinx.android.synthetic.main.theme_dialog.view.*

class SettingsFragment : Fragment() {
    private val TAG = "SettingsFragment"
    private val LOGIN_REQUEST_CODE = 199
    private val SET_PIN_REQUEST_CODE = 191
    private val ENABLE_ENCRYPTION_REQUEST_CODE = 192

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_settings, container, false)
        setupViews(rootView, container)
        return rootView
    }

    private fun setupViews(rootView: View, container: ViewGroup?) {
        val toolbar = rootView.toolbar
        toolbar.title = getString(R.string.menu_settings)
        toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
        }

        val prefs = activity!!.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        val themeIndex: Int
        rootView.pref_theme_text.text = when (prefs.getInt(PREF_THEME, THEME_DEFAULT)) {
            THEME_DEFAULT -> {
                themeIndex = 0
                getString(R.string.light)
            }
            THEME_DARK -> {
                themeIndex = 1
                getString(R.string.dark)
            }
            else -> {
                themeIndex = 2
                getString(R.string.amoled)
            }
        }

        rootView.app_theme_button.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.theme_dialog, container, false)
            val dialog = BottomSheetDialog(context!!)
            val themeGroup = dialogView.theme_group
            (themeGroup.getChildAt(themeIndex) as RadioButton).isChecked = true
            dialogView.light_btn.setOnClickListener {
                changeTheme(prefs, themeIndex, THEME_DEFAULT, dialog)
            }
            dialogView.dark_btn.setOnClickListener {
                changeTheme(prefs, themeIndex, THEME_DARK, dialog)
            }
            dialogView.amoled_btn.setOnClickListener {
                changeTheme(prefs, themeIndex, THEME_AMOLED, dialog)
            }

            dialog.setContentView(dialogView)
            dialog.show()
        }

        configureAppLockButtons(rootView, prefs)

        rootView.about_button.setOnClickListener {
            findNavController(this).navigate(R.id.action_settingsFragment2_to_aboutFragment3)
        }

        rootView.resources_button.setOnClickListener {
            findNavController(this).navigate(R.id.action_settingsFragment2_to_resourcesFragment2)
        }

        rootView.open_source_button.setOnClickListener {
            openLink("https://github.com/KumarManas04/NotesSync")
        }

        configureNoteColorButton(rootView, prefs, container)
        configureMaxLinesButton(rootView, prefs, container)

        configureChecklistMoveButton(rootView, prefs)

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

    private fun changeTheme(prefs: SharedPreferences, themeIndex: Int, toTheme: Int, dialog: BottomSheetDialog){
        dialog.dismiss()
        if(themeIndex != toTheme){
            val editor = prefs.edit()
            editor.putInt(PREF_THEME, toTheme)
            editor.commit()
            updateWidgets()
            activity?.recreate()
        }
    }

    private fun configureNoteColorButton(rootView: View, prefs: SharedPreferences, container: ViewGroup?) {
        var defaultColor = prefs.getInt(PREF_DEFAULT_NOTE_COLOR, 0)
        val colorsUtil = ColorsUtil()
        val colorIndicator = rootView.color_view
        val drawable = ContextCompat.getDrawable(context!!, R.drawable.round_color)
        drawable?.colorFilter = PorterDuffColorFilter(Color.parseColor(colorsUtil.getColor(defaultColor)), PorterDuff.Mode.SRC)
        colorIndicator.background = drawable

        rootView.note_color_button.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.color_picker_dialog, container, false)
            val dialog = BottomSheetDialog(context!!)

            val layoutManager = LinearLayoutManager(context!!, RecyclerView.HORIZONTAL, false)
            dialogView.color_picker.layoutManager = layoutManager
            val adapter = ColorPickerAdapter(context!!, null)
            adapter.setSelectedColor(defaultColor)
            dialogView.color_picker.adapter = adapter
            dialog.setOnDismissListener {
                defaultColor = adapter.getSelectedColor()
                val editor = prefs.edit()
                editor.putInt(PREF_DEFAULT_NOTE_COLOR, defaultColor)
                editor.apply()
                drawable?.colorFilter = PorterDuffColorFilter(Color.parseColor(colorsUtil.getColor(defaultColor)), PorterDuff.Mode.SRC)
                colorIndicator.background = drawable
            }
            dialog.setContentView(dialogView)
            dialog.show()
        }
    }

    private fun configureMaxLinesButton(rootView: View, prefs: SharedPreferences, container: ViewGroup?){
        var value = prefs.getInt(PREF_MAX_PREVIEW_LINES, 32)
        if(value == -1)
            value = 32
        rootView.preview_lines_count_text.text = if(value == 32) "No Limit" else "$value lines"

        rootView.preview_lines_count_button.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.preview_lines_dialog, container, false)
            val dialog = BottomSheetDialog(context!!)
            val seekBar = dialogView.seek_bar
            seekBar.max = 32
            seekBar.progress = value
            seekBar.keyProgressIncrement = 1
            val linesText = dialogView.lines_text
            linesText.text = if(value == 32) "No Limit" else "$value"
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                    value = progress
                    linesText.text = if(value == 32) "No Limit" else "$value"
                }

                override fun onStartTrackingTouch(p0: SeekBar?) {}
                override fun onStopTrackingTouch(p0: SeekBar?) {}
            })

            dialog.setOnDismissListener {
                val editor = prefs.edit()
                editor.putInt(PREF_MAX_PREVIEW_LINES, if(value == 32) Integer.MAX_VALUE else value)
                editor.apply()
                rootView.preview_lines_count_text.text = if(value == 32) "No Limit" else "$value lines"
            }
            dialog.setContentView(dialogView)
            dialog.show()
        }
    }

    private fun configureChecklistMoveButton(rootView: View, prefs: SharedPreferences){
        rootView.move_bottom_toggle.isChecked = prefs.getBoolean(PREF_MOVE_CHECKED_TO_BOTTOM, true)

        rootView.move_bottom_button.setOnClickListener {
            rootView.move_bottom_toggle.toggle()
        }

        rootView.move_bottom_toggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked){
                prefs.edit().putBoolean(PREF_MOVE_CHECKED_TO_BOTTOM, true).apply()
            }else{
                prefs.edit().putBoolean(PREF_MOVE_CHECKED_TO_BOTTOM, false).apply()
            }
        }
    }

    private fun configureAppLockButtons(rootView: View, prefs: SharedPreferences){
        if (prefs.contains(PREF_APP_LOCK_CODE)){
            rootView.app_lock_toggle.isChecked = true
            rootView.change_pin_button.setOnClickListener {
                val appLockIntent = Intent(activity, AppLockActivity::class.java)
                appLockIntent.putExtra(APP_LOCK_STATE, STATE_CHANGE_PIN)
                startActivity(appLockIntent)
            }
        }else{
            rootView.app_lock_toggle.isChecked = false
            rootView.change_pin_button.setOnClickListener {
                Toast.makeText(activity, getString(R.string.toast_enable_app_lock_first), LENGTH_SHORT).show()
            }
        }

        rootView.app_lock_toggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked){
                // Enable App Lock
                val appLockIntent = Intent(activity, AppLockActivity::class.java)
                appLockIntent.putExtra(APP_LOCK_STATE, STATE_NEW_PIN)
                startActivityForResult(appLockIntent, SET_PIN_REQUEST_CODE)
            }else{
                // Disable App Lock
                prefs.edit().remove(PREF_APP_LOCK_CODE).commit()
            }
        }

        rootView.app_lock_button.setOnClickListener {
            rootView.app_lock_toggle.toggle()
        }
    }

    private fun configureChangePassButton(rootView: View){
        //This will only be reached when user is logged in
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE) ?: return
        if(!prefs.contains(PREF_ENCRYPTED))
            return

        if(prefs.getBoolean(PREF_ENCRYPTED, false)){
            rootView.change_pass_title.text = getString(R.string.change_password)
            rootView.change_pass_text.text = getString(R.string.change_password_summary)
            rootView.change_pass_button.setOnClickListener {
                startActivityForResult(Intent(activity, ChangePasswordActivity::class.java), 1234)
            }
        }else{
            rootView.change_pass_title.text = getString(R.string.enable_encrypted_sync)
            rootView.change_pass_text.text = getString(R.string.encrypted_sync_summary)
            rootView.change_pass_button.setOnClickListener {
                startActivityForResult(Intent(activity, EnableEncryptionActivity::class.java), ENABLE_ENCRYPTION_REQUEST_CODE)
            }
        }
    }

    private fun resetLoginButton(rootView: View) {
        rootView.logout_title.text = getString(R.string.login)
        rootView.logout_text.text = getString(R.string.login_pref_summary)
        rootView.logout_icon.setImageResource(R.drawable.pref_login_icon)
        rootView.logout_button.setOnClickListener {
            startActivityForResult(Intent(context, LoginActivity::class.java), LOGIN_REQUEST_CODE)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode == LOGIN_REQUEST_CODE) {
            val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
            if(resultCode == RESULT_OK){
                if(prefs != null) {
                    prefs.edit().putStringSet(Contract.PREF_SYNC_QUEUE, hashSetOf("1")).commit()
                    WorkSchedulerHelper().syncNotes(true, context!!)
                }
            }else{
                val editor = prefs?.edit()
                editor?.remove(PREF_ACCESS_TOKEN)
                editor?.remove(PREF_CLOUD_TYPE)
                editor?.remove(PREF_ID)
                editor?.apply()
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                val googleSignInClient = GoogleSignIn.getClient(activity!!, gso)
                googleSignInClient.signOut()
            }
        }

        if(requestCode == SET_PIN_REQUEST_CODE || requestCode == ENABLE_ENCRYPTION_REQUEST_CODE){
            val rootView = view
            val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
            if(rootView != null && prefs != null)
                configureAppLockButtons(rootView, prefs)
        }

        if(requestCode == ENABLE_ENCRYPTION_REQUEST_CODE){
            val rootView = view
            if(rootView != null)
                configureChangePassButton(rootView)
        }
    }
}