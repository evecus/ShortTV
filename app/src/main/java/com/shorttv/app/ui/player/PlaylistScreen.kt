package com.shorttv.app.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shorttv.app.ui.MainViewModel
import com.shorttv.app.ui.components.EmptyHint
import kotlinx.coroutines.launch

/**
 * 播放列表页：全屏竖屏连播（搜索结果 / 用户作品 / 合集 / 收藏共用）。
 */
@Composable
fun PlaylistScreen(
    vm: MainViewModel,
    title: String,
    startIndex: Int,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onPipActiveChanged: (Boolean) -> Unit,
    onEnterPip: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val danmakuAll by vm.danmaku.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()

    if (playlist.isEmpty()) {
        EmptyHint(text = "播放列表为空", modifier = modifier)
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, playlist.lastIndex)
    ) { playlist.size }
    val scope = rememberCoroutineScope()

    // 当前页激活时请求弹幕
    val currentAwemeId = playlist.getOrNull(pagerState.settledPage)?.awemeId
    LaunchedEffect(currentAwemeId) {
        currentAwemeId?.let { vm.loadDanmaku(it) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val aweme = playlist[page]
            val active = page == pagerState.settledPage
            val danmakuSource = rememberDripDanmaku(
                all = if (active) danmakuAll else emptyList(),
                active = active
            )
            FeedPlayerPage(
                aweme = aweme,
                isActive = active,
                settings = settings,
                history = vm.history,
                danmakuSource = danmakuSource,
                comments = if (active) comments else emptyList(),
                onRequestDanmaku = { vm.loadDanmaku(aweme.awemeId) },
                onToggleDanmaku = { vm.setDanmakuEnabled(!settings.danmakuEnabled) },
                onOpenComments = { vm.loadComments(aweme.awemeId) },
                showBackButton = true,
                onBack = onBack,
                onAuthorClick = { onOpenUser(aweme.author.secUid) },
                onEnded = {
                    if (settings.autoPlayNext && page < playlist.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(page + 1) }
                    }
                },
                onPipActiveChanged = onPipActiveChanged,
                onEnterPip = onEnterPip
            )
        }
    }
}
