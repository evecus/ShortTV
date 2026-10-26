package com.shorttv.app.ui.user

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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.shorttv.app.data.model.Author
import com.shorttv.app.ui.MainViewModel
import com.shorttv.app.ui.components.EmptyHint
import com.shorttv.app.ui.components.LoadingBox
import com.shorttv.app.ui.components.VideoCard
import com.shorttv.app.ui.components.tvClickable
import com.shorttv.app.util.formatCount

/**
 * 用户主页：头像 + 昵称 + 签名 + 统计 + 关注按钮 + 作品网格。
 */
@Composable
fun UserScreen(
    vm: MainViewModel,
    secUid: String,
    onBack: () -> Unit,
    onOpenPlaylist: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val userPage by vm.userPage.collectAsStateWithLifecycle()
    var following by remember { mutableStateOf(false) }

    LaunchedEffect(secUid) { vm.loadUser(secUid) }

    val page = userPage
    if (page == null) {
        LoadingBox(modifier)
        return
    }

    val author: Author = page.user ?: page.items.firstOrNull()?.author
        ?: return EmptyHint(text = "用户不存在", modifier = modifier)

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部返回
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
                text = author.nickname,
                color = Color(0xFFF2F2F5),
                style = MaterialTheme.typography.titleLarge
            )
        }

        // 用户信息
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF14141A))
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = author.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A34))
            )
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (author.signature.isNotBlank()) {
                    Text(
                        text = author.signature,
                        color = Color(0xFFA9A9B8),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    StatItem("获赞", formatCount(author.totalFavorited))
                    StatItem("关注", formatCount(author.followingCount))
                    StatItem("粉丝", formatCount(author.followerCount))
                    StatItem("作品", formatCount(author.awemeCount))
                }
            }
            Box(
                modifier = Modifier
                    .tvClickable(onClick = { following = !following })
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (following) Color(0xFF2A2A34) else Color(0xFFE5394F))
                    .padding(horizontal = 22.dp, vertical = 9.dp)
            ) {
                Text(
                    text = if (following) "已关注" else "+ 关注",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // 作品网格
        if (page.items.isEmpty()) {
            EmptyHint(text = "暂无作品")
        } else {
            Text(
                text = "作品 ${page.items.size}",
                color = Color(0xFFA9A9B8),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(page.items, key = { it.awemeId }) { aweme ->
                    VideoCard(
                        aweme = aweme,
                        onClick = {
                            vm.openPlaylist(page.items, page.items.indexOf(aweme), author.nickname)
                            onOpenPlaylist(author.nickname, page.items.indexOf(aweme))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color(0xFFF2F2F5), style = MaterialTheme.typography.titleMedium)
        Text(text = label, color = Color(0xFF666677), style = MaterialTheme.typography.labelSmall)
    }
}
