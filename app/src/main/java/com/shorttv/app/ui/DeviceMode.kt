package com.shorttv.app.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf

/** 设备形态：TV（Leanback）或手机 / 平板（触屏）。 */
enum class DeviceMode { TV, MOBILE }

/**
 * 全局设备形态（应用启动时检测一次）。
 * TV：横屏全屏 + D-pad 交互 + 顶部页签栏；
 * MOBILE：竖屏 + 触摸手势 + 底部导航栏。
 *
 * 实测（2026-10）：大量国产电视 / 盒子不声明 FEATURE_LEANBACK，
 * 单靠 leanback 检测会把电视误判为手机（表现为 TV 竖屏 + 手机底部栏）。
 * 因此按多信号取并集，其中「无触摸屏」对电视设备最可靠。
 */
val LocalDeviceMode = staticCompositionLocalOf { DeviceMode.TV }

fun detectDeviceMode(context: Context): DeviceMode {
    val pm = context.packageManager
    val uiMode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
        ?.currentModeType
    val cfg = context.resources.configuration
    val isTv =
        pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
            !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) ||
            // 部分电视谎报有触摸屏：用实际输入配置兜底（遥控器 = D-pad / 无触摸）
            cfg.touchscreen == Configuration.TOUCHSCREEN_NOTOUCH ||
            cfg.navigation == Configuration.NAVIGATION_DPAD ||
            // 无电话功能 + 大屏横向 + 无触摸支持的盒子
            (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) &&
                !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH))
    return if (isTv) DeviceMode.TV else DeviceMode.MOBILE
}
