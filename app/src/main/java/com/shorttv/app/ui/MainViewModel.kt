package com.shorttv.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shorttv.app.ShortTVApplication
import com.shorttv.app.data.AppSettings
import com.shorttv.app.data.DataSource
import com.shorttv.app.data.HistoryRepository
import com.shorttv.app.data.SettingsRepository
import com.shorttv.app.data.VideoRepository
import com.shorttv.app.data.model.Author
import com.shorttv.app.data.model.Aweme
import com.shorttv.app.data.model.Comment
import com.shorttv.app.data.model.DanmakuItem
import com.shorttv.app.data.model.HotWord
import com.shorttv.app.data.model.LiveRoom
import com.shorttv.app.data.model.MixPage
import com.shorttv.app.data.model.SuggestItem
import com.shorttv.app.data.model.UserPage
import com.shorttv.app.direct.DouyinDirectApi
import com.shorttv.app.ui.player.LiveDanmakuController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 全局状态容器：推荐流 / 直播 / 搜索 / 用户 / 合集 / 设置。
 * 所有界面共享同一实例，切换页签后状态保持。
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepository = SettingsRepository(app)
    private val historyRepository = HistoryRepository(app)
    private val repository = VideoRepository()

    /** 直连抖音官方 API（DIRECT 数据源使用） */
    private val directApi: DouyinDirectApi =
        (app as ShortTVApplication).directApi

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val history: HistoryRepository get() = historyRepository

    // ---------- 推荐流 ----------
    private val _feed = MutableStateFlow<List<Aweme>>(emptyList())
    val feed: StateFlow<List<Aweme>> = _feed.asStateFlow()

    private val _feedLoading = MutableStateFlow(false)
    val feedLoading: StateFlow<Boolean> = _feedLoading.asStateFlow()

    private val _feedError = MutableStateFlow<String?>(null)
    val feedError: StateFlow<String?> = _feedError.asStateFlow()

    private var feedCursor = 0L
    private var feedHasMore = true

    // ---------- 直播 ----------
    private val _liveRooms = MutableStateFlow<List<LiveRoom>>(emptyList())
    val liveRooms: StateFlow<List<LiveRoom>> = _liveRooms.asStateFlow()

    private val _liveLoading = MutableStateFlow(false)
    val liveLoading: StateFlow<Boolean> = _liveLoading.asStateFlow()

    // ---------- 热搜 / 建议 ----------
    private val _hotWords = MutableStateFlow<List<HotWord>>(emptyList())
    val hotWords: StateFlow<List<HotWord>> = _hotWords.asStateFlow()

    private val _suggestions = MutableStateFlow<List<SuggestItem>>(emptyList())
    val suggestions: StateFlow<List<SuggestItem>> = _suggestions.asStateFlow()

    private var suggestJob: Job? = null

    // ---------- 搜索结果 ----------
    private val _searchResult = MutableStateFlow<List<Aweme>>(emptyList())
    val searchResult: StateFlow<List<Aweme>> = _searchResult.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    private var searchCursor = 0L
    private var searchHasMore = false
    private var searchKeyword = ""

    // ---------- 用户 / 合集 ----------
    private val _userPage = MutableStateFlow<UserPage?>(null)
    val userPage: StateFlow<UserPage?> = _userPage.asStateFlow()

    private val _userLoading = MutableStateFlow(false)
    val userLoading: StateFlow<Boolean> = _userLoading.asStateFlow()

    private val _userError = MutableStateFlow<String?>(null)
    val userError: StateFlow<String?> = _userError.asStateFlow()

    private var currentUserSecUid = ""
    private var userJob: Job? = null
    private var userWorksJob: Job? = null
    private var userGeneration = 0L

    /**
     * feed / 搜索 / 播放列表中出现过的作者缓存。
     *
     * 作者资料接口（user/profile/other）在有风控的网络环境下可能整体不可用，
     * 而作者字段随每条视频返回始终可用 —— 用户主页以缓存作者兜底，
     * 保证头像 / 昵称 / 签名 / 统计有真实数据可展示。
     */
    private val authorCache = LinkedHashMap<String, Author>()

    /** 点击作者头像时立即缓存该视频内嵌的作者数据（与任何接口无关，始终可用）。 */
    fun cacheAuthor(author: Author?) {
        if (author == null) return
        if (author.secUid.isNotBlank() && author.nickname.isNotBlank()) {
            authorCache[author.secUid] = author
        }
    }

    private fun cacheAuthors(items: List<Aweme>) {
        for (item in items) cacheAuthor(item.author)
        if (authorCache.size > 200) {
            val iterator = authorCache.entries.iterator()
            var toDrop = authorCache.size - 200
            while (toDrop-- > 0 && iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    private val _mixPage = MutableStateFlow<MixPage?>(null)
    val mixPage: StateFlow<MixPage?> = _mixPage.asStateFlow()

    // ---------- 播放列表（全屏播放器数据源） ----------
    private val _playlist = MutableStateFlow<List<Aweme>>(emptyList())
    val playlist: StateFlow<List<Aweme>> = _playlist.asStateFlow()

    private val _playlistTitle = MutableStateFlow("")
    val playlistTitle: StateFlow<String> = _playlistTitle.asStateFlow()

    // ---------- 弹幕 / 评论 ----------
    private val _danmaku = MutableStateFlow<List<DanmakuItem>>(emptyList())
    val danmaku: StateFlow<List<DanmakuItem>> = _danmaku.asStateFlow()

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading: StateFlow<Boolean> = _commentsLoading.asStateFlow()

    private val _commentsHasMore = MutableStateFlow(false)
    val commentsHasMore: StateFlow<Boolean> = _commentsHasMore.asStateFlow()

    private var currentCommentsAwemeId = ""
    private var commentsCursor = 0L
    private var commentsGeneration = 0L
    private var commentsJob: Job? = null
    private var danmakuJob: Job? = null

    init {
        // 手机端优先使用移动端 App 推荐流（竖屏内容为主）；TV 保持 Web tab/feed
        directApi.preferMobileFeed = detectDeviceMode(app) == DeviceMode.MOBILE
        // 数据源随设置实时切换（默认 DIRECT 直连抖音）
        viewModelScope.launch {
            settings.collectLatest { s ->
                repository.configure(s.dataSource, directApi)
                directApi.setUserCookie(s.userCookie)
            }
        }
        refreshFeed()
        refreshLive()
        refreshHotWords()
    }

    private fun baseUrl(): String = settings.value.apiBaseUrl
    private fun currentSource(): DataSource = settings.value.dataSource

    // ---------- 推荐流 ----------

    fun refreshFeed() {
        feedCursor = 0L
        feedHasMore = true
        _feed.value = emptyList()
        loadMoreFeed()
    }

    fun loadMoreFeed() {
        if (_feedLoading.value || !feedHasMore) return
        _feedLoading.value = true
        viewModelScope.launch {
            try {
                val page = repository.feed(baseUrl(), feedCursor)
                feedHasMore = page.hasMore
                feedCursor = page.nextCursor
                // 直连 tab/feed 为「刷新」语义（响应无游标，每次推荐不同内容），
                // 追加时按 aweme_id 去重，避免翻页出现重复卡片。
                val existing = _feed.value.map { it.awemeId }.toHashSet()
                val fresh = page.items.filter { it.awemeId.isNotBlank() && it.awemeId !in existing }
                cacheAuthors(page.items)
                if (fresh.isNotEmpty() || _feed.value.isEmpty()) {
                    _feed.value = _feed.value + fresh
                }
                if (fresh.isEmpty() && page.items.isNotEmpty() && _feed.value.isNotEmpty()) {
                    // 本页全部重复：允许再刷一次（hasMore 仍为真时由用户继续上滑触发）
                    feedHasMore = page.hasMore
                }
                _feedError.value = null
            } catch (e: Exception) {
                _feedError.value = e.message ?: "加载失败"
            } finally {
                _feedLoading.value = false
            }
        }
    }

    // ---------- 直播 ----------

    fun refreshLive() {
        if (_liveLoading.value) return
        _liveLoading.value = true
        viewModelScope.launch {
            try {
                val (items, _) = repository.liveRooms(baseUrl(), 0)
                _liveRooms.value = items
            } catch (_: Exception) {
                if (_liveRooms.value.isEmpty()) {
                    _liveRooms.value = emptyList()
                }
            } finally {
                _liveLoading.value = false
            }
        }
    }

    // ---------- 热搜 / 建议 ----------

    fun refreshHotWords() {
        viewModelScope.launch {
            _hotWords.value = repository.hotSearch(baseUrl())
        }
    }

    fun updateKeyword(keyword: String) {
        suggestJob?.cancel()
        if (keyword.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            _suggestions.value = repository.suggest(baseUrl(), keyword)
        }
    }

    suspend fun liveEnter(roomId: String): LiveRoom? =
        repository.liveEnter(baseUrl(), roomId)

    // ---------- 搜索 ----------

    fun doSearch(keyword: String) {
        if (keyword.isBlank()) return
        searchKeyword = keyword
        searchCursor = 0L
        searchHasMore = true
        _searchResult.value = emptyList()
        loadMoreSearch()
    }

    fun loadMoreSearch() {
        if (_searchLoading.value || !searchHasMore || searchKeyword.isBlank()) return
        _searchLoading.value = true
        viewModelScope.launch {
            try {
                val page = repository.searchPage(baseUrl(), searchKeyword, searchCursor)
                searchHasMore = page.hasMore
                searchCursor = page.nextCursor
                cacheAuthors(page.items)
                _searchResult.value = _searchResult.value + page.items
            } catch (_: Exception) {
            } finally {
                _searchLoading.value = false
            }
        }
    }

    // ---------- 播放列表 ----------

    fun openPlaylist(items: List<Aweme>, startIndex: Int, title: String) {
        _playlist.value = items
        _playlistTitle.value = title
        cacheAuthors(items)
        // startIndex 通过导航参数传递
    }

    // ---------- 用户 / 合集 ----------

    fun loadUser(secUid: String) {
        if (secUid.isBlank()) {
            _userPage.value = UserPage()
            _userError.value = "作者标识为空"
            return
        }
        val generation = ++userGeneration
        userJob?.cancel()
        userWorksJob?.cancel()
        currentUserSecUid = secUid
        _userError.value = null
        // 立即用视频内嵌的作者数据渲染主页，接口结果随后刷新
        val cached = authorCache[secUid]
        _userPage.value = if (cached != null) UserPage(user = cached) else null
        _userLoading.value = true
        userJob = viewModelScope.launch {
            try {
                val page = repository.user(
                    baseUrl = baseUrl(),
                    secUid = secUid,
                    fallbackAuthor = cached
                )
                if (generation != userGeneration || secUid != currentUserSecUid) return@launch
                // 接口结果与缓存合并：接口拿到的资料/作品优先，缓存保底
                val merged = if (page.user == null && cached != null) page.copy(user = cached) else page
                _userPage.value = merged
                if (merged.user == null && merged.items.isEmpty()) {
                    _userError.value = "作者资料或作品暂不可用"
                } else if (merged.items.isEmpty() && !merged.hasMore && (merged.user?.awemeCount ?: 0L) > 0L) {
                    // 资料已有（feed 缓存兜底）且作者确有作品，但作品接口未返回数据
                    _userError.value = "作品列表暂不可用（接口受限）"
                }
            } catch (e: Exception) {
                if (generation != userGeneration) return@launch
                _userPage.value = cached?.let { UserPage(user = it) } ?: UserPage()
                _userError.value = e.message ?: "作者主页加载失败"
            } finally {
                if (generation == userGeneration) _userLoading.value = false
            }
        }
    }

    fun loadMoreUserWorks() {
        val current = _userPage.value ?: return
        val secUid = currentUserSecUid
        val generation = userGeneration
        if (_userLoading.value || !current.hasMore || secUid.isBlank()) return
        _userLoading.value = true
        userWorksJob = viewModelScope.launch {
            try {
                val next = repository.userWorks(baseUrl(), secUid, current.cursor)
                if (generation != userGeneration || secUid != currentUserSecUid) return@launch
                val latest = _userPage.value ?: return@launch
                val existing = latest.items.mapTo(HashSet<String>()) { it.awemeId }
                val fresh = next.items.filter { it.awemeId.isNotBlank() && it.awemeId !in existing }
                _userPage.value = latest.copy(
                    user = latest.user ?: next.user,
                    items = latest.items + fresh,
                    cursor = next.cursor,
                    hasMore = next.hasMore && next.cursor != latest.cursor
                )
            } catch (e: Exception) {
                if (generation == userGeneration) {
                    _userError.value = e.message ?: "更多作品加载失败"
                }
            } finally {
                if (generation == userGeneration) _userLoading.value = false
            }
        }
    }

    fun loadMix(mixId: String) {
        viewModelScope.launch {
            val page = repository.mix(baseUrl(), mixId)
            cacheAuthors(page.items)
            _mixPage.value = page
        }
    }

    // ---------- 弹幕 / 评论 ----------

    fun loadDanmaku(awemeId: String, durationMs: Long = 0L) {
        danmakuJob?.cancel()
        _danmaku.value = emptyList()
        if (awemeId.isBlank()) return
        danmakuJob = viewModelScope.launch {
            _danmaku.value = repository.danmaku(baseUrl(), awemeId, durationMs)
        }
    }

    fun clearDanmaku() {
        danmakuJob?.cancel()
        _danmaku.value = emptyList()
    }

    fun loadComments(awemeId: String) {
        commentsGeneration++
        commentsJob?.cancel()
        currentCommentsAwemeId = awemeId
        commentsCursor = 0L
        _comments.value = emptyList()
        _commentsLoading.value = false
        _commentsHasMore.value = awemeId.isNotBlank()
        loadMoreComments()
    }

    fun loadMoreComments() {
        if (_commentsLoading.value || !_commentsHasMore.value || currentCommentsAwemeId.isBlank()) return
        val awemeId = currentCommentsAwemeId
        val cursor = commentsCursor
        val generation = commentsGeneration
        _commentsLoading.value = true
        commentsJob = viewModelScope.launch {
            try {
                val page = repository.comments(
                    baseUrl = baseUrl(),
                    awemeId = awemeId,
                    cursor = cursor
                )
                if (generation != commentsGeneration || awemeId != currentCommentsAwemeId) return@launch
                val existing = _comments.value.mapTo(HashSet<String>()) { it.commentId }
                val fresh = page.items.filter { it.commentId.isNotBlank() && it.commentId !in existing }
                _comments.value = _comments.value + fresh
                commentsCursor = page.cursor
                _commentsHasMore.value = page.hasMore && page.cursor != cursor
            } catch (_: Exception) {
                if (generation == commentsGeneration) _commentsHasMore.value = false
            } finally {
                if (generation == commentsGeneration) _commentsLoading.value = false
            }
        }
    }

    // ---------- 设置 ----------

    fun setLicenseAgreed(value: Boolean) {
        viewModelScope.launch { settingsRepository.setLicenseAgreed(value) }
    }

    fun setApiBaseUrl(value: String) {
        viewModelScope.launch {
            settingsRepository.setApiBaseUrl(value)
            if (value.isNotBlank()) {
                refreshFeed(); refreshLive(); refreshHotWords()
            }
        }
    }

    /** 保存用户自己的抖音登录 Cookie（可选，用于搜索 / 作者统计与作品 / 评论分页） */
    fun setUserCookie(value: String) {
        viewModelScope.launch { settingsRepository.setUserCookie(value) }
    }

    /** 切换数据源（直连 / 服务端 / 演示），切换后全量刷新 */
    fun setDataSource(value: DataSource) {
        viewModelScope.launch {
            settingsRepository.setDataSource(value)
            refreshFeed(); refreshLive(); refreshHotWords()
        }
    }

    /**
     * 直连连通性测试：执行一次真实推荐流请求。
     * @return 成功时返回首条视频标题，失败返回 null（异常信息经 Logcat 输出）。
     */
    suspend fun testDirectConnection(): String? {
        if (currentSource() != DataSource.DIRECT) return null
        return try {
            val page = directApi.feed(0, 1)
            if (page.items.isNotEmpty()) {
                page.items.first().desc.ifBlank { page.items.first().awemeId }
            } else null
        } catch (e: Exception) {
            Log.w("MainViewModel", "testDirectConnection failed: ${e.message}")
            null
        }
    }

    fun setDanmakuEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setDanmakuEnabled(value) }
    }

    fun setDanmakuSize(value: Float) {
        viewModelScope.launch { settingsRepository.setDanmakuSize(value) }
    }

    fun setDanmakuAlpha(value: Float) {
        viewModelScope.launch { settingsRepository.setDanmakuAlpha(value) }
    }

    fun setDanmakuSpeed(value: Float) {
        viewModelScope.launch { settingsRepository.setDanmakuSpeed(value) }
    }

    fun setAutoPlayNext(value: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoPlayNext(value) }
    }

    fun setDefaultH265(value: Boolean) {
        viewModelScope.launch { settingsRepository.setDefaultH265(value) }
    }

    fun setPipOnLeave(value: Boolean) {
        viewModelScope.launch { settingsRepository.setPipOnLeave(value) }
    }

    fun setHistoryResume(value: Boolean) {
        viewModelScope.launch { settingsRepository.setHistoryResume(value) }
    }

    /** 直播弹幕模拟生成（演示模式下按节拍推送） */
    fun startLiveDanmaku(controller: LiveDanmakuController, scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            var tick = 0L
            while (true) {
                delay((700L..1800L).random())
                repository.liveDanmakuTick(tick)?.let { controller.push(it) }
                tick++
            }
        }
    }
}
