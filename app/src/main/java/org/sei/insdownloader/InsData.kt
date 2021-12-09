package org.sei.insdownloader

data class InsData(val entry_data: EntryData)

data class EntryData(val ProfilePage: List<ProfilePages>)

data class ProfilePages(val graphql: Graphql)

data class Graphql(val user: User)

data class User(val id: String, val edge_owner_to_timeline_media: TimeLineMedia)

data class TimeLineMedia(val count: Int, val page_info: PageInfo, val edges: List<Edges>)

data class PageInfo(val has_next_page: Boolean, val end_cursor: String?)

data class Edges(val node: Node)

data class Node(val display_url: String, val shortcode: String?, val edge_sidecar_to_children: EdgeChildren?)

data class EdgeChildren(val edges: List<Edges>)



data class JsonData(val data: Data)

data class Data(val user: User1)

data class User1(val edge_owner_to_timeline_media: TimeLineMedia)

data class PostPage(val graphql: Graphql1)

data class Graphql1(val shortcode_media: Node)