package com.shorttv.app.ui.home

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
import com.shorttv.app.ui.components.LoadingBox
import com.shorttv.app.ui.player.FeedPlayerPage
import kotlinx.coroutines.launch

/**
 * 推荐页：全屏竖屏信息流（上下滑动切换，类抖音电视端体验）。
 */
@Composable
fun FeedScreen(
    vm: MainViewModel,
    onOpenUser: (String) -> Unit,
    onOpenMix: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val feed by vm.feed.collectAsStateWithLifecycle()
    val loading by vm.feedLoading.collectAsStateWithLifecycle()
    val error by vm.feedError.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val danmakuAll by vm.danmaku.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()
    val commentsLoading by vm.commentsLoading.collectAsStateWithLifecycle()
    val commentsHasMore by vm.commentsHasMore.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (feed.isEmpty()) vm.refreshFeed()
    }

    when {
        feed.isEmpty() && loading -> LoadingBox(modifier)
        feed.isEmpty() -> EmptyHint(
            text = error ?: "暂无内容",
            modifier = modifier,
            onRetry = { vm.refreshFeed() }
        )
        else -> FeedPager(
            vm = vm,
            feed = feed,
            danmakuAll = danmakuAll,
            comments = comments,
            commentsLoading = commentsLoading,
            commentsHasMore = commentsHasMore,
            settings = settings,
            onOpenUser = onOpenUser,
            onOpenMix = onOpenMix,
            modifier = modifier
        )
    }
}

@Composable
private fun FeedPager(
    vm: MainViewModel,
    feed: List<com.shorttv.app.data.model.Aweme>,
    danmakuAll: List<com.shorttv.app.data.model.DanmakuItem>,
    comments: List<com.shorttv.app.data.model.Comment>,
    commentsLoading: Boolean,
    commentsHasMore: Boolean,
    settings: com.shorttv.app.data.AppSettings,
    onOpenUser: (String) -> Unit,
    onOpenMix: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = 0) { feed.size }
    val scope = rememberCoroutineScope()

    // 滑动接近底部时预加载
    LaunchedEffect(pagerState, feed.size) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page >= feed.size - 2) vm.loadMoreFeed()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val aweme = feed[page]
            val active = page == pagerState.settledPage
            fun goPage(target: Int) {
                if (target in feed.indices) {
                    scope.launch { pagerState.animateScrollToPage(target) }
                } else if (target >= feed.size) {
                    vm.loadMoreFeed()
                }
            }
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
                onAuthorClick = {
                    // 点击瞬间缓存视频内嵌的作者数据，作者主页不依赖额外接口
                    vm.cacheAuthor(aweme.author)
                    onOpenUser(aweme.author.secUid)
                },
                onMixClick = aweme.mixInfo?.let { { onOpenMix(it.mixId) } },
                // TV 全屏播放时遥控器上下键切换视频
                onSwipeUp = { goPage(page - 1) },
                onSwipeDown = { goPage(page + 1) },
                onEnded = {
                    if (settings.autoPlayNext) {
                        if (page < feed.lastIndex) {
                            scope.launch { pagerState.animateScrollToPage(page + 1) }
                        } else {
                            // 已是最后一条：预加载更多，连播不断流
                            vm.loadMoreFeed()
                        }
                    }
                }
            )
        }
    }
}
