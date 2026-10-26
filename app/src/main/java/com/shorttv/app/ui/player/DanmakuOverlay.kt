package com.shorttv.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.shorttv.app.data.model.DanmakuItem
import kotlin.random.Random
import kotlinx.coroutines.delay

/** 轨道上的单条弹幕（归一化坐标，与画布尺寸解耦） */
private class DanmakuTrack(
    val item: DanmakuItem,
    val layout: TextLayoutResult,
    var xNorm: Float,          // 1.0 = 右边缘外侧；< -1.6 判定离屏
    val yPx: Float,
    val speedNormPerSec: Float
)

/**
 * 弹幕覆盖层（Canvas 绘制，零第三方依赖）。
 *
 * - 滚动弹幕从右向左，速度/尺寸/透明度可配
 * - 同一时刻最多 [maxTrackCount] 条，避免遮挡画面
 * - [source] 每增长一条即入轨一条（由调用方节流 drip-feed）
 */
@Composable
fun DanmakuOverlay(
    source: List<DanmakuItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSizeSp: Float = 16f,
    alpha: Float = 0.85f,
    speedFactor: Float = 1f,
    maxTrackCount: Int = 6
) {
    if (!enabled || source.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val tracks = remember { mutableStateListOf<DanmakuTrack>() }
    var injected by remember { mutableIntStateOf(0) }
    var frameTick by remember { mutableIntStateOf(0) }

    // 帧驱动：推进位置并回收离屏弹幕
    LaunchedEffect(Unit) {
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val dt = ((now - last) / 1000f).coerceIn(0.001f, 0.1f)
            last = now
            frameTick++
            val iter = tracks.iterator()
            while (iter.hasNext()) {
                val t = iter.next()
                t.xNorm -= t.speedNormPerSec * dt
                if (t.xNorm < -1.6f) iter.remove()
            }
        }
    }

    // 注入新弹幕
    LaunchedEffect(source.size, fontSizeSp, speedFactor) {
        if (source.size <= injected) return@LaunchedEffect
        val from = injected.coerceAtMost(source.size)
        val newOnes = source.subList(from, source.size)
        injected = source.size
        newOnes.forEach { item ->
            if (tracks.size >= maxTrackCount) return@forEach
            val layout = textMeasurer.measure(
                text = item.content,
                style = TextStyle(
                    fontSize = fontSizeSp.sp,
                    color = parseColor(item.color, alpha)
                )
            )
            tracks.add(
                DanmakuTrack(
                    item = item,
                    layout = layout,
                    xNorm = 1.05f,
                    yPx = 56f + (tracks.size % maxTrackCount) * 46f,
                    speedNormPerSec = (0.10f + Random.nextFloat() * 0.08f) * speedFactor
                )
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION") frameTick
        tracks.toList().forEach { t ->
            val xPx = t.xNorm * size.width
            drawText(
                textLayoutResult = t.layout,
                topLeft = Offset(xPx, t.yPx),
                alpha = alpha
            )
        }
    }
}

private fun parseColor(hex: String, alpha: Float): Color {
    val base = runCatching { Color(android.graphics.Color.parseColor(hex)) }
        .getOrDefault(Color.White)
    return base.copy(alpha = alpha.coerceIn(0f, 1f))
}

/** 直播弹幕推送器（WS / 轮询结果入口） */
class LiveDanmakuController {
    val items: SnapshotStateList<DanmakuItem> = mutableStateListOf()

    fun push(item: DanmakuItem) {
        items.add(item)
        if (items.size > 80) items.removeAt(0)
    }

    fun clear() {
        items.clear()
    }
}

/**
 * 弹幕滴灌器：将完整弹幕列表按固定节拍逐条送入覆盖层，
 * 模拟真实弹幕节奏，避免一次性刷屏。
 */
@Composable
fun rememberDripDanmaku(
    all: List<DanmakuItem>,
    active: Boolean,
    intervalMs: Long = 450L
): List<DanmakuItem> {
    val visible = remember { mutableStateListOf<DanmakuItem>() }
    LaunchedEffect(active, all) {
        if (!active || all.isEmpty()) return@LaunchedEffect
        var i = 0
        while (true) {
            visible.add(all[i % all.size])
            if (visible.size > 40) visible.removeAt(0)
            i++
            delay(intervalMs)
        }
    }
    return visible
}
