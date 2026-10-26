package com.shorttv.app.direct

import android.content.Context
import android.util.Log
import com.whl.quickjs.wrapper.QuickJSContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 抖音 a_bogus / __ac_signature 签名引擎。
 *
 * 移植自 myDV（com.github.mytv.dv）的 ABogusSigner：
 * - QuickJS 单线程执行 assets/browser_shim.js + abogus_dy.js + sign.js
 * - getABogus(query, data, uri, userAgent) 计算 a_bogus
 * - getMSSDKSignature(msStub, userAgent) 计算直播 __ac_signature
 *
 * 重要实测结论（2026-10，与 myDV 原版 so 逐字节比对后确认）：
 * myDV 内置的 quickjs-android-wrapper 编译时**未启用 WASM**（so 内无
 * WebAssembly 符号），而 sign.js 的 byted_acrawler 依赖 WASM——因此
 * sign.js 在 myDV 自己的 QuickJS 里同样 evaluate 失败，myDV 的
 * __ac_signature 链路实际不可用，其直连完全依赖 abogus_dy.js 的纯 JS
 * a_bogus。本实现如实复刻该行为：sign.js 加载失败被隔离，不影响 a_bogus。
 *
 * 注意：QuickJSContext 非线程安全，所有 JS 调用都固定在同一个线程。
 */
class AbogusSigner private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "AbogusSigner"
        private const val INIT_TIMEOUT_MS = 20_000L
        private const val SIGN_TIMEOUT_MS = 15_000L

        @Volatile
        private var instance: AbogusSigner? = null

        fun get(context: Context): AbogusSigner =
            instance ?: synchronized(this) {
                instance ?: AbogusSigner(context.applicationContext).also { instance = it }
            }
    }

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "quickjs-abogus").apply { isDaemon = true }
    }

    @Volatile
    private var jsContext: QuickJSContext? = null

    @Volatile
    var ready: Boolean = false
        private set

    /**
     * __ac_signature（直播签名）是否可用。
     * QuickJS 无 WASM 时 sign.js 必然 evaluate 失败，此标记为 false，
     * 此时直播 enter 不带 __ac_signature（与 myDV 实际行为一致）。
     */
    @Volatile
    var acSignerReady: Boolean = false
        private set

    /** 初始化（幂等）。失败返回 false，调用方应回退其他数据源。 */
    suspend fun ensureInit(): Boolean {
        if (ready) return true
        return withContext(Dispatchers.IO) {
            if (ready) return@withContext true
            try {
                val future = executor.submit<Boolean> { initBlocking() }
                future.get(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (e: Throwable) {
                Log.e(TAG, "init failed", e)
                false
            }
        }
    }

    private fun initBlocking(): Boolean {
        if (ready) return true
        return try {
            val ctx = QuickJSContext.create()
            jsContext = ctx

            // 1) 浏览器环境垫片（window/document/navigator/localStorage 等）
            val shim = readAsset("browser_shim.js")
            ctx.evaluate(shim, "browser_shim.js")
            Log.d(TAG, "browser shim loaded (${shim.length} chars)")

            // 2) abogus_dy.js：bdms SDK + get_ab/getABogus/initABogus
            val abogus = readAsset("abogus_dy.js")
            ctx.evaluate(abogus, "abogus_dy.js")
            Log.d(TAG, "abogus_dy.js loaded (${abogus.length} chars)")

            val bdmsOk = ctx.evaluate(
                "(function(){ return typeof window.bdms !== 'undefined' && typeof window.bdms.init === 'function'; })()",
            ) as? Boolean ?: false
            if (!bdmsOk) {
                Log.e(TAG, "window.bdms.init not available")
                return false
            }

            // 3) initABogus()（bdms SDK 自动初始化，此处做兼容调用）
            val initOk = ctx.evaluate("initABogus()") as? Boolean ?: false
            Log.d(TAG, "initABogus() -> $initOk")
            if (!initOk) {
                Log.e(TAG, "initABogus() returned false - bdms SDK _v not available")
                return false
            }

            val fnOk = ctx.evaluate(
                "(function(){ return typeof getABogus === 'function' && typeof get_ab === 'function'; })()",
            ) as? Boolean ?: false
            if (!fnOk) {
                Log.e(TAG, "getABogus / get_ab not available after init")
                return false
            }

            // 4) sign.js：byted_acrawler（WASM 签名器），供直播 __ac_signature 使用。
            // 与 myDV 一致：加载前把源码中的 `crawler = _0x5c2014` 补丁为
            // `crawler = _0x5c2014; globalThis.__ac = crawler`。
            // 注意：本 QuickJS 构建未启用 WASM，sign.js 大概率 evaluate 失败——
            // 失败仅意味着直播 __ac_signature 不可用，a_bogus 链路不受影响
            // （myDV 原版 so 同样无 WASM，其直连也只依赖 a_bogus）。
            acSignerReady = loadAcSigner(ctx)
            Log.d(TAG, "ac signer (sign.js) ready=$acSignerReady")

            ready = true
            Log.d(TAG, "QuickJS a_bogus runtime initialised successfully (a_bogus only)")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "initBlocking failed", e)
            ready = false
            acSignerReady = false
            false
        }
    }

    /**
     * 加载并预热 sign.js。失败（如 QuickJS 无 WASM 导致 acrawler 无法实例化）
     * 时返回 false 而不抛出，保证 a_bogus 主链路可用。
     */
    private fun loadAcSigner(ctx: QuickJSContext): Boolean {
        return try {
            val signJs = readAsset("sign.js")
                .replace("crawler = _0x5c2014", "crawler = _0x5c2014; globalThis.__ac = crawler")
            ctx.evaluate(signJs, "sign.js")
            Log.d(TAG, "sign.js loaded (${signJs.length} chars)")
            // 预热：触发一次完整 acrawler 初始化（WASM 不可用时静默跳过）。
            runCatching {
                ctx.evaluate(
                    "try { getMSSDKSignature('', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36') } catch(e) {}",
                )
            }.onFailure { Log.w(TAG, "acrawler warmup skipped: ${it.message}") }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "sign.js unavailable (live __ac_signature disabled): ${e.message}")
            false
        }
    }

    /**
     * 计算 a_bogus 签名。
     *
     * @param query 待签名的查询串（不含 a_bogus 本身）
     * @param userAgent 请求 UA
     * @return a_bogus 值（形如 `&a_bogus=...` 之前的纯值），失败返回 null
     */
    suspend fun signABogus(query: String, userAgent: String): String? {
        if (!ensureInit()) return null
        return withContext(Dispatchers.IO) {
            try {
                val future = executor.submit<String?> {
                    val ctx = jsContext ?: return@submit null
                    val js = buildString {
                        append("(function(){ try { return getABogus('")
                        append(escapeJs(query))
                        append("', '', '', '")
                        append(escapeJs(userAgent))
                        append("'); } catch(e) { return JSON.stringify({ok:false,error:String(e)}); } })()")
                    }
                    val raw = ctx.evaluate(js) as? String ?: return@submit null
                    decodeResult(raw)
                }
                future.get(SIGN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (e: Throwable) {
                Log.e(TAG, "signABogus failed", e)
                null
            }
        }
    }

    /**
     * 计算直播接口 __ac_signature。
     *
     * 与 myDV 的关键差异（经 Node 侧逐条验证）：
     * myDV 走 `globalThis.__ac.sign('', ua)`，但 sign.js 里 crawler 并未挂载
     * .sign/.init 方法，该路径恒失败；真正可用的是 getMSSDKSignature(msStub, ua)，
     * 其内部通过 WASM 签名器计算 `crawler({"X-MS-STUB": msStub})["X-Bogus"]`，
     * 直接返回原始签名字符串（非 JSON）。
     *
     * 本 QuickJS 构建未启用 WASM 时（acSignerReady=false）直接返回 null，
     * 与 myDV 原版环境的实际行为一致。
     *
     * @param msStub 请求体 MD5 十六进制串（X-MS-STUB）
     */
    suspend fun signAcSignature(msStub: String, userAgent: String): String? {
        if (!ensureInit()) return null
        if (!acSignerReady) {
            Log.d(TAG, "ac signer unavailable, skip __ac_signature")
            return null
        }
        return withContext(Dispatchers.IO) {
            try {
                val future = executor.submit<String?> {
                    val ctx = jsContext ?: return@submit null
                    val js = buildString {
                        append("(function(){ try { return getMSSDKSignature('")
                        append(escapeJs(msStub))
                        append("', '")
                        append(escapeJs(userAgent))
                        append("'); } catch(e) { return JSON.stringify({ok:false,error:String(e)}); } })()")
                    }
                    val raw = ctx.evaluate(js) as? String ?: return@submit null
                    decodeSignature(raw)
                }
                future.get(SIGN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (e: Throwable) {
                Log.e(TAG, "signAcSignature failed", e)
                null
            }
        }
    }

    /** getMSSDKSignature 直接返回原始签名字符串；仅错误时返回 {"ok":false,...} JSON。 */
    private fun decodeSignature(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{\"ok\":false")) {
            Log.w(TAG, "sign failed: ${trimmed.take(200)}")
            return null
        }
        return unescapeJson(trimmed).ifBlank { null }
    }

    private fun decodeResult(raw: String): String? {
        // getABogus 返回 JSON {"ok":true,"value":"..."}，value 内可能含 \uXXXX 转义
        val okIdx = raw.indexOf("\"ok\":true")
        if (okIdx < 0) {
            Log.w(TAG, "sign failed: ${raw.take(200)}")
            return null
        }
        val vIdx = raw.indexOf("\"value\":")
        if (vIdx < 0) return null
        var value = raw.substring(vIdx + 8).trim()
        if (value.startsWith("\"")) value = value.substring(1)
        val end = value.lastIndexOf("\"")
        if (end > 0) value = value.substring(0, end)
        return unescapeJson(value).ifBlank { null }
    }

    /** 还原 JSON/JS 转义（对应 myDV s.e() 的后处理）。 */
    private fun unescapeJson(s: String): String = s
        .replace("\\u003d", "=")
        .replace("\\u002b", "+")
        .replace("\\u002f", "/")
        .replace("\\u0026", "&")
        .replace("\\u003f", "?")
        .replace("\\u0025", "%")
        .replace("\\u0023", "#")
        .replace("\\/", "/")
        .replace("\\n", "")
        .replace("\\r", "")
        .replace("\\\"", "\"")

    /** 供拼接进 JS 字面量的字符串转义（对应 myDV s.b()）。 */
    private fun escapeJs(s: String): String = s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    /**
     * 读取 JS 资产；剥除 CR 与 myDV 一致（r54.y(src, "\r", "", false)）：
     * 仓库内部分文件为 CRLF 行尾，a_bogus 的 bytecode 常量池对空白敏感，
     * 不剥除会导致同样一份代码在 Node 可用、QuickJS 解析异常。
     */
    private fun readAsset(name: String): String =
        appContext.assets.open(name).use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.readText().replace("\r", "")
            }
        }

    fun destroy() {
        ready = false
        val ctx = jsContext
        jsContext = null
        if (ctx != null) {
            runCatching { executor.submit { ctx.destroy() }.get(3, TimeUnit.SECONDS) }
                .onFailure { Log.w(TAG, "destroy failed: ${it.message}") }
        }
        executor.shutdown()
    }
}
