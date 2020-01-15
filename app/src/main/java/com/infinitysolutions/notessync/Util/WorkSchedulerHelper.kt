package com.infinitysolutions.notessync.Util

import androidx.work.*
import com.infinitysolutions.notessync.Contracts.Contract.Companion.AUTO_DELETE_WORK_ID
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SYNC_WORK_ID
import com.infinitysolutions.notessync.Workers.AutoDeleteWorker
import com.infinitysolutions.notessync.Workers.ReminderWorker
import com.infinitysolutions.notessync.Workers.SyncWorker
import java.util.*
import java.util.concurrent.TimeUnit

class WorkSchedulerHelper {

    fun setReminder(noteId: Long?, reminderTime: Long) {
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
            WorkManager.getInstance().enqueueUniqueWork(noteId.toString(), ExistingWorkPolicy.REPLACE, reminderBuilder)
        }
    }

    fun syncNotes(syncAll: Boolean){
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val data = Data.Builder()
            .putBoolean("syncAll", syncAll)
            .build()
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .build()

        WorkManager.getInstance().enqueueUniqueWork(SYNC_WORK_ID, ExistingWorkPolicy.APPEND, syncRequest)
    }

    fun setAutoDelete(){
        val deleteRequest = PeriodicWorkRequestBuilder<AutoDeleteWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance().enqueueUniquePeriodicWork(AUTO_DELETE_WORK_ID, ExistingPeriodicWorkPolicy.REPLACE, deleteRequest)
    }

    fun cancelReminderByNoteId(noteId: Long?) {
        if (noteId != null)
            WorkManager.getInstance().cancelUniqueWork(noteId.toString())
    }
}