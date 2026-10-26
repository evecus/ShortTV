package com.shorttv.app.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shorttv.app.ui.MainViewModel
import com.shorttv.app.ui.components.EmptyHint
import com.shorttv.app.ui.components.LiveRoomCard
import com.shorttv.app.ui.components.LoadingBox

/**
 * 直播页：直播间网格（3 列，D-pad 可导航）。
 */
@Composable
fun LiveListScreen(
    vm: MainViewModel,
    onOpenLive: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rooms by vm.liveRooms.collectAsStateWithLifecycle()
    val loading by vm.liveLoading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshLive() }

    when {
        rooms.isEmpty() && loading -> LoadingBox(modifier)
        rooms.isEmpty() -> EmptyHint(
            text = "暂无直播",
            modifier = modifier,
            onRetry = { vm.refreshLive() }
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(rooms, key = { it.roomId }) { room ->
                LiveRoomCard(
                    room = room,
                    onClick = { onOpenLive(room.roomId) }
                )
            }
        }
    }
}
