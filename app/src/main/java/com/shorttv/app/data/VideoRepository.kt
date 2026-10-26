package com.shorttv.app.data

import android.util.Log
import com.shorttv.app.data.demo.DemoData
import com.shorttv.app.data.model.Author
import com.shorttv.app.data.model.Aweme
import com.shorttv.app.data.model.Comment
import com.shorttv.app.data.model.DanmakuItem
import com.shorttv.app.data.model.HotWord
import com.shorttv.app.data.model.LiveRoom
import com.shorttv.app.data.model.MixPage
import com.shorttv.app.data.model.SuggestItem
import com.shorttv.app.data.model.UserPage
import com.shorttv.app.data.remote.ApiClient
import com.shorttv.app.direct.DouyinDirectApi

/** 作品分页结果（nextCursor 由数据源回传：直连为服务端游标，其余为累加值） */
data class AwemePage(
    val items: List<Aweme>,
    val hasMore: Boolean,
    val nextCursor: Long
)

/**
 * 视频仓库：统一编排三种数据源。
 *
 * - DIRECT：直连抖音官方 API（QuickJS 端上 a_bogus 签名），零配置；
 *   请求失败或返回空时回退内置演示数据，保证 UI 任何网络环境下可用。
 * - SERVER：自建 / 兼容服务端（ShortTV 同构 API），地址为空时回退演示数据。
 * - DEMO：仅内置演示数据。
 */
class VideoRepository {

    companion object {
        private const val TAG = "VideoRepository"
    }

    /** 当前数据源（由 ViewModel 随设置变更注入） */
    @Volatile
    var dataSource: DataSource = DataSource.DIRECT

    /** 直连客户端（DIRECT 模式下使用） */
    @Volatile
    var directApi: DouyinDirectApi? = null

    @Volatile
    private var client: ApiClient? = null

    private var clientUrl: String = ""

    private fun apiClientFor(baseUrl: String): ApiClient? {
        if (baseUrl.isBlank()) return null
        if (client == null || clientUrl != baseUrl) {
            client = ApiClient(baseUrl)
            clientUrl = baseUrl
        }
        return client
    }

    /** 一次性配置数据源（设置变更时调用） */
    fun configure(source: DataSource, api: DouyinDirectApi?) {
        dataSource = source
        directApi = api
    }

    // ---------- 演示数据 ----------

    private fun demoFeed(cursor: Long): AwemePage {
        val all = DemoData.feed
        val start = cursor.toInt()
        if (start >= all.size) return AwemePage(emptyList(), false, cursor)
        val end = minOf(start + 5, all.size)
        return AwemePage(all.subList(start, end), end < all.size, end.toLong())
    }

    private fun demoSearch(keyword: String, cursor: Long): AwemePage {
        val matched = DemoData.feed.filter {
            it.desc.contains(keyword, ignoreCase = true) ||
                it.author.nickname.contains(keyword, ignoreCase = true) ||
                it.awemeId.contains(keyword, ignoreCase = true)
        }
        if (cursor >= matched.size) return AwemePage(emptyList(), false, cursor)
        return AwemePage(matched, false, matched.size.toLong())
    }

    // ---------- 推荐流 ----------

    suspend fun feed(baseUrl: String, cursor: Long): AwemePage {
        if (dataSource == DataSource.DIRECT) {
            val api = directApi
            if (api != null) {
                runCatching {
                    val page = api.feed(cursor)
                    if (page.items.isNotEmpty()) return page.let { AwemePage(it.items, it.hasMore, it.nextCursor) }
                }.onFailure { Log.w(TAG, "direct feed failed: ${it.message}") }
            }
            Log.w(TAG, "direct feed unavailable, fallback to demo")
            return demoFeed(cursor)
        }
        if (dataSource == DataSource.DEMO) return demoFeed(cursor)
        val api = apiClientFor(baseUrl)
        if (api != null) {
            runCatching {
                val page = api.feed(cursor)
                if (page.items.isNotEmpty()) {
                    return AwemePage(page.items, page.hasMore, cursor + page.items.size)
                }
            }
        }
        return demoFeed(cursor)
    }

    // ---------- 直播 ----------

    suspend fun liveRooms(baseUrl: String, cursor: Long): Pair<List<LiveRoom>, Boolean> {
        if (dataSource == DataSource.DIRECT) {
            val api = directApi
            if (api != null) {
                runCatching {
                    val rooms = api.liveFeed(cursor)
                        // 只保留直播中的房间（feed 项自带 stream_url，未开播的无法播放）
                        .filter { it.isLiving && it.resolveUrl().isNotBlank() }
                    if (rooms.isNotEmpty()) return rooms to (rooms.size >= 20)
                }.onFailure { Log.w(TAG, "direct liveFeed failed: ${it.message}") }
            }
            return DemoData.liveRooms.filter { it.isLiving } to false
        }
        if (dataSource == DataSource.DEMO) {
            if (cursor >= DemoData.liveRooms.size) return emptyList<LiveRoom>() to false
            return DemoData.liveRooms to false
        }
        val api = apiClientFor(baseUrl)
        if (api != null) {
            runCatching {
                val page = api.liveRooms(cursor)
                if (page.items.isNotEmpty()) return page.items to page.hasMore
            }
        }
        if (cursor >= DemoData.liveRooms.size) return emptyList<LiveRoom>() to false
        return DemoData.liveRooms to false
    }

    suspend fun liveEnter(baseUrl: String, roomId: String): LiveRoom? {
        if (dataSource == DataSource.DIRECT) {
            val api = directApi
            if (api != null) {
                runCatching { api.liveEnter(roomId)?.let { return it } }
                    .onFailure { Log.w(TAG, "direct liveEnter failed: ${it.message}") }
            }
            // 直连模式下 enter 失败（内容限制/风控）返回 null，由调用方
            // 回退已加载的 liveRooms 列表（feed 项自带 stream_url）；
            // 不回退演示数据，避免「真实列表点进演示直播间」。
            return null
        }
        if (dataSource == DataSource.DEMO) return DemoData.liveRooms.firstOrNull { it.roomId == roomId }
        val api = apiClientFor(baseUrl)
        if (api != null) {
            runCatching { val room = api.liveEnter(roomId); if (room.roomId.isNotBlank()) return room }
        }
        return DemoData.liveRooms.firstOrNull { it.roomId == roomId }
    }

    // ---------- 搜索 ----------

    suspend fun hotSearch(baseUrl: String): List<HotWord> {
        if (dataSource == DataSource.DIRECT) {
            val api = directApi
            if (api != null) {
                runCatching { api.hotSearch() }.getOrNull()?.let { if (it.isNotEmpty()) return it }
            }
            return DemoData.hotWords.map { HotWord(word = it.first, hotValue = it.second.toLong()) }
        }
        if (dataSource == DataSource.DEMO) {
            return DemoData.hotWords.map { HotWord(word = it.first, hotValue = it.second.toLong()) }
        }
        val api = apiClientFor(baseUrl)
        if (api != null) {
            runCatching {
                val words = api.hotSearch()
                if (words.isNotEmpty()) return words
            }
        }
        return DemoData.hotWords.map { HotWord(word = it.first, hotValue = it.second.toLong()) }
    }

    suspend fun suggest(baseUrl: String, keyword: String): List<SuggestItem> {
        if (dataSource == DataSource.DIRECT) {
            val api = directApi
            if (api != null) {
                runCatching { api.suggest(keyword) }.getOrNull()?.let { if (it.isNotEmpty()) return it }
            }
        } else if (dataSource == DataSource.SERVER) {
            val api = apiClientFor(baseUrl)
            if (api != null) {
                runCatching {
                    val list = api.suggest(keyword)
                    if (list.isNotEmpty()) return list
                }
            }
        }
        if (keyword.isBlank()) return emptyList()
        return DemoData.feed
            .filter { it.desc.contains(keyword, ignoreCase = true) || it.author.nickname.contains(keyword, ignoreCase = true) }
            .take(8)
            .map { SuggestItem(content = it.desc, hint = it.author.nickname) }
    }

    suspend fun searchPage(baseUrl: String, keyword: String, cursor: Long): AwemePage {
        if (dataSource == DataSource.DIRECT) {
            val api = directApi
            if (api != null) {
                runCatching {
                    val page = api.search(keyword, cursor.toInt())
                    if (page.items.isNotEmpty()) {
                        return AwemePage(page.items, page.hasMore, page.nextCursor)
                    }
                }.onFailure { Log.w(TAG, "direct search failed: ${it.message}") }
            }
            return demoSearch(keyword, cursor)
        }
        if (dataSource == DataSource.DEMO) return demoSearch(keyword, cursor)
        val api = apiClientFor(baseUrl)
        if (api != null) {
            runCatching {
                val page = api.search(keyword, cursor)
                if (page.items.isNotEmpty()) {
                    return AwemePage(page.items, page.hasMore, cursor + page.items.size)
                }
            }
        }
        return demoSearch(keyword, cursor)
    }

    // ---------- 用户 ----------

    suspend fun user(baseUrl: String, secUid: String): Pair<Author?, List<Aweme>> {
        if (dataSource == DataSource.DIRECT) {
            val api = directApi
            if (api != null) {
                runCatching {
                    val page = api.userWorks(secUid)
                    if (page.items.isNotEmpty()) {
                        return (page.items.first().author.takeIf { it.secUid.isNotBlank() }) to page.items
                    }
                }.onFailure { Log.w(TAG, "direct userWorks failed: ${it.message}") }
            }
        } else if (dataSource == DataSource.SERVER) {
            val api = apiClientFor(baseUrl)
            if (api != null) {
                runCatching {
                    val page = api.userWorks(secUid)
                    if (page.user != null || page.items.isNotEmpty()) return page.user to page.items
                }
            }
        }
        val items = DemoData.feed.filter { it.author.secUid == secUid }
        val author = items.firstOrNull()?.author
        return author to items
    }

    // ---------- 合集 ----------

    suspend fun mix(baseUrl: String, mixId: String, cursor: Long = 0): MixPage {
        if (dataSource == DataSource.DIRECT) {
            val api = directApi
            if (api != null) {
                runCatching {
                    val page = api.mix(mixId, cursor)
                    if (page.items.isNotEmpty()) {
                        return MixPage(
                            mixId = mixId,
                            mixName = page.items.first().mixInfo?.mixName ?: "抖音合集",
                            desc = page.items.first().desc,
                            cover = page.items.first().video.cover.urlList.firstOrNull() ?: "",
                            currentEpisode = 1,
                            totalEpisode = page.items.size,
                            items = page.items,
                            cursor = page.nextCursor,
                            hasMore = page.hasMore
                        )
                    }
                }.onFailure { Log.w(TAG, "direct mix failed: ${it.message}") }
            }
        } else if (dataSource == DataSource.SERVER) {
            val api = apiClientFor(baseUrl)
            if (api != null) {
                runCatching { val page = api.mix(mixId); if (page.items.isNotEmpty()) return page }
            }
        }
        val items = DemoData.feed.filter { it.mixInfo?.mixId == mixId }
            .ifEmpty { DemoData.feed.take(4) }
        return MixPage(
            mixId = mixId,
            mixName = items.firstOrNull()?.mixInfo?.mixName ?: "演示合集",
            desc = "内置演示合集，展示合集播放列表效果。",
            cover = items.firstOrNull()?.video?.coverUrl ?: "",
            currentEpisode = 1,
            totalEpisode = items.size,
            items = items,
            hasMore = false
        )
    }

    // ---------- 评论 / 弹幕 ----------

    suspend fun comments(baseUrl: String, awemeId: String): List<Comment> {
        if (dataSource == DataSource.DIRECT) {
            val api = directApi
            if (api != null) {
                runCatching { api.comments(awemeId) }.getOrNull()?.let { if (it.isNotEmpty()) return it }
            }
        } else if (dataSource == DataSource.SERVER) {
            val api = apiClientFor(baseUrl)
            if (api != null) {
                runCatching {
                    val list = api.comments(awemeId).items
                    if (list.isNotEmpty()) return list
                }
            }
        }
        return DemoData.feed.indices.take(12).map { i ->
            Comment(
                commentId = "demo_comment_$i",
                content = DemoData.danmakuPool[i % DemoData.danmakuPool.size] + "！这条视频太赞了，已三连～",
                userNickname = "演示用户${('A' + i)}",
                createTime = 1_700_000_000L + i * 3_600_000L,
                diggCount = (20 - i).toLong() * 7
            )
        }
    }

    suspend fun danmaku(baseUrl: String, awemeId: String): List<DanmakuItem> {
        if (dataSource == DataSource.DIRECT) {
            val api = directApi
            if (api != null) {
                runCatching { api.danmaku(awemeId) }.getOrNull()?.let { if (it.isNotEmpty()) return it }
            }
        } else if (dataSource == DataSource.SERVER) {
            val api = apiClientFor(baseUrl)
            if (api != null) {
                runCatching {
                    val list = api.danmaku(awemeId)
                    if (list.isNotEmpty()) return list
                }
            }
        }
        return DemoData.danmakuPool.mapIndexed { i, text ->
            DanmakuItem(
                id = "demo_dm_$i",
                content = text,
                userNickname = "演示用户${i % 20}"
            )
        }
    }

    /** 直播弹幕模拟（演示模式下按时间滚动） */
    fun liveDanmakuTick(tick: Long): DanmakuItem? {
        val pool = DemoData.danmakuPool
        if (pool.isEmpty()) return null
        val idx = (tick % pool.size).toInt()
        return DanmakuItem(
            id = "live_dm_$tick",
            content = pool[idx],
            userNickname = "观众${(tick % 97) + 1}"
        )
    }

    /** 用户作品分页 */
    suspend fun userWorks(baseUrl: String, secUid: String, cursor: Long): UserPage {
        if (dataSource == DataSource.DIRECT) {
            val api = directApi
            if (api != null) {
                runCatching {
                    val page = api.userWorks(secUid, cursor)
                    if (page.items.isNotEmpty()) {
                        return UserPage(
                            user = page.items.first().author.takeIf { it.secUid.isNotBlank() },
                            items = page.items,
                            cursor = page.nextCursor,
                            hasMore = page.hasMore
                        )
                    }
                }.onFailure { Log.w(TAG, "direct userWorks page failed: ${it.message}") }
            }
        } else if (dataSource == DataSource.SERVER) {
            val api = apiClientFor(baseUrl)
            if (api != null) {
                runCatching {
                    val page = api.userWorks(secUid, cursor)
                    if (page.items.isNotEmpty()) return page
                }
            }
        }
        val items = DemoData.feed.filter { it.author.secUid == secUid }
        return UserPage(items = items, hasMore = false)
    }
}
