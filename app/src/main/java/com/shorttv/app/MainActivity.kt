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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
        setContent {
            ShortTVTheme {
                CompositionLocalProvider(LocalDeviceMode provides deviceMode) {
                    val vm: MainViewModel = viewModel()
                    val settings by vm.settings.collectAsStateWithLifecycle()
                    AppRoot(
                        vm = vm,
                        licenseAgreed = settings.licenseAgreed,
                        onLicenseAgreed = { vm.setLicenseAgreed(true) },
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
}
