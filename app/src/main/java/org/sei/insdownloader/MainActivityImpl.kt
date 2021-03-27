package org.sei.insdownloader

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import java.lang.ref.WeakReference

class MainActivityImpl(mActivity: MainActivity) : DownloadCallback {

    private val activity = WeakReference(mActivity)

    fun onCreate() {
        activity.get()?.let { a ->
            a.bindService(
                Intent(a, DownloadService::class.java),
                DownServiceConnection,
                Context.BIND_AUTO_CREATE
            )

            with(a.viewBinding) {
                cardDownAll.logcat.movementMethod = ScrollingMovementMethod.getInstance()

                cardDownAll.editText.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {

                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {

                    }

                    override fun afterTextChanged(s: Editable?) {
                        if (cardDownAll.textInput.error != null) {
                            cardDownAll.textInput.error = null
                        }
                    }

                })

                cardDownSingle.btnDownSingle.setOnClickListener {
                    val vibrator = it.context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(VibrationEffect.createOneShot(10L, 150))

                    if (a.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        // Snackbar 向用户请求申请权限
                        Snackbar.make(root, "下载图片需要存储权限，现在申请吗？", Snackbar.LENGTH_LONG)
                            .setAction("申请") {
                                a.requestPermissions(
                                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                    0
                                )
                            }
                            .show()
                    } else {
                        val url = getClipboardContent(a)
                        if (checkSingleUrlValid(url)) {
                            a.startService(Intent(a, DownloadService::class.java).apply {
                                putExtra(KEY_ACTION, ACTION_DOWN_SINGLE)
                                putExtra(KEY_DATA, url)
                            })
                        } else {
                            Toast.makeText(a, "无效Ins链接！", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                cardDownAll.btnDownAll.setOnClickListener {
                    cardDownAll.user.text = "user"
                    cardDownAll.count.text = " / 0"
                    cardDownAll.progressBarMain.max = 0
                    cardDownAll.progress.text = "0"
                    cardDownAll.progressBarMain.progress = 0

                    (a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
                        it.windowToken,
                        0
                    )

                    if (a.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        // Snackbar 向用户请求申请权限
                        Snackbar.make(root, "下载图片需要存储权限，现在申请吗？", Snackbar.LENGTH_LONG)
                            .setAction("申请") {
                                a.requestPermissions(
                                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                    2
                                )
                            }
                            .show()
                    } else {
                        if (checkAllUrlValid(cardDownAll.editText.text.toString())) {
                            a.startService(Intent(a, DownloadService::class.java).apply {
                                putExtra(KEY_ACTION, ACTION_DOWN_ALL)
                                putExtra(KEY_DATA, cardDownAll.editText.text.toString())
                            })
                        } else {
                            cardDownAll.textInput.error = "无效Ins链接！"
                        }
                    }
                }
            }
        }
    }

    fun onStart() {

    }

    fun onResume() {

    }

    fun onPause() {

    }

    fun onStop() {

    }

    fun onDestroy() {
        activity.get()?.unbindService(DownServiceConnection)
    }

    private val DownServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            (service as? DownloadServiceImpl)?.let {
                it.setDownloadCallback(this@MainActivityImpl)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            println("连接到Service失败！...............")
        }

    }

    private var user = "user"
    private var count = 0
    private var progress = 0
    private var log = ""

    override fun sendCount(count: Int) {
        this.count = count
        activity.get()?.let {
            it.runOnUiThread {
                it.viewBinding.cardDownAll.count.text = " / $count"
                it.viewBinding.cardDownAll.progressBarMain.max = count
            }
        }
    }

    override fun sendUser(user: String) {
        this.user = user
        activity.get()?.let {
            it.runOnUiThread {
                it.viewBinding.cardDownAll.user.text = user
                it.viewBinding.cardDownAll.logcat.append("\n\n\n")
            }
        }
    }

    override fun sendProgress(progress: Int) {
        this.progress = progress
        activity.get()?.let {
            it.runOnUiThread {
                it.viewBinding.cardDownAll.progress.text = progress.toString()
                it.viewBinding.cardDownAll.progressBarMain.progress = progress
            }
        }
    }

    private val mHandler by lazy { Handler(Looper.getMainLooper()) }

    private val logcat = Runnable {
        activity.get()?.let {
            with(it.viewBinding.cardDownAll.logcat) {
                val offset = lineCount * lineHeight - height
                // 如果内容超过最大行树，就滚动到最新行
                if (offset > 0) scrollTo(0, offset + lineHeight * 2)
            }
        }
    }

    override fun sendMessage(msg: String) {
        activity.get()?.let {
            it.runOnUiThread {
                it.viewBinding.cardDownAll.logcat.append("$msg \n")
                mHandler.postDelayed(logcat, 20L)
                log = it.viewBinding.cardDownAll.logcat.text.toString()
            }
        }
    }

    override fun sendSingleCount(c: Int) {
        activity.get()?.let {
            it.runOnUiThread {
                it.viewBinding.cardDownSingle.btnDownSingle.setMax(c)
            }
        }
    }

    override fun sendSingleProgress(p: Int) {
        activity.get()?.let {
            it.runOnUiThread {
                it.viewBinding.cardDownSingle.btnDownSingle.setProgress(p)
            }
        }
    }

}