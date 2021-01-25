package org.sei.insdownloader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.*
import android.widget.Toast
import com.google.gson.Gson
import okhttp3.*
import okhttp3.EventListener
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class Downloader(private val callback: DownloadCallback, private val context: Context) {

    companion object {
        var queryHash = "003056d32c2554def87228bc3fd9668a"
        var csrftoken = ""
        var sessionID = ""
    }

    init {
        queryHash =
            context.getSharedPreferences("config", Context.MODE_PRIVATE)
                .getString("queryHash", "003056d32c2554def87228bc3fd9668a")
                ?: "003056d32c2554def87228bc3fd9668a"
        csrftoken =
            context.getSharedPreferences("config", Context.MODE_PRIVATE).getString("csrftoken", "")
                ?: ""
        sessionID =
            context.getSharedPreferences("config", Context.MODE_PRIVATE).getString("sessionID", "")
                ?: ""
    }

    private var task =
        Task(time = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.CHINA).format(Date()))

    private val requestBuilder by lazy { Request.Builder() }

    private val client by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(30L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .writeTimeout(30L, TimeUnit.SECONDS)
            .eventListener(object : EventListener() {
                override fun callFailed(call: Call, ioe: IOException) {
                    callback.sendMessage("callFailed: $ioe")
                }

                override fun requestFailed(call: Call, ioe: IOException) {
                    callback.sendMessage("requestFailed: $ioe")
                }

                override fun responseFailed(call: Call, ioe: IOException) {
                    callback.sendMessage("responseFailed: $ioe")
                }

                override fun connectFailed(
                    call: Call,
                    inetSocketAddress: InetSocketAddress,
                    proxy: Proxy,
                    protocol: Protocol?,
                    ioe: IOException
                ) {
                    callback.sendMessage("connectFailed: $ioe")
                }
            })
            .build()
    }

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            (msg.obj as? String)?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun downSingle(url: String) {
        println(url)

        if (!task.isCompleted) {
            return
        }

        task = Task(time = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.CHINA).format(Date()))
        task.isCompleted = false

        val request = requestBuilder.run {
            url(url)
            addHeader("Connection", "keep-alive")
            addHeader(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:82.0) Gecko/20100101 Firefox/82.0"
            )
            addHeader("Referer", "https://www.instagram.com/")
            if (csrftoken != "" && sessionID != "") {
                addHeader("Cookie", "csrftoken=$csrftoken; sessionid=$sessionID")
            }
            build()
        }
        client.newCall(request).enqueue(DownSingleCallback())

    }

    fun saveImg(url: String, index: Int) {
        val request = requestBuilder
            .url(url)
            .get()
            .build()
        client.newCall(request).enqueue(SaveImgCallback(index))
    }

    fun downAll(url: String) {
        println(url)

        if (!task.isCompleted) {
            return
        }

        task = Task(time = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.CHINA).format(Date()))
        task.isCompleted = false
        callback.startForeground()

        val request = requestBuilder.run {
            url(url)
            addHeader("Connection", "keep-alive")
            addHeader(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:82.0) Gecko/20100101 Firefox/82.0"
            )
            addHeader("Referer", "https://www.instagram.com/")
            if (csrftoken != "" && sessionID != "") {
                addHeader("Cookie", "csrftoken=$csrftoken; sessionid=$sessionID")
            }
            build()
        }
        client.newCall(request).enqueue(DownAllCallback())
    }

    fun getImgUrlInOtherPost() {
        callback.sendCount(task.urls.size)
        println(task.postCount)
        when {
            task.postCount in 1..50 -> {
                task.first = task.postCount
                task.postCount = 0
            }
            task.postCount > 50 -> {
                task.first = 50
                task.postCount -= 50
            }
            else -> {
                callback.sendMessage("一共发现了 ${task.urls.size} 张图片......\n 开始下载......")
                for (i in 0..20) {
                    if (i < task.urls.size) {
                        client.newCall(requestBuilder.url(task.urls[i]).build())
                            .enqueue(SaveImgInAllCallback(i))
                    } else {
                        break
                    }
                }

                return
            }
        }

        val query =
            "https://www.instagram.com/graphql/query/?query_hash=$queryHash&variables={\"id\":\"${task.userID}\",\"first\":${task.first},\"after\":\"${task.endCursor}\"}"
        println("query url  $query")

        val request = requestBuilder.run {
            url(query)
            addHeader("Connection", "keep-alive")
            addHeader(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:82.0) Gecko/20100101 Firefox/82.0"
            )
            addHeader("Referer", "https://www.instagram.com/")
            if (csrftoken != "" && sessionID != "") {
                addHeader("Cookie", "csrftoken=$csrftoken; sessionid=$sessionID")
            }
            build()
        }
        client.newCall(request).enqueue(GetOtherPostUrlCallback())
    }

    inner class SaveImgCallback(val index: Int) : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("download img onFailure: ")
            e.printStackTrace()
            client.newCall(call.request()).enqueue(SaveImgCallback(index))
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                when (response.code) {
                    200 -> {
                        val fileName = task.time + "_$index.jpg"

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val relativeLocation =
                                Environment.DIRECTORY_PICTURES + File.separator + "InsDownloader" + File.separator + task.user
                            val bitMap =
                                BitmapFactory.decodeStream(response.body!!.byteStream())
                            saveImgOnQ(
                                context,
                                callback,
                                relativeLocation,
                                fileName,
                                bitMap,
                                Bitmap.CompressFormat.JPEG
                            )
                            // callback

                        } else {
                            saveImgOnP(context, task.user, fileName, response.body!!.bytes())
                            // callback

                        }

                        if (task.completedOne()) {
                            handler.sendMessage(Message.obtain().apply {
                                obj = "下载完成！"
                            })
                        }
                    }

                    else -> {
                        println("download img failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                println("download img Exception: ")
                e.printStackTrace()
            } finally {
                response.close()
            }
        }

    }

    inner class DownSingleCallback : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("downSingle onFailure: ")
            e.printStackTrace()
            client.newCall(call.request()).enqueue(DownSingleCallback())
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                when (response.code) {
                    200 -> {
                        response.body?.let { body ->
                            val code = body.string()
                            val patternUrl =
                                Pattern.compile("\"src\":\"(.{150,300}?)\",\"config_width\":1080,\"config_height\":.{3,5}")
                            val list = mutableListOf<String>()
                            with(patternUrl.matcher(code)) {
                                while (find()) {
                                    group(1)!!.replace("\\u0026", "&").also { s ->
                                        println(s)
                                        if (!list.contains(s)) list.add(s)
                                    }
                                }
                                task.urls = list
                            }
                            if (list.isNullOrEmpty()) {
                                task.isCompleted = true
                                handler.sendMessage(Message.obtain().apply {
                                    obj = "未发现图片 或 未登陆 或 为私密账户"
                                })
                            } else {
                                val patternUser =
                                    Pattern.compile("\"username\":\"(.{2,200})\",\"blocked_by_viewer\"")
                                with(patternUser.matcher(code)) {
                                    if (find() && group(1) != null) {
                                        task.user = group(1)!!
                                        println(group(1))
                                    }
                                }
                                for (i in list.indices) {
                                    saveImg(list[i], i)
                                }
                            }
                        }
                    }

                    else -> {
                        task.isCompleted = true
                        println("downSingle onResponse failed : ${response.code}")
                        handler.sendMessage(Message.obtain().apply {
                            obj = "获取图片URL失败：${response.code}"
                        })
                    }
                }
            } catch (e: Exception) {
                task.isCompleted = true
                println("downSingle onResponse Exception :")
                e.printStackTrace()
            } finally {
                response.close()
            }
        }

    }

    inner class GetOtherPostUrlCallback : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("getImgUrlInOtherPost onFailure：")
            callback.sendMessage("获取后续Post图片URL失败 onFailure : $e")
            e.printStackTrace()
            client.newCall(call.request()).enqueue(GetOtherPostUrlCallback())
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                when (response.code) {
                    200 -> {
                        val code = response.body!!.string()
                        val jsonData = Gson().fromJson(code, JsonData::class.java)
                        val medias = jsonData.data.user.edge_owner_to_timeline_media
                        if (medias.edges.isNotEmpty()) {
                            val list = task.urls.toMutableList()
                            for (i in medias.edges) {
                                if (!list.contains(i.node.display_url)) list.add(i.node.display_url)
                                if (i.node.edge_sidecar_to_children != null) {
                                    for (j in i.node.edge_sidecar_to_children.edges) {
                                        if (!list.contains(j.node.display_url)) list.add(j.node.display_url)
                                    }
                                }
                            }
                            task.urls = list
                        } else {
                            println("获取后续Post图片URL失败：没有图片")
                        }
                        task.endCursor = medias.page_info.end_cursor
                        // 还存在没解析的post，但endCursor已经为null的情况
                        if (task.endCursor == null) {
                            task.postCount = 0
                        }
                        getImgUrlInOtherPost()
                    }

                    400 -> {
                        callback.sendMessage("获取后续Post图片URL失败：400 Bad Request")
                        println("获取后续Post图片URL失败：400 Bad Request")
                        println(call.request().url.toString())
                    }

                    410 -> {  // 410 Gone  被请求的资源在服务器上已经不再可用，而且没有任何已知的转发地址。
                        callback.sendMessage("获取后续Post图片URL失败：410 Gone")
                        println("获取后续Post图片URL失败：410 Gone")
                        task.postCount = 0
                        getImgUrlInOtherPost()
                    }

                    429 -> {
                        callback.sendMessage("获取后续Post图片URL失败：429 Too Many Requests")
                        println("获取后续Post图片URL失败：429 Too Many Requests")
                        Thread.sleep(1000L)
                        client.newCall(call.request()).enqueue(GetOtherPostUrlCallback())
                    }

                    else -> {
                        callback.sendMessage("获取后续Post图片URL失败：${response.code}")
                        println("获取后续Post图片URL失败：${response.code}")
                    }
                }
            } catch (e: Exception) {
                callback.sendMessage("获取后续Post图片URL失败：$e")
                println("获取后续Post图片URL失败：")
                e.printStackTrace()
            } finally {
                response.close()
            }
        }
    }

    inner class DownAllCallback : Callback {
        override fun onFailure(call: Call, e: IOException) {
            callback.sendMessage("获取第1个至第12个Post的图片URL 失败 onFailure : $e")
            println("获取第1个至第12个Post的图片URL 失败 onFailure : ")
            e.printStackTrace()

            client.newCall(call.request()).enqueue(DownAllCallback())
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                when (response.code) {
                    200 -> {
                        val sourceCode = response.body!!.string()  // response 已经 close
                        // 获取 user
                        val matcher1 =
                            Pattern.compile("<link rel=\"canonical\" href=\"https://www.instagram.com/(.{2,40})/\" />")
                                .matcher(sourceCode)
                        if (matcher1.find()) {
                            task.user = matcher1.group(1) ?: ""
                            if (task.user.equals("accounts/login") || task.user.equals("")) {
                                callback.sendMessage("未登陆 或 登录状态已失效！")
                                task.isCompleted = true
                                return
                            } else {
                                callback.sendUser(task.user)
                                callback.sendMessage("开始搜索 ${task.user} 发布的所有图片......")
                            }
                        } else {
                            println("未找到用户！")
                            callback.sendMessage("未登陆 或 登录状态已失效！")
                            task.isCompleted = true
                            return
                        }
                        // 获取第1个至第12个Post的图片URL
                        val matcher = Pattern.compile(
                            "<script type=\"text/javascript\">window._sharedData = (.*?);</script>\n" +
                                    "<script type=\"text/javascript\">window.__initialDataLoaded"
                        ).matcher(sourceCode)
                        if (matcher.find()) {
                            val insData = Gson().fromJson(matcher.group(1), InsData::class.java)
                            task.userID =
                                insData.entry_data.ProfilePage[0].graphql.user.id.also { println("userID $it") }
                            task.postCount =
                                insData.entry_data.ProfilePage[0].graphql.user.edge_owner_to_timeline_media.count.let {
                                    println("postCount $it")
                                    it - 12
                                }
                            val medias =
                                insData.entry_data.ProfilePage[0].graphql.user.edge_owner_to_timeline_media
                            task.endCursor =
                                medias.page_info.end_cursor.also { println("endCursor: $it") }

                            val list = mutableListOf<String>()
                            for (i in medias.edges) {
                                if (!list.contains(i.node.display_url)) list.add(i.node.display_url)
                                if (i.node.edge_sidecar_to_children != null) {
                                    for (j in i.node.edge_sidecar_to_children.edges) {
                                        if (!list.contains(j.node.display_url)) list.add(j.node.display_url)
                                    }
                                }
                            }
                            task.urls = list
                        }

                        if (task.urls.isEmpty()) {
                            callback.sendMessage("没有发现图片......")
                            task.isCompleted = true
                            callback.stopForeground()
                            return
                        }
                        // 获取第13个开始的post的图片Url
                        getImgUrlInOtherPost()
                    }
                    404 -> {
                        callback.sendMessage("获取第1个至第12个Post的图片URL 失败  404")
                        println("获取第1个至第12个Post的图片URL 失败  404")
                    }
                    else -> {
                        callback.sendMessage("获取第1个至第12个Post的图片URL 失败  ${response.code}")
                        println("获取第1个至第12个Post的图片URL 失败  ${response.code}")
                    }
                }
            } catch (e: Exception) {
                callback.sendMessage("获取第1个至第12个Post的图片URL 失败 onResponse : $e")
                println("user = ${task.user}")
                println("获取第1个至第12个Post的图片URL 失败 onResponse : $e")
                e.printStackTrace()
            } finally {
                response.close()
            }
        }

    }


    inner class SaveImgInAllCallback(val index: Int) : Callback {
        override fun onFailure(call: Call, e: IOException) {
            callback.sendMessage("下载第 $index 张图片 onFailure：$e")
            println("下载第 $index 张图片 onFailure：")
            e.printStackTrace()

            client.newCall(call.request()).enqueue(SaveImgInAllCallback(index))
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                when (response.code) {
                    200 -> {
                        val fileName = task.time + "_$index.jpg"

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val relativeLocation =
                                Environment.DIRECTORY_PICTURES + File.separator + "InsDownloader" + File.separator + task.user
                            val bitMap =
                                BitmapFactory.decodeStream(response.body!!.byteStream())
                            saveImgOnQ(
                                context,
                                callback,
                                relativeLocation,
                                fileName,
                                bitMap,
                                Bitmap.CompressFormat.JPEG
                            )
                            // callback

                        } else {
                            saveImgOnP(context, task.user, fileName, response.body!!.bytes())
                            // callback

                        }

                        if (task.completedOne()) {
                            callback.sendMessage("下载完成......")
                            callback.stopForeground()
                        }
                        callback.sendProgress(task.completed)

                        if (index + 21 < task.urls.size) {
                            client.newCall(requestBuilder.url(task.urls[index + 21]).build())
                                .enqueue(SaveImgInAllCallback(index + 21))
                        }
                    }

                    410 -> {  // Gone
                        callback.sendMessage("下载第 $index 张图片 失败：410 Gone")
                        println("下载第 $index 张图片 发生错误：410 Gone")
                        if (task.completedOne()) {
                            callback.sendMessage("下载完成......")
                            callback.stopForeground()
                        }
                        callback.sendProgress(task.completed)

                        if (index + 21 < task.urls.size) {
                            client.newCall(requestBuilder.url(task.urls[index + 21]).build())
                                .enqueue(SaveImgInAllCallback(index + 21))
                        }
                    }

                    429 -> {  // 429 Too Many Requests
                        callback.sendMessage("下载第 $index 张图片 失败：429 Too Many Requests")
                        println("下载第 $index 张图片 发生错误：429 Too Many Requests")
                        Thread.sleep(1000L)
                        client.newCall(call.request()).enqueue(SaveImgInAllCallback(index))
                    }

                    else -> {
                        callback.sendMessage("下载第 $index 张图片 失败：${response.code}")
                        println("下载第 $index 张图片 发生错误：${response.code}")
                    }
                }
            } catch (e: Exception) {
                callback.sendMessage("下载第 $index 张图片过程中发生异常：$e")
                println("下载第 $index 张图片 发生错误：")
                e.printStackTrace()
                client.newCall(call.request()).enqueue(SaveImgInAllCallback(index))
            } finally {
                response.close()
            }
        }

    }


}

interface DownloadCallback {
    fun sendCount(count: Int)
    fun sendUser(user: String)
    fun sendProgress(progress: Int)
    fun sendMessage(msg: String)

    fun startForeground() {}
    fun stopForeground() {}
}