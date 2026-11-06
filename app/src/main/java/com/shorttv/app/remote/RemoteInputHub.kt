package com.shorttv.app.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Web 面板（幕光方案）全局状态枢纽。
 *
 * 与 myDV 2.3.43 同构：电视端在局域网内开放
 *   - HTTP 9979  搜索输入页（手机扫码打开）
 *   - WS   9980  搜索文字实时同步
 *   - HTTP 9978  登录信息推送页（Cookie 提交）
 * 手机端页面与本 Hub 之间的消息协议见 assets/web 目录下的页面脚本。
 */
object RemoteInputHub {

    /** 面板服务器状态（与搜索页状态 Chip 一致） */
    enum class PanelState { IDLE, STARTING, RUNNING, ERROR }

    private val _serverState = MutableStateFlow(PanelState.IDLE)
    val serverState: StateFlow<PanelState> = _serverState.asStateFlow()

    /** 当前电视搜索框文字（手机端打开页面后先收到回显，输入时也持续回显） */
    private val _tvText = MutableStateFlow("")
    val tvText: StateFlow<String> = _tvText.asStateFlow()

    /** 手机端发来的「input」实时输入 */
    val remoteInputs = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** 手机端发来的「search」触发搜索 */
    val remoteSearches = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** 手机端推送的登录 Cookie（POST /api/submit） */
    val cookiePushes = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** 当前连接到 WS 的手机端数量 */
    private val _clientCount = MutableStateFlow(0)
    val clientCount: StateFlow<Int> = _clientCount.asStateFlow()

    @Volatile
    var lastCookiePushTime: Long = 0L
        private set

    fun setServerState(state: PanelState) {
        _serverState.value = state
    }

    /** 电视侧搜索框文字变化（含手机端实时输入回写）→ 更新并广播给所有手机端 */
    fun setTvText(text: String) {
        _tvText.value = text
        WebPanelServers.broadcastTvText(text)
    }

    fun onRemoteInput(text: String) {
        _tvText.value = text
        remoteInputs.tryEmit(text)
        WebPanelServers.broadcastTvText(text)
    }

    fun onRemoteSearch(text: String) {
        remoteSearches.tryEmit(text)
    }

    fun onCookiePushed(cookie: String) {
        lastCookiePushTime = System.currentTimeMillis()
        cookiePushes.tryEmit(cookie)
    }

    fun setClientCount(count: Int) {
        _clientCount.value = count
    }
}
