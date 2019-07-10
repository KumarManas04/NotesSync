package com.infinitysolutions.notessync

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.get
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private val REQUEST_SIGN_IN = 133

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initDataBinding()
    }

    private fun initDataBinding(){
        val mainViewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        mainViewModel.getSyncNotes().observe(this, Observer {
            it.getContentIfNotHandled()?.let {
                if (GoogleSignIn.getLastSignedInAccount(this) != null)
                    syncFiles()
                else
                    requestSignIn()
            }
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
            .addOnDestinationChangedListener { controller, destination, arguments ->
                when(destination.id){
                    R.id.mainFragment-> drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    R.id.noteEditFragment-> drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    R.id.searchFragment-> drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }
            }
    }

    private fun prepareNavDrawer(){
        val mainViewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        navigation_view.menu[0].isChecked = true
        navigation_view.setNavigationItemSelectedListener {
            when(it.itemId){
                R.id.notes->{
                    mainViewModel.setViewMode(1)
                    drawer_layout.closeDrawers()
                }
                R.id.archive->{
                    mainViewModel.setViewMode(2)
                    drawer_layout.closeDrawers()
                }
            }
            true
        }

    }

    private fun syncFiles(){
        if (!isServiceRunning("com.infinitysolutions.notessync.NotesSyncService")){
            Log.d(TAG, "Service not running. Starting it...")
            Toast.makeText(this, "Syncing...", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, NotesSyncService::class.java)
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

    private fun requestSignIn(){
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(this, signInOptions)
        startActivityForResult(client.signInIntent, REQUEST_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode){
            REQUEST_SIGN_IN->{
                if (resultCode == Activity.RESULT_OK && data != null)
                    syncFiles()
                else
                    Log.d(TAG, "Sign in failed")
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }
}
