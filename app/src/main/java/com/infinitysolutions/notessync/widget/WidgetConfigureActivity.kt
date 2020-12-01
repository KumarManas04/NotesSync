package com.infinitysolutions.notessync.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.contracts.Contract.Companion.APP_LOCK_STATE
import com.infinitysolutions.notessync.contracts.Contract.Companion.APP_WIDGET_ID
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_THEME
import com.infinitysolutions.notessync.contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.contracts.Contract.Companion.STATE_WIDGET_CHECK
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_AMOLED
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_DARK
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_DEFAULT


class WidgetConfigureActivity : AppCompatActivity() {
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
        setContentView(R.layout.activity_widget_configure)

        val extras = intent.extras
        val appWidgetId = extras!!.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)

        if (!prefs.contains(Contract.PREF_APP_LOCK_CODE)){
            // No PIN set, let the widget be created
            val intent = Intent()
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, intent)
            finish()
        }else{
            // PIN set, show PIN check fragment
            val type = intent.getIntExtra(APP_LOCK_STATE, STATE_WIDGET_CHECK)
            val bundle = Bundle()
            bundle.putInt(APP_LOCK_STATE, type)
            bundle.putInt(APP_WIDGET_ID, appWidgetId)
            findNavController(R.id.nav_host_fragment).setGraph(R.navigation.widget_configure_nav_graph, bundle)
        }
    }
}