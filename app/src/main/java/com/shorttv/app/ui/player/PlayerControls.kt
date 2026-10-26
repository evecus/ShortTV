package com.shorttv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.shorttv.app.ui.components.tvClickable
import com.shorttv.app.util.formatMs

/**
 * 播放器控制栏（D-pad 唤出，5 秒自动隐藏）。
 *
 * 中间：播放/暂停；左右：快退/快进 10s；
 * 底部：进度条（聚焦后左右键拖动）+ 时间 + 倍速 + 弹幕开关 + 小窗。
 */
@Composable
fun PlayerControlsBar(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    danmakuOn: Boolean,
    speed: Float,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onToggleDanmaku: () -> Unit,
    onSpeedCycle: () -> Unit,
    onPip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // 中部控制
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Replay10,
                contentDescription = "快退10秒",
                tint = Color.White,
                modifier = Modifier
                    .size(44.dp)
                    .tvClickable(focusedScale = 1.15f, onClick = onSeekBack)
                    .padding(6.dp)
            )
            Spacer(Modifier.width(36.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0x33FFFFFF))
                    .tvClickable(focusedScale = 1.15f, onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(Modifier.width(36.dp))
            Icon(
                imageVector = Icons.Filled.Forward10,
                contentDescription = "快进10秒",
                tint = Color.White,
                modifier = Modifier
                    .size(44.dp)
                    .tvClickable(focusedScale = 1.15f, onClick = onSeekForward)
                    .padding(6.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // 进度条 + 时间
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatMs(positionMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.width(10.dp))
            TvSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeekTo = onSeekTo,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = formatMs(durationMs),
                color = Color(0xFFA9A9B8),
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(Modifier.height(10.dp))

        // 功能按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlChip(
                text = "倍速 ${speed}x",
                icon = Icons.Filled.Speed,
                onClick = onSpeedCycle
            )
            ControlChip(
                text = if (danmakuOn) "弹幕开" else "弹幕关",
                icon = if (danmakuOn) Icons.Filled.Subtitles else Icons.Filled.SubtitlesOff,
                active = danmakuOn,
                onClick = onToggleDanmaku
            )
            ControlChip(
                text = "小窗",
                icon = Icons.Filled.PictureInPictureAlt,
                onClick = onPip
            )
        }
    }
}

/** 电视端进度条：Material3 Slider 原生支持 D-pad 左右调整 */
@Composable
private fun TvSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Slider(
        value = fraction,
        onValueChange = { onSeekTo(it) },
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFFE5394F),
            activeTrackColor = Color(0xFFE5394F),
            inactiveTrackColor = Color(0x33FFFFFF)
        ),
        modifier = modifier.height(24.dp)
    )
}

@Composable
private fun ControlChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .tvClickable(focusedScale = 1.08f, onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0xFF3A1220) else Color(0x33FFFFFF))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) Color(0xFFFF8FA0) else Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
