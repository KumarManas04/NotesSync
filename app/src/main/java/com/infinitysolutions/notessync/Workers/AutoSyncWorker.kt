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
import com.infinitysolutions.notessync.Contracts.Contract.Companion.AUTO_SYNC_WORK_ID
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_SCHEDULE_TIME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.NotesSyncService
import com.infinitysolutions.notessync.Util.WorkSchedulerHelper
import java.util.*

class AutoSyncWorker(private val context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val loginStatus = getLoginStatus()
        if (loginStatus != -1) {
            if (!isServiceRunning("com.infinitysolutions.notessync.NotesSyncService")) {
                val intent = Intent(context, NotesSyncService::class.java)
                intent.putExtra("Drive", loginStatus)
                context.startService(intent)
            }
        }
        scheduleNext()

        return Result.success()
    }

    private fun scheduleNext() {
        val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs != null && prefs.contains(PREF_SCHEDULE_TIME)) {
            val syncTime = prefs.getLong(PREF_SCHEDULE_TIME, 0L)
            val c = Calendar.getInstance()
            c.timeInMillis = syncTime
            val cal = Calendar.getInstance()
            cal.set(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DATE) + 1,
                c.get(Calendar.HOUR),
                c.get(Calendar.MINUTE),
                0
            )
            WorkSchedulerHelper().setAutoSync(AUTO_SYNC_WORK_ID, cal.timeInMillis)
        }
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
        if (prefs != null && prefs.contains(Contract.PREF_CLOUD_TYPE)) {
            if (prefs.getInt(Contract.PREF_CLOUD_TYPE, Contract.CLOUD_GOOGLE_DRIVE) == Contract.CLOUD_DROPBOX) {
                if (prefs.contains(Contract.PREF_ACCESS_TOKEN) && prefs.getString(
                        Contract.PREF_ACCESS_TOKEN,
                        null
                    ) != null
                )
                    return Contract.CLOUD_DROPBOX
            } else {
                if (GoogleSignIn.getLastSignedInAccount(context) != null)
                    return Contract.CLOUD_GOOGLE_DRIVE
            }
        }
        return -1
    }

}