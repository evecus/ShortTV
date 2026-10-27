package com.shorttv.app.direct

import android.util.Log
import com.shorttv.app.data.model.Author
import com.shorttv.app.data.model.Aweme
import com.shorttv.app.data.model.Comment
import com.shorttv.app.data.model.CommentPage
import com.shorttv.app.data.model.DanmakuItem
import com.shorttv.app.data.model.HotWord
import com.shorttv.app.data.model.LiveQuality
import com.shorttv.app.data.model.LiveRoom
import com.shorttv.app.data.model.SuggestItem
import com.shorttv.app.data.model.douyinPlayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 抖音官方 API 直连客户端（移植自 myDV DouyinDirectApi）。
 *
 * 域名与端点（均写死，无需用户配置）：
 * - Web 端点：https://www.douyin.com/aweme/v1/web/ 下各端点
 * - 移动端点：https://aweme.snssdk.com/aweme/v2/feed/
 * - 直播：https://live.douyin.com/webcast/ 下各端点
 *
 * 风控：a_bogus 签名由 AbogusSigner（QuickJS）计算；
 * 身份：ttwid/msToken/webid/uifid 由 DouyinIdentity 管理。
 */
class DouyinDirectApi(
    private val identity: DouyinIdentity,
    private val signer: AbogusSigner,
) {
    companion object {
        private const val TAG = "DouyinDirectApi"

        const val WEB_BASE = "https://www.douyin.com"
        const val APP_BASE = "https://aweme.snssdk.com"
        const val LIVE_BASE = "https://live.douyin.com"

        /** iesdouyin 旧接口 UA（Chrome 桌面 UA 实测可用）。 */
        private const val LEGACY_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"

        /** 移动端 App feed UA。 */
        private const val APP_UA =
            "com.ss.android.ugc.aweme/170400 (Linux; U; Android 13; zh_CN; Pixel 7; Build/TQ3A;ttid;) 202304"

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

        // ----------  tolerant DTO ----------

        @Serializable
        data class UrlList(@SerialName("url_list") val urlList: List<String> = emptyList())

        @Serializable
        data class DyPlayAddr(
            val uri: String = "",
            @SerialName("url_list") val urlList: List<String> = emptyList(),
            val width: Int = 0,
            val height: Int = 0
        )

        @Serializable
        data class DyBitRate(
            @SerialName("gear_name") val gearName: String = "",
            @SerialName("quality_type") val qualityType: Int = 0,
            @SerialName("bit_rate_value") val bitRateValue: Int = 0,
            val format: String = "",
            @SerialName("is_h265") val isH265: Int = 0,
            @SerialName("is_bytevc1") val isBytevc1: Int = 0,
            @SerialName("play_addr") val playAddr: DyPlayAddr? = null,
            val fps: Int = 0
        )

        @Serializable
        data class DyVideo(
            val duration: Double = 0.0,
            val width: Int = 0,
            val height: Int = 0,
            val ratio: String = "",
            val cover: UrlList = UrlList(),
            @SerialName("dynamic_cover") val dynamicCover: UrlList = UrlList(),
            @SerialName("origin_cover") val originCover: UrlList = UrlList(),
            @SerialName("play_addr") val playAddr: DyPlayAddr? = null,
            @SerialName("play_addr_265") val playAddr265: DyPlayAddr? = null,
            @SerialName("download_addr") val downloadAddr: DyPlayAddr? = null,
            @SerialName("bit_rate") val bitRate: List<DyBitRate> = emptyList(),
            // 实测（2026-10）：big_thumbs 为对象数组 [{duration,fext,uri,url_list}]，
            // 不是字符串数组，早期按 List<String> 解析会导致整页 JSON 失败。
            @SerialName("big_thumbs") val bigThumbs: List<DyBigThumb> = emptyList()
        )

        /** 进度条预览图条目。 */
        @Serializable
        data class DyBigThumb(
            val uri: String = "",
            @SerialName("url_list") val urlList: List<String> = emptyList(),
            val duration: Double = 0.0,
            val fext: String = ""
        )

        @Serializable
        data class DyAuthor(
            val uid: String = "",
            @SerialName("sec_uid") val secUid: String = "",
            val nickname: String = "",
            val signature: String = "",
            @SerialName("avatar_thumb") val avatarThumb: UrlList = UrlList(),
            @SerialName("avatar_medium") val avatarMedium: UrlList = UrlList(),
            @SerialName("avatar_larger") val avatarLarger: UrlList = UrlList(),
            @SerialName("follower_count") val followerCount: Long = 0L,
            @SerialName("following_count") val followingCount: Long = 0L,
            @SerialName("aweme_count") val awemeCount: Long = 0L,
            @SerialName("total_favorited") val totalFavorited: Long = 0L,
            @SerialName("favoriting_count") val favoritingCount: Long = 0L
        )

        @Serializable
        data class DyStatistics(
            @SerialName("digg_count") val diggCount: Long = 0L,
            @SerialName("comment_count") val commentCount: Long = 0L,
            @SerialName("collect_count") val collectCount: Long = 0L,
            @SerialName("share_count") val shareCount: Long = 0L,
            @SerialName("play_count") val playCount: Long = 0L
        )

        @Serializable
        data class DyMixInfo(
            @SerialName("mix_id") val mixId: String = "",
            @SerialName("mix_name") val mixName: String = "",
            val desc: String = ""
        )

        @Serializable
        data class DyAweme(
            @SerialName("aweme_id") val awemeId: String = "",
            @SerialName("aweme_type") val awemeType: Int = 0,
            val desc: String = "",
            val video: DyVideo? = null,
            val author: DyAuthor? = null,
            val statistics: DyStatistics? = null,
            @SerialName("create_time") val createTime: Long = 0L,
            @SerialName("collect_stat") val collectStat: Long = 0L,
            @SerialName("user_digged") val userDigged: Long = 0L,
            @SerialName("mix_info") val mixInfo: DyMixInfo? = null
        )

        @Serializable
        data class FeedResp(
            @SerialName("status_code") val statusCode: Int = 0,
            @SerialName("aweme_list") val awemeList: List<JsonElement> = emptyList(),
            val data: List<JsonElement> = emptyList(),
            @SerialName("has_more") val hasMore: Int = 0,
            val cursor: Long = 0L,
            @SerialName("max_cursor") val maxCursor: Long = 0L
        )

        @Serializable
        data class DetailResp(
            @SerialName("status_code") val statusCode: Int = 0,
            @SerialName("aweme_detail") val awemeDetail: JsonElement? = null
        )

        @Serializable
        data class SuggestResp(
            @SerialName("status_code") val statusCode: Int = 0,
            @SerialName("sug_list") val sugList: List<DySuggest> = emptyList()
        )

        @Serializable
        data class DySuggest(
            val word: String = "",
            val content: String = ""
        )

        @Serializable
        data class HotSearchResp(
            @SerialName("status_code") val statusCode: Int = 0,
            val data: HotSearchData? = null
        )

        @Serializable
        data class HotSearchData(
            @SerialName("word_list") val wordList: List<DyHotWord> = emptyList()
        )

        @Serializable
        data class DyHotWord(
            val word: String = "",
            @SerialName("hot_value") val hotValue: Long = 0L,
            val label: Int = 0,
            val position: Int = 0
        )

        @Serializable
        data class UserProfileResp(
            @SerialName("status_code") val statusCode: Int = 0,
            val user: DyAuthor? = null
        )

        @Serializable
        data class CommentResp(
            @SerialName("status_code") val statusCode: Int = 0,
            val comments: List<JsonElement> = emptyList(),
            @SerialName("has_more") val hasMore: Int = 0,
            val cursor: Long = 0L
        )

        @Serializable
        data class DyComment(
            val cid: String = "",
            val text: String = "",
            @SerialName("create_time") val createTime: JsonElement? = null,
            @SerialName("digg_count") val diggCount: JsonElement? = null,
            @SerialName("reply_comment_total") val replyCommentTotal: JsonElement? = null,
            @SerialName("ip_label") val ipLabel: String = "",
            val user: DyCommentUser? = null
        )

        @Serializable
        data class DyCommentUser(
            val nickname: String = "",
            @SerialName("avatar_thumb") val avatarThumb: UrlList = UrlList(),
            @SerialName("avatar_medium") val avatarMedium: UrlList = UrlList()
        )

        @Serializable
        data class DanmakuResp(
            @SerialName("status_code") val statusCode: Int = 0,
            @SerialName("danmaku_list") val danmakuList: List<JsonElement> = emptyList(),
            val data: DanmakuData? = null
        )

        @Serializable
        data class DanmakuData(
            @SerialName("danmaku_list") val danmakuList: List<JsonElement> = emptyList()
        )

        @Serializable
        data class DyDanmaku(
            @SerialName("danmaku_id") val danmakuId: String = "",
            val content: String = "",
            val text: String = "",
            @SerialName("user_nickname") val userNickname: String = "",
            @SerialName("offset_time") val offsetTime: JsonElement? = null,
            @SerialName("digg_count") val diggCount: JsonElement? = null,
            val color: JsonElement? = null
        )

        // ---------- 直播 ----------

        @Serializable
        data class LiveEnterResp(
            @SerialName("status_code") val statusCode: Int = 0,
            val data: LiveEnterData? = null
        )

        @Serializable
        data class LiveEnterData(
            val room: DyLiveRoom? = null,
            val user: DyLiveUser? = null
        )

        @Serializable
        data class DyLiveUser(
            @SerialName("sec_uid") val secUid: String = "",
            val nickname: String = "",
            @SerialName("avatar_medium") val avatarMedium: UrlList = UrlList()
        )

        @Serializable
        data class DyLiveRoom(
            @SerialName("id_str") val idStr: String = "",
            @SerialName("room_id_str") val roomIdStr: String = "",
            val title: String = "",
            val status: Int = 0,
            @SerialName("user_count_str") val userCountStr: String = "0",
            @SerialName("total_user_str") val totalUserStr: String = "0",
            @SerialName("live_type_webcast") val liveType: Int = 0,
            val owner: DyLiveOwner? = null,
            @SerialName("stream_url") val streamUrl: DyStreamUrl? = null,
            val cover: UrlList = UrlList()
        )

        @Serializable
        data class DyLiveOwner(
            val nickname: String = "",
            @SerialName("avatar_medium") val avatarMedium: UrlList = UrlList()
        )

        @Serializable
        data class DyStreamUrl(
            @SerialName("flv_pull_url") val flvPullUrl: Map<String, String> = emptyMap(),
            @SerialName("hls_pull_url_map") val hlsPullUrlMap: Map<String, String> = emptyMap(),
            @SerialName("flv_pull_url_map") val flvPullUrlMap: Map<String, String> = emptyMap(),
            @SerialName("default_resolution") val defaultResolution: String = ""
        )

        @Serializable
        data class LiveFeedResp(
            @SerialName("status_code") val statusCode: Int = 0,
            val data: List<LiveFeedItem> = emptyList()
        )

        /** webcast/feed 列表项：真实结构为 { type, rid, data: room }。 */
        @Serializable
        data class LiveFeedItem(
            val type: Int = 0,
            val rid: String = "",
            val data: DyLiveRoom? = null
        )

        /** 分页结果（携带服务端游标，直连模式翻页必需） */
        data class DirectPage(
            val items: List<Aweme>,
            val hasMore: Boolean,
            val nextCursor: Long
        )
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 手机端优先使用移动端 App 推荐流（竖屏内容为主），由 ViewModel 按设备形态注入。 */
    @Volatile
    var preferMobileFeed: Boolean = false

    /** 注入用户自己的登录 Cookie（设置页配置，可选）。 */
    fun setUserCookie(value: String) {
        identity.userCookie = value
    }

    // ---------- 公共参数 ----------

    /** Web 端基础环境参数（对齐抖音 Web 请求）。 */
    private fun webBaseParams(): LinkedHashMap<String, String> = linkedMapOf(
        "device_platform" to "webapp",
        "aid" to "6383",
        "channel" to "channel_pc_web",
        "pc_client_type" to "1",
        "version_code" to "190400",
        "version_name" to "19.4.0",
        "cookie_enabled" to "true",
        "screen_width" to "2560",
        "screen_height" to "1440",
        "browser_language" to "zh-CN",
        "browser_platform" to "Win32",
        "browser_name" to "Chrome",
        "browser_version" to "147.0.0.0",
        "browser_online" to "true",
        "engine_name" to "Blink",
        "engine_version" to "147.0.0.0",
        "os_name" to "Windows",
        "os_version" to "10",
        "cpu_core_num" to "24",
        "device_memory" to "8",
        "platform" to "PC",
        "downlink" to "10",
        "effective_type" to "4g",
        "round_trip_time" to "50",
        "webid" to identity.webid,
        "verifyFp" to identity.verifyFp,
        // s_v_web_id 为 verify_ 前缀格式（Web 端指纹），与 verifyFp 同源
        "s_v_web_id" to "verify_${identity.verifyFp.take(16)}",
        "fp" to identity.verifyFp,
        "uifid" to identity.uifid,
        "UIFID" to identity.uifid.uppercase()
    )

    private fun buildQuery(params: Map<String, String>): String =
        params.entries.joinToString("&") { "${it.key}=${urlEncode(it.value)}" }

    private fun urlEncode(v: String): String =
        java.net.URLEncoder.encode(v, "UTF-8")

    /**
     * 逐条容错解析 aweme 列表。
     *
     * 抖音字段类型不时会变（如 big_thumbs 由字符串数组改为对象数组），整体
     * decodeFromString 会让一个坏字段拖垮整页响应。这里改为逐条解析，
     * 单条异常只跳过该条视频，其余正常返回。
     */
    private fun parseAwemes(elements: List<JsonElement>): List<Aweme> =
        elements.mapNotNull { el ->
            runCatching { json.decodeFromJsonElement(DyAweme.serializer(), el).toAweme() }
                .getOrElse { e ->
                    Log.w(TAG, "aweme decode partially failed, use minimal: ${e.message?.take(120)}")
                    return@getOrElse minimalAweme(el)
                }
        }

    /**
     * 最小兜底解析：DTO 跟不上抖音字段变化时（如新增/改类型），
     * 仍取出 aweme_id / desc / play_addr.uri，保证有视频可播放。
     */
    private fun minimalAweme(el: JsonElement): Aweme? {
        return runCatching {
            val obj = el.jsonObject
            val id = obj["aweme_id"]?.jsonPrimitive?.contentOrNull ?: return null
            val desc = obj["desc"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val videoObj = obj["video"]?.jsonObject ?: return null
            val playUri = videoObj["play_addr"]?.jsonObject
                ?.get("uri")?.jsonPrimitive?.contentOrNull ?: return null
            if (playUri.isBlank()) return null
            Aweme(
                awemeId = id,
                desc = desc,
                video = com.shorttv.app.data.model.Video(
                    playAddr = com.shorttv.app.data.model.PlayAddr(
                        uri = playUri,
                        urlList = listOf(douyinPlayUrl(playUri))
                    )
                )
            )
        }.onFailure { Log.w(TAG, "minimal aweme failed: ${it.message}") }.getOrNull()
    }

    private fun JsonElement?.longValue(): Long = runCatching {
        this?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0L
    }.getOrDefault(0L)

    private fun JsonElement?.colorHex(): String {
        val raw = runCatching { this?.jsonPrimitive?.contentOrNull.orEmpty() }.getOrDefault("")
        if (raw.startsWith("#") && raw.length in 4..9) return raw.uppercase()
        val value = raw.toLongOrNull()?.toInt() ?: 0xFFFFFF
        return String.format("#%06X", 0xFFFFFF and value)
    }

    /**
     * 执行一次带 a_bogus 签名的 Web 请求。
     * 流程：构造 query → getABogus 签名 → 拼 a_bogus → 带 Cookie/UA/Referer 请求。
     *
     * @param base 域名（默认 www.douyin.com；直播接口需传 live.douyin.com）
     */
    private suspend fun signedWebGet(
        path: String,
        params: Map<String, String>,
        base: String = WEB_BASE,
        referer: String = "$WEB_BASE/",
    ): String? {
        val all = LinkedHashMap(params).apply {
            put("msToken", identity.msToken ?: identity.ensureMsToken())
        }
        val query = buildQuery(all)
        val aBogus = signer.signABogus(query, identity.userAgent)
        val finalQuery = if (aBogus != null) "$query&a_bogus=$aBogus" else query
        val url = "$base$path?$finalQuery"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", identity.userAgent)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", referer)
            .header("Origin", WEB_BASE)
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Cookie", identity.cookieHeader())
            .get()
            .build()
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "$path HTTP ${resp.code}: ${body.take(200)}")
                        return@use null
                    }
                    body
                }
            } catch (e: Exception) {
                Log.w(TAG, "$path failed: ${e.message}")
                null
            }
        }
    }

    // ---------- 模型映射 ----------

    private fun DyAuthor.toAuthor(): Author = Author(
        uid = uid,
        secUid = secUid,
        nickname = nickname,
        signature = signature,
        avatarThumb = com.shorttv.app.data.model.Avatar(avatarThumb.urlList),
        avatarMedium = com.shorttv.app.data.model.Avatar(avatarMedium.urlList),
        avatarLarger = com.shorttv.app.data.model.Avatar(avatarLarger.urlList),
        followerCount = followerCount,
        followingCount = followingCount,
        awemeCount = awemeCount,
        totalFavorited = totalFavorited,
        favoritingCount = favoritingCount
    )

    private fun DyAweme.toAweme(): Aweme {
        val v = this.video
        return Aweme(
            awemeId = awemeId,
            awemeType = awemeType,
            desc = desc,
            video = com.shorttv.app.data.model.Video(
                duration = (v?.duration ?: 0.0).toInt(),
                width = v?.width ?: 0,
                height = v?.height ?: 0,
                ratio = v?.ratio ?: "",
                cover = com.shorttv.app.data.model.Cover(v?.cover?.urlList ?: emptyList()),
                dynamicCover = com.shorttv.app.data.model.Cover(v?.dynamicCover?.urlList ?: emptyList()),
                originCover = com.shorttv.app.data.model.Cover(v?.originCover?.urlList ?: emptyList()),
                playAddr = v?.playAddr?.let {
                    com.shorttv.app.data.model.PlayAddr(it.uri, it.urlList, it.width, it.height)
                },
                playAddr265 = v?.playAddr265?.let {
                    com.shorttv.app.data.model.PlayAddr(it.uri, it.urlList, it.width, it.height)
                },
                downloadAddr = v?.downloadAddr?.let {
                    com.shorttv.app.data.model.PlayAddr(it.uri, it.urlList, it.width, it.height)
                },
                bitRate = v?.bitRate?.map { br ->
                    com.shorttv.app.data.model.BitRate(
                        gearName = br.gearName,
                        qualityType = br.qualityType,
                        bitRateValue = br.bitRateValue,
                        format = br.format,
                        isH265 = br.isH265,
                        isBytevc1 = br.isBytevc1,
                        playAddr = br.playAddr?.let {
                            com.shorttv.app.data.model.PlayAddr(it.uri, it.urlList, it.width, it.height)
                        },
                        fps = br.fps
                    )
                } ?: emptyList(),
                bigThumbs = v?.bigThumbs
                    ?.mapNotNull { t -> t.urlList.firstOrNull()?.takeIf { it.isNotBlank() } ?: t.uri.takeIf { it.isNotBlank() } }
                    ?: emptyList()
            ),
            author = author?.toAuthor() ?: Author(),
            statistics = com.shorttv.app.data.model.Statistics(
                diggCount = statistics?.diggCount ?: 0L,
                commentCount = statistics?.commentCount ?: 0L,
                collectCount = statistics?.collectCount ?: 0L,
                shareCount = statistics?.shareCount ?: 0L,
                playCount = statistics?.playCount ?: 0L
            ),
            createTime = createTime,
            collectStat = collectStat,
            userDigged = userDigged,
            mixInfo = mixInfo?.let {
                com.shorttv.app.data.model.MixInfo(
                    mixId = it.mixId,
                    mixName = it.mixName,
                    desc = it.desc
                )
            }
        )
    }

    // ---------- API ----------

    /**
     * 推荐流（Web tab/feed）。
     *
     * 实测（2026-10）：tab/feed 为「刷新」语义——响应无游标字段，每页仅回
     * 2~3 条（新访客冷启动），has_more=1。因此这里自动补页：循环请求并
     * 按 aweme_id 去重，直到凑满 [minItems] 条或达到 [maxRounds] 轮，
     * 让首屏有足够内容滚动。
     *
     * 手机端（preferMobileFeed=true）优先走移动端 App feed（竖屏内容为主，
     * 与 myDV「移动推荐」同源），失败时回退 Web tab/feed。
     */
    suspend fun feed(cursor: Long = 0, count: Int = 10, minItems: Int = 6, maxRounds: Int = 4): DirectPage {
        identity.ensureAll()
        if (preferMobileFeed) {
            val app = runCatching { appFeed(count, minItems, maxRounds) }
                .onFailure { Log.w(TAG, "app feed failed: ${it.message}") }
                .getOrNull()
            if (app != null && app.items.isNotEmpty()) return app
            Log.w(TAG, "app feed unavailable, fallback to web tab/feed")
        }
        val acc = LinkedHashMap<String, Aweme>()
        var hasMore = false
        var lastCursor = cursor
        repeat(maxRounds) { round ->
            val params = webBaseParams().apply {
                put("count", count.toString())
                put("feed_flow_flag", "1")
                if (lastCursor > 0) put("cursor", lastCursor.toString())
            }
            val body = signedWebGet("/aweme/v1/web/tab/feed/", params)
                ?: return DirectPage(acc.values.toList(), hasMore, lastCursor)
            val resp = json.decodeFromString<FeedResp>(body)
            hasMore = resp.hasMore == 1 || hasMore
            val items = (resp.awemeList.ifEmpty { resp.data })
            if (items.isEmpty()) return DirectPage(acc.values.toList(), hasMore, lastCursor)
            parseAwemes(items).forEach { aweme ->
                if (aweme.awemeId !in acc) acc[aweme.awemeId] = aweme
            }
            val next = if (resp.maxCursor > 0) resp.maxCursor else if (resp.cursor > 0) resp.cursor else 0
            if (next > 0) lastCursor = next
            if (acc.size >= minItems) return DirectPage(acc.values.toList(), hasMore, lastCursor)
        }
        return DirectPage(acc.values.toList(), hasMore, lastCursor)
    }

    /**
     * 移动端 App 推荐流（/aweme/v1/feed/，无需 Web 签名）。
     * 同为「刷新」语义：多轮请求 + aweme_id 去重。
     */
    private suspend fun appFeed(count: Int, minItems: Int, maxRounds: Int): DirectPage {
        val acc = LinkedHashMap<String, Aweme>()
        var hasMore = false
        repeat(maxRounds) {
            val params = linkedMapOf(
                "count" to count.toString(),
                "pull_type" to "0",
                "aid" to "1128",
                "app_name" to "aweme",
                "version_code" to "170400",
                "device_platform" to "android",
                "device_type" to "Pixel 7",
                "device_brand" to "Google",
                "os_api" to "33",
                "os_version" to "13",
                "device_id" to identity.appDeviceId,
                "iid" to identity.appIid,
                "openudid" to identity.appOpenudid,
                "channel" to "googleplay",
                "resolution" to "1080*2400",
                "dpi" to "420",
                "language" to "zh",
                "accept-language" to "zh-CN",
                "ac" to "wifi",
                "network_type" to "wifi",
                "os" to "android",
                "ts" to (System.currentTimeMillis() / 1000).toString(),
                "_rticket" to System.currentTimeMillis().toString()
            )
            val url = "$APP_BASE/aweme/v1/feed/?${buildQuery(params)}"
            val body = withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(
                        Request.Builder()
                            .url(url)
                            .header("User-Agent", APP_UA)
                            .header("Accept", "application/json")
                            .get()
                            .build()
                    ).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            Log.w(TAG, "appFeed HTTP ${resp.code}")
                            null
                        } else {
                            resp.body?.string().orEmpty()
                        }
                    }
                }.onFailure { Log.w(TAG, "appFeed request failed: ${it.message}") }
                    .getOrNull().orEmpty()
            }
            if (body.isBlank()) return DirectPage(acc.values.toList(), hasMore, 0L)
            val resp = runCatching { json.decodeFromString<FeedResp>(body) }
                .onFailure { Log.w(TAG, "appFeed decode failed: ${it.message}") }
                .getOrNull() ?: return DirectPage(acc.values.toList(), hasMore, 0L)
            hasMore = resp.hasMore == 1 || hasMore
            val items = parseAwemes(resp.awemeList.ifEmpty { resp.data })
            if (items.isEmpty()) return DirectPage(acc.values.toList(), hasMore, 0L)
            items.forEach { aweme ->
                if (aweme.awemeId.isNotBlank() && aweme.awemeId !in acc) acc[aweme.awemeId] = aweme
            }
            if (acc.size >= minItems) return DirectPage(acc.values.toList(), hasMore, 0L)
        }
        return DirectPage(acc.values.toList(), hasMore, 0L)
    }

    /** 视频详情。 */
    suspend fun detail(awemeId: String): Aweme? {
        identity.ensureAll()
        val params = webBaseParams().apply {
            put("aweme_id", awemeId)
            put("version_code", "190400")
            put("channel", "channel_pc_web")
        }
        val body = signedWebGet("/aweme/v1/web/aweme/detail/", params) ?: return null
        val resp = json.decodeFromString<DetailResp>(body)
        val el = resp.awemeDetail ?: return null
        return runCatching { json.decodeFromJsonElement(DyAweme.serializer(), el).toAweme() }
            .getOrElse { minimalAweme(el) }
    }

    /** 相关推荐。 */
    suspend fun related(awemeId: String, count: Int = 10): List<Aweme> {
        identity.ensureAll()
        val params = webBaseParams().apply {
            put("aweme_id", awemeId)
            put("count", count.toString())
        }
        val body = signedWebGet("/aweme/v1/web/aweme/related/", params) ?: return emptyList()
        val resp = json.decodeFromString<FeedResp>(body)
        return parseAwemes(resp.awemeList.ifEmpty { resp.data })
    }

    /** 用户公开资料。独立请求资料接口，避免从第一条作品反推作者。 */
    suspend fun userProfile(secUid: String): Author? {
        if (secUid.isBlank()) return null
        identity.ensureAll()
        val params = webBaseParams().apply {
            put("sec_user_id", secUid)
            put("publish_video_strategy_type", "2")
            put("personal_center_strategy", "1")
            put("source", "channel_pc_web")
        }
        val body = signedWebGet(
            path = "/aweme/v1/web/user/profile/other/",
            params = params,
            referer = "$WEB_BASE/user/$secUid"
        ) ?: return null
        val resp = json.decodeFromString<UserProfileResp>(body)
        return resp.user?.toAuthor()
    }

    /** 用户公开作品。 */
    suspend fun userWorks(secUid: String, cursor: Long = 0, count: Int = 20): DirectPage {
        if (secUid.isBlank()) return DirectPage(emptyList(), false, cursor)
        identity.ensureAll()
        val params = webBaseParams().apply {
            put("sec_user_id", secUid)
            put("count", count.toString())
            put("max_cursor", cursor.toString())
            put("cut_version", "1")
            put("locate_query", "false")
            put("publish_video_strategy_type", "2")
            put("from_user_page", "1")
            put("need_time_list", "0")
            put("show_live_replay_strategy", "1")
            put("version_code", "290100")
            put("version_name", "29.1.0")
        }
        val body = signedWebGet(
            path = "/aweme/v1/web/aweme/post/",
            params = params,
            referer = "$WEB_BASE/user/$secUid"
        ) ?: return DirectPage(emptyList(), false, cursor)
        val resp = json.decodeFromString<FeedResp>(body)
        val items = parseAwemes(resp.awemeList.ifEmpty { resp.data })
        val next = if (resp.maxCursor > 0) resp.maxCursor else resp.cursor
        return DirectPage(items, resp.hasMore == 1, next)
    }

    /** 搜索。 */
    suspend fun search(keyword: String, offset: Int = 0, count: Int = 20): DirectPage {
        identity.ensureAll()
        val params = webBaseParams().apply {
            put("keyword", keyword)
            put("search_channel", "aweme_general")
            put("search_source", "normal_search")
            put("query_correct_type", "1")
            put("is_filter_search", "0")
            put("from_group_id", "")
            put("offset", offset.toString())
            put("count", count.toString())
            put("list_type", "single")
            put("sort_type", "0")
            put("publish_time", "0")
            put("filter_duration", "0")
            put("enable_history", "1")
        }
        val body = signedWebGet("/aweme/v1/web/search/item/", params)
            ?: return DirectPage(emptyList(), false, offset.toLong())
        val resp = json.decodeFromString<FeedResp>(body)
        val items = parseAwemes(resp.awemeList.ifEmpty { resp.data })
        val hasMore = resp.hasMore == 1 || items.size >= count
        return DirectPage(items, hasMore, (offset + items.size).toLong())
    }

    /** 搜索联想。 */
    suspend fun suggest(keyword: String): List<SuggestItem> {
        identity.ensureAll()
        val params = webBaseParams().apply {
            put("keyword", keyword)
        }
        val body = signedWebGet("/aweme/v1/web/search/sug/", params) ?: return emptyList()
        val resp = json.decodeFromString<SuggestResp>(body)
        return resp.sugList.map { SuggestItem(content = it.word.ifBlank { it.content }, hint = it.content) }
    }

    /** 热搜榜。 */
    suspend fun hotSearch(): List<HotWord> {
        identity.ensureAll()
        val params = webBaseParams()
        val body = signedWebGet("/aweme/v1/web/hot/search/list/", params) ?: return emptyList()
        val resp = json.decodeFromString<HotSearchResp>(body)
        return resp.data?.wordList?.sortedBy { it.position }?.map {
            HotWord(word = it.word, hotValue = it.hotValue, label = it.label)
        } ?: emptyList()
    }

    /** 合集视频。 */
    suspend fun mix(mixId: String, cursor: Long = 0, count: Int = 20): DirectPage {
        identity.ensureAll()
        val params = webBaseParams().apply {
            put("mix_id", mixId)
            put("cursor", cursor.toString())
            put("count", count.toString())
        }
        val body = signedWebGet("/aweme/v1/web/mix/aweme/", params)
            ?: return DirectPage(emptyList(), false, cursor)
        val resp = json.decodeFromString<FeedResp>(body)
        val items = parseAwemes(resp.awemeList.ifEmpty { resp.data })
        val next = if (resp.maxCursor > 0) resp.maxCursor else resp.cursor
        return DirectPage(items, resp.hasMore == 1, next)
    }

    /** 评论列表。优先走 Web 签名接口（支持游标分页），空响应时回退 iesdouyin v2 公开接口。 */
    suspend fun comments(awemeId: String, cursor: Long = 0, count: Int = 20): CommentPage {
        if (awemeId.isBlank()) return CommentPage()
        identity.ensureAll()
        val web = commentsWeb(awemeId, cursor, count)
        if (web.items.isNotEmpty() || cursor > 0L) return web
        // iesdouyin v2 兜底源不支持游标翻页（固定返回热门首页），仅首页可回退
        val legacy = commentsLegacy(awemeId, count)
        if (legacy.items.isNotEmpty()) return legacy
        return web
    }

    private suspend fun commentsWeb(awemeId: String, cursor: Long, count: Int): CommentPage {
        val params = webBaseParams().apply {
            put("aweme_id", awemeId)
            put("cursor", cursor.toString())
            put("count", count.toString())
            put("item_type", "0")
        }
        val body = signedWebGet(
            path = "/aweme/v1/web/comment/list/",
            params = params,
            referer = "$WEB_BASE/video/$awemeId"
        ) ?: return CommentPage(cursor = cursor)
        if (body.isBlank()) return CommentPage(cursor = cursor)
        val resp = runCatching { json.decodeFromString<CommentResp>(body) }
            .onFailure { Log.w(TAG, "comment response decode failed: ${it.message}") }
            .getOrNull() ?: return CommentPage(cursor = cursor)
        return CommentPage(
            items = resp.comments.mapNotNull { element ->
                val c = runCatching {
                    json.decodeFromJsonElement(DyComment.serializer(), element)
                }.onFailure { Log.w(TAG, "comment decode skipped: ${it.message}") }
                    .getOrNull() ?: return@mapNotNull null
                Comment(
                    commentId = c.cid,
                    content = c.text,
                    userNickname = c.user?.nickname ?: "",
                    userAvatar = c.user?.avatarMedium?.urlList?.firstOrNull()
                        ?: c.user?.avatarThumb?.urlList?.firstOrNull().orEmpty(),
                    createTime = c.createTime.longValue(),
                    diggCount = c.diggCount.longValue(),
                    replyCount = c.replyCommentTotal.longValue(),
                    ipLabel = c.ipLabel
                )
            },
            cursor = resp.cursor,
            hasMore = resp.hasMore == 1
        )
    }

    /**
     * iesdouyin v2 公开评论接口（无需签名）。
     *
     * 实测（2026-10）：该端点只返回热门首页（cursor 参数无效、响应无
     * has_more/cursor），作为 Web 签名接口被风控拦截时的兜底数据源。
     * 响应结构差异：status_code 为对象、时间为驼峰 `createTime`（秒）。
     */
    private suspend fun commentsLegacy(awemeId: String, count: Int): CommentPage {
        val url = "https://www.iesdouyin.com/web/api/v2/comment/list/" +
            "?aweme_id=$awemeId&count=$count&cursor=0"
        val body = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", LEGACY_UA)
                        .header("Accept", "application/json, */*")
                        .header("Referer", "https://www.iesdouyin.com/")
                        .get()
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string().orEmpty()
                }
            }.onFailure { Log.w(TAG, "legacy comments failed: ${it.message}") }
                .getOrNull().orEmpty()
        }
        if (body.isBlank()) return CommentPage()
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val rawComments = root["comments"]?.let { el ->
                runCatching { el.jsonArray.toList() }.getOrDefault(emptyList())
            }.orEmpty()
            CommentPage(
                items = rawComments.mapNotNull { element ->
                    runCatching {
                        val obj = element.jsonObject
                        val cid = obj["cid"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                        val text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val user = obj["user"]?.jsonObject
                        val avatar = user?.get("avatar_medium")?.jsonObject?.get("url_list")
                            ?.let { runCatching { it.jsonArray }.getOrNull() }
                            ?.firstOrNull()?.jsonPrimitive?.contentOrNull
                            ?: user?.get("avatar_thumb")?.jsonObject?.get("url_list")
                                ?.let { runCatching { it.jsonArray }.getOrNull() }
                                ?.firstOrNull()?.jsonPrimitive?.contentOrNull
                        Comment(
                            commentId = cid,
                            content = text,
                            userNickname = user?.get("nickname")?.jsonPrimitive?.contentOrNull.orEmpty(),
                            userAvatar = avatar.orEmpty(),
                            createTime = obj["createTime"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                ?: obj["create_time"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                ?: 0L,
                            diggCount = obj["digg_count"]?.longValue() ?: 0L,
                            replyCount = obj["reply_comment_total"]?.longValue() ?: 0L,
                            ipLabel = obj["ip_label"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        )
                    }.getOrNull()
                },
                cursor = 0L,
                hasMore = false
            )
        }.onFailure { Log.w(TAG, "legacy comments parse failed: ${it.message}") }
            .getOrDefault(CommentPage())
    }

    /**
     * 点播滚动弹幕。抖音接口使用 item_id/group_id，而不是 aweme_id；
     * offset_time 为相对视频起点的毫秒时间轴。
     */
    suspend fun danmaku(awemeId: String, durationMs: Long = 0L): List<DanmakuItem> {
        if (awemeId.isBlank()) return emptyList()
        identity.ensureAll()
        val resolvedDuration = durationMs.takeIf { it > 0 }
            ?: detail(awemeId)?.video?.duration?.toLong()?.takeIf { it > 0 }
            ?: 0L
        val params = webBaseParams().apply {
            put("item_id", awemeId)
            put("group_id", awemeId)
            put("aweme_id", awemeId)
            put("start_time", "0")
            put("end_time", resolvedDuration.toString())
            put("duration", resolvedDuration.toString())
            put("format", "json")
            put("app_name", "aweme")
            put("version_code", "170400")
            put("version_name", "17.4.0")
            put("pc_libra_divert", "Windows")
            put("support_dash", "1")
            put("support_h265", "1")
        }
        val body = signedWebGet(
            path = "/aweme/v1/web/danmaku/get_v2/",
            params = params,
            referer = "$WEB_BASE/video/$awemeId"
        ) ?: return emptyList()
        val resp = runCatching { json.decodeFromString<DanmakuResp>(body) }
            .onFailure { Log.w(TAG, "danmaku response decode failed: ${it.message}") }
            .getOrNull() ?: return emptyList()
        val elements = resp.danmakuList.ifEmpty { resp.data?.danmakuList.orEmpty() }
        return elements.mapIndexedNotNull { i, element ->
            val d = runCatching {
                json.decodeFromJsonElement(DyDanmaku.serializer(), element)
            }.onFailure { Log.w(TAG, "danmaku decode skipped: ${it.message}") }
                .getOrNull() ?: return@mapIndexedNotNull null
            val text = d.content.ifBlank { d.text }.trim()
            if (text.isBlank()) return@mapIndexedNotNull null
            DanmakuItem(
                id = d.danmakuId.ifBlank { "${awemeId}_dm_$i" },
                content = text,
                userNickname = d.userNickname,
                color = d.color.colorHex(),
                offsetTimeMs = d.offsetTime.longValue(),
                diggCount = d.diggCount.longValue()
            )
        }.distinctBy { it.id }
    }

    // ---------- 直播 ----------

    /**
     * 进入直播间（webcast/room/web/enter）。
     * 使用 __ac_signature + X-MS-STUB（msStub=MD5(请求体)）。
     */
    suspend fun liveEnter(roomId: String): LiveRoom? {
        identity.ensureAll()
        identity.ensureTtwid()
        val params = linkedMapOf(
            "aid" to "6383",
            "app_name" to "douyin_web",
            "device_platform" to "web",
            "browser_language" to "zh-CN",
            "browser_platform" to "Win32",
            "browser_name" to "Chrome",
            "browser_version" to "147.0.0.0",
            "browser_online" to "true",
            "cookie_enabled" to "true",
            "room_id" to roomId,
            "user_unique_id" to identity.userUniqueId,
            "web_rid" to roomId,
            "webid_src" to "1",
            "url_source" to "https://live.douyin.com/$roomId"
        )
        val query = buildQuery(params)
        // a_bogus 对 query 签名
        val aBogus = signer.signABogus(query, identity.userAgent)
        val msStub = DouyinIdentity.md5Upper(query)
        val acSignature = signer.signAcSignature(msStub, identity.userAgent)

        val finalQuery = if (aBogus != null) "$query&a_bogus=$aBogus" else query
        val url = "$LIVE_BASE/webcast/room/web/enter/?$finalQuery"

        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", identity.userAgent)
            .header("Referer", "$LIVE_BASE/")
            .header("Origin", LIVE_BASE)
            .header("Cookie", identity.cookieHeader())
            .header("x-ms-token", identity.msToken ?: "")
            .header("X-Secsdk-Csrf-Token", "")
            .header("Uifid", identity.uifid)
            .header("UIFID", identity.uifid)
        if (acSignature != null) builder.header("__ac_signature", acSignature)
        builder.header("X-MS-STUB", msStub)

        return withContext(Dispatchers.IO) {
            try {
                http.newCall(builder.get().build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "liveEnter HTTP ${resp.code}: ${body.take(200)}")
                        return@use null
                    }
                    val r = json.decodeFromString<LiveEnterResp>(body)
                    r.data?.room?.toLiveRoom(roomId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "liveEnter failed: ${e.message}")
                null
            }
        }
    }

    /**
     * 直播推荐列表（webcast/feed，域名 live.douyin.com）。
     *
     * 实测（2026-10）：响应 data 为 [{type, rid, data: room}] 包装结构，
     * 需取 item.data；请求需带 a_bogus（GET）。
     */
    suspend fun liveFeed(cursor: Long = 0, count: Int = 20): List<LiveRoom> {
        identity.ensureAll()
        val params = webBaseParams().apply {
            put("update_version_code", "170400")
            put("partition", "1")
            put("req_type", "0")
            put("scene", "aweme_pc_home_hot")
            put("count", count.toString())
        }
        val body = signedWebGet("/webcast/feed/", params, base = LIVE_BASE) ?: return emptyList()
        val resp = json.decodeFromString<LiveFeedResp>(body)
        return resp.data.mapNotNull { item ->
            item.data?.toLiveRoom(item.rid.ifBlank { item.data.idStr })
        }
    }

    private fun DyLiveRoom.toLiveRoom(fallbackId: String): LiveRoom {
        val qualities = buildList {
            streamUrl?.flvPullUrl?.forEach { (name, url) ->
                add(LiveQuality(name = name, url = url, level = parseLevel(name)))
            }
            streamUrl?.hlsPullUrlMap?.forEach { (name, url) ->
                if (none { it.url == url }) add(LiveQuality(name = "$name(HLS)", url = url, level = parseLevel(name)))
            }
        }
        val best = qualities.maxByOrNull { it.level }
        return LiveRoom(
            roomId = idStr.ifBlank { roomIdStr.ifBlank { fallbackId } },
            title = title,
            userCount = userCountStr.toLongOrNull() ?: 0L,
            totalUserCount = totalUserStr.toLongOrNull() ?: 0L,
            status = status,
            streamUrl = best?.url ?: "",
            flvUrl = qualities.firstOrNull()?.url ?: "",
            hevcUrl = "",
            ownerNickname = owner?.nickname ?: "",
            ownerAvatar = owner?.avatarMedium?.urlList?.firstOrNull() ?: "",
            roomCover = cover.urlList.firstOrNull() ?: "",
            liveType = liveType,
            qualities = qualities
        )
    }

    private fun parseLevel(name: String): Int = when {
        name.contains("原画") || name.contains("origin") || name.contains("OD") -> 4
        name.contains("超清") || name.contains("uhd") || name.contains("HD") -> 3
        name.contains("高清") || name.contains("hd") || name.contains("SD") -> 2
        name.contains("标清") || name.contains("sd") || name.contains("LD") -> 1
        else -> 0
    }
}
