@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.shorttv.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.shorttv.app.data.AppSettings
import com.shorttv.app.data.HistoryRepository
import com.shorttv.app.data.model.Aweme
import com.shorttv.app.data.model.DanmakuItem
import com.shorttv.app.ui.DeviceMode
import com.shorttv.app.ui.LocalDeviceMode
import com.shorttv.app.ui.components.tvClickable
import com.shorttv.app.util.formatCount
import kotlinx.coroutines.delay

/** 抖音 Web UA（与 DouyinIdentity 保持一致，供播放器数据源使用）。 */
private const val DOUYIN_WEB_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"

/**
 * 全屏短视频播放页（推荐流 / 播放列表共用）。
 *
 * 功能：ExoPlayer 播放、封面渐显、右侧互动栏、双击点赞爱心动画、
 * 弹幕覆盖层、D-pad 控制栏（播放/暂停/快进/快退/进度/倍速/弹幕开关/PiP）、
 * 评论面板、播放进度持久化。
 */
@Composable
fun FeedPlayerPage(
    aweme: Aweme,
    isActive: Boolean,
    settings: AppSettings,
    history: HistoryRepository,
    danmakuSource: List<DanmakuItem>,
    comments: List<com.shorttv.app.data.model.Comment>,
    onRequestDanmaku: () -> Unit,
    onToggleDanmaku: () -> Unit,
    onOpenComments: () -> Unit,
    showBackButton: Boolean = false,
    onBack: (() -> Unit)? = null,
    onAuthorClick: () -> Unit,
    onMixClick: (() -> Unit)? = null,
    onEnded: () -> Unit = {},
    onPipActiveChanged: (Boolean) -> Unit = {},
    onEnterPip: () -> Boolean = { false },
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ---------- 播放器 ----------
    val playUrl = remember(aweme.awemeId) { aweme.video.resolvePlayUrl() }
    val player = remember(aweme.awemeId) {
        // 抖音 play/ 中转地址对 Referer/Cookie 敏感（无 Referer 直接 403），
        // 必须为 ExoPlayer 的数据源注入 UA + Referer（对齐 Web 端请求头）。
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
                if (playUrl != null) {
                    setMediaItem(MediaItem.fromUri(playUrl))
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = true
                    prepare()
                }
            }
    }
    DisposableEffect(aweme.awemeId) {
        onDispose { player.release() }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var firstFrameReady by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                onPipActiveChanged(playing)
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    firstFrameReady = true
                    durationMs = player.duration.coerceAtLeast(0L)
                }
                if (state == Player.STATE_ENDED) onEnded()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 激活 / 失焦控制播放
    LaunchedEffect(isActive, aweme.awemeId) {
        player.playWhenReady = isActive
        if (!isActive) player.pause()
    }

    // 进度轮询 + 历史保存
    LaunchedEffect(isActive, aweme.awemeId) {
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            if (positionMs > 3000) history.saveProgress(aweme.awemeId, positionMs, durationMs)
            delay(500)
        }
    }

    // 首次激活加载弹幕
    LaunchedEffect(isActive, aweme.awemeId) {
        if (isActive) onRequestDanmaku()
    }

    // ---------- 互动状态 ----------
    var liked by remember(aweme.awemeId) { mutableStateOf(history.isDigged(aweme.awemeId)) }
    var collected by remember(aweme.awemeId) {
        mutableStateOf(history.getCollects().contains(aweme.awemeId))
    }
    var following by remember(aweme.awemeId) { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var likeBump by remember { mutableLongStateOf(0L) }
    var toast by remember { mutableStateOf<String?>(null) }

    // 沉浸模式（TV 端）：控制栏/评论面板都不在时 = 完全全屏，
    // 底部信息与右侧互动栏隐藏，仅保留弹幕。手机端信息栏常驻。
    val deviceMode = LocalDeviceMode.current
    val chromeVisible = deviceMode == DeviceMode.MOBILE ||
        controlsVisible || showComments || !isActive

    // 双击爱心动画
    val heartAnim = remember { Animatable(0f) }
    var heartPos by remember { mutableStateOf(Offset.Zero) }
    var heartVisible by remember { mutableStateOf(false) }

    // 控制栏自动隐藏
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(5000)
            controlsVisible = false
        }
    }

    // toast 自动消失
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1500)
            toast = null
        }
    }

    fun toggleLike() {
        liked = history.toggleDigg(aweme.awemeId)
        likeBump = if (liked) likeBump + 1 else (likeBump - 1).coerceAtLeast(0L)
    }

    fun toggleCollect() {
        collected = history.toggleCollect(aweme.awemeId)
        toast = if (collected) "已收藏" else "已取消收藏"
    }

    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs)
            .coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
        positionMs = target
    }

    // ---------- 界面 ----------
    // TV 端：活跃页持有焦点，遥控器按键（上下切换 / OK 唤出控制栏）才路由到这里
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isActive) {
        if (isActive) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(aweme.awemeId) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        heartPos = offset
                        heartVisible = true
                        if (!liked) toggleLike()
                    },
                    onTap = { controlsVisible = !controlsVisible }
                )
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        // 全屏（chrome 隐藏）时：遥控器上下键直接切换视频
                        Key.DirectionUp -> {
                            if (chromeVisible) {
                                controlsVisible = true
                                false
                            } else {
                                onSwipeUp()
                                true
                            }
                        }
                        Key.DirectionDown -> {
                            if (chromeVisible) {
                                controlsVisible = true
                                false
                            } else {
                                onSwipeDown()
                                true
                            }
                        }
                        Key.DirectionLeft, Key.DirectionRight,
                        Key.DirectionCenter, Key.Enter -> {
                            if (!controlsVisible) controlsVisible = true
                            false
                        }
                        Key.MediaPlayPause, Key.Spacebar -> {
                            if (isPlaying) player.pause() else player.play()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // 视频画面
        if (playUrl != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setKeepContentOnPlayerReset(true)
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 封面（首帧就绪前显示）
        AsyncImage(
            model = aweme.video.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (firstFrameReady) 0f else 1f)
        )

        // 加载指示
        if (!firstFrameReady && playUrl != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE5394F))
            }
        }

        // 弹幕
        DanmakuOverlay(
            source = danmakuSource,
            enabled = settings.danmakuEnabled,
            fontSizeSp = settings.danmakuSize,
            alpha = settings.danmakuAlpha,
            speedFactor = settings.danmakuSpeed,
            modifier = Modifier.fillMaxSize()
        )

        // 双击爱心
        if (heartVisible) {
            LaunchedEffect(heartVisible) {
                heartAnim.snapTo(0f)
                heartAnim.animateTo(1f, tween(220, easing = LinearEasing))
                delay(300)
                heartAnim.animateTo(0f, tween(220))
                heartVisible = false
            }
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color(0xFFFF4D6A),
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        translationX = heartPos.x - 48.dp.toPx()
                        translationY = heartPos.y - 48.dp.toPx()
                        val s = 0.5f + heartAnim.value * 0.7f
                        scaleX = s
                        scaleY = s
                        alpha = heartAnim.value
                    }
            )
        }

        // 底部信息（作者 + 标题 + 合集入口）——TV 沉浸播放时隐藏
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Column(
                modifier = Modifier
                    .let { if (deviceMode == DeviceMode.MOBILE) it.navigationBarsPadding() else it }
                    .padding(
                        start = 20.dp,
                        bottom = if (deviceMode == DeviceMode.MOBILE) 16.dp else 28.dp,
                        end = 96.dp
                    )
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@${aweme.author.nickname}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                if (aweme.mixInfo != null && onMixClick != null) {
                    Spacer(Modifier.width(10.dp))
                    Row(
                        modifier = Modifier
                            .tvClickable(onClick = onMixClick)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x66000000))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "合集 · ${aweme.mixInfo.mixName}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = aweme.desc,
                color = Color(0xE6FFFFFF),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "${aweme.author.nickname} · 原声",
                    color = Color(0xCCFFFFFF),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            }
        }

        // 右侧互动栏 —— TV 沉浸播放时隐藏
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            ActionRail(
                avatarUrl = aweme.author.avatarUrl,
                likeCount = aweme.statistics.diggCount + likeBump,
                liked = liked,
                commentCount = aweme.statistics.commentCount,
                collected = collected,
                collectCount = aweme.statistics.collectCount,
                shareCount = aweme.statistics.shareCount,
                following = following,
                onAvatarClick = onAuthorClick,
                onLikeClick = { toggleLike() },
                onCommentClick = { showComments = true; onOpenComments() },
                onCollectClick = { toggleCollect() },
                onShareClick = { toast = "分享链接已复制（演示）" },
                onFollowClick = { following = !following }
            )
        }

        // 控制栏
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .let { if (deviceMode == DeviceMode.MOBILE) it.navigationBarsPadding() else it }
            ) {
                PlayerControlsBar(
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                danmakuOn = settings.danmakuEnabled,
                speed = playbackSpeed,
                onPlayPause = { if (isPlaying) player.pause() else player.play() },
                onSeekBack = { seekBy(-10_000) },
                onSeekForward = { seekBy(10_000) },
                onSeekTo = { fraction ->
                    val target = (fraction * durationMs).toLong()
                    player.seekTo(target)
                    positionMs = target
                },
                onToggleDanmaku = onToggleDanmaku,
                onSpeedCycle = {
                    playbackSpeed = when (playbackSpeed) {
                        1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f
                    }
                    player.setPlaybackSpeed(playbackSpeed)
                },
                onPip = { onEnterPip() }
                )
            }
        }

        // 返回按钮（播放列表场景）
        if (showBackButton && onBack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .tvClickable(onClick = onBack)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
        }

        // 评论面板
        AnimatedVisibility(
            visible = showComments,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CommentsPanel(
                comments = comments,
                onClose = { showComments = false }
            )
        }

        // Toast 提示
        toast?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** 右侧互动栏 */
@Composable
private fun ActionRail(
    avatarUrl: String?,
    likeCount: Long,
    liked: Boolean,
    commentCount: Long,
    collected: Boolean,
    collectCount: Long,
    shareCount: Long,
    following: Boolean,
    onAvatarClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onCollectClick: () -> Unit,
    onShareClick: () -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(end = 12.dp, bottom = 90.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
    ) {
        // 头像 + 关注
        Box {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A34))
                    .tvClickable(focusedScale = 1.12f, onClick = onAvatarClick)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (following) Color(0xFF555566) else Color(0xFFE5394F))
                    .tvClickable(focusedScale = 1.2f, onClick = onFollowClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (following) "✓" else "+",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        RailButton(
            icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            label = formatCount(likeCount),
            active = liked,
            onClick = onLikeClick
        )
        RailButton(
            icon = Icons.Filled.ChatBubbleOutline,
            label = formatCount(commentCount),
            onClick = onCommentClick
        )
        RailButton(
            icon = if (collected) Icons.Filled.Star else Icons.Filled.StarBorder,
            label = formatCount(collectCount),
            active = collected,
            onClick = onCollectClick
        )
        RailButton(
            icon = Icons.Filled.Share,
            label = formatCount(shareCount),
            onClick = onShareClick
        )
    }
}

@Composable
private fun RailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.tvClickable(focusedScale = 1.15f, onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) Color(0xFFFF4D6A) else Color.White,
            modifier = Modifier.size(30.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
