package com.shorttv.app.direct

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 抖音访客身份管理：ttwid / msToken / webid / user_unique_id / uifid。
 *
 * 移植自 myDV 2.3.43 的身份链路：
 * - ttwid：POST https://ttwid.bytedance.com/ttwid/union/register/ 注册访客 Cookie
 * - msToken：POST https://mssdk.bytedance.com/web/r/token 携带指纹 strData 真实签发（失败则本地生成）
 * - ttwid / msToken / UIFID / UIFID_TEMP：从抖音响应 Set-Cookie 持续采集（noteSetCookies）
 */
class DouyinIdentity private constructor(private val appContext: Context) {

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
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** WebView 采集的抖音真实 Cookie（对齐 myDV 的 WebView 初始化链路）。 */
    @Volatile
    private var harvestAttempted = false

    @Volatile
    private var harvestedCookie: String =
        prefs.getString("harvested_cookie", "").orEmpty()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .dns(DohDns)
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

    /** 优先使用 WebView 采集的真实指纹（s_v_web_id = verify_ 前缀格式）。 */
    val verifyFp: String
        get() = harvestedValue("s_v_web_id")?.removePrefix("verify_")
            ?.takeIf { it.length >= 16 }
            ?: (prefs.getString("verifyFp", null) ?: randomHex(32).also {
                prefs.edit().putString("verifyFp", it).apply()
            })

    /** 优先使用 WebView 采集的真实 UIFID（其次 UIFID_TEMP，均为抖音真实下发）。 */
    val uifid: String
        get() = harvestedValue("UIFID")?.takeIf { it.length >= 16 }
            ?: harvestedValue("UIFID_TEMP")?.takeIf { it.length >= 16 }
            ?: (prefs.getString("uifid", null) ?: randomHex(32).uppercase().also {
                prefs.edit().putString("uifid", it).apply()
            })

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

    /** 拼接 Cookie 头。优先级：用户 Cookie > WebView 采集的真实 Cookie > 最小匿名 Cookie。 */
    fun cookieHeader(): String {
        if (userCookie.isNotBlank()) return userCookie
        val harvested = harvestedCookie.ifBlank { prefs.getString("harvested_cookie", "").orEmpty() }
        if (harvested.isNotBlank()) {
            return if (harvested.contains("__ac_nonce=")) harvested
            else "$harvested; __ac_nonce=${randomAlnum(21)}"
        }
        return buildString {
            ttwid?.let { append("ttwid=$it; ") }
            append("__ac_nonce=${randomAlnum(21)}")
        }
    }

    /** 从采集/用户 Cookie 提取指定字段。 */
    private fun harvestedValue(name: String): String? {
        val src = harvestedCookie.ifBlank { prefs.getString("harvested_cookie", "").orEmpty() }
        if (src.isBlank()) return null
        return Regex("(?:^|;\\s*)${Regex.escape(name)}=([^;\\s]+)").find(src)
            ?.groupValues?.get(1)
    }

    /**
     * 从抖音 API 响应的 Set-Cookie 持续采集身份字段（对齐 myDV 2.3.43 的 Cookie jar）：
     * - msToken（长度 120/128）：真实签发值，随请求轮换，始终更新
     * - UIFID / UIFID_TEMP：抖音真实下发的设备指纹，用户中心签名链依赖它
     * 采集结果并入 harvested cookie 库并持久化。
     */
    fun noteSetCookies(setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return
        var changed = false
        synchronized(this) {
            val merged = HashMap<String, String>()
            val src = harvestedCookie.ifBlank { prefs.getString("harvested_cookie", "").orEmpty() }
            for (pair in src.split(";")) {
                val i = pair.indexOf('=')
                if (i > 0) merged[pair.substring(0, i).trim()] = pair.substring(i + 1).trim()
            }
            for (sc in setCookieHeaders) {
                val first = sc.substringBefore(';').trim()
                val i = first.indexOf('=')
                if (i <= 0) continue
                val name = first.substring(0, i).trim()
                val value = first.substring(i + 1).trim()
                when (name) {
                    // mssdk 签发值实测 184 位（120/128 是旧认知），放宽为 >=100
                    "msToken" -> if (value.length >= 100) {
                        merged[name] = value
                        msToken = value
                        prefs.edit().putBoolean("msToken_issued", true).apply()
                        changed = true
                    }
                    "UIFID", "UIFID_TEMP" -> if (value.length >= 16 && merged[name] != value) {
                        merged[name] = value
                        changed = true
                        Log.d(TAG, "$name harvested from Set-Cookie (len=${value.length})")
                    }
                    "ttwid" -> if (value.isNotBlank() && merged[name] != value) {
                        merged[name] = value
                        changed = true
                        if (ttwid.isNullOrBlank()) ttwid = value
                    }
                }
            }
            if (changed) {
                val joined = merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
                harvestedCookie = joined
                prefs.edit().putString("harvested_cookie", joined).apply()
            }
        }
    }

    /** 从用户 Cookie 提取指定字段（msToken / sessionid / sessionid_ss 等）。 */
    fun cookieValue(name: String): String? {
        if (userCookie.isBlank()) return null
        return Regex("(?:^|;\\s*)${Regex.escape(name)}=([^;\\s]+)").find(userCookie)
            ?.groupValues?.get(1)
    }

    /**
     * 最优 msToken：优先用户 Cookie 中浏览器真实签发的 msToken
     * （对 Argus 校验严格的接口通过率远高于本地随机值），否则本地生成。
     */
    suspend fun bestMsToken(): String =
        cookieValue("msToken")?.takeIf { it.length >= 32 }
            ?: harvestedValue("msToken")?.takeIf { it.length >= 32 }
            ?: ensureMsToken()

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
     * 从 mssdk 签发真实 msToken（对齐 myDV 2.3.43）。
     *
     * POST `mssdk.bytedance.com/web/r/token?ms_appid=6383&msToken=<seed>`，
     * 请求体携带指纹上报 JSON（dataType=8, magic=538969122, strData=指纹常量），
     * 服务端校验通过后经 Set-Cookie 下发真实 msToken（长度 120 或 128）；
     * 下发失败才回退本地随机（随机值在 Argus 严格接口会被拦截）。
     */
    suspend fun ensureMsToken(): String = withContext(Dispatchers.IO) {
        // 仅复用 mssdk 真实签发的 token（本地随机也是 128 长，须用签发标记区分）
        if (prefs.getBoolean("msToken_issued", false)) {
            msToken?.let { return@withContext it }
        }
        try {
            val seedUrl = "https://mssdk.bytedance.com/web/r/token?ms_appid=6383&msToken=" +
                java.net.URLEncoder.encode(MssdkFingerprint.SEED_MS_TOKEN, "UTF-8")
            val body = "{\"dataType\":8,\"magic\":538969122,\"strData\":\"" +
                MssdkFingerprint.STR_DATA +
                "\",\"tspFromClient\":" + (System.currentTimeMillis() / 1000) +
                ",\"ulr\":0,\"version\":1}"
            val request = Request.Builder()
                .url(seedUrl)
                .header("User-Agent", MSSDK_UA)
                .header("Content-Type", "text/plain;charset=UTF-8")
                .post(body.toRequestBody("text/plain;charset=UTF-8".toMediaTypeOrNull()))
                .build()
            http.newCall(request).execute().use { resp ->
                // mssdk 签发的真实 msToken：实测（2026-11）为 184 位（含 == 结尾），
                // 不能按 120/128 校验（会拒掉全部真实签发，回退本地随机）。
                fun accept(token: String): Boolean {
                    if (token.length < 100) return false
                    msToken = token
                    prefs.edit().putBoolean("msToken_issued", true).apply()
                    DirectDiagnostics.recordMsToken("mssdk 签发（长度 ${token.length}）", token)
                    Log.d(TAG, "msToken issued by mssdk (len=${token.length})")
                    return true
                }
                resp.header("x-ms-token")?.trim()?.takeIf { it.isNotEmpty() }?.let { if (accept(it)) return@withContext it }
                for (sc in resp.headers("Set-Cookie")) {
                    val m = Regex("(?:^|;\\s*)msToken=([^;\\s]+)").find(sc)
                    val token = m?.groupValues?.get(1)?.trim()
                    if (!token.isNullOrEmpty() && accept(token)) return@withContext token
                }
                val text = resp.body?.string().orEmpty()
                Regex("\"msToken\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)?.let { token ->
                    if (token.length >= 100 && accept(token)) return@withContext token
                }
                Log.w(TAG, "mssdk token response has no msToken (http=${resp.code})")
                DirectDiagnostics.recordMsToken("mssdk 响应无签发（http=${resp.code}）", null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "mssdk token fetch failed: ${e.message}")
            DirectDiagnostics.recordMsToken("mssdk 不可达：${e.message?.take(60)}", null)
        }
        // 回退 1：WebView 采集 / 用户 Cookie 中的真实 msToken（电视端 mssdk
        // 域名可能不通，但 douyin.com WebView 采集成功时 Cookie 里已有真 token）
        if (applyHarvestedMsToken()) {
            msToken?.let { return@withContext it }
        }
        val local = generateMsToken().also {
            msToken = it
            prefs.edit().putBoolean("msToken_issued", false).apply()
        }
        DirectDiagnostics.recordMsToken("本地随机（弱身份）", local)
        Log.d(TAG, "msToken generated locally (fallback)")
        local
    }

    private val MSSDK_UA: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"

    /** 一次性初始化全部身份（ttwid + msToken + 必要时 WebView 兜底）。 */
    suspend fun ensureAll() {
        ensureTtwid()
        ensureMsToken()
        // myDV 2.3.43 无 WebView 链路：mssdk 签发成功或已有真实 Cookie 时不动 WebView。
        // 电视端 mssdk 域名可能不通（DNS/TLS 受限），此时依赖 WebView 采集兜底。
        val issued = prefs.getBoolean("msToken_issued", false)
        if (!issued && userCookie.isBlank() &&
            !harvestedCookie.contains("msToken=")
        ) {
            if (harvestedCookie.isNotBlank()) {
                // 旧版采集的 Cookie 缺 msToken → 清掉重新采集
                harvestedCookie = ""
                prefs.edit().remove("harvested_cookie").apply()
            }
            harvestAttempted = false
            ensureWebCookies()
            applyHarvestedMsToken()
        }
        DirectDiagnostics.recordIdentity(
            ttwid = !ttwid.isNullOrBlank(),
            uifid = uifid.length >= 16,
            source = when {
                userCookie.isNotBlank() -> "用户扫码 Cookie"
                harvestedCookie.isNotBlank() -> "WebView/Set-Cookie 采集"
                prefs.getBoolean("msToken_issued", false) -> "mssdk 签发"
                else -> "本地生成（弱身份）"
            },
        )
    }

    /**
     * 从已采集/用户 Cookie 中提取真实 msToken。
     * WebView 访问 douyin.com 成功时 Cookie 里通常带有 douyin 下发的真实 msToken。
     * @return 是否成功应用
     */
    private fun applyHarvestedMsToken(): Boolean {
        for (source in listOf(userCookie, harvestedCookie)) {
            if (source.isBlank()) continue
            Regex("(?:^|;\\s*)msToken=([^;\\s]+)").find(source)?.groupValues?.get(1)?.let { token ->
                if (token.length >= 100) {
                    msToken = token
                    prefs.edit().putBoolean("msToken_issued", false).apply()
                    DirectDiagnostics.recordMsToken("Cookie 采集（真实）", token)
                    Log.d(TAG, "msToken from harvested cookie (len=${token.length})")
                    return true
                }
            }
        }
        return false
    }

    /**
     * 用 WebView 访问 douyin.com 采集真实 Cookie（ttwid / msToken / s_v_web_id /
     * UIFID 等）。这是 myDV 的身份初始化链路：严格校验的接口（作者作品等）
     * 依赖真实签发的 Cookie，本地随机身份无法通过。
     * 采集结果持久化，之后启动无需重复加载页面。
     */
    private suspend fun ensureWebCookies() = withContext(Dispatchers.IO) {
        if (userCookie.isNotBlank()) return@withContext
        if (!harvestedCookie.isBlank()) return@withContext
        if (harvestAttempted) return@withContext
        harvestAttempted = true
        val cookie = harvestCookiesBlocking()
        if (cookie.isNotBlank()) {
            harvestedCookie = cookie
            prefs.edit().putString("harvested_cookie", cookie).apply()
            // 同步 ttwid，供直播等其它链路使用
            Regex("ttwid=([^;\\s]+)").find(cookie)?.groupValues?.get(1)?.let {
                if (ttwid.isNullOrBlank()) ttwid = it
            }
            Log.d(TAG, "webview cookies harvested (${cookie.length} chars)")
        }
    }

    private fun harvestCookiesBlocking(): String {
        val latch = CountDownLatch(1)
        var cookie = ""
        val main = Handler(Looper.getMainLooper())
        main.post {
            try {
                CookieManager.getInstance().setAcceptCookie(true)
                // 此前已采集过真实 Cookie 时直接复用，不再加载页面
                val existing = CookieManager.getInstance()
                    .getCookie("https://www.douyin.com/").orEmpty()
                if (existing.contains("ttwid=") && existing.contains("msToken=")) {
                    cookie = existing
                    latch.countDown()
                    return@post
                }
                val webView = WebView(appContext)
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        cookie = CookieManager.getInstance()
                            .getCookie("https://www.douyin.com/").orEmpty()
                        latch.countDown()
                        view.postDelayed({ view.destroy() }, 1500)
                    }
                }
                webView.loadUrl("https://www.douyin.com/?recommend=1")
                main.postDelayed({
                    if (cookie.isBlank()) {
                        cookie = CookieManager.getInstance()
                            .getCookie("https://www.douyin.com/").orEmpty()
                    }
                    latch.countDown()
                }, 7000)
            } catch (e: Throwable) {
                Log.w(TAG, "webview harvest failed: ${e.message}")
                latch.countDown()
            }
        }
        latch.await(10, TimeUnit.SECONDS)
        return cookie
    }
}
