package com.shorttv.app.ui.settings

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shorttv.app.data.DataSource
import com.shorttv.app.ui.DeviceMode
import com.shorttv.app.ui.LocalDeviceMode
import com.shorttv.app.ui.MainViewModel
import com.shorttv.app.ui.components.tvClickable
import com.shorttv.app.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

/**
 * 设置页：服务端 / 播放 / 弹幕 / 收藏 / 关于。
 */
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onOpenCollect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var apiUrl by remember(settings.apiBaseUrl) { mutableStateOf(settings.apiBaseUrl) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var directTesting by remember { mutableStateOf(false) }
    var directResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "设置",
            color = Color(0xFFF2F2F5),
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(Modifier.height(6.dp))
        // 版本与界面模式自查：装没装对版本、TV/手机判定是否正确，一眼可见
        val ctx = LocalContext.current
        val versionName = remember {
            runCatching {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            }.getOrNull() ?: "?"
        }
        Text(
            text = "版本 $versionName · 界面模式：" +
                if (LocalDeviceMode.current == DeviceMode.TV) "TV（电视）" else "手机",
            color = Color(0xFF77778A),
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(Modifier.height(18.dp))

        // ---------- 数据源 ----------
        SectionTitle("数据源")
        Text(
            text = "直连模式对齐 myDV：端上计算抖音风控签名，无需任何服务端配置即可观看官方数据。",
            color = Color(0xFFA9A9B8),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        val sourceOptions = listOf(
            Triple(DataSource.DIRECT, "直连抖音", "官方 API · 端上签名 · 零配置（默认）"),
            Triple(DataSource.SERVER, "自建服务端", "填写下方兼容 API 地址"),
            Triple(DataSource.DEMO, "演示数据", "内置离线数据（测试流在境外 CDN，国内网络大多无法播放）")
        )
        sourceOptions.forEach { (source, title, subtitle) ->
            val selected = settings.dataSource == source
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Color(0x33E5394F) else Color(0xFF1B1B22))
                    .tvClickable(focusedScale = 1.02f) { vm.setDataSource(source) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Color(0xFFE5394F) else Color(0xFF2A2A34)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color(0xFFF2F2F5), style = MaterialTheme.typography.bodyLarge)
                    Text(subtitle, color = Color(0xFF666677), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (settings.dataSource == DataSource.DIRECT) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionChip(
                    text = if (directTesting) "测试中…" else "测试直连",
                    onClick = {
                        directTesting = true
                        directResult = null
                        scope.launch {
                            val title = withContext(Dispatchers.IO) { vm.testDirectConnection() }
                            directTesting = false
                            directResult = if (title != null) {
                                "直连成功：${title.take(20)}"
                            } else {
                                "直连失败（可能被风控或网络不通），已回退演示数据"
                            }
                        }
                    }
                )
                if (directResult != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = directResult!!,
                        color = if (directResult!!.startsWith("直连成功")) Color(0xFF4DDFA0) else Color(0xFFFF4D6A),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---------- 服务端 ----------
        SectionTitle("服务端")
        Text(
            text = "仅「自建服务端」模式使用：配置兼容的 ShortTV / myDV 同构 API 服务地址；留空则使用内置演示数据。",
            color = Color(0xFFA9A9B8),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1B1B22))
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFF2F2F5)),
                cursorBrush = SolidColor(Color(0xFFE5394F)),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { keyboard?.hide() }
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp)
            )
            Spacer(Modifier.width(10.dp))
            ActionChip(
                text = if (testing) "测试中…" else "测试连接",
                onClick = {
                    testing = true
                    testResult = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { ApiClient(apiUrl).ping() }.getOrDefault(false)
                        }
                        testing = false
                        testResult = if (ok) "连接成功" else "连接失败"
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            ActionChip(
                text = "保存",
                primary = true,
                onClick = { vm.setApiBaseUrl(apiUrl); testResult = "已保存" }
            )
        }
        if (testResult != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = testResult!!,
                color = if (testResult == "连接失败") Color(0xFFFF4D6A) else Color(0xFF4DDFA0),
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(Modifier.height(24.dp))

        // ---------- 播放 ----------
        SectionTitle("播放")
        SwitchRow(
            title = "自动连播",
            subtitle = "播放结束后自动切换下一条",
            checked = settings.autoPlayNext,
            onCheckedChange = { vm.setAutoPlayNext(it) }
        )
        SwitchRow(
            title = "优先 H265 编码",
            subtitle = "省带宽，部分设备不支持时自动回退",
            checked = settings.defaultQualityH265,
            onCheckedChange = { vm.setDefaultH265(it) }
        )
        SwitchRow(
            title = "离开时进入小窗",
            subtitle = "按 Home 键后以小窗继续播放",
            checked = settings.pipOnLeave,
            onCheckedChange = { vm.setPipOnLeave(it) }
        )
        SwitchRow(
            title = "记录播放进度",
            subtitle = "本地保存每个视频的观看位置",
            checked = settings.historyResume,
            onCheckedChange = { vm.setHistoryResume(it) }
        )

        Spacer(Modifier.height(24.dp))

        // ---------- 弹幕 ----------
        SectionTitle("弹幕")
        SwitchRow(
            title = "弹幕总开关",
            subtitle = null,
            checked = settings.danmakuEnabled,
            onCheckedChange = { vm.setDanmakuEnabled(it) }
        )
        if (settings.danmakuEnabled) {
            SliderRow(
                title = "弹幕字号",
                value = settings.danmakuSize,
                range = 12f..28f,
                display = "${settings.danmakuSize.toInt()} sp",
                onValueChange = { vm.setDanmakuSize(it) }
            )
            SliderRow(
                title = "弹幕透明度",
                value = settings.danmakuAlpha,
                range = 0.3f..1f,
                display = "${(settings.danmakuAlpha * 100).toInt()}%",
                onValueChange = { vm.setDanmakuAlpha(it) }
            )
            SliderRow(
                title = "弹幕速度",
                value = settings.danmakuSpeed,
                range = 0.5f..2f,
                display = "${settings.danmakuSpeed}x",
                onValueChange = { vm.setDanmakuSpeed(it) }
            )
        }

        Spacer(Modifier.height(24.dp))

        // ---------- 其他 ----------
        SectionTitle("其他")
        Row(
            modifier = Modifier
                .tvClickable(onClick = onOpenCollect)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1B1B22))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFE5394F))
            Spacer(Modifier.width(12.dp))
            Text("我的收藏", color = Color(0xFFF2F2F5), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${vm.history.getCollects().size} 个",
                color = Color(0xFFA9A9B8),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .tvClickable(onClick = { vm.history.clearAll() })
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1B1B22))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = Color(0xFFFF4D6A))
            Spacer(Modifier.width(12.dp))
            Text("清除播放记录", color = Color(0xFFF2F2F5), style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(24.dp))

        // ---------- 关于 ----------
        SectionTitle("关于")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = Color(0xFFA9A9B8))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "ShortTV 1.0.0",
                    color = Color(0xFFF2F2F5),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Android TV 短视频客户端 · 参考 myDV 逆向分析实现",
                    color = Color(0xFF666677),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color(0xFFE5394F),
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ActionChip(
    text: String,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .tvClickable(onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(if (primary) Color(0xFFE5394F) else Color(0xFF2A2A34))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (primary) Color.White else Color(0xFFA9A9B8),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color(0xFFF2F2F5), style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, color = Color(0xFF666677), style = MaterialTheme.typography.labelSmall)
            }
        }
        TvSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 电视端开关（焦点框 + 状态色） */
@Composable
private fun TvSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val bg = if (checked) Color(0xFFE5394F) else Color(0xFF2A2A34)
    Box(
        modifier = Modifier
            .tvClickable(focusedScale = 1.1f) { onCheckedChange(!checked) }
            .size(width = 52.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(bg),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color(0xFFF2F2F5), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(display, color = Color(0xFFA9A9B8), style = MaterialTheme.typography.labelSmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFE5394F),
                activeTrackColor = Color(0xFFE5394F),
                inactiveTrackColor = Color(0x33FFFFFF)
            )
        )
    }
}
