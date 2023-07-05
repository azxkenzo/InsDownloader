package org.sei.insdownloader

data class InsData(val entry_data: EntryData)
data class EntryData(val ProfilePage: List<ProfilePages>)
data class ProfilePages(val graphql: Graphql)
data class Graphql(val user: User)
data class User(val id: String, val edge_owner_to_timeline_media: TimeLineMedia)


data class JsonData(val data: Data)
data class Data(val user: User1)
data class User1(val edge_owner_to_timeline_media: TimeLineMedia)


data class PostPage(val graphql: Graphql1)

data class Graphql1(val shortcode_media: Node)

data class PostPage2(val items: List<Item>)
data class Item(
    val taken_at: Int,
    val image_versions2: ImageVersions2?,
    val carousel_media_count: Int?,
    val carousel_media: List<CarouselMedia>?,
    val user: User2,
    val video_versions: List<VideoVersions>?,
    val media_type: Int
)

data class User2(val username: String)
data class CarouselMedia(
    val media_type: Int,
    val image_versions2: ImageVersions2,
    val video_versions: List<VideoVersions>?
)

data class ImageVersions2(val candidates: List<Candidate>)
data class VideoVersions(val type: Int, val width: Int, val height: Int, val url: String)
data class Candidate(val width: Int, val height: Int, val url: String)

data class ProfileInfo(val data: ProfileInfoData)
data class ProfileInfoData(val user: ProfileInfoDataUser)
data class ProfileInfoDataUser(val id: String, val edge_owner_to_timeline_media: TimeLineMedia)
data class TimeLineMedia(val count: Int, val page_info: PageInfo, val edges: List<Edges>)
data class PageInfo(val has_next_page: Boolean, val end_cursor: String?)
data class Edges(val node: Node)
data class Node(
    val is_video: Boolean,
    val display_url: String,
    val shortcode: String?,
    val edge_sidecar_to_children: EdgeChildren?,
    val video_url: String?,
    val taken_at_timestamp: Int
)

data class EdgeChildren(val edges: List<Edges>)

data class DownloadMedia(val taken_at: Int, val url: String, val index: Byte)