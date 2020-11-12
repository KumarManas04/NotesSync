package com.infinitysolutions.notessync.services

import android.content.Intent
import android.widget.RemoteViewsService
import com.infinitysolutions.notessync.adapters.WidgetRemoteViewsFactory

class WidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        return WidgetRemoteViewsFactory(this.applicationContext)
    }
}