package com.shorttv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shorttv.app.data.model.Comment
import com.shorttv.app.ui.components.tvClickable
import com.shorttv.app.util.formatCount
import com.shorttv.app.util.formatRelativeTime

/**
 * 评论面板（底部滑出，遮挡播放器下半部分）。
 */
@Composable
fun CommentsPanel(
    comments: List<Comment>,
    loading: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F12))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "评论 ${comments.size}",
                color = Color(0xFFF2F2F5),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .tvClickable(onClick = onClose)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A34))
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭评论",
                    tint = Color(0xFFA9A9B8),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (comments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color(0xFFE5394F), modifier = Modifier.size(28.dp))
                } else {
                    Text(
                        text = "暂无可用评论",
                        color = Color(0xFFA9A9B8),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.height(260.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(comments, key = { it.commentId }) { comment ->
                    CommentRow(comment)
                }
                item(key = "comments_footer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (hasMore && !loading) Modifier.tvClickable(onClick = onLoadMore)
                                else Modifier
                            )
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            loading -> CircularProgressIndicator(
                                color = Color(0xFFE5394F),
                                modifier = Modifier.size(24.dp)
                            )
                            hasMore -> Text(
                                text = "加载更多评论",
                                color = Color(0xFFE5394F),
                                style = MaterialTheme.typography.labelLarge
                            )
                            else -> Text(
                                text = "已加载全部评论",
                                color = Color(0xFF666677),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment) {
    var liked by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = comment.userAvatar.ifBlank { null },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2A34))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = comment.userNickname,
                color = Color(0xFFA9A9B8),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = comment.content,
                color = Color(0xFFF2F2F5),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(formatRelativeTime(comment.createTime))
                    if (comment.ipLabel.isNotBlank()) append(" · ${comment.ipLabel}")
                    if (comment.replyCount > 0) append(" · ${comment.replyCount} 条回复")
                },
                color = Color(0xFF666677),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.tvClickable(focusedScale = 1.15f) { liked = !liked }
        ) {
            Icon(
                imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "点赞",
                tint = if (liked) Color(0xFFFF4D6A) else Color(0xFF666677),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = formatCount(comment.diggCount + if (liked) 1 else 0),
                color = Color(0xFF666677),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
