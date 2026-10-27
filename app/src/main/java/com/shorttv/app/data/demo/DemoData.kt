package com.shorttv.app.data.demo

import com.shorttv.app.data.model.Author
import com.shorttv.app.data.model.Avatar
import com.shorttv.app.data.model.Aweme
import com.shorttv.app.data.model.Cover
import com.shorttv.app.data.model.LiveQuality
import com.shorttv.app.data.model.LiveRoom
import com.shorttv.app.data.model.PlayAddr
import com.shorttv.app.data.model.Statistics
import com.shorttv.app.data.model.Video

/**
 * 内置演示数据。
 *
 * 视频源使用公开测试流（Google gtv-videos-bucket / Apple HLS / Mux 测试流），
 * 封面使用 picsum.photos。未配置服务端时 App 以演示模式运行，
 * 保证开箱即可体验完整交互。
 */
object DemoData {

    private const val MP4_BASE = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample"
    private const val HLS_BIPBOP = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
    private const val HLS_MUX = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

    data class DemoVideo(
        val id: String,
        val title: String,
        val author: String,
        val authorUid: String,
        val seconds: Int,
        val playUrl: String,
        val coverSeed: Int,
        val likes: Long,
        val comments: Long,
        val collects: Long,
        val shares: Long,
        val plays: Long
    )

    val videos = listOf(
        DemoVideo(
            id = "demo_bigbuckbunny",
            title = "大兔子 Big Buck Bunny — 4K 开源动画短片",
            author = "Blender 基金会",
            authorUid = "blender_official",
            seconds = 596,
            playUrl = "$MP4_BASE/BigBuckBunny.mp4",
            coverSeed = 101,
            likes = 128_400,
            comments = 3_210,
            collects = 8_920,
            shares = 1_204,
            plays = 2_450_000
        ),
        DemoVideo(
            id = "demo_elephantsdream",
            title = "大象之梦 Elephants Dream — 史上第一部开源电影",
            author = "Orange Open Movie",
            authorUid = "orange_project",
            seconds = 654,
            playUrl = "$MP4_BASE/ElephantsDream.mp4",
            coverSeed = 202,
            likes = 76_500,
            comments = 1_850,
            collects = 4_320,
            shares = 680,
            plays = 1_120_000
        ),
        DemoVideo(
            id = "demo_forbiggerblazes",
            title = "For Bigger Blazes — Chromecast 官方测试短片",
            author = "Google Chromecast",
            authorUid = "chromecast",
            seconds = 15,
            playUrl = "$MP4_BASE/ForBiggerBlazes.mp4",
            coverSeed = 303,
            likes = 45_200,
            comments = 987,
            collects = 1_230,
            shares = 210,
            plays = 890_000
        ),
        DemoVideo(
            id = "demo_forbiggerescapes",
            title = "For Bigger Escapes — 旅行风景测试片",
            author = "Google Chromecast",
            authorUid = "chromecast",
            seconds = 15,
            playUrl = "$MP4_BASE/ForBiggerEscapes.mp4",
            coverSeed = 404,
            likes = 33_100,
            comments = 654,
            collects = 890,
            shares = 120,
            plays = 560_000
        ),
        DemoVideo(
            id = "demo_forbiggerfun",
            title = "For Bigger Fun — 派对合集测试片",
            author = "Google Chromecast",
            authorUid = "chromecast",
            seconds = 60,
            playUrl = "$MP4_BASE/ForBiggerFun.mp4",
            coverSeed = 505,
            likes = 21_900,
            comments = 432,
            collects = 560,
            shares = 98,
            plays = 430_000
        ),
        DemoVideo(
            id = "demo_forbiggerjoyrides",
            title = "For Bigger Joyrides — 自驾游测试片",
            author = "Google Chromecast",
            authorUid = "chromecast",
            seconds = 15,
            playUrl = "$MP4_BASE/ForBiggerJoyrides.mp4",
            coverSeed = 606,
            likes = 18_700,
            comments = 321,
            collects = 430,
            shares = 76,
            plays = 320_000
        ),
        DemoVideo(
            id = "demo_sintel",
            title = "Sintel — 龙与少女的奇幻之旅",
            author = "Blender 基金会",
            authorUid = "blender_official",
            seconds = 888,
            playUrl = "$MP4_BASE/Sintel.mp4",
            coverSeed = 707,
            likes = 156_300,
            comments = 4_560,
            collects = 12_300,
            shares = 1_890,
            plays = 3_120_000
        ),
        DemoVideo(
            id = "demo_tearsofsteel",
            title = "Tears of Steel — 科幻真人合成短片",
            author = "Blender 基金会",
            authorUid = "blender_official",
            seconds = 734,
            playUrl = "$MP4_BASE/TearsOfSteel.mp4",
            coverSeed = 808,
            likes = 98_400,
            comments = 2_340,
            collects = 6_780,
            shares = 920,
            plays = 1_890_000
        ),
        DemoVideo(
            id = "demo_hls_bipbop",
            title = "Apple HLS 自适应码率演示流 (BipBop)",
            author = "Apple 开发者",
            authorUid = "apple_dev",
            seconds = 300,
            playUrl = HLS_BIPBOP,
            coverSeed = 909,
            likes = 12_300,
            comments = 210,
            collects = 320,
            shares = 45,
            plays = 210_000
        ),
        DemoVideo(
            id = "demo_hls_mux",
            title = "Mux 测试流 — HLS 多码率切换演示",
            author = "Mux Video",
            authorUid = "mux_video",
            seconds = 232,
            playUrl = HLS_MUX,
            coverSeed = 111,
            likes = 9_800,
            comments = 156,
            collects = 210,
            shares = 34,
            plays = 180_000
        )
    )

    val feed: List<Aweme> = videos.map { it.toAweme() }

    val liveRooms: List<LiveRoom> = listOf(
        LiveRoom(
            roomId = "live_001",
            title = "【直播】4K 开源动画马拉松 · Big Buck Bunny 连播",
            userCount = 12_340,
            totalUserCount = 128_000,
            status = 2,
            streamUrl = "$MP4_BASE/BigBuckBunny.mp4",
            flvUrl = HLS_BIPBOP,
            ownerNickname = "Blender 直播间",
            ownerAvatar = "https://picsum.photos/seed/avatar_blender/200/200",
            roomCover = "https://picsum.photos/seed/live_cover_1/640/360",
            qualities = listOf(
                LiveQuality("原画", "$MP4_BASE/BigBuckBunny.mp4", 0),
                LiveQuality("高清", HLS_BIPBOP, 1),
                LiveQuality("标清", HLS_MUX, 2)
            )
        ),
        LiveRoom(
            roomId = "live_002",
            title = "HLS 技术分享：自适应码流原理实战",
            userCount = 3_210,
            totalUserCount = 45_600,
            status = 2,
            streamUrl = HLS_BIPBOP,
            flvUrl = HLS_MUX,
            ownerNickname = "音视频实验室",
            ownerAvatar = "https://picsum.photos/seed/avatar_lab/200/200",
            roomCover = "https://picsum.photos/seed/live_cover_2/640/360",
            qualities = listOf(
                LiveQuality("原画", HLS_BIPBOP, 0),
                LiveQuality("高清", HLS_MUX, 1)
            )
        ),
        LiveRoom(
            roomId = "live_003",
            title = "ExoPlayer 源码解读：从 DataSource 到 Surface",
            userCount = 8_900,
            totalUserCount = 96_000,
            status = 2,
            streamUrl = HLS_MUX,
            flvUrl = "$MP4_BASE/Sintel.mp4",
            ownerNickname = "Android 开发之路",
            ownerAvatar = "https://picsum.photos/seed/avatar_android/200/200",
            roomCover = "https://picsum.photos/seed/live_cover_3/640/360",
            qualities = listOf(
                LiveQuality("原画", HLS_MUX, 0),
                LiveQuality("高清", "$MP4_BASE/Sintel.mp4", 1)
            )
        ),
        LiveRoom(
            roomId = "live_004",
            title = "周末影院：Sintel 放映室（弹幕护体）",
            userCount = 21_500,
            totalUserCount = 320_000,
            status = 2,
            streamUrl = "$MP4_BASE/Sintel.mp4",
            flvUrl = "$MP4_BASE/ElephantsDream.mp4",
            ownerNickname = "周末放映厅",
            ownerAvatar = "https://picsum.photos/seed/avatar_cinema/200/200",
            roomCover = "https://picsum.photos/seed/live_cover_4/640/360",
            qualities = listOf(
                LiveQuality("原画", "$MP4_BASE/Sintel.mp4", 0),
                LiveQuality("高清", "$MP4_BASE/ElephantsDream.mp4", 1)
            )
        ),
        LiveRoom(
            roomId = "live_005",
            title = "Tears of Steel — VFX 幕后解密",
            userCount = 5_600,
            totalUserCount = 61_000,
            status = 4,
            streamUrl = "$MP4_BASE/TearsOfSteel.mp4",
            flvUrl = "",
            ownerNickname = "VFX 视界",
            ownerAvatar = "https://picsum.photos/seed/avatar_vfx/200/200",
            roomCover = "https://picsum.photos/seed/live_cover_5/640/360",
            qualities = emptyList()
        ),
        LiveRoom(
            roomId = "live_006",
            title = "Chromecast 测试片轮播：For Bigger 系列",
            userCount = 1_230,
            totalUserCount = 18_900,
            status = 2,
            streamUrl = "$MP4_BASE/ForBiggerBlazes.mp4",
            flvUrl = "$MP4_BASE/ForBiggerEscapes.mp4",
            ownerNickname = "测试片收藏家",
            ownerAvatar = "https://picsum.photos/seed/avatar_tester/200/200",
            roomCover = "https://picsum.photos/seed/live_cover_6/640/360",
            qualities = listOf(
                LiveQuality("原画", "$MP4_BASE/ForBiggerBlazes.mp4", 0),
                LiveQuality("高清", "$MP4_BASE/ForBiggerEscapes.mp4", 1)
            )
        )
    )

    val hotWords = listOf(
        "开源动画" to 1_284_000,
        "Big Buck Bunny" to 986_000,
        "HLS 协议" to 765_000,
        "ExoPlayer" to 654_000,
        "Blender" to 543_000,
        "Sintel" to 432_000,
        "Mux 测试流" to 321_000,
        "弹幕护体" to 298_000
    )

    val danmakuPool = listOf(
        "前排围观", "这画质绝了", "666", "开源万岁", "爷青回", "弹幕护体",
        "55555", "这个转场丝滑", "Blender yyds", "4K 无压力", "沙发", "名场面",
        "弹幕比正片好看", "已三连", "泪目", "高能预警", "进度条撑住", "这才是开源"
    )

    private fun DemoVideo.toAweme(): Aweme {
        val coverUrl = "https://picsum.photos/seed/$coverSeed/640/360"
        val avatarUrl = "https://picsum.photos/seed/avatar_$authorUid/200/200"
        return Aweme(
            awemeId = id,
            awemeType = 1,
            desc = title,
            video = Video(
                duration = seconds * 1000,
                width = 1280,
                height = 720,
                ratio = "720p",
                cover = Cover(listOf(coverUrl)),
                dynamicCover = Cover(listOf(coverUrl)),
                originCover = Cover(listOf(coverUrl)),
                playAddr = PlayAddr(uri = id, urlList = listOf(playUrl), width = 1280, height = 720)
            ),
            author = Author(
                uid = authorUid,
                secUid = authorUid,
                nickname = author,
                signature = "演示账号 · $authorUid",
                avatarThumb = Avatar(listOf(avatarUrl)),
                avatarMedium = Avatar(listOf(avatarUrl)),
                avatarLarger = Avatar(listOf(avatarUrl)),
                followerCount = 100_000 + coverSeed * 137L,
                followingCount = 100 + coverSeed.toLong(),
                awemeCount = 20L + coverSeed,
                totalFavorited = 500_000 + coverSeed * 991L,
                favoritingCount = 2_000L
            ),
            statistics = Statistics(
                diggCount = likes,
                commentCount = comments,
                collectCount = collects,
                shareCount = shares,
                playCount = plays
            ),
            createTime = 1_700_000_000L + coverSeed * 8_640_000L,
            collectStat = collects,
            userDigged = 0L
        )
    }
}
