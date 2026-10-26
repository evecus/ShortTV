package com.shorttv.app.util

import java.util.Locale

/** 数字格式化：12800 → 1.3万 */
fun formatCount(count: Long): String = when {
    count < 10_000 -> count.toString()
    count < 100_000_000 -> String.format(Locale.CHINA, "%.1f万", count / 10_000.0)
    else -> String.format(Locale.CHINA, "%.1f亿", count / 100_000_000.0)
}

/** 秒 → mm:ss / h:mm:ss */
fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "00:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

/** 毫秒 → mm:ss */
fun formatMs(ms: Long): String = formatDuration((ms / 1000).toInt())

/** 相对时间：刚刚 / x分钟前 / x小时前 / x天前 / yyyy-MM-dd */
fun formatRelativeTime(timestampSec: Long): String {
    if (timestampSec <= 0) return ""
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestampSec
    return when {
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60}分钟前"
        diff < 86_400 -> "${diff / 3600}小时前"
        diff < 30 * 86_400 -> "${diff / 86_400}天前"
        else -> {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            date.format(java.util.Date(timestampSec * 1000))
        }
    }
}
