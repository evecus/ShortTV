package com.shorttv.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shorttv.app.ui.components.tvClickable
import com.shorttv.app.ui.live.LiveListScreen
import com.shorttv.app.ui.live.LivePlayerScreen
import com.shorttv.app.ui.mix.MixScreen
import com.shorttv.app.ui.player.PlaylistScreen
import com.shorttv.app.ui.search.SearchScreen
import com.shorttv.app.ui.settings.CollectScreen
import com.shorttv.app.ui.settings.SettingsScreen
import com.shorttv.app.ui.tabs.HomeTabsScreen
import com.shorttv.app.ui.user.UserScreen

object Routes {
    const val TABS = "tabs"
    const val LIVE = "live/{roomId}"
    const val PLAYLIST = "playlist?title={title}&index={index}"
    const val USER = "user/{secUid}"
    const val MIX = "mix/{mixId}"
    const val COLLECT = "collect"

    fun live(roomId: String) = "live/${android.net.Uri.encode(roomId)}"
    fun playlist(title: String, index: Int) =
        "playlist?title=${android.net.Uri.encode(title)}&index=$index"
    fun user(secUid: String) = "user/${android.net.Uri.encode(secUid)}"
    fun mix(mixId: String) = "mix/${android.net.Uri.encode(mixId)}"
}

@Composable
fun AppRoot(
    vm: MainViewModel,
    onEnterPip: () -> Boolean
) {
    val navController = rememberNavController()
    var pipActive by remember { mutableStateOf(false) }

    // 注册 PiP 离开回调
    val activity = androidx.compose.ui.platform.LocalContext.current as? com.shorttv.app.MainActivity
    LaunchedEffect(Unit) {
        activity?.onUserLeaveForPip = {
            if (vm.settings.value.pipOnLeave && pipActive) onEnterPip() else false
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.TABS,
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(200)) }
    ) {
        composable(Routes.TABS) {
            HomeTabsScreen(
                vm = vm,
                onOpenLive = { roomId -> navController.navigate(Routes.live(roomId)) },
                onOpenUser = { secUid -> navController.navigate(Routes.user(secUid)) },
                onOpenMix = { mixId -> navController.navigate(Routes.mix(mixId)) },
                onOpenPlaylist = { title, index ->
                    navController.navigate(Routes.playlist(title, index))
                },
                onOpenCollect = { navController.navigate(Routes.COLLECT) }
            )
        }
        composable(Routes.LIVE) { entry ->
            val roomId = entry.arguments?.getString("roomId") ?: ""
            LivePlayerScreen(
                vm = vm,
                roomId = roomId,
                onBack = { navController.popBackStack() },
                onPipActiveChanged = { pipActive = it },
                onEnterPip = onEnterPip
            )
        }
        composable(Routes.PLAYLIST) { entry ->
            val title = entry.arguments?.getString("title") ?: ""
            val index = entry.arguments?.getString("index")?.toIntOrNull() ?: 0
            PlaylistScreen(
                vm = vm,
                title = title,
                startIndex = index,
                onBack = { navController.popBackStack() },
                onOpenUser = { secUid -> navController.navigate(Routes.user(secUid)) },
                onPipActiveChanged = { pipActive = it },
                onEnterPip = onEnterPip
            )
        }
        composable(Routes.USER) { entry ->
            val secUid = entry.arguments?.getString("secUid") ?: ""
            UserScreen(
                vm = vm,
                secUid = secUid,
                onBack = { navController.popBackStack() },
                onOpenPlaylist = { title, index ->
                    navController.navigate(Routes.playlist(title, index))
                }
            )
        }
        composable(Routes.MIX) { entry ->
            val mixId = entry.arguments?.getString("mixId") ?: ""
            MixScreen(
                vm = vm,
                mixId = mixId,
                onBack = { navController.popBackStack() },
                onOpenPlaylist = { title, index ->
                    navController.navigate(Routes.playlist(title, index))
                }
            )
        }
        composable(Routes.COLLECT) {
            CollectScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onOpenPlaylist = { title, index ->
                    navController.navigate(Routes.playlist(title, index))
                }
            )
        }
    }
}

