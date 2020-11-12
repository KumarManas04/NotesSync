package com.infinitysolutions.notessync.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.infinitysolutions.notessync.R

class NotificationHelper {

    fun getReminderNotificationBuilder(context: Context, contentTitle: String, contentText: String) : NotificationCompat.Builder{
        val channelId = "reminder"
        buildNotificationChannel(context, channelId, "Reminders", "Used to show reminders for notes")

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
        notificationBuilder.setSmallIcon(R.drawable.reminder_icon)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setAutoCancel(true)
        return notificationBuilder
    }

    private fun buildNotificationChannel(context: Context, channelId: String, name: String, description: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_LOW)
            channel.description = description
            channel.setSound(null, null)
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}