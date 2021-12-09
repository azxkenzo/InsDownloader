package org.sei.insdownloader

import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.*
import java.io.IOException
import java.util.regex.Pattern

data class A(val a: String, val b: String)
fun main() {
    val json = "[{\"a\":\"aaa\",\"b\":\"bbb\"},{\"a\":\"111\",\"b\":\"222\"},{\"a\":\"zzz\",\"b\":\"xxx\"}]"
    val jsonArray = JsonParser.parseString(json).asJsonArray
    for (i in jsonArray) {
        val a = Gson().fromJson(i, A::class.java)
        println(a)
    }
    return

    val client = OkHttpClient.Builder().run {


        build()
    }

    val builderWithCookie = Request.Builder().apply {
        addHeader("Cookie", WEIBO_COOKIE)
    }
    val builderWithoutCookie = Request.Builder().apply {
        addHeader("Cookie", "SUB=_2AkMWDBrWf8NxqwJRmP4TzGjjbY1yzA_EieKgUOsNJRMxHRl-yT9jqlIQtRB6PYw0OQBQi5X-jd6ATqeG7NCQxsJXbsGu; SUBP=0033WrSXqPxfM72-Ws9jqgMF55529P9D9WWxn4kSvT9AY_PfcbQnyqC9")
    }

    val getUid = builderWithoutCookie.run {
        url("https://weibo.com/u/2215245793?is_all=1")
        build()
    }
    println("111")
    client.newCall(getUid).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("onFailure")
        }

        override fun onResponse(call: Call, response: Response) {
            println(response.code)
            val code = response.body!!.string()
            //println(code)
            val matcher = Pattern.compile(regex_getWeiboUid).matcher(code)
            var uid = ""
            if (matcher.find()) {
                uid = matcher.group(1) ?: ""
                println("uid: $uid")
            }

            val matche = Pattern.compile(regex_getWeiboUsername).matcher(code)
            if (matche.find()) {
                println("username : ${matche.group(1)}")
            }

            val getAlbumId = builderWithCookie.run {
                url(getWeiboAlbumId(uid))
                build()
            }
            client.newCall(getAlbumId).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {

                }

                override fun onResponse(call: Call, response: Response) {
                    println(response.code)

                    val matcher1 = Pattern.compile(regex_getWeiboAlbumId).matcher(response.body!!.string())
                    var albumid = ""
                    if (matcher1.find()) {
                        albumid = matcher1.group(1) ?: ""
                        println("albumId: $albumid")
                    }

                    val getPicUrls = builderWithCookie.run {
                        url("https://photo.weibo.com/photos/get_all?uid=$uid&album_id=$albumid&count=1&page=1&type=3")
                        build()
                    }
                    client.newCall(getPicUrls).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {

                        }

                        override fun onResponse(call: Call, response: Response) {
                            println(response.code)

                            val getPicUrlsObject = Gson().fromJson(response.body!!.string(), GetPicUrlsObject::class.java)
                            getPicUrlsObject.data?.let { data ->
                                println("total: ${data.total}")
                                for (i in data.photo_list) {
                                    client.newCall(builderWithoutCookie.url(i.getPicUrl()).build()).enqueue(SaveCallback())
                                }
                            }


                            response.close()
                        }

                    })


                    response.close()
                }

            })

            response.close()
        }

    })

    while (true) {}
}

private class SaveCallback : Callback {
    override fun onFailure(call: Call, e: IOException) {

    }

    override fun onResponse(call: Call, response: Response) {
        println("content-length: ${response.header("Content-length", "-101")}")
        println("contentLength: ${response.body!!.contentLength()}")
        val contentLength = response.body!!.contentLength()
        val inputStream = response.body!!.byteStream()
        try {
            var size = 0
            val byteArray = ByteArray(1024)
            while (true) {
                val readCount = inputStream.read(byteArray)
                println("read $readCount bytes")  // 没数据之后为 -1
                size += readCount
                println("size: $size    final: $contentLength")
                Thread.sleep(10)
        
            }
        } catch (e: Exception) {

        } finally {
            response.close()
        }
    }

}
