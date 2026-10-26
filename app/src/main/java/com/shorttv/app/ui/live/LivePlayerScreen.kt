@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.shorttv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.shorttv.app.data.model.LiveQuality
import com.shorttv.app.data.model.LiveRoom
import com.shorttv.app.ui.MainViewModel
import com.shorttv.app.ui.components.tvClickable
import com.shorttv.app.ui.player.DanmakuOverlay
import com.shorttv.app.ui.player.LiveDanmakuController
import com.shorttv.app.util.formatCount
import kotlinx.coroutines.delay

/** 抖音 Web UA（与 DouyinIdentity 保持一致，供播放器数据源使用）。 */
private const val DOUYIN_WEB_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"

/**
 * 直播播放页：HLS 直播流 + 弹幕 + 在线人数 + 清晰度切换 + 聊天列表。
 */
@Composable
fun LivePlayerScreen(
    vm: MainViewModel,
    roomId: String,
    onBack: () -> Unit,
    onPipActiveChanged: (Boolean) -> Unit,
    onEnterPip: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val rooms by vm.liveRooms.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var room by remember { mutableStateOf<LiveRoom?>(null) }
    var showChat by remember { mutableStateOf(true) }
    var showQuality by remember { mutableStateOf(false) }
    var roomLoadFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 加载房间详情：优先已加载的直播列表（feed 项自带 stream_url），
    // 否则请求 enter（部分房间受内容限制会 4001038，此时提示而非转圈）。
    LaunchedEffect(roomId) {
        room = rooms.firstOrNull { it.roomId == roomId }
            ?: runCatching { vm.liveEnter(roomId) }.getOrNull()
        roomLoadFailed = room == null
    }

    val liveRoom = room
    if (liveRoom == null) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (roomLoadFailed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (rooms.isEmpty()) "直播间暂不可用，请返回重试" else "该直播间暂时无法播放", color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text("按返回键回到直播列表", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                CircularProgressIndicator(color = Color(0xFFE5394F))
            }
        }
        return
    }

    val playUrl = liveRoom.resolveUrl()
    val player = remember(liveRoom.roomId, playUrl) {
        if (playUrl.isBlank()) null
        else {
            // 直播流（douyinvod / flv）同样校验 Referer，无 Referer 403。
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(DOUYIN_WEB_UA)
                .setDefaultRequestProperties(
                    mapOf(
                        "Referer" to "https://www.douyin.com/",
                        "Origin" to "https://www.douyin.com",
                    )
                )
                .setAllowCrossProtocolRedirects(true)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build().apply {
                    setMediaItem(MediaItem.fromUri(playUrl))
                    playWhenReady = true
                    prepare()
                }
        }
    }
    DisposableEffect(liveRoom.roomId, playUrl) {
        onDispose { player?.release() }
    }

    var isPlaying by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                onPipActiveChanged(playing)
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    // 直播弹幕（演示模式按节拍生成）
    val danmakuController = remember { LiveDanmakuController() }
    LaunchedEffect(liveRoom.roomId) {
        vm.startLiveDanmaku(danmakuController, this)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // 直播画面
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 直播弹幕
        DanmakuOverlay(
            source = danmakuController.items,
            enabled = settings.danmakuEnabled,
            fontSizeSp = settings.danmakuSize,
            alpha = settings.danmakuAlpha,
            speedFactor = settings.danmakuSpeed,
            modifier = Modifier.fillMaxSize()
        )

        // 顶部：返回 + 房间信息
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .tvClickable(onClick = onBack)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .padding(10.dp)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFF4D6A))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (liveRoom.isLiving) "LIVE" else "回放",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${formatCount(liveRoom.userCount)}人在线",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = liveRoom.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(520.dp)
                )
            }
        }

        // 右上角功能
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiveIconButton(
                icon = Icons.Filled.HighQuality,
                label = "清晰度",
                onClick = { showQuality = !showQuality }
            )
            LiveIconButton(
                icon = Icons.Filled.Cast,
                label = "小窗",
                onClick = { onEnterPip() }
            )
            LiveIconButton(
                icon = if (showChat) Icons.Filled.Close else Icons.Filled.Send,
                label = if (showChat) "收起聊天" else "展开聊天",
                onClick = { showChat = !showChat }
            )
        }

        // 清晰度面板
        if (showQuality && liveRoom.qualities.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp, top = 80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC14141A))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "清晰度",
                    color = Color(0xFFA9A9B8),
                    style = MaterialTheme.typography.labelMedium
                )
                liveRoom.qualities.forEach { q ->
                    QualityRow(q, onSelected = { url ->
                        player?.let { p ->
                            p.setMediaItem(MediaItem.fromUri(url))
                            p.prepare()
                            p.play()
                        }
                        showQuality = false
                    })
                }
            }
        }

        // 底部主播信息
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = liveRoom.ownerAvatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A34))
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = liveRoom.ownerNickname,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "累计 ${formatCount(liveRoom.totalUserCount)} 人次观看",
                    color = Color(0xCCFFFFFF),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.width(16.dp))
            Row(
                modifier = Modifier
                    .tvClickable(onClick = {})
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFE5394F))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("关注", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }

        // 右侧聊天列表
        if (showChat) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp)
                    .width(300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x99000000))
                    .padding(12.dp)
            ) {
                Text(
                    text = "直播间聊天",
                    color = Color(0xFFA9A9B8),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(6.dp))
                val recent = danmakuController.items.takeLast(6).reversed()
                recent.forEach { item ->
                    Text(
                        text = "${item.userNickname}：${item.content}",
                        color = Color(0xE6FFFFFF),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        // 缓冲指示
        if (!isPlaying && playUrl.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE5394F))
            }
        }
    }
}

@Composable
private fun LiveIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.tvClickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x66000000)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color(0xCCFFFFFF), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun QualityRow(q: LiveQuality, onSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .tvClickable(onClick = { onSelected(q.url) })
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2A2A34))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(q.name, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}
