package com.infinitysolutions.notessync.util

import android.content.Context
import androidx.work.*
import com.infinitysolutions.notessync.contracts.Contract.Companion.AUTO_DELETE_WORK_ID
import com.infinitysolutions.notessync.contracts.Contract.Companion.SYNC_WORK_ID
import com.infinitysolutions.notessync.workers.AutoDeleteWorker
import com.infinitysolutions.notessync.workers.ReminderWorker
import com.infinitysolutions.notessync.workers.SyncWorker
import java.util.*
import java.util.concurrent.TimeUnit

class WorkSchedulerHelper {

    fun setReminder(noteId: Long?, reminderTime: Long, context: Context) {
        if (noteId != null) {
            val data = Data.Builder()
                .putLong("NOTE_ID", noteId)
                .putLong("REMINDER_TIME", reminderTime)
                .build()
            val delay = reminderTime - Calendar.getInstance().timeInMillis
            val reminderBuilder = OneTimeWorkRequestBuilder<ReminderWorker>()
                .addTag(noteId.toString())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(noteId.toString(), ExistingWorkPolicy.REPLACE, reminderBuilder)
        }
    }

    fun syncNotes(syncAll: Boolean, context: Context){
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val data = Data.Builder()
            .putBoolean("syncAll", syncAll)
            .build()
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(SYNC_WORK_ID, ExistingWorkPolicy.APPEND, syncRequest)
    }

    fun setAutoDelete(context: Context){
        val deleteRequest = PeriodicWorkRequestBuilder<AutoDeleteWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(AUTO_DELETE_WORK_ID, ExistingPeriodicWorkPolicy.REPLACE, deleteRequest)
    }

    fun cancelReminderByNoteId(noteId: Long?, context: Context) {
        if (noteId != null)
            WorkManager.getInstance(context).cancelUniqueWork(noteId.toString())
    }
}