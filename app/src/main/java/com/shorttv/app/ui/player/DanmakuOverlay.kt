package com.shorttv.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shorttv.app.data.model.DanmakuItem

/** 轨道上的单条弹幕（归一化坐标，与画布尺寸解耦）。 */
private class DanmakuTrack(
    val item: DanmakuItem,
    val layout: TextLayoutResult,
    var xNorm: Float,
    val yPx: Float,
    val speedNormPerSec: Float,
    val widthNorm: Float,
    val lane: Int
)

/**
 * 按抖音 offset_time 时间轴（毫秒）绘制真实点播弹幕。
 *
 * - 每条弹幕只显示一次，不再循环固定列表；
 * - 车道分配防重叠：每条车道速度恒定（后车不会追上前车），且只有在前一条
 *   尾部腾出足够间距时才允许新弹幕入轨；没有空闲车道时丢弃当条；
 * - 暂停时停止移动，快退/大幅跳转时清空轨道重新同步；
 * - 兼容不带 offset_time 的自建服务端，按列表顺序生成临时时间轴。
 */
@Composable
fun DanmakuOverlay(
    source: List<DanmakuItem>,
    playbackPositionMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSizeSp: Float = 16f,
    alpha: Float = 0.85f,
    speedFactor: Float = 1f,
    maxTrackCount: Int = 6
) {
    val textMeasurer = rememberTextMeasurer()
    val tracks = remember { mutableStateListOf<DanmakuTrack>() }
    val emittedIds = remember { mutableSetOf<String>() }
    var frameTick by remember { mutableIntStateOf(0) }
    var lastPlaybackMs by remember { mutableLongStateOf(0L) }
    var canvasWidthPx by remember { mutableStateOf(0) }

    val density = LocalDensity.current
    val laneHeightPx = with(density) { 46.dp.toPx() }
    val topPadPx = with(density) { 56.dp.toPx() }
    val laneGapNorm = 0.06f

    LaunchedEffect(source) {
        tracks.clear()
        emittedIds.clear()
        lastPlaybackMs = playbackPositionMs
    }

    // 帧驱动：播放时推进位置，暂停时保持当前位置。
    LaunchedEffect(enabled, isPlaying, speedFactor) {
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val dt = ((now - last) / 1000f).coerceIn(0.001f, 0.1f)
            last = now
            if (!isPlaying) continue
            frameTick++
            val iter = tracks.iterator()
            while (iter.hasNext()) {
                val track = iter.next()
                track.xNorm -= track.speedNormPerSec * dt
                if (track.xNorm < -1.6f) iter.remove()
            }
        }
    }

    // 播放进度推进时，把进入时间窗的真实弹幕按车道注入。
    LaunchedEffect(source, playbackPositionMs, fontSizeSp, speedFactor, enabled, canvasWidthPx) {
        val jumped = playbackPositionMs + 1_000L < lastPlaybackMs ||
            playbackPositionMs > lastPlaybackMs + 5_000L
        if (jumped) {
            tracks.clear()
            emittedIds.clear()
        }

        val widthPx = canvasWidthPx
        val lowerBound = (playbackPositionMs - 1_500L).coerceAtLeast(0L)
        val useFallbackTimeline = source.all { it.offsetTimeMs == 0L }
        source.mapIndexed { index, item ->
            val offset = if (useFallbackTimeline) index * 750L else item.offsetTimeMs
            item to offset
        }
            .asSequence()
            .filter { (item, offset) ->
                offset in lowerBound..(playbackPositionMs + 250L) && item.id !in emittedIds
            }
            .sortedBy { it.second }
            .forEach { (item, _) ->
                emittedIds.add(item.id)
                if (widthPx <= 0 || tracks.size >= maxTrackCount) return@forEach
                val layout = textMeasurer.measure(
                    text = item.content,
                    style = TextStyle(
                        fontSize = fontSizeSp.sp,
                        color = parseColor(item.color, alpha)
                    )
                )
                val widthNorm = (layout.size.width.toFloat() / widthPx).coerceAtLeast(0.01f)
                val occupied = tracks.map { it.lane }.toHashSet()
                for (lane in 0 until maxTrackCount) {
                    if (lane in occupied) continue
                    val tail = tracks.filter { it.lane == lane }.maxByOrNull { it.xNorm }
                    if (tail != null && tail.xNorm + tail.widthNorm >
                        1.05f - laneGapNorm - widthNorm
                    ) {
                        // 尾部尚未腾出足够间距，避免同车道追尾
                        continue
                    }
                    // 每条车道固定速度：同车道后车不会追上前车
                    val speed = (0.11f + 0.012f * lane) * speedFactor
                    tracks.add(
                        DanmakuTrack(
                            item = item,
                            layout = layout,
                            xNorm = 1.05f,
                            yPx = topPadPx + lane * laneHeightPx,
                            speedNormPerSec = speed,
                            widthNorm = widthNorm,
                            lane = lane
                        )
                    )
                    break
                }
            }
        lastPlaybackMs = playbackPositionMs
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasWidthPx = it.width }
    ) {
        @Suppress("UNUSED_EXPRESSION") frameTick
        tracks.toList().forEach { track ->
            drawText(
                textLayoutResult = track.layout,
                topLeft = Offset(track.xNorm * size.width, track.yPx),
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

/** 直播弹幕推送器（WS / 轮询结果入口）。 */
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
