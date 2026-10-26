package com.shorttv.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shorttv_settings")

/** 数据源模式（对齐 myDV：默认直连抖音官方 API，零配置开箱即用） */
enum class DataSource {
    /** 直连抖音官方 API（端上 a_bogus 签名，无需服务端） */
    DIRECT,

    /** 自建 / 兼容服务端（ShortTV 同构 API） */
    SERVER,

    /** 内置演示数据（离线可用） */
    DEMO
}

/** 应用设置（对齐 myDV 的设置项：数据源 / 服务端 / 弹幕 / 播放 / 协议） */
data class AppSettings(
    val licenseAgreed: Boolean = false,
    val dataSource: DataSource = DataSource.DIRECT,
    val apiBaseUrl: String = "",
    val danmakuEnabled: Boolean = true,
    val danmakuSize: Float = 16f,
    val danmakuAlpha: Float = 0.85f,
    val danmakuSpeed: Float = 1f,
    val autoPlayNext: Boolean = true,
    val defaultQualityH265: Boolean = false,
    val pipOnLeave: Boolean = true,
    val historyResume: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val LICENSE = booleanPreferencesKey("license_agreed")
        val DATA_SOURCE = stringPreferencesKey("data_source")
        val API_BASE = stringPreferencesKey("api_base_url")
        val DANMAKU_ON = booleanPreferencesKey("danmaku_enabled")
        val DANMAKU_SIZE = floatPreferencesKey("danmaku_size")
        val DANMAKU_ALPHA = floatPreferencesKey("danmaku_alpha")
        val DANMAKU_SPEED = floatPreferencesKey("danmaku_speed")
        val AUTOPLAY = booleanPreferencesKey("autoplay_next")
        val H265 = booleanPreferencesKey("default_h265")
        val PIP = booleanPreferencesKey("pip_on_leave")
        val RESUME = booleanPreferencesKey("history_resume")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            licenseAgreed = prefs[Keys.LICENSE] ?: false,
            dataSource = prefs[Keys.DATA_SOURCE]
                ?.let { name -> DataSource.entries.firstOrNull { it.name == name } }
                ?: DataSource.DIRECT,
            apiBaseUrl = prefs[Keys.API_BASE] ?: "",
            danmakuEnabled = prefs[Keys.DANMAKU_ON] ?: true,
            danmakuSize = prefs[Keys.DANMAKU_SIZE] ?: 16f,
            danmakuAlpha = prefs[Keys.DANMAKU_ALPHA] ?: 0.85f,
            danmakuSpeed = prefs[Keys.DANMAKU_SPEED] ?: 1f,
            autoPlayNext = prefs[Keys.AUTOPLAY] ?: true,
            defaultQualityH265 = prefs[Keys.H265] ?: false,
            pipOnLeave = prefs[Keys.PIP] ?: true,
            historyResume = prefs[Keys.RESUME] ?: true
        )
    }

    suspend fun setLicenseAgreed(value: Boolean) {
        context.dataStore.edit { it[Keys.LICENSE] = value }
    }

    suspend fun setDataSource(value: DataSource) {
        context.dataStore.edit { it[Keys.DATA_SOURCE] = value.name }
    }

    suspend fun setApiBaseUrl(value: String) {
        context.dataStore.edit { it[Keys.API_BASE] = value }
    }

    suspend fun setDanmakuEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.DANMAKU_ON] = value }
    }

    suspend fun setDanmakuSize(value: Float) {
        context.dataStore.edit { it[Keys.DANMAKU_SIZE] = value }
    }

    suspend fun setDanmakuAlpha(value: Float) {
        context.dataStore.edit { it[Keys.DANMAKU_ALPHA] = value }
    }

    suspend fun setDanmakuSpeed(value: Float) {
        context.dataStore.edit { it[Keys.DANMAKU_SPEED] = value }
    }

    suspend fun setAutoPlayNext(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTOPLAY] = value }
    }

    suspend fun setDefaultH265(value: Boolean) {
        context.dataStore.edit { it[Keys.H265] = value }
    }

    suspend fun setPipOnLeave(value: Boolean) {
        context.dataStore.edit { it[Keys.PIP] = value }
    }

    suspend fun setHistoryResume(value: Boolean) {
        context.dataStore.edit { it[Keys.RESUME] = value }
    }
}

/** 播放历史（本地 SharedPreferences 轻量实现） */
class HistoryRepository(context: Context) {

    private val prefs = context.getSharedPreferences("shorttv_history", Context.MODE_PRIVATE)

    fun saveProgress(awemeId: String, positionMs: Long, durationMs: Long) {
        prefs.edit()
            .putLong("pos_$awemeId", positionMs)
            .putLong("dur_$awemeId", durationMs)
            .putLong("ts_$awemeId", System.currentTimeMillis())
            .apply()
    }

    fun progress(awemeId: String): Long = prefs.getLong("pos_$awemeId", 0L)

    fun clearProgress(awemeId: String) {
        prefs.edit().remove("pos_$awemeId").remove("dur_$awemeId").remove("ts_$awemeId").apply()
    }

    /** 清除全部播放进度（保留收藏） */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /** 收藏（本地） */
    fun getCollects(): Set<String> =
        prefs.getStringSet("collects", emptySet()) ?: emptySet()

    fun toggleCollect(awemeId: String): Boolean {
        val current = getCollects().toMutableSet()
        val added = !current.contains(awemeId)
        if (added) current.add(awemeId) else current.remove(awemeId)
        prefs.edit().putStringSet("collects", current).apply()
        return added
    }

    /** 点赞（本地） */
    fun isDigged(awemeId: String): Boolean = prefs.getBoolean("digg_$awemeId", false)

    fun toggleDigg(awemeId: String): Boolean {
        val next = !isDigged(awemeId)
        prefs.edit().putBoolean("digg_$awemeId", next).apply()
        return next
    }
}
