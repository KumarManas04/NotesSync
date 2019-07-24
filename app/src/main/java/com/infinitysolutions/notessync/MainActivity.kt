package com.infinitysolutions.notessync

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.get
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import com.infinitysolutions.notessync.Contracts.Contract
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_THEME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPrefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        if(sharedPrefs.contains(PREF_THEME)){
            if (sharedPrefs.getInt(PREF_THEME, 0) == 1)
                setTheme(R.style.AppThemeDark)
            else
                setTheme(R.style.AppTheme)
        }
        setContentView(R.layout.activity_main)
        initDataBinding()
    }

    private fun initDataBinding(){
        val mainViewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        if (intent != null){
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (text != null){
                mainViewModel.setSelectedNote(Note(-1L, "", text, 0, 0, "-1", Contract.NOTE_DEFAULT, false, null, -1L))
                mainViewModel.setShouldOpenEditor(true)
            }
        }

        mainViewModel.getSyncNotes().observe(this, Observer {
            it.getContentIfNotHandled()?.let {noteType-> syncFiles(noteType) }
        })

        mainViewModel.getToolbar().observe(this, Observer {toolbar->
            if (toolbar != null) {
                val toggle = ActionBarDrawerToggle(this, drawer_layout, toolbar, R.string.app_name, R.string.app_name)
                drawer_layout.addDrawerListener(toggle)
                toggle.isDrawerIndicatorEnabled = true
                toggle.syncState()
                prepareNavDrawer()
            }
        })

        Navigation.findNavController(this, R.id.nav_host_fragment)
            .addOnDestinationChangedListener { _, destination, _ ->
                when(destination.id){
                    R.id.mainFragment-> {
                        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                        val view = this.currentFocus
                        view?.let { v ->
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.hideSoftInputFromWindow(v.windowToken, 0)
                        }
                    }
                    else-> drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }
            }
    }

    private fun prepareNavDrawer(){
        val mainViewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        val index = mainViewModel.getViewMode().value
        if (index != null)
            navigation_view.menu[index -1].isChecked = true
        else
            navigation_view.menu[0].isChecked = true
        navigation_view.setNavigationItemSelectedListener {
            when(it.itemId){
                R.id.all->{
                    mainViewModel.setViewMode(1)
                    drawer_layout.closeDrawers()
                }
                R.id.notes->{
                    mainViewModel.setViewMode(2)
                    drawer_layout.closeDrawers()
                }
                R.id.lists->{
                    mainViewModel.setViewMode(3)
                    drawer_layout.closeDrawers()
                }
                R.id.archive->{
                    mainViewModel.setViewMode(4)
                    drawer_layout.closeDrawers()
                }
                R.id.settings->{
                    Navigation.findNavController(this, R.id.nav_host_fragment).navigate(R.id.action_mainFragment_to_settingsFragment)
                    drawer_layout.closeDrawers()
                }
                R.id.share->{
                    val message = "Hey there! Try Notes Sync. It is really fast, easy to use and privacy focused with lots of cool features."
                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.type = "text/plain"
                    shareIntent.putExtra(Intent.EXTRA_TEXT, message)
                    startActivity(Intent.createChooser(shareIntent, "Share..."))
                }
                R.id.about->{
                    Navigation.findNavController(this, R.id.nav_host_fragment).navigate(R.id.action_mainFragment_to_aboutFragment)
                    drawer_layout.closeDrawers()
                }
            }
            true
        }
    }

    private fun syncFiles(noteType: Int){
        if (!isServiceRunning("com.infinitysolutions.notessync.NotesSyncService")){
            Log.d(TAG, "Service not running. Starting it...")
            Toast.makeText(this, "Syncing...", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, NotesSyncService::class.java)
            intent.putExtra("Drive", noteType)
            startService(intent)
        }else{
            Toast.makeText(this, "Already syncing. Please wait...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isServiceRunning(serviceName: String): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceName == service.service.className) {
                return true
            }
        }
        return false
    }
}