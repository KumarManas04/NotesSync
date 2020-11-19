package com.infinitysolutions.notessync.noteedit

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.navigation.findNavController
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_ID_EXTRA
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_THEME
import com.infinitysolutions.notessync.contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_DEFAULT

class NoteEditActivity : AppCompatActivity() {
    private val TAG = "NoteEditActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs.contains(PREF_THEME)) {
            when (prefs.getInt(PREF_THEME, THEME_DEFAULT)) {
                Contract.THEME_DEFAULT -> setTheme(R.style.AppTheme)
                Contract.THEME_DARK -> setTheme(R.style.AppThemeDark)
                Contract.THEME_AMOLED -> setTheme(R.style.AppThemeAmoled)
            }
        }

        setContentView(R.layout.activity_note_edit)

        val noteId: Long = intent.getLongExtra(NOTE_ID_EXTRA, -1)
        val bundle = Bundle()
        bundle.putLong(NOTE_ID_EXTRA, noteId)
        //TODO: Handle noteType, photoUri and filePath here
        findNavController(R.id.nav_host_fragment).setGraph(R.navigation.note_edit_nav_graph, bundle)
    }
}
