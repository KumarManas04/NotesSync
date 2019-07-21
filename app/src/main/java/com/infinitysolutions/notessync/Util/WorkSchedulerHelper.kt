package com.infinitysolutions.notessync.Util

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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

    fun setAutoSync(workId: String, syncTime: Long){
        val delay = syncTime - Calendar.getInstance().timeInMillis
        val syncBuilder = OneTimeWorkRequestBuilder<AutoSyncWorker>()
            .addTag(workId)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance().enqueueUniqueWork(workId, ExistingWorkPolicy.REPLACE, syncBuilder)
    }

    fun cancelReminderByNoteId(noteId: Long?) {
        if (noteId != null)
            WorkManager.getInstance().cancelUniqueWork(noteId.toString())
    }

    fun cancelUniqueWork(workId: String) {
        WorkManager.getInstance().cancelUniqueWork(workId)
    }
}