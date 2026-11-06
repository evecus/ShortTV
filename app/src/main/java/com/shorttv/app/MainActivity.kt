package com.shorttv.app

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import com.shorttv.app.ui.AppRoot
import com.shorttv.app.ui.DeviceMode
import com.shorttv.app.ui.LocalDeviceMode
import com.shorttv.app.ui.MainViewModel
import com.shorttv.app.ui.detectDeviceMode
import com.shorttv.app.ui.theme.ShortTVTheme

class MainActivity : ComponentActivity() {

    /** 播放页注册：按 Home 离开时是否进入小窗（返回 false 表示不拦截） */
    var onUserLeaveForPip: (() -> Boolean)? = null

    /** 设备形态：TV（Leanback）横屏 / 手机竖屏，锁定后不再随传感器旋转。 */
    private val deviceMode: DeviceMode by lazy {
        detectDeviceMode(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 手机端固定竖屏（解决旋转错乱）。
        // TV 端不锁方向：部分电视 / 盒子对 requestedOrientation 处理异常，
        // 会把 app 渲染成竖屏居中（屏幕横、画面竖），保持 fullSensor 由系统决定。
        if (deviceMode == DeviceMode.MOBILE) {
            requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        enableEdgeToEdge()
        // 沉浸式黑色视频底：系统状态栏 / 导航栏图标统一用白色（深色背景上更清晰）。
        // minSdk 23，WindowCompat 内部已做版本兼容，低版本自动空实现。
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            ShortTVTheme {
                CompositionLocalProvider(LocalDeviceMode provides deviceMode) {
                    val vm: MainViewModel = viewModel()
                    val settings by vm.settings.collectAsStateWithLifecycle()
                    AppRoot(
                        vm = vm,
                        onEnterPip = ::tryEnterPip
                    )
                }
            }
        }
    }

    private fun tryEnterPip(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } catch (_: Exception) {
            false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        onUserLeaveForPip?.invoke()
    }

    override fun onDestroy() {
        // 返回键退出应用（isFinishing）时停掉所有已注册播放器，避免退出后仍在后台出声；
        // 非 finishing（转屏重建 / PiP）不干预，重新进入后由页面自行恢复播放。
        if (isFinishing) {
            PlayerRegistry.stopAll()
        }
        super.onDestroy()
    }
}

/** 活跃播放器注册表：播放页组合时注册、销毁时注销；Activity 退出时统一停声。 */
object PlayerRegistry {
    private val players = java.util.concurrent.CopyOnWriteArraySet<ExoPlayer>()

    fun register(player: ExoPlayer) {
        players.add(player)
    }

    fun unregister(player: ExoPlayer) {
        players.remove(player)
    }

    /** pause + 清空媒体（不 release：释放仍由各页面组合销毁负责，避免二次释放）。 */
    fun stopAll() {
        players.forEach { player ->
            runCatching {
                player.pause()
                player.playWhenReady = false
                player.clearMediaItems()
            }
        }
        players.clear()
    }
}
