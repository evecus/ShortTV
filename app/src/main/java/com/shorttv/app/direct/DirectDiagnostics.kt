package com.shorttv.app.direct

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 直连链路运行时诊断（电视端无 adb 时，在设置页直接展示失败原因）。
 *
 * 记录内容与 myDV 2.3.43 直连链路的各关键环节对齐：
 * JS 签名引擎、ttwid、msToken、UIFID、最近一次 feed 请求结果。
 */
object DirectDiagnostics {

    @Volatile var jsReady: Boolean = false
        private set
    @Volatile var jsInitError: String = ""
        private set
    @Volatile var msTokenSource: String = "未获取"
        private set
    @Volatile var msTokenLen: Int = 0
        private set
    @Volatile var ttwidPresent: Boolean = false
        private set
    @Volatile var uifidPresent: Boolean = false
        private set
    @Volatile var identitySource: String = ""
        private set
    @Volatile var lastFeedSummary: String = "尚未请求"
        private set

    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

    fun recordJs(ready: Boolean, error: String = "") {
        jsReady = ready
        jsInitError = if (ready) "" else error.take(120)
    }

    fun recordMsToken(source: String, token: String?) {
        msTokenSource = source
        msTokenLen = token?.length ?: 0
    }

    fun recordIdentity(ttwid: Boolean, uifid: Boolean, source: String) {
        ttwidPresent = ttwid
        uifidPresent = uifid
        identitySource = source
    }

    fun recordFeed(summary: String) {
        lastFeedSummary = "[${fmt.format(Date())}] $summary".take(160)
    }

    /** 设置页展示行。 */
    fun lines(): List<String> = listOf(
        "JS 签名引擎：" + if (jsReady) "就绪" else if (jsInitError.isBlank()) "未初始化" else "失败：$jsInitError",
        "msToken：$msTokenSource" + if (msTokenLen > 0) "（长度 $msTokenLen）" else "",
        "ttwid：" + if (ttwidPresent) "已获取" else "缺失",
        "UIFID：" + if (uifidPresent) "已获取" else "缺失",
        "身份来源：${identitySource.ifBlank { "未知" }}",
        "最近推荐流：$lastFeedSummary",
    )
}
