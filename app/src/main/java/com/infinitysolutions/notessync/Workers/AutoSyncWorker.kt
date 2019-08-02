package com.infinitysolutions.notessync.Workers

import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.infinitysolutions.notessync.Contracts.Contract
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_DROPBOX
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.DRIVE_EXTRA
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ACCESS_TOKEN
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SYNC_INDICATOR_EXTRA
import com.infinitysolutions.notessync.Model.NotesRoomDatabase
import com.infinitysolutions.notessync.Services.NotesSyncService

class AutoSyncWorker(private val context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val notesDatabase = NotesRoomDatabase.getDatabase(applicationContext)
        val notesDao = notesDatabase.notesDao()
        val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        val lastModifiedTime = notesDao.getLastModifiedTime()
        val lastSyncedTime = prefs.getLong(Contract.PREF_LAST_SYNCED_TIME, 0)
        if (lastModifiedTime > lastSyncedTime) {
            val loginStatus = getLoginStatus()
            if (loginStatus != -1) {
                if (!isServiceRunning("com.infinitysolutions.notessync.Services.NotesSyncService")) {
                    val intent = Intent(context, NotesSyncService::class.java)
                    intent.putExtra(DRIVE_EXTRA, loginStatus)
                    intent.putExtra(SYNC_INDICATOR_EXTRA, false)
                    context.startService(intent)
                }
            }else{
                return Result.retry()
            }
        }
        return Result.success()
    }

    private fun isServiceRunning(serviceName: String): Boolean {
        val manager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceName == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun getLoginStatus(): Int {
        val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs != null && prefs.contains(PREF_CLOUD_TYPE)) {
            if (prefs.getInt(PREF_CLOUD_TYPE, CLOUD_GOOGLE_DRIVE) == CLOUD_DROPBOX) {
                if (prefs.contains(PREF_ACCESS_TOKEN) && prefs.getString(PREF_ACCESS_TOKEN, null) != null)
                    return CLOUD_DROPBOX
            } else {
                if (GoogleSignIn.getLastSignedInAccount(context) != null)
                    return CLOUD_GOOGLE_DRIVE
            }
        }
        return -1
    }
}