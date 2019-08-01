package com.infinitysolutions.notessync.Util

import androidx.work.*
import com.infinitysolutions.notessync.Contracts.Contract.Companion.AUTO_SYNC_WORK_ID
import com.infinitysolutions.notessync.Workers.AutoSyncWorker
import com.infinitysolutions.notessync.Workers.ReminderWorker
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

    fun setAutoSync(){
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val syncRequest = PeriodicWorkRequestBuilder<AutoSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance().enqueueUniquePeriodicWork(AUTO_SYNC_WORK_ID, ExistingPeriodicWorkPolicy.REPLACE, syncRequest)
    }

    fun cancelReminderByNoteId(noteId: Long?) {
        if (noteId != null)
            WorkManager.getInstance().cancelUniqueWork(noteId.toString())
    }

    fun cancelUniqueWork(workId: String) {
        WorkManager.getInstance().cancelUniqueWork(workId)
    }
}