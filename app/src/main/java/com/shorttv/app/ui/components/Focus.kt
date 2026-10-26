package com.shorttv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import com.shorttv.app.ui.theme.TvFocusRing

/**
 * 电视端可点击容器。
 *
 * 统一支持三种触发方式，保证遥控器 / 触屏 / 键盘行为一致：
 * 1. D-pad 中心键 / Enter / 小键盘 Enter（KeyUp 触发一次）
 * 2. 触屏 / 鼠标点击
 * 3. 获取焦点时放大 + 焦点环
 */
fun Modifier.tvClickable(
    focusedScale: Float = 1.06f,
    ringColor: Color = TvFocusRing,
    ringWidth: Int = 2,
    onClick: () -> Unit
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        label = "tvFocusScale"
    )
    this
        .onFocusChanged { focused = it.isFocused }
        .scale(scale)
        .then(
            if (focused) Modifier.border(ringWidth.dp, ringColor, RoundedCornerShape(12.dp))
            else Modifier
        )
        .focusable(interactionSource = remember { MutableInteractionSource() })
        .onKeyEvent { event ->
            val confirm = event.key == Key.DirectionCenter ||
                event.key == Key.Enter ||
                event.key == Key.NumPadEnter
            if (confirm && event.type == KeyEventType.KeyUp) {
                onClick()
                true
            } else {
                false
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(onTap = { onClick() })
        }
}

/** 纯展示用焦点高亮（不可点击，用于卡片等） */
fun Modifier.focusHighlight(
    scale: Float = 1.05f,
    ringColor: Color = TvFocusRing
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val animScale by animateFloatAsState(
        targetValue = if (focused) scale else 1f,
        label = "focusHighlightScale"
    )
    this
        .onFocusChanged { focused = it.isFocused }
        .scale(animScale)
        .then(
            if (focused) Modifier.border(2.dp, ringColor, RoundedCornerShape(12.dp))
            else Modifier
        )
        .focusable()
}

/** 加载指示 */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x66000000)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = TvFocusRing)
    }
}

/** 圆角裁剪 */
fun Modifier.clipRounded(radius: Int = 12): Modifier =
    this.clip(RoundedCornerShape(radius.dp))
