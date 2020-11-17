package com.infinitysolutions.notessync.login

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.infinitysolutions.notessync.contracts.Contract.Companion.MODE_CHANGE_PASSWORD
import com.infinitysolutions.notessync.contracts.Contract.Companion.PASSWORD_MODE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ID
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_THEME
import com.infinitysolutions.notessync.contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_AMOLED
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_DARK
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_DEFAULT
import com.infinitysolutions.notessync.R

class ChangePasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs.contains(PREF_THEME)) {
            when (prefs.getInt(PREF_THEME, THEME_DEFAULT)) {
                THEME_DEFAULT -> setTheme(R.style.AppTheme)
                THEME_DARK -> setTheme(R.style.AppThemeDark)
                THEME_AMOLED -> setTheme(R.style.AppThemeAmoled)
            }
        }

        setContentView(R.layout.activity_change_password)
        val bundle = Bundle()
        bundle.putString(PREF_ID, prefs.getString(PREF_ID, null))
        bundle.putInt(PREF_CLOUD_TYPE, prefs.getInt(PREF_CLOUD_TYPE, -1))
        bundle.putInt(PASSWORD_MODE, MODE_CHANGE_PASSWORD)
        findNavController(R.id.nav_host_fragment).setGraph(R.navigation.change_pass_nav_graph, bundle)
    }
}