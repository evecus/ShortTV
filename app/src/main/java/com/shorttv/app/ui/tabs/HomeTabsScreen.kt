package com.shorttv.app.ui.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shorttv.app.ui.DeviceMode
import com.shorttv.app.ui.LocalDeviceMode
import com.shorttv.app.ui.MainViewModel
import com.shorttv.app.ui.components.tvClickable
import com.shorttv.app.ui.home.FeedScreen
import com.shorttv.app.ui.live.LiveListScreen
import com.shorttv.app.ui.search.SearchScreen
import com.shorttv.app.ui.settings.SettingsScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("推荐", Icons.Filled.Home),
    LIVE("直播", Icons.Filled.LiveTv),
    SEARCH("搜索", Icons.Filled.Search),
    SETTINGS("设置", Icons.Filled.Settings)
}

/**
 * 主界面。
 * TV：顶部页签栏 + 内容区（保持原有遥控器焦点体验）；
 * 手机：内容区铺满全屏 + 底部导航栏（类抖音 App）。
 * 页签状态用 rememberSaveable 保持，切换后各页数据由 ViewModel 持有不丢失。
 */
@Composable
fun HomeTabsScreen(
    vm: MainViewModel,
    onOpenLive: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenMix: (String) -> Unit,
    onOpenPlaylist: (String, Int) -> Unit,
    onOpenCollect: () -> Unit
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val deviceMode = LocalDeviceMode.current

    // 返回键：非首页页签先回首页
    androidx.activity.compose.BackHandler(enabled = selected != 0) {
        selected = 0
    }

    if (deviceMode == DeviceMode.MOBILE) {
        // 手机：内容全屏 + 底部导航
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F12))) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    },
                    label = "tabContent"
                ) { tab ->
                    val isImmersive = Tab.entries[tab] == Tab.HOME
                    if (deviceMode == DeviceMode.MOBILE && !isImmersive) {
                        // 手机端非全屏视频页：避开系统状态栏
                        Box(Modifier.fillMaxSize().statusBarsPadding()) {
                            TabContent(
                                tab = tab,
                                vm = vm,
                                onOpenLive = onOpenLive,
                                onOpenUser = onOpenUser,
                                onOpenMix = onOpenMix,
                                onOpenPlaylist = onOpenPlaylist,
                                onOpenCollect = onOpenCollect
                            )
                        }
                    } else {
                        TabContent(
                            tab = tab,
                            vm = vm,
                            onOpenLive = onOpenLive,
                            onOpenUser = onOpenUser,
                            onOpenMix = onOpenMix,
                            onOpenPlaylist = onOpenPlaylist,
                            onOpenCollect = onOpenCollect
                        )
                    }
                }
            }
            BottomNavBar(selected = selected, onSelect = { selected = it })
        }
    } else {
        // TV：顶部页签栏（原有布局）
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F12))) {
            TopTabBar(
                selected = selected,
                onSelect = { selected = it }
            )
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    },
                    label = "tabContentTv"
                ) { tab ->
                    TabContent(
                        tab = tab,
                        vm = vm,
                        onOpenLive = onOpenLive,
                        onOpenUser = onOpenUser,
                        onOpenMix = onOpenMix,
                        onOpenPlaylist = onOpenPlaylist,
                        onOpenCollect = onOpenCollect
                    )
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    tab: Int,
    vm: MainViewModel,
    onOpenLive: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenMix: (String) -> Unit,
    onOpenPlaylist: (String, Int) -> Unit,
    onOpenCollect: () -> Unit
) {
    when (Tab.entries[tab]) {
        Tab.HOME -> FeedScreen(
            vm = vm,
            onOpenUser = onOpenUser,
            onOpenMix = onOpenMix
        )
        Tab.LIVE -> LiveListScreen(
            vm = vm,
            onOpenLive = onOpenLive
        )
        Tab.SEARCH -> SearchScreen(
            vm = vm,
            onOpenPlaylist = onOpenPlaylist,
            onOpenUser = onOpenUser
        )
        Tab.SETTINGS -> SettingsScreen(
            vm = vm,
            onOpenCollect = onOpenCollect
        )
    }
}

/** 手机底部导航栏（类抖音 App）。 */
@Composable
private fun BottomNavBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF14141A))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.entries.forEachIndexed { index, tab ->
            val isSel = selected == index
            val fg = if (isSel) Color(0xFFE5394F) else Color(0xFF9A9AA8)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .tvClickable(onClick = { onSelect(index) })
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = fg,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tab.label,
                    color = fg,
                    fontSize = 11.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TopTabBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF14141A))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 品牌标
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE5394F)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "ShortTV",
                color = Color(0xFFF2F2F5),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tab.entries.forEachIndexed { index, tab ->
                TabItem(
                    tab = tab,
                    selected = selected == index,
                    onClick = { onSelect(index) }
                )
            }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Color(0xFFE5394F) else Color.Transparent
    val fg = if (selected) Color.White else Color(0xFFA9A9B8)
    Row(
        modifier = Modifier
            .tvClickable(focusedScale = 1.08f, onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = tab.label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
