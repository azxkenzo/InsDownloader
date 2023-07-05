package org.sei.insdownloader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import okhttp3.*
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class Downloader(private val callback: DownloadCallback, private val context: Context) {

    companion object {
        @Volatile
        var queryHash = ""

        @Volatile
        var csrftoken = ""

        @Volatile
        var sessionID = ""

        @Volatile
        var dsUserId = ""
    }

    init {
        if (queryHash.isBlank()) {
            queryHash = context.getSharedPreferences("config", Context.MODE_PRIVATE)
                .getString("queryHash", "69cba40317214236af40e7efa697781d")
                ?: "69cba40317214236af40e7efa697781d"
        }
        if (csrftoken.isBlank()) {
            csrftoken = context.getSharedPreferences("config", Context.MODE_PRIVATE)
                .getString("csrftoken", "") ?: ""
        }
        if (sessionID.isBlank()) {
            sessionID = context.getSharedPreferences("config", Context.MODE_PRIVATE)
                .getString("sessionID", "") ?: ""
        }
        if (dsUserId.isBlank()) {
            dsUserId = context.getSharedPreferences("config", Context.MODE_PRIVATE)
                .getString("dsUserId", "") ?: ""
        }
    }

    private var singleTask = Task()
    private var allTask = Task()

    private val saveImageRequestBuilder by lazy { Request.Builder() }

    private val client by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(10L, TimeUnit.SECONDS)
            .readTimeout(50L, TimeUnit.SECONDS)
            .writeTimeout(50L, TimeUnit.SECONDS)
            .dispatcher(Dispatcher().apply {
                maxRequestsPerHost = 50
            })
            .connectionPool(ConnectionPool())
            .eventListener(object : EventListener() {
                override fun callFailed(call: Call, ioe: IOException) {
                    callback.sendInsMessage("EventListener.callFailed: $ioe")
                }

                override fun requestFailed(call: Call, ioe: IOException) {
                    callback.sendInsMessage("EventListener.requestFailed: $ioe")
                }

                override fun responseFailed(call: Call, ioe: IOException) {
                    callback.sendInsMessage("EventListener.responseFailed: $ioe")
                }

                override fun connectFailed(
                    call: Call,
                    inetSocketAddress: InetSocketAddress,
                    proxy: Proxy,
                    protocol: Protocol?,
                    ioe: IOException
                ) {
                    callback.sendInsMessage("connectFailed: $ioe")
                }
            })
            .build()
    }

    private val showToastHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            (msg.obj as? String)?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun downSingle(url: String) {
        println("[Downloader.downSingle] url : $url")

        if (!singleTask.isCompleted) {
            println("[Downloader.downSingle] singleTask is not completed")
            return
        }

        singleTask = Task()
        singleTask.isCompleted = false

        val request = Request.Builder().run {
            url(url)
            addHeader("Connection", "keep-alive")
            addHeader(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:82.0) Gecko/20100101 Firefox/82.0"
            )
            addHeader("Referer", "https://www.instagram.com/")
            println("[Downloader.downSingle] csrftoken=$csrftoken; ds_user_id=$dsUserId; sessionid=$sessionID")
            addHeader("Cookie", "csrftoken=$csrftoken; ds_user_id=$dsUserId; sessionid=$sessionID")
            build()
        }
        client.newCall(request).enqueue(DownSingleCallback())
    }

    fun saveImgOrVideo(url: DownloadMedia) {
        val request = saveImageRequestBuilder
            .url(url.url)
            .get()
            .build()
        client.newCall(request).enqueue(SaveImgOrVideoCallback(url))
    }

    fun downAll(url: String) {
        println("downAll:  $url")

        if (!allTask.isCompleted) {
            println("allTask is not completed")
            return
        }

        allTask = Task()
        allTask.isCompleted = false
        callback.startForeground()

        val request = Request.Builder().run {
            url(url)
            addHeader("Connection", "keep-alive")
            addHeader(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:82.0) Gecko/20100101 Firefox/82.0"
            )
            addHeader("Referer", "https://www.instagram.com/")
            addHeader("Cookie", "csrftoken=$csrftoken; ds_user_id=$dsUserId; sessionid=$sessionID")
            build()
        }
        client.newCall(request).enqueue(DownAllCallback())
    }

    fun getImgUrlInOtherPost() {
        callback.sendInsCount(allTask.urls.size)
        println("allTask.postCount: ${allTask.postCount}")
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
                callback.sendInsMessage("一共发现了 ${allTask.urls.size} 张图片......\n 开始下载......")
                // 顺序模式
                allTask.urls = allTask.urls.reversed()
                for (i in allTask.urls) {
                    println(i)
                }
                allTask.nameFormat = allTask.urls.size

                for (i in allTask.urls.indices) {
                    client.newCall(saveImageRequestBuilder.url(allTask.urls[i].url).build()).enqueue(SaveImgInAllCallback(allTask.urls[i], i))
                }

                return
            }
        }

        val query =
            "https://www.instagram.com/graphql/query/?query_hash=$queryHash&variables={\"id\":\"${allTask.userID}\",\"first\":${allTask.first},\"after\":\"${allTask.endCursor}\"}"
        println("query url  $query")

        val request = Request.Builder().run {
            url(query)
            addHeader("Connection", "keep-alive")
            addHeader(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:82.0) Gecko/20100101 Firefox/82.0"
            )
            addHeader("Referer", "https://www.instagram.com/")
            addHeader("Cookie", "csrftoken=$csrftoken; ds_user_id=$dsUserId; sessionid=$sessionID")
            build()
        }
        client.newCall(request).enqueue(GetOtherPostUrlCallback())
    }

    inner class DownSingleCallback : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("[DownSingleCallback.onFailure]: ")
            e.printStackTrace()
            // 这里需要限定重试次数
            client.newCall(call.request()).enqueue(DownSingleCallback())
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        override fun onResponse(call: Call, response: Response) {
            try {
                when (response.code) {
                    200 -> {
                        response.body?.let { body ->
                            val code = body.string()
                            val regex = "\"media_id\":\"(.+?)\""
                            var mediaId = ""
                            with(Pattern.compile(regex).matcher(code)) {
                                if (find()) {
                                    mediaId = group(1) ?: ""
                                }
                            }
                            if (mediaId.isEmpty()) {
                                singleTask.isCompleted = true
                                showToastHandler.sendMessage(Message.obtain().apply {
                                    obj = "未发现图片 或 未登陆 或 为私密账户"
                                })
                            } else {
                                println("[DownSingleCallback]  mediaId: $mediaId")
                                val request = Request.Builder().run {
                                    url("https://i.instagram.com/api/v1/media/$mediaId/info/")
                                    addHeader("Connection", "keep-alive")
                                    addHeader("X-IG-App-ID", "936619743392459")  // 访问 api 时需要
                                    addHeader(
                                        "User-Agent",
                                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:102.0) Gecko/20100101 Firefox/102.0"
                                    )
                                    addHeader("Referer", "https://www.instagram.com/")
                                    addHeader(
                                        "Cookie",
                                        "csrftoken=$csrftoken; sessionid=$sessionID"
                                    )
                                    build()
                                }
                                client.newCall(request).enqueue(RetrieveSingleUriCallback())
                            }
                        }
                    }

                    else -> {
                        singleTask.isCompleted = true
                        println("[DownSingleCallback.onResponse] failed : ${response.code}")
                        showToastHandler.sendMessage(Message.obtain().apply {
                            obj = "downSingle 获取图片URL失败：${response.code}"
                        })
                    }
                }
            } catch (e: Exception) {
                showToastHandler.sendMessage(Message.obtain().apply {
                    obj = "downSingle 获取图片URL发生异常：$e"
                })
                singleTask.isCompleted = true
                println("[DownSingleCallback.onResponse] Exception :")
                e.printStackTrace()
            } finally {
                response.close()
            }
        }
    }

    inner class RetrieveSingleUriCallback : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("[RetrieveSingleUriCallback]  onFailure")
        }

        override fun onResponse(call: Call, response: Response) {
            when (response.code) {
                200 -> {
                    println("[RetrieveSingleUriCallback]  mimeType: ${response.header("content-type", "")}") // application/json; charset=utf-8
                    // println("RetrieveSingleUriCallback  ${response.headers}")
                    val json = response.body?.string()
                    json?.let {
                        val postPage2 = Gson().fromJson(json, PostPage2::class.java)
                        val urls = mutableListOf<DownloadMedia>()

                        when (postPage2.items[0].media_type) {
                            1 -> { // for image
                                postPage2.items[0].image_versions2?.let { image_versions2 ->
                                    println(image_versions2.candidates[0].width)
                                    println(image_versions2.candidates[0].height)
                                    println(image_versions2.candidates[0].url)
                                    urls.add(
                                        DownloadMedia(
                                            postPage2.items[0].taken_at,
                                            image_versions2.candidates[0].url.replace(
                                                "\\u0026",
                                                "&"
                                            ), 0
                                        )
                                    )
                                }
                            }

                            2 -> { // for video
                                postPage2.items[0].video_versions?.let { video_versions ->
                                    println(video_versions[0].width)
                                    println(video_versions[0].height)
                                    println(video_versions[0].url)
                                    urls.add(
                                        DownloadMedia(
                                            postPage2.items[0].taken_at,
                                            video_versions[0].url.replace("\\u0026", "&"), 0
                                        )
                                    )
                                }
                            }

                            else -> { // for other
                                postPage2.items[0].carousel_media?.let { carousel_media ->
                                    println(carousel_media[0].image_versions2.candidates[0].width)
                                    println(carousel_media[0].image_versions2.candidates[0].height)
                                    println(carousel_media[0].image_versions2.candidates[0].url)
                                    for (i in carousel_media.indices) {
                                        if (carousel_media[i].media_type == 1) {
                                            urls.add(
                                                DownloadMedia(
                                                    postPage2.items[0].taken_at,
                                                    carousel_media[i].image_versions2.candidates[0].url.replace(
                                                        "\\u0026",
                                                        "&"
                                                    ), i.toByte()
                                                )
                                            )
                                        } else if (carousel_media[i].media_type == 2) {
                                            carousel_media[i].video_versions?.get(0)?.url?.replace(
                                                "\\u0026",
                                                "&"
                                            )
                                                ?.let { url ->
                                                    urls.add(DownloadMedia(postPage2.items[0].taken_at, url, i.toByte()))
                                                }
                                        }
                                    }
                                }
                            }
                        }
                        singleTask.user = postPage2.items[0].user.username
                        singleTask.urls = urls
                        println("[RetrieveSingleUriCallback]  user: ${singleTask.user}")
                        callback.sendInsSingleCount(urls.size)
                        Thread.sleep(50L)
                        for (i in urls.indices) {
                            saveImgOrVideo(urls[i])
                        }
                    }
                }

                else -> {
                    println("[RetrieveUriCallback] failed: code = ${response.code}")
                }
            }
        }
    }

    inner class SaveImgOrVideoCallback(val url: DownloadMedia) : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("[SaveImgOrVideoCallback] onFailure: ")
            e.printStackTrace()
            client.newCall(call.request()).enqueue(SaveImgOrVideoCallback(url))
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                when (response.code) {
                    200 -> {
                        val mimeType = response.header("content-type", "") ?: ""
                        val fileName =
                            url.taken_at.toString() + "_${url.index}.${getFileType(mimeType)}"

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val relativeLocation =
                                Environment.DIRECTORY_PICTURES + File.separator + "InsDownloader" + File.separator + singleTask.user
                            if (mimeType == "image/jpeg") {
                                val bitMap: Bitmap? =
                                    BitmapFactory.decodeStream(response.body!!.byteStream())
                                if (bitMap == null) {
                                    client.newCall(call.request())
                                        .enqueue(SaveImgOrVideoCallback(url))
                                    return
                                }
                                saveImgOnQ(
                                    context,
                                    callback,
                                    relativeLocation,
                                    fileName,
                                    bitMap,
                                    Bitmap.CompressFormat.JPEG,
                                    dateTaken = url.taken_at
                                )
                                // callback
                            } else if (mimeType == "video/mp4") {
                                saveVideoOnQ(
                                    context,
                                    response.body!!.byteStream(),
                                    callback,
                                    relativeLocation,
                                    fileName,
                                    mimeType,
                                    response.body!!.contentLength(),
                                    url.taken_at
                                )
                            }
                        } else {
                            saveImgOnP(context, singleTask.user, fileName, response.body!!.bytes())
                            // callback

                        }

                        if (singleTask.completedOne()) {
                            showToastHandler.sendMessage(Message.obtain().apply {
                                obj = context.resources.getString(R.string.download_complete)
                            })
                        }
                        callback.sendInsSingleProgress(singleTask.completed)
                    }

                    else -> {
                        println("SaveImgCallback failed: ${response.code}")
                        showToastHandler.sendMessage(Message.obtain().apply {
                            obj = "保存图片错误：${response.code}"
                        })
                    }
                }
            } catch (e: Exception) {
                showToastHandler.sendMessage(Message.obtain().apply {
                    obj = "保存图片出现异常：$e"
                })
                println("download img Exception: ")
                e.printStackTrace()
            } finally {
                response.close()
            }
        }

    }


    inner class GetOtherPostUrlCallback : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("getImgUrlInOtherPost onFailure：")
            callback.sendInsMessage("获取后续Post图片URL失败 onFailure : $e")
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
                                // 只有1张图片时 edge_sidecar_to_children 才为 null
                                val estc = i.node.edge_sidecar_to_children
                                if (estc != null) {
                                    for (j in estc.edges.indices) {
                                        if (estc.edges[j].node.is_video) {
                                            if (!list.any { it.url == estc.edges[j].node.video_url }) list.add(
                                                DownloadMedia(
                                                    i.node.taken_at_timestamp,
                                                    estc.edges[j].node.video_url!!,
                                                    j.toByte()
                                                )
                                            )
                                        } else {
                                            if (!list.any { it.url == estc.edges[j].node.display_url }) list.add(
                                                DownloadMedia(
                                                    i.node.taken_at_timestamp,
                                                    estc.edges[j].node.display_url,
                                                    j.toByte()
                                                )
                                            )
                                        }
                                    }
                                } else {
                                    if (i.node.is_video) {
                                        if (!list.any { it.url == i.node.video_url }) list.add(DownloadMedia(i.node.taken_at_timestamp, i.node.video_url!!, 0))
                                    } else {
                                        if (!list.any { it.url == i.node.display_url }) list.add(DownloadMedia(i.node.taken_at_timestamp, i.node.display_url, 0))
                                    }
                                }
                            }
                            allTask.urls = list
                        } else {
                            callback.sendInsMessage("获取后续Post图片URL失败：未发现图片")
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
                        callback.sendInsMessage("获取后续Post图片URL错误：400 Bad Request")
                        println("获取后续Post图片URL错误：400 Bad Request")
                        println(call.request().url.toString())
                        client.newCall(call.request()).enqueue(GetOtherPostUrlCallback())
                    }

                    410 -> {  // 410 Gone  被请求的资源在服务器上已经不再可用，而且没有任何已知的转发地址。
                        callback.sendInsMessage("获取后续Post图片URL错误：410 Gone")
                        println("获取后续Post图片URL错误：410 Gone")
                        allTask.postCount = 0
                        getImgUrlInOtherPost()
                    }

                    429 -> {
                        callback.sendInsMessage("获取后续Post图片URL错误：429 Too Many Requests")
                        println("获取后续Post图片URL错误：429 Too Many Requests")
                        Thread.sleep(1000L)
                        client.newCall(call.request()).enqueue(GetOtherPostUrlCallback())
                    }

                    else -> {
                        callback.sendInsMessage("获取后续Post图片URL错误：${response.code}")
                        println("获取后续Post图片URL错误：${response.code}")
                        client.newCall(call.request()).enqueue(GetOtherPostUrlCallback())
                    }
                }
            } catch (e: Exception) {
                callback.sendInsMessage("获取后续Post图片URL发生异常：$e")
                println("获取后续Post图片URL发生异常：")
                e.printStackTrace()
            } finally {
                response.close()
            }
        }
    }

    inner class DownAllCallback : Callback {
        override fun onFailure(call: Call, e: IOException) {
            callback.sendInsMessage("获取第1个至第12个Post的图片URL onFailure : $e")
            println("获取第1个至第12个Post的图片URL 失败 onFailure : ")
            e.printStackTrace()
            // 重试次数
            client.newCall(call.request()).enqueue(DownAllCallback())
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                when (response.code) {
                    200 -> {
                        val sourceCode = response.body!!.string()
                        // 获取 user
                        val matcher1 =
                            Pattern.compile("<link rel=\"alternate\" href=\"https://www.instagram.com/(.+?)/")
                                .matcher(sourceCode)
                        if (matcher1.find()) {
                            allTask.user = matcher1.group(1) ?: ""
                            if (allTask.user == "accounts/login" || allTask.user == "") {
                                callback.sendInsMessage("未登陆 或 登录状态已失效！")
                                allTask.isCompleted = true
                                return
                            } else {
                                callback.sendInsUser(allTask.user)
                                callback.sendInsMessage("开始搜索 ${allTask.user} 发布的所有图片......")
                            }
                        } else {
                            println("未找到用户！")
                            callback.sendInsMessage("未登陆 或 登录状态已失效！")
                            allTask.isCompleted = true
                            return
                        }

                        // 获取第1个至第12个Post的图片URL
                        val request = Request.Builder().run {
                            url("https://i.instagram.com/api/v1/users/web_profile_info/?username=${allTask.user}")
                            addHeader("Connection", "keep-alive")
                            addHeader("X-IG-App-ID", "936619743392459")  // 访问 api 时需要
                            addHeader(
                                "User-Agent",
                                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:102.0) Gecko/20100101 Firefox/102.0"
                            )
                            addHeader("Referer", "https://www.instagram.com/")
                            println("csrftoken=$csrftoken; ds_user_id=$dsUserId; sessionid=$sessionID;")
                            addHeader(
                                "Cookie",
                                "csrftoken=$csrftoken; ds_user_id=$dsUserId; sessionid=$sessionID;"
                            )
                            build()
                        }
                        client.newCall(request).enqueue(RetrieveAllUriCallback())
                    }
                    404 -> {
                        callback.sendInsMessage("获取第1个至第12个Post的图片URL 错误：404")
                        println("获取第1个至第12个Post的图片URL 错误：404")
                    }
                    else -> {
                        callback.sendInsMessage("获取第1个至第12个Post的图片URL 错误：${response.code}")
                        println("获取第1个至第12个Post的图片URL 错误：${response.code}")
                    }
                }
            } catch (e: Exception) {
                callback.sendInsMessage("获取第1个至第12个Post的图片URL发生异常: $e")
                println("user = ${allTask.user}")
                println("获取第1个至第12个Post的图片URL发生异常: $e")
                e.printStackTrace()
            } finally {
                response.close()
            }
        }

    }

    inner class RetrieveAllUriCallback : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("RetrieveAllUriCallback  onFailure")
        }

        override fun onResponse(call: Call, response: Response) {
            val code = response.body?.string()
            code?.let {
                println(code)
                try {
                    val profileInfo = Gson().fromJson(code, ProfileInfo::class.java)
                    allTask.userID = profileInfo.data.user.id.also { println("userID $it") }
                    allTask.postCount =
                        profileInfo.data.user.edge_owner_to_timeline_media.count.let {
                            println("postCount $it")
                            it - 12
                        }
                    val medias = profileInfo.data.user.edge_owner_to_timeline_media
                    allTask.endCursor =
                        medias.page_info.end_cursor.also { println("endCursor: $it") }

                    val list = mutableListOf<DownloadMedia>()
                    for (i in medias.edges) {
                        // 只有1张图片时 edge_sidecar_to_children 才为 null
                        val estc = i.node.edge_sidecar_to_children
                        if (estc != null) {
                            for (j in estc.edges.indices) {
                                if (estc.edges[j].node.is_video) {
                                    list.any { it.url == estc.edges[j].node.video_url }
                                    if (!list.any { it.url == estc.edges[j].node.video_url }) list.add(
                                        DownloadMedia(
                                            i.node.taken_at_timestamp,
                                            estc.edges[j].node.video_url!!,
                                            j.toByte()
                                        )
                                    )
                                } else {
                                    if (!list.any { it.url == estc.edges[j].node.display_url }) list.add(
                                        DownloadMedia(
                                            i.node.taken_at_timestamp,
                                            estc.edges[j].node.display_url,
                                            j.toByte()
                                        )
                                    )
                                }
                            }
                        } else {
                            if (i.node.is_video) {
                                if (!list.any { it.url == i.node.video_url }) list.add(DownloadMedia(i.node.taken_at_timestamp, i.node.video_url!!, 0))
                            } else {
                                if (!list.any { it.url == i.node.display_url }) list.add(DownloadMedia(i.node.taken_at_timestamp, i.node.display_url, 0))
                            }
                        }
                    }
                    allTask.urls = list
                } catch (e: Exception) {
                    println(e)
                }

                if (allTask.urls.isEmpty()) {
                    callback.sendInsMessage("没有发现图片......")
                    allTask.isCompleted = true
                    callback.stopForeground()
                    return
                }
                // 获取第13个post开始的图片Url
                getImgUrlInOtherPost()
            }
        }

    }


    inner class SaveImgInAllCallback(val url: DownloadMedia, private val index: Int) : Callback {
        override fun onFailure(call: Call, e: IOException) {
            callback.sendInsMessage("下载第 $index 张图片 onFailure：$e")
            println("下载第 $index 张图片 onFailure：")
            e.printStackTrace()

            client.newCall(call.request()).enqueue(SaveImgInAllCallback(url, index))
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                when (response.code) {
                    200 -> {
                        val mimeType = response.header("content-type", "") ?: ""
                        val fileName =
                            url.taken_at.toString() + "_${url.index}.${getFileType(mimeType)}"

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val relativeLocation =
                                Environment.DIRECTORY_PICTURES + File.separator + "InsDownloader" + File.separator + allTask.user

                            if (mimeType == "image/jpeg") {
                                val bitMap: Bitmap? =
                                    BitmapFactory.decodeStream(response.body!!.byteStream())
                                if (bitMap == null) {
                                    client.newCall(call.request()).enqueue(SaveImgOrVideoCallback(url))
                                    return
                                }
                                saveImgOnQ(
                                    context,
                                    callback,
                                    relativeLocation,
                                    fileName,
                                    bitMap,
                                    Bitmap.CompressFormat.JPEG,
                                    dateTaken = url.taken_at
                                )
                                // callback
                            } else if (mimeType == "video/mp4") {
                                saveVideoOnQ(
                                    context,
                                    response.body!!.byteStream(),
                                    callback,
                                    relativeLocation,
                                    fileName,
                                    mimeType,
                                    response.body!!.contentLength(),
                                    url.taken_at
                                )
                            }

                        } else {
                            saveImgOnP(context, allTask.user, fileName, response.body!!.bytes())
                            // callback

                        }

                        if (allTask.completedOne()) {
                            callback.sendInsMessage("${context.resources.getString(R.string.download_complete)}......")
                            callback.stopForeground()
                        }
                        callback.sendInsProgress(allTask.completed)

                        // 顺序模式
//                        if (index + 1 < allTask.urls.size) {
//                            client.newCall(requestBuilder.url(allTask.urls[index + 1]).build())
//                                .enqueue(SaveImgInAllCallback(index + 1))
//                        }
                        // 速度模式
                        /*if (index + 21 < task.urls.size) {
                            client.newCall(requestBuilder.url(task.urls[index + 21]).build())
                                .enqueue(SaveImgInAllCallback(index + 21))
                        }*/
                    }

                    410 -> {  // Gone
                        callback.sendInsMessage("下载第 $index 张图片 错误：410 Gone")
                        println("下载第 $index 张图片 错误：410 Gone")
                        if (allTask.completedOne()) {
                            callback.sendInsMessage("${context.resources.getString(R.string.download_complete)}......")
                            callback.stopForeground()
                        }
                        callback.sendInsProgress(allTask.completed)

                        if (index + 1 < allTask.urls.size) {
                            client.newCall(
                                saveImageRequestBuilder.url(allTask.urls[index + 1].url).build()
                            )
                                .enqueue(SaveImgInAllCallback(allTask.urls[index + 1], index + 1))
                        }
                        /*if (index + 21 < task.urls.size) {
                            client.newCall(requestBuilder.url(task.urls[index + 21]).build())
                                .enqueue(SaveImgInAllCallback(index + 21))
                        }*/
                    }

                    429 -> {  // 429 Too Many Requests
                        callback.sendInsMessage("下载第 $index 张图片 错误：429 Too Many Requests")
                        println("下载第 $index 张图片 错误：429 Too Many Requests")
                        Thread.sleep(1000L)
                        client.newCall(call.request()).enqueue(SaveImgInAllCallback(url, index))
                    }

                    else -> {
                        callback.sendInsMessage("下载第 $index 张图片 错误：${response.code}")
                        println("下载第 $index 张图片 错误：${response.code}")
                        client.newCall(call.request()).enqueue(SaveImgInAllCallback(url, index))
                    }
                }
            } catch (e: Exception) {
                callback.sendInsMessage("下载第 $index 张图片 发生异常：$e")
                println("下载第 $index 张图片 发生异常：")
                e.printStackTrace()
                client.newCall(call.request()).enqueue(SaveImgInAllCallback(url, index))
            } finally {
                response.close()
            }
        }

    }


}

