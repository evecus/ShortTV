package com.whl.quickjs.android

/**
 * 加载 native 库。库名必须为 quickjs-android-wrapper
 * （对应 libquickjs-android-wrapper.so，JNI 符号由 native 侧静态绑定）。
 */
object QuickJSLoader {
    private var loaded = false

    @JvmStatic
    @Synchronized
    fun init() {
        if (!loaded) {
            System.loadLibrary("quickjs-android-wrapper")
            loaded = true
        }
    }
}
