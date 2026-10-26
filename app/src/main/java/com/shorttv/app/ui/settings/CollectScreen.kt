package com.shorttv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.shorttv.app.data.model.Aweme
import com.shorttv.app.ui.MainViewModel
import com.shorttv.app.ui.components.EmptyHint
import com.shorttv.app.ui.components.VideoCard
import com.shorttv.app.ui.components.tvClickable

/**
 * 收藏页：本地收藏的视频网格，支持取消收藏与进入播放。
 */
@Composable
fun CollectScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenPlaylist: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 从演示数据与用户数据中筛选已收藏项
    var collectIds by remember { mutableStateOf(vm.history.getCollects()) }
    val feed by vm.feed.collectAsStateWithLifecycle()

    val collected: List<Aweme> = feed.filter { collectIds.contains(it.awemeId) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF14141A))
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
                text = "我的收藏",
                color = Color(0xFFF2F2F5),
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (collected.isEmpty()) {
            EmptyHint(text = "还没有收藏，去推荐页双击点心心吧")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(collected, key = { it.awemeId }) { aweme ->
                    Box {
                        VideoCard(
                            aweme = aweme,
                            onClick = {
                                vm.openPlaylist(collected, collected.indexOf(aweme), "我的收藏")
                                onOpenPlaylist("我的收藏", collected.indexOf(aweme))
                            }
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(14.dp)
                                .tvClickable(onClick = {
                                    vm.history.toggleCollect(aweme.awemeId)
                                    collectIds = vm.history.getCollects()
                                })
                                .clip(CircleShape)
                                .background(Color(0xCC000000))
                                .padding(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "取消收藏",
                                tint = Color(0xFFFF4D6A),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
