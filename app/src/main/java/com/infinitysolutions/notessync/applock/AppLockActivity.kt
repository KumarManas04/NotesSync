package com.infinitysolutions.notessync.applock

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.navigation.findNavController
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.contracts.Contract.Companion.APP_LOCK_STATE
import com.infinitysolutions.notessync.contracts.Contract.Companion.STATE_CHECK_PIN

class AppLockActivity : AppCompatActivity() {
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

        val type = intent.getIntExtra(APP_LOCK_STATE, STATE_CHECK_PIN)
        val bundle = Bundle()
        bundle.putInt(APP_LOCK_STATE, type)
        findNavController(R.id.nav_host_fragment).setGraph(R.navigation.app_lock_nav_graph, bundle)
        setContentView(R.layout.activity_app_lock)
    }
}