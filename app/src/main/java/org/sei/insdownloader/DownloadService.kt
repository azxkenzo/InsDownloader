package org.sei.insdownloader

import android.app.Service
import android.content.Intent
import android.os.IBinder

class DownloadService : Service() {

    private val impl = DownloadServiceImpl(this)

    override fun onCreate() {
        super.onCreate()
        impl.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        impl.onStartCommand(intent,flags, startId)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder = impl

    override fun onUnbind(intent: Intent?): Boolean {
        impl.onUnbind()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        impl.onDestroy()
    }


}