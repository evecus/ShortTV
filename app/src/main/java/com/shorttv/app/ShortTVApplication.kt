package com.shorttv.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.shorttv.app.data.HistoryRepository
import com.shorttv.app.data.SettingsRepository
import com.shorttv.app.direct.AbogusSigner
import com.shorttv.app.direct.DouyinDirectApi
import com.shorttv.app.direct.DouyinIdentity
import com.shorttv.app.remote.WebPanelServers

class ShortTVApplication : Application(), ImageLoaderFactory {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var historyRepository: HistoryRepository
        private set

    /** 直连抖音官方 API 客户端（单例；签名引擎与身份管理内部懒初始化） */
    val directApi: DouyinDirectApi by lazy {
        DouyinDirectApi(
            identity = DouyinIdentity.get(this),
            signer = AbogusSigner.get(this)
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepository = SettingsRepository(this)
        historyRepository = HistoryRepository(this)
        // Web 面板服务器（myDV 2.3.43 幕光方案）：
        // 9978 登录信息推送页 / 9979 搜索输入页 / 9980 文字同步 WS
        WebPanelServers.start(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: ShortTVApplication
            private set
    }
}
