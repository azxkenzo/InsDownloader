package org.sei.insdownloader

// Twitter ------------------------------------------------------------------
const val API_KEY = "aMScbEFsiI1gpamqDlwvENokQ"
const val API_KEY_SECRET = "oSUzT0IrHznUpIiWI17hHj9BBISJoGOeLarkPoLVsUlVMizPYX"
const val BEARER_TOKEN = "AAAAAAAAAAAAAAAAAAAAAJkWUAEAAAAAvC2W5ebxgKHr6YsRiIkhO5BJBLk%3DIOhPx2wbM3aGf6Ae4Vo8zixfsLbvELNndkeIFRYW2rUIsCcI4q"
const val ACCESS_TOKEN = "1441258921058979852-eN1SgrfvMnYvX4nABQo4kkTlVT1o11"
const val ACCESS_TOKEN_SECRET = "lStemIzvYwGXNzHmJf8wD2sJ62fnz6oZVPeLnggGGjrtC"


/**
 * get user id using username
 * Header : Authorization: Bearer $BEARER_TOKEN
 */
fun getTwitterUserId(username: String) = "https://api.twitter.com/2/users/by/username/$username"
data class UserIdResponse(val data: TwitterUser)
data class TwitterUser(val id: String, val name: String, val username: String)

/**
 * get recently tweets
 * Header : Authorization: Bearer $BEARER_TOKEN
 * param : max_results integer  Specifies the number of Tweets to try and retrieve, up to a maximum of 100 per distinct request.
 * param : end_time date  YYYY-MM-DDTHH:mm:ssZ   The newest or most recent UTC timestamp from which the Tweets will be provided.
 * param : exclude enum (retweets, replies)   Comma-separated list of the types of Tweets to exclude from the response.
 * param : expansions enum (attachments.media_keys )   Expansions enable you to request additional data objects that relate to the originally returned Tweets.
 * param : media.fields enum ( )   This fields parameter enables you to select which specific media fields will deliver in each returned Tweet.
 * param : pagination_token string   This parameter is used to move forwards or backwards through 'pages' of results, based on the value of the next_token or previous_token in the response.
 * param : since_id string   Returns results with a Tweet ID greater than (that is, more recent than) the specified 'since' Tweet ID.
 * param : until_id string   Returns results with a Tweet ID less less than (that is, older than) the specified 'until' Tweet ID.
 */
fun getTimelinesV2(id: String, query: String = _query) = "https://api.twitter.com/2/users/$id/tweets?$query"
private val _query = "max_results=100&expansions=attachments.media_keys&media.fields=preview_image_url,url&exclude=retweets,replies"
data class TimelinesResponse(val includes: Include?, val meta: MetaV2)
data class MetaV2(val oldest_id: String?, val newest_id: String?, val result_count: Int)
data class Include(val media: List<MediaV2>)
data class MediaV2(val type: String, val url: String?)


/**
 * Media Object
 * field: media_key (default) String
 * field: type (default) String  Type of content (animated_gif, photo, video).
 * field: height integer  Height of this content in pixels.
 * field: width integer  Width of this content in pixels.
 * field: preview_image_url string    URL to the static placeholder preview of this content.  视频的预览图
 */

// https://pbs.twimg.com/media/E_zBZLEVEA4-IjY.jpg
// https://pbs.twimg.com/media/E_90okIUYAM186L.jpg
// https://pbs.twimg.com/media/E_90okIUYAM186L?format=jpg&name=4096x4096

// {"data":{"id":"1335202232292102144","name":"仙仙桃","username":"xianxiantao"}}
// {"data":{"id":"1403414832506834944","name":"酥","username":"Baochaoxiaosu"}}
// {"data":{"id":"1408719422672412678","name":"狐狸小妖","username":"Luckycxyhlxy"}}
// {"data":{"id":"970112464741216257","name":"眼酱大魔王w","username":"SXeyes_"}}
// {"data":{"id":"1079556598501179393","name":"小妖（互推）","username":"luckyclwclwclw"}}

// api v2 目前还不支持获取video url          api v1.1 可以

/**
 * API 1.1 GET statuses/user_timeline
 * param : user_id  The ID of the user for whom to return results.
 * param : count  Specifies the number of Tweets to try and retrieve, up to a maximum of 200 per distinct request.
 * param : exclude_replies  This parameter will prevent replies from appearing in the returned timeline.
 * param : include_rts  When set to false , the timeline will strip any native retweets
 * param : since_id  Returns results with an ID greater than (that is, more recent than) the specified ID.
 * param : max_id  Returns results with an ID less than (that is, older than) or equal to the specified ID.
 */
fun getTimelinesV101(user_id: String) = "https://api.twitter.com/1.1/statuses/user_timeline.json?user_id=$user_id&count=200&exclude_replies=true&include_rts=false"

// https://video.twimg.com/ext_tw_video/1441378471561682952/pu/pl/F3BeYAbohoCKPXC5.m3u8?tag=12&container=fmp4
// https://video.twimg.com/ext_tw_video/1441378471561682952/pu/vid/640x360/WQ03cgv9HWwv1J4T.mp4?tag=12
// https://video.twimg.com/ext_tw_video/1441378471561682952/pu/vid/1280x720/9zF2BTsidbGjufX-.mp4?tag=12
// https://video.twimg.com/ext_tw_video/1441378471561682952/pu/vid/480x270/XxkxjXHQyTr9be5s.mp4?tag=12

data class TweetV101(val id: String, val extended_entities: ExtendedEntity?)
data class ExtendedEntity(val media: List<MediaV101>?)
data class MediaV101(val type: String, val video_info: VideoInfo?)
data class VideoInfo(val variants: List<VideoVariant>)
data class VideoVariant(val bitrate: Int?, val content_type: String, val url: String)






// Instagram ------------------------------------------------------------------

var queryHash = ""
var csrftoken = ""
var sessionID = ""

val DownSingle = "(\\{\"graphql\":.*?)\\);</script><script type=\"text/javascript\">"
val x = "\"username\":\"(.{2,200})\",\"blocked_by_viewer\""












// Weibo ------------------------------------------------------------------

// https://photo.weibo.com/2215245793/talbum/index?from=profile_wb
// "album_id":"3555077064049743","uid":"2215245793",
// https://photo.weibo.com/photos/get_all?uid=2215245793&album_id=3555077064049743&count=32&page=1&type=3

// https://wx2.sinaimg.cn/large/002pUWCRgy1guu0f82oqdj62c033ye8602.jpg
// pic_host	"https://wx2.sinaimg.cn"
// pic_name	"002pUWCRgy1guu0f82oqdj62c033ye8602.jpg"
// Cookie: SUB=_2A25MVPhSDeRhGeNP7FAZ-CzMyDmIHXVvIG6arDV8PUNbmtB-LWLckW9NTnhVfhd7UmZ8xIM0dSifSIF5cki9zpzi; SUBP=0033WrSXqPxfM725Ws9jqgMF55529P9D9WW.2j4xmK7IBQ4YdynLsKkQ5JpX5KzhUgL.Fo-pS0zR1hz7e0-2dJLoI7LrdcvadcvaIN-t; login_sid_t=3130c798f92e12ba74e0263265b98f0f; cross_origin_proto=SSL; _s_tentry=weibo.com; Apache=5296645402436.372.1632666909374; SINAGLOBAL=5296645402436.372.1632666909374; ULV=1632666909379:1:1:1:5296645402436.372.1632666909374:; UOR=,,www.baidu.com; ALF=1664203650; SSOLoginState=1632667367; webim_unReadCount=%7B%22time%22%3A1632668114265%2C%22dm_pub_total%22%3A0%2C%22chat_group_client%22%3A0%2C%22chat_group_notice%22%3A0%2C%22allcountNum%22%3A0%2C%22msgbox%22%3A0%7D; wvr=6; WBStorage=6ff1c79b|undefined

const val WEIBO_COOKIE = "SUB=_2A25MVi5TDeRhGeNP7FAZ-CzMyDmIHXVvIhibrDV8PUNbmtB-LXHAkW9NTnhVfm10Eq52EBD60G3doTVIcvEf6fKa; SUBP=0033WrSXqPxfM725Ws9jqgMF55529P9D9WW.2j4xmK7IBQ4YdynLsKkQ5JpX5KzhUgL.Fo-pS0zR1hz7e0-2dJLoI7LrdcvadcvaIN-t; login_sid_t=c61afb8b3b5a28bc864d38db207eef27; cross_origin_proto=SSL; WBStorage=6ff1c79b|undefined; _s_tentry=passport.weibo.com; Apache=8673293929360.139.1632787907548; SINAGLOBAL=8673293929360.139.1632787907548; ULV=1632787907551:1:1:1:8673293929360.139.1632787907548:; wb_view_log=2560*14401; ALF=1664323970; SSOLoginState=1632787971; wvr=6; wb_view_log_5172882035=2560*14401; webim_unReadCount=%7B%22time%22%3A1632787997610%2C%22dm_pub_total%22%3A0%2C%22chat_group_client%22%3A0%2C%22chat_group_notice%22%3A0%2C%22allcountNum%22%3A0%2C%22msgbox%22%3A0%7D"

const val regex_getWeiboUid = "CONFIG\\['oid']='(.+?)';"
const val regex_getWeiboUsername = "CONFIG\\['onick']='(.+?)';"

fun getWeiboAlbumId(uid: String) = "https://photo.weibo.com/$uid/talbum/index?from=profile_wb"
const val regex_getWeiboAlbumId = "\"album_id\":\"(.+?)\",\"uid\""


fun getPicUrls(uid: String, albumId: String, page: Int = 1) = "https://photo.weibo.com/photos/get_all?uid=$uid&album_id=$albumId&count=100&page=$page&type=3"
data class GetPicUrlsObject(val result: Boolean, val data: GetPicUrlsData?)
data class GetPicUrlsData(val total: Int, val photo_list: List<GetPicUrlsPhotoInfo>)
data class GetPicUrlsPhotoInfo(val pic_host: String, val pic_name: String) {
    fun  getPicUrl() = "$pic_host/large/$pic_name"
}









// Tieba ------------------------------------------------------------------




