package com.infinitysolutions.notessync.Workers

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.navigation.NavDeepLinkBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.infinitysolutions.notessync.Model.NotesRoomDatabase
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.Util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ReminderWorker(private val context: Context, params: WorkerParameters): Worker(context, params){

    override fun doWork(): Result {
        val noteId = inputData.getLong("NOTE_ID", -1L)
        val reminderTime = inputData.getLong("REMINDER_TIME", -1L)
        if (noteId == -1L || reminderTime == -1L)
            return Result.failure()

        val notesDao = NotesRoomDatabase.getDatabase(applicationContext).notesDao()
        val note = notesDao.getNoteById(noteId)

        val bundle = Bundle()
        bundle.putLong("NOTE_ID", noteId)
        val pendingIntent = NavDeepLinkBuilder(applicationContext)
            .setGraph(R.navigation.nav_graph)
            .setArguments(bundle)
            .setDestination(R.id.noteEditFragment)
            .createPendingIntent()

        val formatter = SimpleDateFormat("h:mm a MMM d", Locale.ENGLISH)
        val notifyBuilder = NotificationHelper().getReminderNotificationBuilder(context, note.noteTitle.toString(), formatter.format(reminderTime))
        notifyBuilder.setContentIntent(pendingIntent)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(noteId.toInt(), notifyBuilder.build())

        note.reminderTime = -1L
        GlobalScope.launch(Dispatchers.IO) {
            notesDao.insert(note)
        }
        return Result.success()
    }
}