package com.infinitysolutions.notessync.Services

import android.content.Intent
import android.widget.RemoteViewsService
import com.infinitysolutions.notessync.Adapters.WidgetRemoteViewsFactory

class WidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        return WidgetRemoteViewsFactory(this.applicationContext)
    }

}