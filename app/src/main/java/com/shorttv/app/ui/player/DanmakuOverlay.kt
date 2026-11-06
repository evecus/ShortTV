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

/** 弹幕只允许出现在屏幕顶部该比例的高度内。 */
private const val DANMAKU_MAX_ZONE_FRACTION = 0.25f

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
    var canvasHeightPx by remember { mutableStateOf(0) }

    val density = LocalDensity.current
    val laneHeightPx = with(density) { 42.dp.toPx() }
    // 顶部留白压到最小（TV 上 25% 区域只能放 2 行就是被这里吃掉的）
    val topPadPx = with(density) { 10.dp.toPx() }
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
                // 右缘完全滚出屏幕才移除，防止超长弹幕未滚完就腾出轨道
                if (track.xNorm + track.widthNorm < -0.05f) iter.remove()
            }
        }
    }

    // 播放进度推进时，把进入时间窗的真实弹幕按车道注入。
    LaunchedEffect(source, playbackPositionMs, fontSizeSp, speedFactor, enabled, canvasWidthPx, canvasHeightPx) {
        val jumped = playbackPositionMs + 1_000L < lastPlaybackMs ||
            playbackPositionMs > lastPlaybackMs + 5_000L
        if (jumped) {
            tracks.clear()
            emittedIds.clear()
        }

        val widthPx = canvasWidthPx
        // 可用弹幕区域 = 屏幕顶部 25% 高度；轨道数 = 可用高度 / 单条高度（向下取整），至少 1 条
        val zoneBottomPx = canvasHeightPx * DANMAKU_MAX_ZONE_FRACTION
        val laneCount = maxOf(1, ((zoneBottomPx - topPadPx) / laneHeightPx).toInt())
            .coerceAtMost(maxTrackCount)
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
                for (lane in 0 until laneCount) {
                    // 同轨道防重叠：轨道上任一弹幕右缘未离开入轨安全线前视为忙，
                    // 所有轨道都忙则丢弃当条（入轨后同轨道速度恒定，不会追尾）
                    val laneBusy = tracks.any { track ->
                        track.lane == lane && track.xNorm + track.widthNorm >
                            1.05f - laneGapNorm
                    }
                    if (laneBusy) continue
                    tracks.add(
                        DanmakuTrack(
                            item = item,
                            layout = layout,
                            xNorm = 1.05f,
                            yPx = topPadPx + lane * laneHeightPx,
                            speedNormPerSec = (0.11f + 0.012f * lane) * speedFactor,
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
            .onSizeChanged {
                canvasWidthPx = it.width
                canvasHeightPx = it.height
            }
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
