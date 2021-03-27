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
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class Downloader(private val callback: DownloadCallback, private val context: Context) {

    companion object {
        var queryHash = ""
        var csrftoken = ""
        var sessionID = ""
    }

    init {
        if (queryHash.isBlank()) {
            queryHash =
                context.getSharedPreferences("config", Context.MODE_PRIVATE)
                    .getString("queryHash", "003056d32c2554def87228bc3fd9668a")
                    ?: "003056d32c2554def87228bc3fd9668a"
        }
        if (csrftoken.isBlank()) {
            csrftoken =
                context.getSharedPreferences("config", Context.MODE_PRIVATE)
                    .getString("csrftoken", "")
                    ?: ""
        }
        if (sessionID.isBlank()) {
            sessionID =
                context.getSharedPreferences("config", Context.MODE_PRIVATE)
                    .getString("sessionID", "")
                    ?: ""
        }
    }

    private var singleTask = Task()
    private var allTask = Task()

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

        if (!singleTask.isCompleted) {
            return
        }

        singleTask = Task()
        singleTask.isCompleted = false

        val request = requestBuilder.run {
            url(url)
            addHeader("Connection", "keep-alive")
            addHeader(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:82.0) Gecko/20100101 Firefox/82.0"
            )
            addHeader("Referer", "https://www.instagram.com/")
            if (csrftoken.isNotBlank() && sessionID.isNotBlank()) {
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

        if (!allTask.isCompleted) {
            return
        }

        allTask = Task()
        allTask.isCompleted = false
        callback.startForeground()

        val request = requestBuilder.run {
            url(url)
            addHeader("Connection", "keep-alive")
            addHeader(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:82.0) Gecko/20100101 Firefox/82.0"
            )
            addHeader("Referer", "https://www.instagram.com/")
            if (csrftoken.isNotBlank() && sessionID.isNotBlank()) {
                addHeader("Cookie", "csrftoken=$csrftoken; sessionid=$sessionID")
            }
            build()
        }
        client.newCall(request).enqueue(DownAllCallback())
    }

    fun getImgUrlInOtherPost() {
        callback.sendCount(allTask.urls.size)
        println(allTask.postCount)
        when {
            allTask.postCount in 1..50 -> {
                allTask.first = allTask.postCount
                allTask.postCount = 0
            }
            allTask.postCount > 50 -> {
                allTask.first = 50
                allTask.postCount -= 50
            }
            else -> {
                callback.sendMessage("一共发现了 ${allTask.urls.size} 张图片......\n 开始下载......")
                // 顺序模式
                allTask.urls = allTask.urls.reversed()
                client.newCall(requestBuilder.url(allTask.urls[0]).build())
                    .enqueue(SaveImgInAllCallback(0))

                // 速度模式
                /*for (i in 0..20) {
                    if (i < task.urls.size) {
                        client.newCall(requestBuilder.url(task.urls[i]).build())
                            .enqueue(SaveImgInAllCallback(i))
                    } else {
                        break
                    }
                }*/

                return
            }
        }

        val query =
            "https://www.instagram.com/graphql/query/?query_hash=$queryHash&variables={\"id\":\"${allTask.userID}\",\"first\":${allTask.first},\"after\":\"${allTask.endCursor}\"}"
        println("query url  $query")

        val request = requestBuilder.run {
            url(query)
            addHeader("Connection", "keep-alive")
            addHeader(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:82.0) Gecko/20100101 Firefox/82.0"
            )
            addHeader("Referer", "https://www.instagram.com/")
            if (csrftoken.isNotBlank() && sessionID.isNotBlank()) {
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
                        val fileName = singleTask.time + "_$index.jpg"

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val relativeLocation =
                                Environment.DIRECTORY_PICTURES + File.separator + "InsDownloader" + File.separator + singleTask.user
                            val bitMap: Bitmap? = BitmapFactory.decodeStream(response.body!!.byteStream())
                            if (bitMap == null) {
                                client.newCall(call.request()).enqueue(SaveImgCallback(index))
                                return
                            }
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
                            saveImgOnP(context, singleTask.user, fileName, response.body!!.bytes())
                            // callback

                        }

                        if (singleTask.completedOne()) {
                            handler.sendMessage(Message.obtain().apply {
                                obj = context.resources.getString(R.string.download_complete)
                            })
                        }
                        callback.sendSingleProgress(singleTask.completed)
                    }

                    else -> {
                        println("download img failed: ${response.code}")
                        handler.sendMessage(Message.obtain().apply {
                            obj = "保存图片错误：${response.code}"
                        })
                    }
                }
            } catch (e: Exception) {
                handler.sendMessage(Message.obtain().apply {
                    obj = "保存图片出现异常：$e"
                })
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
            // 这里需要限定重试次数
            client.newCall(call.request()).enqueue(DownSingleCallback())
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                when (response.code) {
                    200 -> {
                        response.body?.let { body ->
                            val code = body.string()
                            val patternUrl =
                                Pattern.compile("\"src\":\"(.{150,400}?)\",\"config_width\":1080,\"config_height\":.{3,5}")
                            val list = mutableListOf<String>()
                            with(patternUrl.matcher(code)) {
                                while (find()) {
                                    group(1)!!.replace("\\u0026", "&").also { s ->
                                        println(s)
                                        if (!list.contains(s)) list.add(s)
                                    }
                                }
                                singleTask.urls = list
                            }
                            if (list.isNullOrEmpty()) {
                                singleTask.isCompleted = true
                                handler.sendMessage(Message.obtain().apply {
                                    obj = "未发现图片 或 未登陆 或 为私密账户"
                                })
                            } else {
                                val patternUser =
                                    Pattern.compile("\"username\":\"(.{2,200})\",\"blocked_by_viewer\"")
                                with(patternUser.matcher(code)) {
                                    if (find() && group(1) != null) {
                                        singleTask.user = group(1)!!
                                        println(group(1))
                                    }
                                }
                                callback.sendSingleCount(list.size)
                                Thread.sleep(500L)
                                for (i in list.indices) {
                                    saveImg(list[i], i)
                                }
                            }
                        }
                    }

                    else -> {
                        singleTask.isCompleted = true
                        println("downSingle onResponse failed : ${response.code}")
                        handler.sendMessage(Message.obtain().apply {
                            obj = "downSingle 获取图片URL失败：${response.code}"
                        })
                    }
                }
            } catch (e: Exception) {
                handler.sendMessage(Message.obtain().apply {
                    obj = "downSingle 获取图片URL发生异常：$e"
                })
                singleTask.isCompleted = true
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
                            val list = allTask.urls.toMutableList()
                            for (i in medias.edges) {
                                if (!list.contains(i.node.display_url)) list.add(i.node.display_url)
                                if (i.node.edge_sidecar_to_children != null) {
                                    for (j in i.node.edge_sidecar_to_children.edges) {
                                        if (!list.contains(j.node.display_url)) list.add(j.node.display_url)
                                    }
                                }
                            }
                            allTask.urls = list
                        } else {
                            callback.sendMessage("获取后续Post图片URL失败：未发现图片")
                            println("获取后续Post图片URL失败：没有图片")
                        }
                        allTask.endCursor = medias.page_info.end_cursor

                        // 还存在没解析的post，但endCursor已经为null 的情况
                        if (allTask.endCursor == null) {
                            allTask.postCount = 0
                        }
                        getImgUrlInOtherPost()
                    }

                    400 -> {
                        callback.sendMessage("获取后续Post图片URL错误：400 Bad Request")
                        println("获取后续Post图片URL错误：400 Bad Request")
                        println(call.request().url.toString())
                        client.newCall(call.request()).enqueue(GetOtherPostUrlCallback())
                    }

                    410 -> {  // 410 Gone  被请求的资源在服务器上已经不再可用，而且没有任何已知的转发地址。
                        callback.sendMessage("获取后续Post图片URL错误：410 Gone")
                        println("获取后续Post图片URL错误：410 Gone")
                        allTask.postCount = 0
                        getImgUrlInOtherPost()
                    }

                    429 -> {
                        callback.sendMessage("获取后续Post图片URL错误：429 Too Many Requests")
                        println("获取后续Post图片URL错误：429 Too Many Requests")
                        Thread.sleep(1000L)
                        client.newCall(call.request()).enqueue(GetOtherPostUrlCallback())
                    }

                    else -> {
                        callback.sendMessage("获取后续Post图片URL错误：${response.code}")
                        println("获取后续Post图片URL错误：${response.code}")
                        client.newCall(call.request()).enqueue(GetOtherPostUrlCallback())
                    }
                }
            } catch (e: Exception) {
                callback.sendMessage("获取后续Post图片URL发生异常：$e")
                println("获取后续Post图片URL发生异常：")
                e.printStackTrace()
            } finally {
                response.close()
            }
        }
    }

    inner class DownAllCallback : Callback {
        override fun onFailure(call: Call, e: IOException) {
            callback.sendMessage("获取第1个至第12个Post的图片URL onFailure : $e")
            println("获取第1个至第12个Post的图片URL 失败 onFailure : ")
            e.printStackTrace()
            // 重试次数
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
                            allTask.user = matcher1.group(1) ?: ""
                            if (allTask.user == "accounts/login" || allTask.user == "") {
                                callback.sendMessage("未登陆 或 登录状态已失效！")
                                allTask.isCompleted = true
                                return
                            } else {
                                callback.sendUser(allTask.user)
                                callback.sendMessage("开始搜索 ${allTask.user} 发布的所有图片......")
                            }
                        } else {
                            println("未找到用户！")
                            callback.sendMessage("未登陆 或 登录状态已失效！")
                            allTask.isCompleted = true
                            return
                        }

                        // 获取第1个至第12个Post的图片URL
                        val matcher = Pattern.compile(
                            "<script type=\"text/javascript\">window._sharedData = (.*?);</script>\n" +
                                    "<script type=\"text/javascript\">window.__initialDataLoaded"
                        ).matcher(sourceCode)
                        if (matcher.find()) {
                            val insData = Gson().fromJson(matcher.group(1), InsData::class.java)
                            allTask.userID =
                                insData.entry_data.ProfilePage[0].graphql.user.id.also { println("userID $it") }
                            allTask.postCount =
                                insData.entry_data.ProfilePage[0].graphql.user.edge_owner_to_timeline_media.count.let {
                                    println("postCount $it")
                                    it - 12
                                }
                            val medias =
                                insData.entry_data.ProfilePage[0].graphql.user.edge_owner_to_timeline_media
                            allTask.endCursor =
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
                            allTask.urls = list
                        }

                        if (allTask.urls.isEmpty()) {
                            callback.sendMessage("没有发现图片......")
                            allTask.isCompleted = true
                            callback.stopForeground()
                            return
                        }
                        // 获取第13个post开始的图片Url
                        getImgUrlInOtherPost()
                    }
                    404 -> {
                        callback.sendMessage("获取第1个至第12个Post的图片URL 错误：404")
                        println("获取第1个至第12个Post的图片URL 错误：404")
                    }
                    else -> {
                        callback.sendMessage("获取第1个至第12个Post的图片URL 错误：${response.code}")
                        println("获取第1个至第12个Post的图片URL 错误：${response.code}")
                    }
                }
            } catch (e: Exception) {
                callback.sendMessage("获取第1个至第12个Post的图片URL发生异常: $e")
                println("user = ${allTask.user}")
                println("获取第1个至第12个Post的图片URL发生异常: $e")
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
                        val fileName = allTask.time + "_$index.jpg"

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val relativeLocation =
                                Environment.DIRECTORY_PICTURES + File.separator + "InsDownloader" + File.separator + allTask.user
                            val bitMap: Bitmap? = BitmapFactory.decodeStream(response.body!!.byteStream())
                            if (bitMap == null) {
                                client.newCall(call.request()).enqueue(SaveImgInAllCallback(index))
                                return
                            }
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
                            saveImgOnP(context, allTask.user, fileName, response.body!!.bytes())
                            // callback

                        }

                        if (allTask.completedOne()) {
                            callback.sendMessage("${context.resources.getString(R.string.download_complete)}......")
                            callback.stopForeground()
                        }
                        callback.sendProgress(allTask.completed)

                        // 顺序模式
                        if (index + 1 < allTask.urls.size) {
                            client.newCall(requestBuilder.url(allTask.urls[index + 1]).build())
                                .enqueue(SaveImgInAllCallback(index + 1))
                        }
                        // 速度模式
                        /*if (index + 21 < task.urls.size) {
                            client.newCall(requestBuilder.url(task.urls[index + 21]).build())
                                .enqueue(SaveImgInAllCallback(index + 21))
                        }*/
                    }

                    410 -> {  // Gone
                        callback.sendMessage("下载第 $index 张图片 错误：410 Gone")
                        println("下载第 $index 张图片 错误：410 Gone")
                        if (allTask.completedOne()) {
                            callback.sendMessage("${context.resources.getString(R.string.download_complete)}......")
                            callback.stopForeground()
                        }
                        callback.sendProgress(allTask.completed)

                        if (index + 1 < allTask.urls.size) {
                            client.newCall(requestBuilder.url(allTask.urls[index + 1]).build())
                                .enqueue(SaveImgInAllCallback(index + 1))
                        }
                        /*if (index + 21 < task.urls.size) {
                            client.newCall(requestBuilder.url(task.urls[index + 21]).build())
                                .enqueue(SaveImgInAllCallback(index + 21))
                        }*/
                    }

                    429 -> {  // 429 Too Many Requests
                        callback.sendMessage("下载第 $index 张图片 错误：429 Too Many Requests")
                        println("下载第 $index 张图片 错误：429 Too Many Requests")
                        Thread.sleep(1000L)
                        client.newCall(call.request()).enqueue(SaveImgInAllCallback(index))
                    }

                    else -> {
                        callback.sendMessage("下载第 $index 张图片 错误：${response.code}")
                        println("下载第 $index 张图片 错误：${response.code}")
                        client.newCall(call.request()).enqueue(SaveImgInAllCallback(index))
                    }
                }
            } catch (e: Exception) {
                callback.sendMessage("下载第 $index 张图片 发生异常：$e")
                println("下载第 $index 张图片 发生异常：")
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

    fun sendSingleProgress(p: Int)
    fun sendSingleCount(c: Int)

    fun startForeground() {}
    fun stopForeground() {}
}