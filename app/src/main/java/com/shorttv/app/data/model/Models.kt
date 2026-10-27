package com.shorttv.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ShortTV 数据模型。
 *
 * 字段命名对齐服务端 JSON（snake_case），与 myDV 的 Aweme / FeedResponse 等
 * 结构保持一致，方便直接对接同类型后端；未配置服务端时使用内置演示数据。
 */

@Serializable
data class Cover(
    @SerialName("url_list") val urlList: List<String> = emptyList()
) {
    val best: String?
        get() = urlList.maxByOrNull { it.length }
}

@Serializable
data class Avatar(
    @SerialName("url_list") val urlList: List<String> = emptyList()
) {
    val best: String?
        get() = urlList.maxByOrNull { it.length }
}

@Serializable
data class PlayAddr(
    val uri: String = "",
    @SerialName("url_list") val urlList: List<String> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("data_size") val dataSize: Long = 0L
) {
    val best: String?
        get() = urlList.firstOrNull()?.takeIf { it.isNotBlank() }
}

@Serializable
data class BitRate(
    @SerialName("gear_name") val gearName: String = "",
    @SerialName("quality_type") val qualityType: Int = 0,
    @SerialName("bit_rate_value") val bitRateValue: Int = 0,
    val format: String = "",
    @SerialName("is_h265") val isH265: Int = 0,
    @SerialName("is_bytevc1") val isBytevc1: Int = 0,
    @SerialName("play_addr") val playAddr: PlayAddr? = null,
    val fps: Int = 0
)

@Serializable
data class Video(
    val duration: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val ratio: String = "",
    val cover: Cover = Cover(),
    @SerialName("dynamic_cover") val dynamicCover: Cover = Cover(),
    @SerialName("origin_cover") val originCover: Cover = Cover(),
    @SerialName("play_addr") val playAddr: PlayAddr? = null,
    @SerialName("play_addr_265") val playAddr265: PlayAddr? = null,
    @SerialName("download_addr") val downloadAddr: PlayAddr? = null,
    @SerialName("bit_rate") val bitRate: List<BitRate> = emptyList(),
    @SerialName("big_thumbs") val bigThumbs: List<String> = emptyList()
) {
    /**
     * 选取可播放的地址（对齐 myDV VideoPlayUrlResolver）。
     *
     * 实测结论（2026-10）：douyinvod.com 直链无 Referer/Cookie 时 403，
     * 必须走 `www.douyin.com/aweme/v1/play/` 中转（返回 video/mp4）。
     * 优先级：
     *   1. play_addr.url_list 中已有的 /aweme/v1/play/ 链接（带 biz_sign，原画）
     *   2. 用 play_addr.uri 构造 play/ 链接（video_id+line+is_play_url）
     *   3. 265 / bit_rate 中的 play/ 链接
     *   4. 兜底：play_addr 直链（服务器/演示模式下可用）
     */
    fun resolvePlayUrl(): String? {
        playAddr?.let { pa ->
            pa.urlList.firstOrNull { it.contains("/aweme/v1/play/") }?.let { return it }
            pa.uri.takeIf { it.isNotBlank() }?.let { return playUrlFor(it) }
        }
        playAddr265?.let { pa ->
            pa.urlList.firstOrNull { it.contains("/aweme/v1/play/") }?.let { return it }
            pa.uri.takeIf { it.isNotBlank() }?.let { return playUrlFor(it) }
        }
        bitRate.forEach { br ->
            val pa = br.playAddr ?: return@forEach
            pa.urlList.firstOrNull { it.contains("/aweme/v1/play/") }?.let { return it }
        }
        return playAddr?.best ?: playAddr265?.best
            ?: bitRate.firstOrNull { it.playAddr?.best?.isNotBlank() == true }?.playAddr?.best
    }

    private fun playUrlFor(videoId: String): String = douyinPlayUrl(videoId)

    val coverUrl: String?
        get() = originCover.best ?: cover.best ?: dynamicCover.best
}

/**
 * 抖音 play/ 中转地址：douyinvod 直链无 Referer 时 403，必须走该中转。
 * 播放器直连解析（Models）与兜底解析（DouyinDirectApi）共用。
 */
fun douyinPlayUrl(videoId: String): String =
    "https://www.douyin.com/aweme/v1/play/?video_id=$videoId&line=0&is_play_url=1&aid=6383"

@Serializable
data class Author(
    val uid: String = "",
    @SerialName("sec_uid") val secUid: String = "",
    val nickname: String = "",
    val signature: String = "",
    @SerialName("avatar_thumb") val avatarThumb: Avatar = Avatar(),
    @SerialName("avatar_medium") val avatarMedium: Avatar = Avatar(),
    @SerialName("avatar_larger") val avatarLarger: Avatar = Avatar(),
    @SerialName("follower_count") val followerCount: Long = 0L,
    @SerialName("following_count") val followingCount: Long = 0L,
    @SerialName("aweme_count") val awemeCount: Long = 0L,
    @SerialName("total_favorited") val totalFavorited: Long = 0L,
    @SerialName("favoriting_count") val favoritingCount: Long = 0L
) {
    val avatarUrl: String?
        get() = avatarLarger.best ?: avatarMedium.best ?: avatarThumb.best
}

@Serializable
data class Statistics(
    @SerialName("digg_count") val diggCount: Long = 0L,
    @SerialName("comment_count") val commentCount: Long = 0L,
    @SerialName("collect_count") val collectCount: Long = 0L,
    @SerialName("share_count") val shareCount: Long = 0L,
    @SerialName("play_count") val playCount: Long = 0L
)

@Serializable
data class MixStatis(
    @SerialName("current_episode") val currentEpisode: Int = 0,
    @SerialName("updated_to_episode") val updatedToEpisode: Int = 0
)

@Serializable
data class MixInfo(
    @SerialName("mix_id") val mixId: String = "",
    @SerialName("mix_name") val mixName: String = "",
    val desc: String = "",
    @SerialName("cover_url") val coverUrl: Cover = Cover(),
    val statis: MixStatis = MixStatis()
)

@Serializable
data class Aweme(
    @SerialName("aweme_id") val awemeId: String = "",
    @SerialName("aweme_type") val awemeType: Int = 0,
    val desc: String = "",
    val video: Video = Video(),
    val author: Author = Author(),
    val statistics: Statistics = Statistics(),
    @SerialName("create_time") val createTime: Long = 0L,
    @SerialName("collect_stat") val collectStat: Long = 0L,
    @SerialName("user_digged") val userDigged: Long = 0L,
    @SerialName("mix_info") val mixInfo: MixInfo? = null
)

/** 直播房间 */
@Serializable
data class LiveRoom(
    @SerialName("room_id") val roomId: String = "",
    val title: String = "",
    @SerialName("user_count") val userCount: Long = 0L,
    @SerialName("total_user_count") val totalUserCount: Long = 0L,
    val status: Int = 0,
    @SerialName("stream_url") val streamUrl: String = "",
    @SerialName("flv_url") val flvUrl: String = "",
    @SerialName("hevc_url") val hevcUrl: String = "",
    @SerialName("owner_nickname") val ownerNickname: String = "",
    @SerialName("owner_avatar") val ownerAvatar: String = "",
    @SerialName("room_cover") val roomCover: String = "",
    val liveType: Int = 0,
    val qualities: List<LiveQuality> = emptyList()
) {
    val isLiving: Boolean get() = status == 2
    val coverUrl: String get() = roomCover.ifBlank { ownerAvatar }
    fun resolveUrl(): String = streamUrl.ifBlank { flvUrl.ifBlank { hevcUrl } }
}

@Serializable
data class LiveQuality(
    val name: String = "",
    val url: String = "",
    val level: Int = 0
)

/** 点播弹幕 / 直播聊天消息 */
@Serializable
data class DanmakuItem(
    val id: String = "",
    val content: String = "",
    @SerialName("user_nickname") val userNickname: String = "",
    val color: String = "#FFFFFF",
    val self: Boolean = false,
    /** 点播弹幕相对视频起点的毫秒偏移；直播消息保持为 0。 */
    @SerialName("offset_time") val offsetTimeMs: Long = 0L,
    @SerialName("digg_count") val diggCount: Long = 0L
)

/** 评论 */
@Serializable
data class Comment(
    @SerialName("comment_id") val commentId: String = "",
    val content: String = "",
    @SerialName("user_nickname") val userNickname: String = "",
    @SerialName("user_avatar") val userAvatar: String = "",
    @SerialName("create_time") val createTime: Long = 0L,
    @SerialName("digg_count") val diggCount: Long = 0L,
    @SerialName("reply_comment_total") val replyCount: Long = 0L,
    @SerialName("ip_label") val ipLabel: String = ""
)

// ---------- 接口响应包装 ----------

@Serializable
data class ApiResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null
)

@Serializable
data class FeedPage(
    val items: List<Aweme> = emptyList(),
    val cursor: Long = 0L,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class LivePage(
    val items: List<LiveRoom> = emptyList(),
    val cursor: Long = 0L,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class SearchPage(
    val items: List<Aweme> = emptyList(),
    val cursor: Long = 0L,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class SuggestItem(
    val content: String = "",
    val hint: String = ""
)

@Serializable
data class HotWord(
    val word: String = "",
    @SerialName("hot_value") val hotValue: Long = 0L,
    val label: Int = 0
)

@Serializable
data class UserPage(
    val user: Author? = null,
    val items: List<Aweme> = emptyList(),
    val cursor: Long = 0L,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class MixPage(
    @SerialName("mix_id") val mixId: String = "",
    @SerialName("mix_name") val mixName: String = "",
    val desc: String = "",
    val cover: String = "",
    @SerialName("current_episode") val currentEpisode: Int = 0,
    @SerialName("total_episode") val totalEpisode: Int = 0,
    val items: List<Aweme> = emptyList(),
    val cursor: Long = 0L,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class CommentPage(
    val items: List<Comment> = emptyList(),
    val cursor: Long = 0L,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class DanmakuPage(
    val items: List<DanmakuItem> = emptyList()
)

/** 收藏项（本地维护，服务端可选同步） */
@Serializable
data class CollectItem(
    @SerialName("aweme_id") val awemeId: String = "",
    val title: String = "",
    val cover: String = "",
    @SerialName("author_name") val authorName: String = "",
    val duration: Int = 0,
    @SerialName("collect_time") val collectTime: Long = 0L
)
