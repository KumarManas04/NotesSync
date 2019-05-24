package com.infinitysolutions.notessync

import android.app.Service
import android.content.Intent
import android.os.IBinder

class NotesSyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        return START_NOT_STICKY
    }
}
