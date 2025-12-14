package com.sentinelai.tak.plugin

import android.app.Service
import android.content.Intent
import android.os.IBinder

class SentinelAiPluginService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
