package com.shorttv.app.direct

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 抖音访客身份管理：ttwid / msToken / webid / user_unique_id / uifid。
 *
 * 移植自 myDV DouyinDirectApi 的身份链路：
 * - ttwid：POST https://ttwid.bytedance.com/ttwid/union/register/ 注册访客 Cookie
 * - msToken：GET https://mssdk.bytedance.com/web/r/token 获取（失败则本地生成）
 * - webid / uifid：本地随机生成并持久化
 */
class DouyinIdentity private constructor(context: Context) {

    companion object {
        private const val TAG = "DouyinIdentity"
        private const val PREFS = "douyin_identity"

        private const val ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        @Volatile
        private var instance: DouyinIdentity? = null

        fun get(context: Context): DouyinIdentity =
            instance ?: synchronized(this) {
                instance ?: DouyinIdentity(context.applicationContext).also { instance = it }
            }

        private fun randomAlnum(length: Int): String {
            val r = java.util.Random()
            return (1..length).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
        }

        private fun randomHex(length: Int): String {
            val r = java.util.Random()
            val sb = StringBuilder(length)
            while (sb.length < length) sb.append(Integer.toHexString(r.nextInt(16)))
            return sb.substring(0, length)
        }

        private fun randomDigits(length: Int): String {
            val r = java.util.Random()
            return (1..length).joinToString("") { r.nextInt(10).toString() }
        }

        fun md5Upper(input: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.uppercase()
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** mssdk msToken 探测是否已失败（官方端点已下线，失败后不再重试）。 */
    private var msTokenProbeFailed = false

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 抖音 Web UA（与浏览器垫片保持一致）。 */
    val userAgent: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"

    var ttwid: String?
        get() = prefs.getString("ttwid", null)
        set(value) {
            prefs.edit().putString("ttwid", value).apply()
        }

    var msToken: String?
        get() = prefs.getString("msToken", null)
        set(value) {
            prefs.edit().putString("msToken", value).apply()
        }

    /** webid：32 位十六进制，持久化。 */
    val webid: String
        get() = prefs.getString("webid", null) ?: randomHex(32).also {
            prefs.edit().putString("webid", it).apply()
        }

    /** verifyFp / s_v_web_id。 */
    val verifyFp: String
        get() = prefs.getString("verifyFp", null) ?: randomHex(32).also {
            prefs.edit().putString("verifyFp", it).apply()
        }

    /** UIFID（大写十六进制，较长）。 */
    val uifid: String
        get() = prefs.getString("uifid", null) ?: randomHex(32).uppercase().also {
            prefs.edit().putString("uifid", it).apply()
        }

    /** user_unique_id：直播接口使用。 */
    val userUniqueId: String
        get() = prefs.getString("user_unique_id", null) ?: randomHex(19).also {
            prefs.edit().putString("user_unique_id", it).apply()
        }

    /** 移动端 App feed 设备参数（持久化，保持推荐流身份稳定）。 */
    val appDeviceId: String
        get() = prefs.getString("app_device_id", null) ?: randomDigits(19).also {
            prefs.edit().putString("app_device_id", it).apply()
        }

    val appIid: String
        get() = prefs.getString("app_iid", null) ?: randomDigits(19).also {
            prefs.edit().putString("app_iid", it).apply()
        }

    val appOpenudid: String
        get() = prefs.getString("app_openudid", null) ?: randomHex(16).also {
            prefs.edit().putString("app_openudid", it).apply()
        }

    /**
     * 用户自己的抖音 Web 登录 Cookie（可选，来自设置页）。
     * 配置后搜索、作者资料/作品、评论分页等需要登录态的接口可用；
     * 仅使用用户自己账号的会话，不做任何登录绕过。
     */
    @Volatile
    var userCookie: String = ""

    /** 拼接 Cookie 头。 */
    fun cookieHeader(): String {
        if (userCookie.isNotBlank()) return userCookie
        return buildString {
            ttwid?.let { append("ttwid=$it; ") }
            append("__ac_nonce=${randomAlnum(21)}")
        }
    }

    /** 本地生成 msToken（126 位字母数字 + "=="，与 myDV 一致）。 */
    fun generateMsToken(): String = randomAlnum(126) + "=="

    /**
     * 注册 ttwid 访客身份。已注册过则直接返回。
     * 对应 myDV fetchTtwid()。
     */
    suspend fun ensureTtwid(): String? = withContext(Dispatchers.IO) {
        ttwid?.let { return@withContext it }
        try {
            val body = JSONObject()
                .put("region", "cn")
                .put("aid", 1768)
                .put("needFid", false)
                .put("service", "www.ixigua.com")
                .put("migrate_info", JSONObject().put("ticket", "").put("source", "node"))
                .put("cbUrlProtocol", "https")
                .put("union", true)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("https://ttwid.bytedance.com/ttwid/union/register/")
                .header("Content-Type", "application/json")
                .header("Origin", "https://www.douyin.com")
                .header("Referer", "https://www.douyin.com/")
                .header("User-Agent", userAgent)
                .post(body)
                .build()

            http.newCall(request).execute().use { resp ->
                val setCookies = resp.headers.values("Set-Cookie")
                var found: String? = null
                for (sc in setCookies) {
                    val m = Regex("ttwid=([^;]+)").find(sc)
                    if (m != null) {
                        found = m.groupValues[1]
                        break
                    }
                }
                if (found != null) {
                    ttwid = found
                    Log.d(TAG, "ttwid registered: ${found.take(12)}...")
                    return@withContext found
                }
                Log.w(TAG, "ttwid register: no Set-Cookie (code=${resp.code})")
            }
        } catch (e: Exception) {
            Log.w(TAG, "ttwid register failed: ${e.message}")
        }
        null
    }

    /**
     * 从 mssdk 获取 msToken。失败时回退本地生成。
     *
     * 实测（2026-10）：`mssdk.bytedance.com/web/r/token` 已 404（官方下线），
     * 但本地生成的 126 位 msToken 实测可通过抖音风控（status_code=0），
     * 故保留一次探测、失败即静默走本地生成，不再每次冷启动重试。
     */
    suspend fun ensureMsToken(): String = withContext(Dispatchers.IO) {
        msToken?.let { return@withContext it }
        if (msTokenProbeFailed) {
            val local = generateMsToken().also { msToken = it }
            return@withContext local
        }
        try {
            val seed = generateMsToken()
            val url = "https://mssdk.bytedance.com/web/r/token?ms_appid=6383&msToken=" +
                java.net.URLEncoder.encode(seed, "UTF-8")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Referer", "https://www.douyin.com/")
                .get()
                .build()
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val m = Regex("\"msToken\"\\s*:\\s*\"([^\"]+)\"").find(text)
                if (m != null) {
                    val token = m.groupValues[1]
                    msToken = token
                    Log.d(TAG, "msToken fetched: ${token.take(12)}...")
                    return@withContext token
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "msToken fetch unavailable, use local: ${e.message}")
        }
        msTokenProbeFailed = true
        val local = generateMsToken().also { msToken = it }
        Log.d(TAG, "msToken generated locally")
        local
    }

    /** 一次性初始化全部身份（ttwid + msToken）。 */
    suspend fun ensureAll() {
        ensureTtwid()
        ensureMsToken()
    }
}
