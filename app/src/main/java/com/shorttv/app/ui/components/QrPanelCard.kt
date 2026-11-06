package com.shorttv.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shorttv.app.remote.RemoteInputHub
import com.shorttv.app.remote.WebPanelServers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 服务器状态 → 状态 Chip 文案与颜色（与 myDV 幕光方案一致） */
private fun statusChip(state: RemoteInputHub.PanelState): Pair<String, Color> = when (state) {
    RemoteInputHub.PanelState.IDLE, RemoteInputHub.PanelState.STARTING -> "连接中" to Color(0xFF666677)
    RemoteInputHub.PanelState.RUNNING -> "已就绪" to Color(0xFF4DDFA0)
    RemoteInputHub.PanelState.ERROR -> "服务不可用" to Color(0xFFFF4D6A)
}

/**
 * 「手机扫码」卡片：二维码 + 状态 Chip + 地址。
 *
 * 对齐 myDV 2.3.43 幕光方案：搜索页扫码 = 远程输入搜索关键词；
 * 设置页扫码 = 推送抖音登录 Cookie 到电视。
 */
@Composable
fun QrPanelCard(
    title: String,
    subtitle: String,
    url: String,
    modifier: Modifier = Modifier,
    qrSizeDp: Int = 96,
) {
    val state by RemoteInputHub.serverState.collectAsStateWithLifecycle()
    val clientCount by RemoteInputHub.clientCount.collectAsStateWithLifecycle()
    var qr by remember(url) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(url) {
        if (WebPanelServers.localIpv4().isNotBlank()) {
            qr = withContext(Dispatchers.IO) { WebPanelServers.qrBitmap(url, 512) }
        }
    }
    val (statusText, statusColor) = statusChip(state)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1B1B22))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(qrSizeDp.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = qr
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "扫码用二维码",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size((qrSizeDp - 6).dp)
                )
            } else {
                Text(
                    text = if (WebPanelServers.localIpv4().isBlank()) "无局域网" else "…",
                    color = Color(0xFF24242E),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = Color(0xFFF2F2F5),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2A2A34))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(statusText, color = statusColor, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = Color(0xFFA9A9B8),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = url,
                color = Color(0xFF666677),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (clientCount > 0) {
                Text(
                    text = "已连接 $clientCount 台设备",
                    color = Color(0xFF4DDFA0),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
