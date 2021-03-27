package org.sei.insdownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Binder

class DownloadServiceImpl(private val service: DownloadService) : Binder(), DownloadCallback {

    private var downCallback: DownloadCallback? = null

    private val downloader by lazy { Downloader(this, service.applicationContext) }

    fun onCreate() {

        (service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).run {
            if (getNotificationChannel("download_notification") == null) {
                val name = "下载通知"
                val descriptionText = "下载图片"
                val importance = NotificationManager.IMPORTANCE_LOW     // 静音通知
                val channel = NotificationChannel("download_notification", name, importance).apply {
                    description = descriptionText
                }
                createNotificationChannel(channel)
            }
        }
    }

    fun onStartCommand(intent: Intent?, flags: Int, startId: Int) {
        intent?.let {
            when (it.getStringExtra(KEY_ACTION)) {
                ACTION_DOWN_SINGLE -> {
                    downloader.downSingle(it.getStringExtra(KEY_DATA)!!)
                }
                ACTION_DOWN_ALL -> {
                    val url = it.getStringExtra(KEY_DATA)
                    downloader.downAll(url!!)
                }
                ACTION_DOWN_SINGLE_FROM_FLOATING -> {

                }
                ACTION_DOWN_SINGLE_FROM_NOTIFICATION -> {

                }

                else -> {

                }
            }
        }
    }

    fun onUnbind() {
        downCallback = null
    }

    fun onDestroy() {

    }

    override fun startForeground() {
        val notification = createNotification(false)
        service.startForeground(1, notification)
        (service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1, notification)
    }

    override fun stopForeground() {
        service.stopForeground(false)
    }

    private fun createNotification(isOngoing: Boolean): Notification {
        return Notification.Builder(service, "download_notification")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("图片下载服务")
            .setOngoing(isOngoing)                      // 不能手动消除
            //.setContentIntent(pendingIntent)       // 主内容点击事件
            .setVisibility(Notification.VISIBILITY_SECRET)      // 锁屏不可见
//            .setAutoCancel(true)                  //点击时自动消除
            .build()
    }

    fun setDownloadCallback(callback: DownloadCallback) {
        downCallback = callback
        callback.sendUser(user)
        callback.sendCount(count)
        callback.sendProgress(progress)
        callback.sendMessage(log.toString())
    }

    private var count = 0
    private var progress = 0
    private var user = "user"
    private val log = StringBuilder()

    override fun sendCount(count: Int) {
        this.count = count
        downCallback?.sendCount(count)
    }

    override fun sendUser(user: String) {
        this.user = user
        downCallback?.sendUser(user)
    }

    override fun sendProgress(progress: Int) {
        this.progress = progress
        downCallback?.sendProgress(progress)
    }

    override fun sendMessage(msg: String) {
        downCallback?.sendMessage(msg)
        log.append(msg)
    }

    override fun sendSingleCount(c: Int) {
        downCallback?.sendSingleCount(c)
    }

    override fun sendSingleProgress(p: Int) {
        downCallback?.sendSingleProgress(p)
    }

}