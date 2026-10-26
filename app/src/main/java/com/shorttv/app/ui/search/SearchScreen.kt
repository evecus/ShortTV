package com.shorttv.app.ui.search

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shorttv.app.ui.MainViewModel
import com.shorttv.app.ui.components.EmptyHint
import com.shorttv.app.ui.components.LoadingBox
import com.shorttv.app.ui.components.VideoCard
import com.shorttv.app.ui.components.tvClickable
import com.shorttv.app.util.formatCount

/**
 * 搜索页：输入框 + 热搜榜 + 联想建议 + 结果网格。
 */
@Composable
fun SearchScreen(
    vm: MainViewModel,
    onOpenPlaylist: (String, Int) -> Unit,
    onOpenUser: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var keyword by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val hotWords by vm.hotWords.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val results by vm.searchResult.collectAsStateWithLifecycle()
    val loading by vm.searchLoading.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { vm.refreshHotWords() }

    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        // 搜索输入框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1B1B22))
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = Color(0xFFA9A9B8),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = keyword,
                onValueChange = {
                    keyword = it
                    if (it.isNotBlank()) {
                        searching = true
                        vm.updateKeyword(it)
                    } else {
                        searching = false
                        vm.updateKeyword("")
                    }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = Color(0xFFF2F2F5),
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                ),
                cursorBrush = SolidColor(Color(0xFFE5394F)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        searching = true
                        vm.doSearch(keyword)
                        keyboard?.hide()
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp)
            )
            if (keyword.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .tvClickable(onClick = {
                            keyword = ""
                            searching = false
                            vm.updateKeyword("")
                        })
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2A2A34))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("清空", color = Color(0xFFA9A9B8), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            // 联想建议
            searching && suggestions.isNotEmpty() -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    suggestions.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvClickable {
                                    keyword = item.content
                                    searching = true
                                    vm.doSearch(item.content)
                                }
                                .clip(RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = Color(0xFF666677),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = item.content,
                                color = Color(0xFFF2F2F5),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.hint.isNotBlank()) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = item.hint,
                                    color = Color(0xFF666677),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 搜索结果
            searching && results.isNotEmpty() -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 240.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(results, key = { it.awemeId }) { aweme ->
                        VideoCard(
                            aweme = aweme,
                            onClick = {
                                vm.openPlaylist(results, results.indexOf(aweme), "搜索：$keyword")
                                onOpenPlaylist("搜索：$keyword", results.indexOf(aweme))
                            }
                        )
                    }
                }
            }

            searching && loading -> LoadingBox()

            searching -> EmptyHint(text = "没有找到「$keyword」相关内容")

            // 默认：热搜榜
            else -> HotWordsPanel(
                hotWords = hotWords.map { it.word to it.hotValue },
                onWordClick = { word ->
                    keyword = word
                    searching = true
                    vm.doSearch(word)
                }
            )
        }
    }
}

@Composable
private fun HotWordsPanel(
    hotWords: List<Pair<String, Long>>,
    onWordClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.TrendingUp,
                contentDescription = null,
                tint = Color(0xFFE5394F),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "抖音电视端热搜",
                color = Color(0xFFF2F2F5),
                style = MaterialTheme.typography.titleLarge
            )
        }
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(hotWords.size) { index ->
                val (word, value) = hotWords[index]
                Row(
                    modifier = Modifier
                        .tvClickable(onClick = { onWordClick(word) })
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1B1B22))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        color = if (index < 3) Color(0xFFE5394F) else Color(0xFF666677),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = word,
                        color = Color(0xFFF2F2F5),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatCount(value),
                        color = Color(0xFF666677),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
