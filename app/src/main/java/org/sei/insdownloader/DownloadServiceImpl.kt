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

    private val twitterDownloader by lazy { TwitterDownloader(service.applicationContext, this) }

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

                ACTION_TWITTER_DOWNLOAD_IMAGE -> {
                    twitterDownloader.downloadImage(it.getStringExtra(KEY_DATA)!!)
                }

                ACTION_TWITTER_DOWNLOAD_VIDEO -> {
                    twitterDownloader.downloadVideo(it.getStringExtra(KEY_DATA)!!)
                }

                ACTION_WEIBO_DOWNLOAD -> {
                    twitterDownloader.weiboDownload(it.getStringExtra(KEY_DATA)!!)
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
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("图片下载服务")
            .setOngoing(isOngoing)                      // 不能手动消除
            //.setContentIntent(pendingIntent)       // 主内容点击事件
            .setVisibility(Notification.VISIBILITY_SECRET)      // 锁屏不可见
//            .setAutoCancel(true)                  //点击时自动消除
            .build()
    }

    fun setDownloadCallback(callback: DownloadCallback) {
        downCallback = callback
        callback.sendInsUser(insUser)
        callback.sendInsCount(insCount)
        callback.sendInsProgress(insProgress)
        callback.sendInsMessage(log.toString())

        callback.sendTwitterProgress(twitterProgress)
        callback.sendTwitterCount(twitterCount)
    }

    private var insCount = 0
    private var insProgress = 0
    private var insUser = "user"
    private val log = StringBuilder()

    private var twitterCount = 0
    private var twitterProgress = 0

    override fun sendInsCount(count: Int) {
        insCount = count
        downCallback?.sendInsCount(count)
    }

    override fun sendInsUser(user: String) {
        insUser = user
        downCallback?.sendInsUser(user)
    }

    override fun sendInsProgress(progress: Int) {
        insProgress = progress
        downCallback?.sendInsProgress(progress)
    }

    override fun sendInsMessage(msg: String) {
        downCallback?.sendInsMessage(msg)
        log.append(msg)
    }

    override fun sendInsSingleCount(c: Int) {
        downCallback?.sendInsSingleCount(c)
    }

    override fun sendInsSingleProgress(p: Int) {
        downCallback?.sendInsSingleProgress(p)
    }

    override fun sendTwitterCount(count: Int) {
        twitterCount = count
        downCallback?.sendTwitterCount(count)
    }
    override fun sendTwitterProgress(progress: Int) {
        twitterProgress = progress
        downCallback?.sendTwitterProgress(progress)
    }

}

interface DownloadCallback {
    fun sendInsCount(count: Int) {}
    fun sendInsUser(user: String) {}
    fun sendInsProgress(progress: Int) {}
    fun sendInsMessage(msg: String) {}

    fun sendInsSingleProgress(p: Int) {}
    fun sendInsSingleCount(c: Int) {}

    fun startForeground() {}
    fun stopForeground() {}

    fun sendTwitterCount(count: Int) {}
    fun sendTwitterProgress(progress: Int) {}
    fun sendTwitterUser(user: String) {}
}