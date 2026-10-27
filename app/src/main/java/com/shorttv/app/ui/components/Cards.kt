package com.shorttv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shorttv.app.data.model.Aweme
import com.shorttv.app.data.model.LiveRoom
import com.shorttv.app.util.formatCount
import com.shorttv.app.util.formatMs

/** 视频网格卡片（搜索结果 / 用户作品 / 合集） */
@Composable
fun VideoCard(
    aweme: Aweme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Column(
        modifier = modifier
            .tvClickable(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1B1B22))
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF101014))
        ) {
            AsyncImage(
                model = aweme.video.coverUrl,
                contentDescription = aweme.desc,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 时长标签
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color(0x99000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatMs(aweme.video.duration.toLong()),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color(0xFFE5394F), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = aweme.desc,
            color = Color(0xFFF2F2F5),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(36.dp)
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = aweme.author.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A34))
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = aweme.author.nickname,
                color = Color(0xFFA9A9B8),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color(0xFFA9A9B8),
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = formatCount(aweme.statistics.diggCount),
                color = Color(0xFFA9A9B8),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** 直播间卡片 */
@Composable
fun LiveRoomCard(
    room: LiveRoom,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .tvClickable(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1B1B22))
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF101014))
        ) {
            AsyncImage(
                model = room.coverUrl,
                contentDescription = room.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color(0xCC000000)
                        )
                    )
            )
            // LIVE 标签
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(
                        if (room.isLiving) Color(0xFFFF4D6A) else Color(0x99000000),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (room.isLiving) "LIVE" else "回放",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            // 在线人数
            Text(
                text = "${formatCount(room.userCount)}人在线",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = room.title,
            color = Color(0xFFF2F2F5),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(36.dp)
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = room.ownerAvatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A34))
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = room.ownerNickname,
                color = Color(0xFFA9A9B8),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 横屏视频卡片（用于合集剧集列表） */
@Composable
fun EpisodeCard(
    aweme: Aweme,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .tvClickable(onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1B1B22))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF101014)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = aweme.video.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "第 ${index + 1} 集",
                color = Color(0xFFE5394F),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = aweme.desc,
                color = Color(0xFFF2F2F5),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatMs(aweme.video.duration.toLong()),
                color = Color(0xFFA9A9B8),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** 通用空态 / 错误提示 */
@Composable
fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            color = Color(0xFFA9A9B8),
            style = MaterialTheme.typography.bodyLarge
        )
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .tvClickable(onClick = onRetry)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE5394F))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("重试", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
