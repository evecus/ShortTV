package com.shorttv.app.data.remote

import com.shorttv.app.data.model.ApiResponse
import com.shorttv.app.data.model.CommentPage
import com.shorttv.app.data.model.DanmakuPage
import com.shorttv.app.data.model.FeedPage
import com.shorttv.app.data.model.HotWord
import com.shorttv.app.data.model.LivePage
import com.shorttv.app.data.model.LiveRoom
import com.shorttv.app.data.model.MixPage
import com.shorttv.app.data.model.SearchPage
import com.shorttv.app.data.model.SuggestItem
import com.shorttv.app.data.model.UserPage
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor

/**
 * ShortTV API 客户端。
 *
 * 对应后端接口（与 myDV 服务端同构）：
 * - GET /api/v1/feed?cursor=&count=              推荐流
 * - GET /api/v1/live?cursor=&count=              直播列表
 * - GET /api/v1/live/enter?room_id=              进入直播（取流地址）
 * - GET /api/v1/search?keyword=&cursor=&count=   搜索
 * - GET /api/v1/search/suggest?keyword=          搜索建议
 * - GET /api/v1/hot_search                       热搜榜
 * - GET /api/v1/user?sec_uid=                    用户信息
 * - GET /api/v1/user/works?sec_uid=&cursor=      用户作品
 * - GET /api/v1/mix?mix_id=&cursor=              合集
 * - GET /api/v1/comment?aweme_id=&cursor=        评论
 * - GET /api/v1/danmaku?aweme_id=                弹幕
 */
class ApiClient(baseUrl: String) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        )
        .build()

    private val base: String = baseUrl.trimEnd('/')

    private fun get(path: String): String {
        val request = Request.Builder()
            .url("$base$path")
            .header("User-Agent", "ShortTV/1.0 (Android TV)")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Empty body")
        }
    }

    private inline fun <reified T> fetchWrapped(path: String): T? =
        json.decodeFromString<ApiResponse<T>>(get(path)).data

    fun feed(cursor: Long = 0, count: Int = 10): FeedPage =
        fetchWrapped("api/v1/feed?cursor=$cursor&count=$count") ?: FeedPage()

    fun liveRooms(cursor: Long = 0, count: Int = 20): LivePage =
        fetchWrapped("api/v1/live?cursor=$cursor&count=$count") ?: LivePage()

    fun liveEnter(roomId: String): LiveRoom =
        fetchWrapped("api/v1/live/enter?room_id=${roomId.urlEncode()}") ?: LiveRoom()

    fun search(keyword: String, cursor: Long = 0, count: Int = 20): SearchPage =
        fetchWrapped("api/v1/search?keyword=${keyword.urlEncode()}&cursor=$cursor&count=$count")
            ?: SearchPage()

    fun suggest(keyword: String): List<SuggestItem> =
        fetchWrapped("api/v1/search/suggest?keyword=${keyword.urlEncode()}") ?: emptyList()

    fun hotSearch(): List<HotWord> =
        fetchWrapped("api/v1/hot_search") ?: emptyList()

    fun user(secUid: String): UserPage =
        fetchWrapped("api/v1/user?sec_uid=${secUid.urlEncode()}") ?: UserPage()

    fun userWorks(secUid: String, cursor: Long = 0, count: Int = 20): UserPage =
        fetchWrapped("api/v1/user/works?sec_uid=${secUid.urlEncode()}&cursor=$cursor&count=$count")
            ?: UserPage()

    fun mix(mixId: String, cursor: Long = 0, count: Int = 20): MixPage =
        fetchWrapped("api/v1/mix?mix_id=${mixId.urlEncode()}&cursor=$cursor&count=$count")
            ?: MixPage()

    fun comments(awemeId: String, cursor: Long = 0, count: Int = 20): CommentPage =
        fetchWrapped("api/v1/comment?aweme_id=${awemeId.urlEncode()}&cursor=$cursor&count=$count")
            ?: CommentPage()

    fun danmaku(awemeId: String): List<com.shorttv.app.data.model.DanmakuItem> =
        fetchWrapped<DanmakuPage>("api/v1/danmaku?aweme_id=${awemeId.urlEncode()}")?.items
            ?: emptyList()

    /** 服务端连通性探测 */
    fun ping(): Boolean = try {
        get("api/v1/feed?cursor=0&count=1")
        true
    } catch (_: Exception) {
        false
    }

    private fun String.urlEncode(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}
