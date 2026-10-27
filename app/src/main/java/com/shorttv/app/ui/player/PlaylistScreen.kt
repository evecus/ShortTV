package com.shorttv.app.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
    val commentsLoading by vm.commentsLoading.collectAsStateWithLifecycle()
    val commentsHasMore by vm.commentsHasMore.collectAsStateWithLifecycle()

    if (playlist.isEmpty()) {
        EmptyHint(text = "播放列表为空", modifier = modifier)
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, playlist.lastIndex)
    ) { playlist.size }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val aweme = playlist[page]
            val active = page == pagerState.settledPage
            FeedPlayerPage(
                aweme = aweme,
                isActive = active,
                settings = settings,
                history = vm.history,
                danmakuSource = if (active) danmakuAll else emptyList(),
                comments = if (active) comments else emptyList(),
                commentsLoading = active && commentsLoading,
                commentsHasMore = active && commentsHasMore,
                onRequestDanmaku = { vm.loadDanmaku(aweme.awemeId, aweme.video.duration.toLong()) },
                onToggleDanmaku = { vm.setDanmakuEnabled(!settings.danmakuEnabled) },
                onOpenComments = { vm.loadComments(aweme.awemeId) },
                onLoadMoreComments = { vm.loadMoreComments() },
                showBackButton = true,
                onBack = onBack,
                onAuthorClick = {
                    // 点击瞬间缓存视频内嵌的作者数据，作者主页不依赖额外接口
                    vm.cacheAuthor(aweme.author)
                    onOpenUser(aweme.author.secUid)
                },
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
