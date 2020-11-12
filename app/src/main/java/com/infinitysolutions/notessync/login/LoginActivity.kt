package com.infinitysolutions.notessync.login

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.R

class LoginActivity : AppCompatActivity() {

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
        setContentView(R.layout.activity_cloud_login)
    }
}
