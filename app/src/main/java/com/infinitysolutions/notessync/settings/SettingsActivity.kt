package com.infinitysolutions.notessync.settings

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.contracts.Contract

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(Contract.SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs.contains(Contract.PREF_THEME)) {
            when (prefs.getInt(Contract.PREF_THEME, Contract.THEME_DEFAULT)) {
                Contract.THEME_DEFAULT -> setTheme(R.style.AppTheme)
                Contract.THEME_DARK -> setTheme(R.style.AppThemeDark)
                Contract.THEME_AMOLED -> setTheme(R.style.AppThemeAmoled)
            }
        }

        setContentView(R.layout.activity_settings)
    }

}
