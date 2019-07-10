package com.infinitysolutions.notessync.Util

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.infinitysolutions.notessync.ReminderWorker
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

    fun cancelReminder(noteId: Long?) {
        if (noteId != null)
            WorkManager.getInstance().cancelUniqueWork(noteId.toString())
    }
}