package org.sei.insdownloader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.*
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class TwitterDownloader(private val context: Context, private val callback: DownloadCallback) {

    private var imageTask = Task()
    private var videoTask = Task()
    private var weiboTask = Task()

    private val gson by lazy { Gson() }

    private val client by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(60L, TimeUnit.SECONDS)
            .readTimeout(300L, TimeUnit.SECONDS)
            .writeTimeout(300L, TimeUnit.SECONDS)
            .dispatcher(Dispatcher().apply {
                maxRequestsPerHost = 10
            })
            .connectionPool(ConnectionPool().apply {

            })
            .eventListener(object : EventListener() {
                override fun callFailed(call: Call, ioe: IOException) {
                    println("Call Failed: $ioe")
                }

                override fun requestFailed(call: Call, ioe: IOException) {
                    println("Request Failed: $ioe")
                }

                override fun responseFailed(call: Call, ioe: IOException) {
                    println("Response Failed: $ioe")
                }

                override fun connectFailed(
                    call: Call,
                    inetSocketAddress: InetSocketAddress,
                    proxy: Proxy,
                    protocol: Protocol?,
                    ioe: IOException
                ) {
                    println("Connect Failed: $ioe")
                }
            })
            .build()
    }

    private val twitterRequestBuilder by lazy { Request.Builder().addHeader("Authorization", "Bearer $BEARER_TOKEN") }

    private val builderWithCookie = Request.Builder().apply {
        addHeader("Cookie", "SUB=_2A25MVi5TDeRhGeNP7FAZ-CzMyDmIHXVvIhibrDV8PUNbmtB-LXHAkW9NTnhVfm10Eq52EBD60G3doTVIcvEf6fKa; SUBP=0033WrSXqPxfM725Ws9jqgMF55529P9D9WW.2j4xmK7IBQ4YdynLsKkQ5JpX5KzhUgL.Fo-pS0zR1hz7e0-2dJLoI7LrdcvadcvaIN-t; login_sid_t=c61afb8b3b5a28bc864d38db207eef27; cross_origin_proto=SSL; WBStorage=6ff1c79b|undefined; _s_tentry=passport.weibo.com; Apache=8673293929360.139.1632787907548; SINAGLOBAL=8673293929360.139.1632787907548; ULV=1632787907551:1:1:1:8673293929360.139.1632787907548:; wb_view_log=2560*14401; ALF=1664323970; SSOLoginState=1632787971; wvr=6; wb_view_log_5172882035=2560*14401; webim_unReadCount=%7B%22time%22%3A1632787997610%2C%22dm_pub_total%22%3A0%2C%22chat_group_client%22%3A0%2C%22chat_group_notice%22%3A0%2C%22allcountNum%22%3A0%2C%22msgbox%22%3A0%7D")
    }
    private val builderWithoutCookie = Request.Builder().apply {
        addHeader("Cookie", "SUB=_2AkMWDBrWf8NxqwJRmP4TzGjjbY1yzA_EieKgUOsNJRMxHRl-yT9jqlIQtRB6PYw0OQBQi5X-jd6ATqeG7NCQxsJXbsGu; SUBP=0033WrSXqPxfM72-Ws9jqgMF55529P9D9WWxn4kSvT9AY_PfcbQnyqC9")
        //addHeader("User-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/93.0.4577.82 Safari/537.36")
    }

    fun downloadImage(username: String) {
        if (!imageTask.isCompleted) {
            return
        }
        imageTask = Task()
        imageTask.isCompleted = false
        retrieveUserId(username) { id ->
            retrieveImageUrl(id)
        }
    }

    fun downloadVideo(username: String) {
        if (!videoTask.isCompleted) {
            return
        }
        videoTask = Task()
        videoTask.isCompleted = false
        retrieveUserId(username) { id ->
            retrieveVideoUrl(id)
        }
    }

    private fun retrieveUserId(username: String, doThen: (String) -> Unit) {
        val request = twitterRequestBuilder.run {
            url(getTwitterUserId(username))
            build()
        }
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("retrieveUserId onFailure $e")
            }

            override fun onResponse(call: Call, response: Response) {
                when (response.code) {
                    200 -> {
                        try {
                            val json = response.body?.string()
                            println(json)
                            val userIdResponse = gson.fromJson(json, UserIdResponse::class.java)
                            println("id = ${userIdResponse.data.id}")
                            doThen(userIdResponse.data.id)
                        } catch (e: Exception) {
                            println("retrieveUserId Exception  $e")
                        } finally {
                            response.close()
                        }
                    }

                    else -> {  // 用户名不存在时 code 为 400
                        println("retrieveUserId code ${response.code} ${response.message}")
                    }
                }
            }

        })
    }

    private fun retrieveImageUrl(userid: String) {
        val request = twitterRequestBuilder.run {
            url(getTimelinesV2(userid))
            build()
        }
        client.newCall(request).enqueue(RetrieveImageUrlCallback(userid))
    }

    private fun retrieveVideoUrl(userid: String) {
        val request = twitterRequestBuilder.run {
            url(getTimelinesV101(userid))
            build()
        }
        client.newCall(request).enqueue(RetrieveVideoUrlCallback(userid))
    }

    inner class RetrieveImageUrlCallback(private val userid: String) : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("retrieveImageUrl onFailure $e")
        }

        override fun onResponse(call: Call, response: Response) {
            when (response.code) {
                200 -> {
                    try {
                        val json = response.body?.string()
                        val timelinesResponse = gson.fromJson(json, TimelinesResponse::class.java)
                        println("tweet count  ${timelinesResponse.meta.result_count}")
                        imageTask.untilId = timelinesResponse.meta.oldest_id ?: ""
                        timelinesResponse.includes?.let { includes ->
                            val urls = includes.media.filter { it.url != null }.map { DownloadMedia(0, it.url!!, 0) }
                            imageTask.urls = imageTask.urls.toMutableList().apply { addAll(urls) }.toList()
                        }

                        if (timelinesResponse.meta.result_count > 0) {
                            val request = twitterRequestBuilder.run {
                                url("${getTimelinesV2(userid)}&until_id=${timelinesResponse.meta.oldest_id}")
                                build()
                            }
                            client.newCall(request).enqueue(RetrieveImageUrlCallback(userid))
                            return
                        }

                        println("count : ${imageTask.urls.size}")
                        callback.sendTwitterCount(imageTask.urls.size)
                        callback.startForeground()
                        imageTask.nameFormat = imageTask.urls.size
                        for (index in imageTask.urls.indices) {
                            client.newCall(twitterRequestBuilder.run {
                                url("${imageTask.urls[index]}?name=4096x4096")
                                build()
                            }).enqueue(TwitterDownloadCallback(index))
                        }
                    } catch (e: Exception) {
                        println("retrieveImageUrl Exception  $e")
                    } finally {
                        response.close()
                    }
                }

                else -> {
                    println("retrieveImageUrl code ${response.code} ${response.message}")
                }
            }
        }
    }


    inner class RetrieveVideoUrlCallback(private val userid: String) : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("RetrieveVideoUrlCallback onFailure $e")
        }

        override fun onResponse(call: Call, response: Response) {
            when (response.code) {
                200 -> {
                    try {
                        println("RetrieveVideoUrlCallback")
                        val json = response.body!!.string()
                        for (i in JsonParser.parseString(json).asJsonArray) {
                            println(gson.fromJson(i, TweetV101::class.java))
                        }

                        val jsonArray = JsonParser.parseString(json).asJsonArray
                        if (!jsonArray.isEmpty) {
                            if (jsonArray.size() > 1 || gson.fromJson(jsonArray.first(), TweetV101::class.java).id != videoTask.untilId) {
                                val urls = mutableListOf<DownloadMedia>()
                                for (i in jsonArray) {
                                    val tweetV101 = gson.fromJson(i, TweetV101::class.java)
                                    if (tweetV101.id == videoTask.untilId) continue
                                    tweetV101.extended_entities?.media?.get(0)?.video_info?.let {
                                        var bitrate = 0
                                        var url = ""
                                        for (j in it.variants) {
                                            if (j.bitrate != null && j.bitrate > bitrate) {
                                                bitrate = j.bitrate
                                                url = j.url
                                            }
                                        }
                                        println(url)
                                        urls.add(DownloadMedia(0, url, 0))
                                    }
                                }
                                videoTask.untilId = gson.fromJson(jsonArray.last(), TweetV101::class.java).id
                                videoTask.urls = videoTask.urls.toMutableList().apply { addAll(urls) }

                                val request = twitterRequestBuilder.run {
                                    url(getTimelinesV101(userid) + "&max_id=${videoTask.untilId}")
                                    build()
                                }
                                client.newCall(request).enqueue(RetrieveVideoUrlCallback(userid))
                            } else {
                                videoTask.nameFormat = videoTask.urls.size
                                callback.sendTwitterCount(videoTask.urls.size)
                                callback.startForeground()
                                client.newCall(twitterRequestBuilder.url(videoTask.urls[0].url).build()).enqueue(TwitterDownloadVideoCallback(0))

                            }
                        } else {
                            println("jsonArray.isEmpty")
                        }
                    } catch (e: Exception) {
                        println("RetrieveVideoUrlCallback Exception  $e")
                    } finally {
                        response.close()
                    }
                }

                else -> {
                    println("RetrieveVideoUrlCallback code ${response.code} ${response.message}")
                }
            }
        }
    }

    inner class TwitterDownloadCallback(private val index: Int) : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("TwitterDownloadCallback $index  onFailure  $e")
        }

        override fun onResponse(call: Call, response: Response) {
            when (response.code) {
                200 -> {
                    try {
                        val fileName = imageTask.time + "_${String.format("%0${imageTask.nameFormat}d", index)}.jpg"
                        val relativeLocation =
                            Environment.DIRECTORY_PICTURES + File.separator + "Twitter"// + File.separator + allTask.user
                        val bitMap: Bitmap? = BitmapFactory.decodeStream(response.body!!.byteStream())
                        if (bitMap == null) {
                            //client.newCall(call.request()).enqueue(SaveImgInAllCallback(index))
                            println("TwitterDownloadCallback  bitMap == null")
                            return
                        }
                        saveImgOnQ(
                            context,
                            null,
                            relativeLocation,
                            fileName,
                            bitMap,
                            Bitmap.CompressFormat.JPEG,
                            dateTaken = 0
                        )

                        if (imageTask.completedOne()) {
                            println("download completed")
                            callback.stopForeground()
                        }
                        callback.sendTwitterProgress(imageTask.completed)
                    } catch (e: Exception) {
                        println("TwitterDownloadCallback Exception  $e")
                    } finally {
                        response.close()
                    }
                }

                else -> {
                    println("TwitterDownloadCallback $index  code ${response.code} ${response.message}")
                }
            }
        }

    }

    inner class TwitterDownloadVideoCallback(private val index: Int) : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("TwitterDownloadVideoCallback $index  onFailure  $e")
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        override fun onResponse(call: Call, response: Response) {
            when (response.code) {
                200 -> {
                    try {
                        val fileName = videoTask.time + "_${String.format("%0${videoTask.nameFormat}d", index)}.mp4"
                        val relativeLocation =
                            Environment.DIRECTORY_PICTURES + File.separator + "Twitter" + File.separator + "Video"
                        println("content length  ${response.body!!.contentLength()}")
                        saveVideoOnQ(
                            context,
                            response.body!!.byteStream(),
                            null,
                            relativeLocation,
                            fileName,
                            response.body!!.contentType().toString(),
                            response.body!!.contentLength(),
                            0
                        )

                        if (videoTask.completedOne()) {
                            println("download completed")
                            callback.stopForeground()
                        } else {
                            client.newCall(twitterRequestBuilder.url(videoTask.urls[index + 1].url).build()).enqueue(TwitterDownloadVideoCallback(index + 1))
                        }
                        callback.sendTwitterProgress(videoTask.completed)
                    } catch (e: Exception) {
                        println("TwitterDownloadVideoCallback Exception  $e")
                    } finally {
                        response.close()
                    }
                }

                else -> {
                    println("TwitterDownloadVideoCallback $index  code ${response.code} ${response.message}")
                }
            }
        }

    }

    fun weiboDownload(url: String) {
        if (!weiboTask.isCompleted) {
            return
        }
        weiboTask = Task()
        weiboTask.isCompleted = false
        retrieveWeiboUid(url)
    }

    private fun retrieveWeiboUid(url: String) {
        val getUid = builderWithoutCookie.run {
            url(url)
            build()
        }

        client.newCall(getUid).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("retrieveWeiboUid onFailure $e")
            }

            override fun onResponse(call: Call, response: Response) {
                println("retrieveWeiboUid onResponse ${response.code}")
                val code = response.body!!.string()
                println(code)
                val matcher = Pattern.compile(regex_getWeiboUid).matcher(code)
                var uid = ""
                if (matcher.find()) {
                    uid = matcher.group(1) ?: ""
                    println("uid: $uid")
                }

                val matcher1 = Pattern.compile(regex_getWeiboUsername).matcher(code)
                if (matcher1.find()) {
                    weiboTask.user = matcher1.group(1) ?: "0"
                    println("username: ${weiboTask.user}")
                }

                retrieveWeiboAlbumId(uid)

                response.close()
            }
        })
    }

    private fun retrieveWeiboAlbumId(uid: String) {
        val getAlbumId = builderWithCookie.run {
            url(getWeiboAlbumId(uid))
            build()
        }

        client.newCall(getAlbumId).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("retrieveWeiboAlbumId onFailure $e")
            }

            override fun onResponse(call: Call, response: Response) {
                println("retrieveWeiboAlbumId onResponse ${response.code}")

                val code = response.body!!.string()
                println(code)
                val matcher = Pattern.compile(regex_getWeiboAlbumId).matcher(code)
                var albumid = ""
                if (matcher.find()) {
                    albumid = matcher.group(1) ?: ""
                    println("albumId: $albumid")
                }
                retrieveWeiboPicUrls(uid, albumid, 0)

                response.close()
            }
        })
    }

    private fun retrieveWeiboPicUrls(uid: String, albumid: String, page: Int) {
        val getPicUrls = builderWithCookie.run {
            url(getPicUrls(uid, albumid, page))
            build()
        }
        client.newCall(getPicUrls).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("retrieveWeiboPicUrls onFailure $e")
            }

            override fun onResponse(call: Call, response: Response) {
                println("retrieveWeiboPicUrls onResponse ${response.code}")

                val getPicUrlsObject = gson.fromJson(response.body!!.string(), GetPicUrlsObject::class.java)
                if (getPicUrlsObject.data != null && getPicUrlsObject.data.photo_list.isNotEmpty()) {
                    println("total: ${getPicUrlsObject.data.total}")
//                    for (i in getPicUrlsObject.data.photo_list) {
//                        println(i.getPicUrl())
//                    }
                    println(getPicUrlsObject.data.photo_list.size)
                   // weiboTask.urls = getPicUrlsObject.data.photo_list.map { it.getPicUrl() }.toMutableList().apply { addAll(weiboTask.urls) }
                    retrieveWeiboPicUrls(uid, albumid, page + 1)
                } else if (weiboTask.urls.isNotEmpty()) {
                    println("getPicUrlsObject.data  ${getPicUrlsObject.data}")
                    println("result: ${getPicUrlsObject.result}")
                    println("count : ${weiboTask.urls.size}")
                    weiboTask.nameFormat = weiboTask.urls.size
                    callback.sendTwitterCount(weiboTask.urls.size)
                    callback.startForeground()
                    for (i in weiboTask.urls.indices) {
                     //   client.newCall(builderWithoutCookie.url(weiboTask.urls[i]).build()).enqueue(WeiboDownloadCallback(i))
                    }
                }


                response.close()
            }

        })
    }

    inner class WeiboDownloadCallback(private val index: Int) : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("WeiboDownloadCallback $index  onFailure  $e")
        }

        override fun onResponse(call: Call, response: Response) {
            when (response.code) {
                200 -> {
                    try {
//                        for (i in response.headers) {
//                            println("${i.first}: ${i.second}")
//                        }
                        // content-type: image/jpeg
                        // content-length: 6246252
                        // cache-control: max-age=864000
                        // expires: Fri, 08 Oct 2021 00:38:07 GMT
                        val fileName = weiboTask.time + "_${String.format("%0${weiboTask.nameFormat}d", index)}.jpg"
                        val relativeLocation =
                            Environment.DIRECTORY_PICTURES + File.separator + "Weibo" + File.separator + weiboTask.user
                        val bitMap: Bitmap? = BitmapFactory.decodeStream(response.body!!.byteStream())
                        if (bitMap == null) {
                            //client.newCall(call.request()).enqueue(SaveImgInAllCallback(index))
                            println("WeiboDownloadCallback  bitMap == null")
                            return
                        }
                        // byteCount: Returns the minimum number of bytes that can be used to store this bitmap's pixels.
                        // AllocationByteCount: Returns the size of the allocated memory used to store this bitmap's pixels.
                        if (bitMap.allocationByteCount.toString() != response.header("Content-length", "-100")) {
                            println("bitmap.allocationByteCount: ${bitMap.allocationByteCount}  Content-length: ${response.header("Content-length", "-1")}")
                        }
                        saveImgOnQ(
                            context,
                            null,
                            relativeLocation,
                            fileName,
                            bitMap,
                            Bitmap.CompressFormat.JPEG,
                            dateTaken = 0
                        )

                        if (weiboTask.completedOne()) {
                            println("download completed")
                            callback.stopForeground()
                        }
                        callback.sendTwitterProgress(weiboTask.completed)
                    } catch (e: Exception) {
                        println("WeiboDownloadCallback Exception  $e")
                    } finally {
                        response.close()
                    }
                }

                else -> {
                    println("WeiboDownloadCallback $index  code ${response.code} ${response.message}")
                }
            }
        }

    }

}