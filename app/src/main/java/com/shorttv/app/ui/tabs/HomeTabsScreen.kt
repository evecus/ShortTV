package com.shorttv.app.ui.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.delay
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

    // ---- TV 沉浸模式：推荐页顶部栏自动隐藏 ----
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    var barVisible by remember { mutableStateOf(true) }      // 推荐页上顶部栏是否显示
    var barHasFocus by remember { mutableStateOf(false) }
    var barHeight by remember { mutableStateOf(0.dp) }
    var interaction by remember { mutableIntStateOf(0) }      // 遥控器操作后重置隐藏计时
    var focusTick by remember { mutableIntStateOf(0) }        // 递增 = 请求把焦点交给顶部栏
    val barRequester = remember { FocusRequester() }

    fun hideBar() {
        // 先把焦点还给播放器，否则顶部栏移除后遥控器按键会丢失
        if (barHasFocus) focusManager.moveFocus(FocusDirection.Down)
        barVisible = false
    }

    // 推荐页：顶部栏显示 5 秒无操作后自动隐藏
    LaunchedEffect(selected, barVisible, interaction) {
        if (deviceMode == DeviceMode.TV && selected == 0 && barVisible) {
            delay(5000)
            hideBar()
        }
    }
    LaunchedEffect(focusTick) {
        if (focusTick > 0) {
            delay(80)
            runCatching { barRequester.requestFocus() }
        }
    }
    // 推荐页全屏播放时按返回键 = 唤出顶部栏（再按一次才退出应用）
    androidx.activity.compose.BackHandler(
        enabled = deviceMode == DeviceMode.TV && selected == 0 && !barVisible
    ) {
        barVisible = true
        focusTick++
    }

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
        // TV：推荐页沉浸全屏（顶部栏浮层，自动隐藏）；其它页签顶部栏常驻
        val immersive = selected == 0
        val showBar = !immersive || barVisible
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F12))) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (immersive) 0.dp else barHeight),
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
            AnimatedVisibility(
                visible = showBar,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically { -it } + fadeIn(tween(200)),
                exit = slideOutVertically { -it } + fadeOut(tween(200))
            ) {
                TopTabBar(
                    selected = selected,
                    onSelect = {
                        selected = it
                        barVisible = true
                        interaction++
                    },
                    selectedRequester = barRequester,
                    translucent = immersive,
                    modifier = Modifier
                        .onSizeChanged { barHeight = with(density) { it.height.toDp() } }
                        .onFocusChanged {
                            // 推荐页：焦点从顶部栏移走（按下键回到视频）即隐藏
                            if (immersive && barHasFocus && !it.hasFocus) barVisible = false
                            barHasFocus = it.hasFocus
                        }
                        .onPreviewKeyEvent { interaction++; false }
                )
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
private fun TopTabBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    selectedRequester: FocusRequester? = null,
    translucent: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (translucent) Color(0xE614141A) else Color(0xFF14141A))
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
                    focusRequester = if (selected == index) selectedRequester else null,
                    onClick = { onSelect(index) }
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: Tab,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val bg = if (selected) Color(0xFFE5394F) else Color.Transparent
    val fg = if (selected) Color.White else Color(0xFFA9A9B8)
    Row(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
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
