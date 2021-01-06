package org.sei.insdownloader

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import java.io.*

val rootPath = Environment.getExternalStorageDirectory().absolutePath

data class Task(
    var urls: List<String> = listOf(),
    var user: String = "",
    var endCursor: String? = "",
    var first: Int = 0,
    var userID: String = "",
    var postCount: Int = 0,
    var completed: Int = 0,
    var isCompleted: Boolean = true,
) {
    @Synchronized
    fun completedOne(): Boolean {
        if (++completed == urls.size) {
            isCompleted = true
        }

        return isCompleted
    }
}

fun getClipboardContent(context: Context): String {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.primaryClip?.let {
        if (it.itemCount > 0) {
            return it.getItemAt(0).text.toString()
        }
    }
    return ""
}

fun checkSingleUrlValid(url: String?): Boolean {
    return !TextUtils.isEmpty(url) && url!!.contains("https://www.instagram.com/p/")
}

fun checkAllUrlValid(url: String?): Boolean {
    return !TextUtils.isEmpty(url) && url!!.contains("https://instagram.com/")
}


fun getStackTrace(e: Exception): String {
    val stringWriter = StringWriter()
    e.printStackTrace(PrintWriter(stringWriter))
    return stringWriter.toString()
}

fun saveImgOnQ(
    context: Context,
    callback: DownloadCallback,
    relativeLocation: String,
    fileName: String,
    bitmap: Bitmap,
    format: Bitmap.CompressFormat,
    mimeType: String = "image/jpeg"
) {
    var imgUri: Uri? = null
    var outputStream: OutputStream? = null
    try {
        // 创建 ContentValues    包括：DISPLAY_NAME、MIME_TYPE、RELATIVE_PATH
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation)
        }
        // 创建 Uri
        val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        imgUri = context.contentResolver.insert(contentUri, contentValues)
            ?: throw IOException("Failed to insert img.")
        // 获取 OutputStream
        outputStream = context.contentResolver.openOutputStream(imgUri)
            ?: throw IOException("Failed to get output stream.")
        // 输出到文件
        if (!bitmap.compress(format, 95, outputStream)) {
            throw IOException("Failed to save bitmap.")
        }
        outputStream.flush()
        println("保存图片成功")
    } catch (e: Exception) {
        if (imgUri != null) context.contentResolver.delete(imgUri, null, null)
        e.printStackTrace()
        callback.sendMessage("图片保存异常：${getStackTrace(e)}")
    } finally {
        outputStream?.close()
    }
}

/**
 * Android P 及以下 保存图片
 */
fun saveImgOnP(context: Context, user: String, fileName: String, byteArray: ByteArray) {
    val dirPath = "$rootPath/pictures/InsDownloader/$user"
    if (!File(dirPath).exists()) File(dirPath).mkdirs()
    val img = File("$dirPath/$fileName")
    if (!img.exists()) {
        img.createNewFile()
        val fOpS = FileOutputStream(img)
        fOpS.write(byteArray)
        fOpS.flush()
        fOpS.close()

        // 把文件插入到系统图库
        // MediaStore.Images.Media.insertImage(resolver, img.absolutePath, fileName, null)
        // 把文件插入到系统图库
        context.sendBroadcast(
            Intent(
                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.parse("file://" + img.absolutePath)
            )
        )
        println("保存图片成功")
    }
}