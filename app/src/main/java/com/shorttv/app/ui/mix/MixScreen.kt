package com.shorttv.app.ui.mix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.shorttv.app.ui.DeviceMode
import com.shorttv.app.ui.LocalDeviceMode
import com.shorttv.app.ui.MainViewModel
import com.shorttv.app.ui.components.EpisodeCard
import com.shorttv.app.ui.components.EmptyHint
import com.shorttv.app.ui.components.LoadingBox
import com.shorttv.app.ui.components.tvClickable

/**
 * 合集页：合集信息 + 剧集列表（点击剧集进入播放列表）。
 */
@Composable
fun MixScreen(
    vm: MainViewModel,
    mixId: String,
    onBack: () -> Unit,
    onOpenPlaylist: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val mixPage by vm.mixPage.collectAsStateWithLifecycle()

    LaunchedEffect(mixId) { vm.loadMix(mixId) }

    val page = mixPage
    if (page == null) {
        LoadingBox(modifier)
        return
    }

    val deviceMode = LocalDeviceMode.current
    val topInset = if (deviceMode == DeviceMode.MOBILE) Modifier.statusBarsPadding() else Modifier

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部返回
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF14141A))
                .then(topInset)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .tvClickable(onClick = onBack)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A34))
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color(0xFFA9A9B8),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = page.mixName,
                color = Color(0xFFF2F2F5),
                style = MaterialTheme.typography.titleLarge
            )
        }

        // 合集信息卡
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101014))
            ) {
                AsyncImage(
                    model = page.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = page.mixName,
                    color = Color(0xFFF2F2F5),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "共 ${page.totalEpisode} 集 · 更新至第 ${page.currentEpisode} 集",
                    color = Color(0xFFE5394F),
                    style = MaterialTheme.typography.labelLarge
                )
                if (page.desc.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = page.desc,
                        color = Color(0xFFA9A9B8),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .tvClickable(onClick = {
                            vm.openPlaylist(page.items, 0, page.mixName)
                            onOpenPlaylist(page.mixName, 0)
                        })
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE5394F))
                        .padding(horizontal = 22.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "从第 1 集开始播放",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // 剧集列表
        if (page.items.isEmpty()) {
            EmptyHint(text = "合集暂无内容")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(page.items.size, key = { it }) { index ->
                    val aweme = page.items[index]
                    EpisodeCard(
                        aweme = aweme,
                        index = index,
                        onClick = {
                            vm.openPlaylist(page.items, index, page.mixName)
                            onOpenPlaylist(page.mixName, index)
                        }
                    )
                }
            }
        }
    }
}
