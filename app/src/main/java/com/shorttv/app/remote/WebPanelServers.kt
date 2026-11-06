package com.shorttv.app.remote

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Web 面板服务器（对齐 myDV 2.3.43「幕光方案」）：
 *
 *  - 9979 HTTP：搜索输入页 search.html（手机扫码打开）
 *  - 9980 WS  ：搜索文字实时同步（type=input / search / tv_text）
 *  - 9978 HTTP：登录信息推送页 submit.html + POST /api/submit
 *
 * 纯 Kotlin/JDK 实现（线程 + ServerSocket），不引入额外服务端依赖。
 */
object WebPanelServers {

    private const val TAG = "ShortTVWebPanel"
    const val PORT_COOKIE = 9978
    const val PORT_SEARCH = 9979
    const val PORT_WS = 9980
    private const val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    private val started = AtomicBoolean(false)
    private lateinit var appContext: android.content.Context

    // ---------- 对外入口 ----------

    fun start(context: android.content.Context) {
        if (!started.compareAndSet(false, true)) return
        appContext = context.applicationContext
        RemoteInputHub.setServerState(RemoteInputHub.PanelState.STARTING)
        Thread({
            var ok = 0
            try {
                CookiePanelServer().start()
                ok++
            } catch (e: Exception) {
                Log.e(TAG, "Cookie 面板服务器($PORT_COOKIE)启动失败", e)
            }
            try {
                SearchPanelServer().start()
                ok++
            } catch (e: Exception) {
                Log.e(TAG, "搜索面板服务器($PORT_SEARCH)启动失败", e)
            }
            try {
                WsInputServer().start()
                ok++
            } catch (e: Exception) {
                Log.e(TAG, "搜索输入 WS 服务器($PORT_WS)启动失败", e)
            }
            RemoteInputHub.setServerState(
                if (ok > 0) RemoteInputHub.PanelState.RUNNING else RemoteInputHub.PanelState.ERROR
            )
            Log.i(TAG, "Web 面板服务器已启动 $ok/3 —— http://" + localIpv4() + ":" + PORT_SEARCH + "/")
        }, "shorttv-webpanel").start()
    }

    /** 局域网 IPv4（TV 常为以太网，遍历网卡取 site-local 地址） */
    fun localIpv4(): String = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.asSequence() }
            .filter { it.isSiteLocalAddress && !it.isLoopbackAddress }
            .map { it.hostAddress }
            .firstOrNull { it != null && !it.contains(':') }
    }.getOrNull() ?: ""

    /** 搜索输入页地址（二维码内容） */
    fun searchPanelUrl(): String = "http://" + localIpv4() + ":" + PORT_SEARCH + "/"

    /** 登录信息推送页地址（二维码内容） */
    fun cookiePanelUrl(): String = "http://" + localIpv4() + ":" + PORT_COOKIE + "/submit"

    // ---------- 二维码 ----------

    /** 生成二维码位图（白底黑码，M 纠错，留 1 模块白边） */
    fun qrBitmap(text: String, size: Int = 512): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (y in 0 until size) {
            for (x in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    }.getOrNull()

    // ---------- 面板渲染 ----------

    private fun htmlEscape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    /** 主题 token（ShortTV 深色主题 → web 面板 CSS 变量） */
    private val THEME_CSS = """
        :root{color-scheme:dark;
          --primary:#E5394F;--on-primary:#FFFFFF;
          --primary-container:#59131F;--on-primary-container:#FFD9DC;
          --secondary-container:#2A2A34;--on-secondary-container:#E0E0EA;
          --tertiary-container:#2A2A34;--on-tertiary-container:#E0E0EA;
          --error:#FFB4AB;--on-error:#690005;
          --error-container:#5C1A22;--on-error-container:#FFD9DC;
          --surface:#0E0E14;--on-surface:#F2F2F5;--on-surface-variant:#A9A9B8;
          --sc-lowest:#0A0A0F;--sc-low:#16161D;--sc:#1B1B22;--sc-high:#24242E;--sc-highest:#2E2E3A;
          --outline:#484856;--outline-variant:#33333F;
          --inverse-surface:#F2F2F5;--inverse-on-surface:#1B1B22;--inverse-primary:#E5394F;
          --surface-dim:#0E0E14;--surface-bright:#2E2E3A;}
    """.trimIndent().replace("\n", "")

    /** 读取 assets/web/{file} 并做 {{KEY}} 模板替换 */
    private fun renderPanel(file: String, vars: Map<String, String> = emptyMap()): String {
        val raw = appContext.assets.open("web/$file").bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val merged = HashMap(vars)
        merged["THEME_CSS"] = THEME_CSS
        merged["PANEL_HEAD"] = appContext.assets.open("web/panel-head.html")
            .bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        merged["DEVICE_NAME"] = "ShortTV · " + android.os.Build.MODEL
        val escaped = merged.mapValues { (_, v) ->
            // PANEL_HEAD / THEME_CSS 为内部注入的样式与脚本，不做 HTML 转义
            if (v === THEME_CSS || v.startsWith("<style>") || v.startsWith("<script>")) v else htmlEscape(v)
        }
        var out = raw
        for ((k, v) in escaped) out = out.replace("{{" + k + "}}", v)
        return out
    }

    // ---------- 极简 HTTP ----------

    private class HttpRequest(
        val method: String,
        val path: String,
        val body: String
    )

    /**
     * 字节级读取请求头直到空行，返回头块文本。
     * 不能用 BufferedReader：它会预读缓冲，把随后的 POST body / WS 帧吞进缓冲区。
     */
    private fun readHeaderBlock(input: java.io.InputStream): String? {
        val buf = StringBuilder()
        var prev = 0
        var cur: Int
        while (input.read().also { cur = it } >= 0) {
            buf.append(cur.toChar())
            if (prev == '\r'.code && cur == '\n'.code) {
                // 检查是否刚读完一个空行（\r\n\r\n）
                val text = buf.toString()
                if (text.endsWith("\r\n\r\n")) return text.trimEnd('\r', '\n')
                if (text.endsWith("\n\n")) return text.trimEnd('\n')
            }
            prev = cur
            if (buf.length > 32 * 1024) return null
        }
        return null
    }

    private fun readFullyStatic(input: java.io.InputStream, buf: ByteArray): Boolean {
        var read = 0
        while (read < buf.size) {
            val n = input.read(buf, read, buf.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    private fun serveHttp(server: ServerSocket, handler: (HttpRequest) -> HttpResponse) {
        val accept = Thread({
            while (true) {
                val socket = try {
                    server.accept()
                } catch (e: Exception) {
                    break
                }
                Thread({
                    try {
                        socket.soTimeout = 10_000
                        val input = socket.getInputStream()
                        val head = readHeaderBlock(input) ?: return@Thread
                        val lines = head.split("\r\n", "\n")
                        val parts = (lines.firstOrNull() ?: "").split(" ")
                        if (parts.size < 2) return@Thread
                        val method = parts[0].uppercase(Locale.US)
                        var contentLength = 0
                        for (h in lines.drop(1)) {
                            if (h.startsWith("Content-Length:", true)) {
                                contentLength = h.substringAfter(':').trim().toIntOrNull() ?: 0
                            }
                        }
                        val body = if (contentLength > 0) {
                            val buf = ByteArray(contentLength.coerceAtMost(10 * 1024 * 1024))
                            if (!readFullyStatic(input, buf)) ""
                            else String(buf, StandardCharsets.UTF_8)
                        } else ""
                        val resp = handler(HttpRequest(method, parts[1], body))
                        val out = BufferedOutputStream(socket.getOutputStream())
                        val respHead = buildString {
                            append("HTTP/1.1 ").append(resp.status).append("\r\n")
                            append("Content-Type: ").append(resp.contentType).append("\r\n")
                            append("Content-Length: ").append(resp.body.toByteArray(StandardCharsets.UTF_8).size).append("\r\n")
                            append("Cache-Control: no-store\r\n")
                            append("Connection: close\r\n\r\n")
                        }
                        out.write(respHead.toByteArray(StandardCharsets.UTF_8))
                        out.write(resp.body.toByteArray(StandardCharsets.UTF_8))
                        out.flush()
                    } catch (e: Exception) {
                        Log.w(TAG, "HTTP 处理异常", e)
                    } finally {
                        runCatching { socket.close() }
                    }
                }, "shorttv-http").start()
            }
        }, "shorttv-http-accept")
        accept.isDaemon = true
        accept.start()
    }

    private class HttpResponse(val status: String, val contentType: String, val body: String)

    private fun textResponse(body: String) = HttpResponse("200 OK", "text/plain; charset=utf-8", body)

    private fun jsonResponse(body: String) = HttpResponse("200 OK", "application/json; charset=utf-8", body)

    /** 搜索输入页：GET / 或 /search → search.html */
    private class SearchPanelServer {
        fun start() {
            val server = ServerSocket(PORT_SEARCH)
            server.reuseAddress = true
            serveHttp(server) { req ->
                when {
                    req.method == "GET" && (req.path == "/" || req.path == "/search") ->
                        HttpResponse(
                            "200 OK", "text/html; charset=utf-8",
                            renderPanel("search.html")
                        )
                    else -> textResponse("404")
                }
            }
            Log.i(TAG, "搜索输入服务器启动，HTTP 端口: $PORT_SEARCH")
        }
    }

    /** 登录信息推送页：GET /submit → submit.html；POST /api/submit → JSON {cookie,...} */
    private class CookiePanelServer {
        fun start() {
            val server = ServerSocket(PORT_COOKIE)
            server.reuseAddress = true
            serveHttp(server) { req ->
                when {
                    req.method == "GET" && (req.path == "/" || req.path == "/submit") ->
                        HttpResponse(
                            "200 OK", "text/html; charset=utf-8",
                            renderPanel("submit.html")
                        )
                    req.method == "POST" && req.path == "/api/submit" -> {
                        try {
                            val obj = Json.parseToJsonElement(req.body).jsonObject
                            val cookie = obj["cookie"]?.jsonPrimitive?.content?.trim().orEmpty()
                            val security = obj["security_payload"]?.jsonPrimitive?.content?.trim().orEmpty()
                            val privateKey = obj["private_key"]?.jsonPrimitive?.content?.trim().orEmpty()
                            if (cookie.isBlank() && security.isBlank() && privateKey.isBlank()) {
                                jsonResponse("{\"success\":false,\"message\":\"请至少填写 Cookie 或互动安全凭据\"}")
                            } else {
                                RemoteInputHub.onCookiePushed(cookie.ifBlank { security })
                                Log.i(TAG, "收到登录信息推送：cookie=${cookie.length} 字符, security=${security.length} 字符")
                                jsonResponse("{\"success\":true,\"message\":\"已推送到电视\"}")
                            }
                        } catch (e: Exception) {
                            jsonResponse("{\"success\":false,\"message\":\"请求体解析失败\"}")
                        }
                    }
                    else -> textResponse("404")
                }
            }
            Log.i(TAG, "Cookie 服务器启动: " + cookiePanelUrl())
        }
    }

    // ---------- 极简 WebSocket（RFC6455 文本帧） ----------

    private class WsClient(val socket: Socket) {
        val out = BufferedOutputStream(socket.getOutputStream())
        val lock = Any()
        @Volatile var open = true
    }

    private class WsInputServer {
        private val clients = CopyOnWriteArraySet<WsClient>()

        /** 向所有已连接的手机端广播文本帧 */
        fun broadcastText(payload: ByteArray) {
            for (client in clients) sendFrame(client, 0x1, payload)
        }

        fun start() {
            WebPanelServers.wsInstance = this
            val server = ServerSocket(PORT_WS)
            server.reuseAddress = true
            val accept = Thread({
                while (true) {
                    val socket = try {
                        server.accept()
                    } catch (_: Exception) {
                        return@Thread
                    }
                    Thread({
                        handle(socket)
                    }, "shorttv-ws").start()
                }
            }, "shorttv-ws-accept")
            accept.isDaemon = true
            accept.start()
            Log.i(TAG, "WsServer started on port $PORT_WS")
        }

        private fun handle(socket: Socket) {
            try {
                socket.soTimeout = 0
                val input = socket.getInputStream()
                val head = readHeaderBlock(input) ?: return
                val lines = head.split("\r\n", "\n")
                val requestLine = lines.firstOrNull() ?: return
                var wsKey: String? = null
                for (h in lines.drop(1)) {
                    if (h.startsWith("Sec-WebSocket-Key:", true)) wsKey = h.substringAfter(':').trim()
                }
                val key = wsKey
                if (key == null || !requestLine.contains("Upgrade", true)) {
                    socket.close(); return
                }
                val acceptB64 = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1").digest((key + WS_GUID).toByteArray(StandardCharsets.US_ASCII))
                )
                val handshake = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: $acceptB64\r\n\r\n"
                val client = WsClient(socket)
                client.out.write(handshake.toByteArray(StandardCharsets.US_ASCII))
                client.out.flush()
                clients.add(client)
                RemoteInputHub.setClientCount(clients.size)
                // 连接建立后立刻回显电视当前搜索框文字（与 myDV 行为一致）
                sendTvText(client, RemoteInputHub.tvText.value)
                readFrames(client)
            } catch (e: Exception) {
                Log.w(TAG, "WS 连接异常", e)
            } finally {
                runCatching { socket.close() }
            }
        }

        private fun readFrames(client: WsClient) {
            val input = client.socket.getInputStream()
            val head = ByteArray(2)
            val lenBuf = ByteArray(8)
            val mask = ByteArray(4)
            val fragmented = StringBuilder()
            try {
                while (client.open) {
                    if (!readFully(input, head, 2)) break
                    val fin = head[0].toInt() and 0x80 != 0
                    val opcode = head[0].toInt() and 0x0F
                    val masked = head[1].toInt() and 0x80 != 0
                    var len = (head[1].toInt() and 0x7F).toLong()
                    when {
                        len == 126L -> {
                            if (!readFully(input, lenBuf, 2)) break
                            len = ((lenBuf[0].toInt() and 0xFF) shl 8 or (lenBuf[1].toInt() and 0xFF)).toLong()
                        }
                        len == 127L -> {
                            if (!readFully(input, lenBuf, 8)) break
                            len = 0
                            for (i in 0 until 8) len = (len shl 8) or (lenBuf[i].toInt() and 0xFF).toLong()
                        }
                    }
                    if (len > 10 * 1024 * 1024) break // 防御：单帧上限
                    if (masked && !readFully(input, mask, 4)) break
                    val payload = ByteArray(len.toInt())
                    if (len > 0 && !readFully(input, payload, payload.size)) break
                    if (masked) {
                        for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                    }
                    when (opcode) {
                        0x1, 0x2, 0x0 -> { // 文本 / 二进制 / 分片续帧：累积到 FIN 一起交付
                            fragmented.append(String(payload, StandardCharsets.UTF_8))
                            if (fin) {
                                val full = fragmented.toString()
                                fragmented.setLength(0)
                                if (full.isNotEmpty()) onMessage(client, full)
                            }
                        }
                        0x8 -> { // close
                            sendFrame(client, 0x8, payload)
                            break
                        }
                        0x9 -> sendFrame(client, 0xA, payload) // ping → pong
                        else -> {}
                    }
                }
            } catch (_: Exception) {
            } finally {
                clients.remove(client)
                client.open = false
                runCatching { client.socket.close() }
                RemoteInputHub.setClientCount(clients.size)
            }
        }

        private fun onMessage(client: WsClient, text: String) {
            try {
                val obj = Json.parseToJsonElement(text).jsonObject
                val type = obj["type"]?.jsonPrimitive?.content.orEmpty()
                val value = obj["text"]?.jsonPrimitive?.content.orEmpty()
                when (type) {
                    "input" -> RemoteInputHub.onRemoteInput(value)
                    "search" -> RemoteInputHub.onRemoteSearch(value)
                }
            } catch (e: Exception) {
                Log.w(TAG, "消息解析失败: $text", e)
            }
        }

        private fun readFully(input: java.io.InputStream, buf: ByteArray, count: Int): Boolean {
            var read = 0
            while (read < count) {
                val n = input.read(buf, read, count - read)
                if (n < 0) return false
                read += n
            }
            return true
        }

        private fun sendFrame(client: WsClient, opcode: Int, payload: ByteArray) {
            synchronized(client.lock) {
                if (!client.open) return
                try {
                    val header = ArrayList<Byte>(10)
                    header.add(((0x80 or opcode).toByte()))
                    when {
                        payload.size < 126 -> header.add(payload.size.toByte())
                        payload.size < 65536 -> {
                            header.add(126.toByte())
                            header.add(((payload.size shr 8) and 0xFF).toByte())
                            header.add((payload.size and 0xFF).toByte())
                        }
                        else -> {
                            header.add(127.toByte())
                            val n = payload.size.toLong()
                            for (shift in 56 downTo 0 step 8) header.add((n shr shift).toByte())
                        }
                    }
                    client.out.write(header.toByteArray())
                    client.out.write(payload)
                    client.out.flush()
                } catch (e: Exception) {
                    client.open = false
                    clients.remove(client)
                }
            }
        }

        private fun sendTvText(client: WsClient, text: String) {
            val json = "{\"type\":\"tv_text\",\"text\":" + jsonQuote(text) + "}"
            sendFrame(client, 0x1, json.toByteArray(StandardCharsets.UTF_8))
        }
    }

    /** 广播电视侧当前搜索框文字给所有手机端 */
    fun broadcastTvText(text: String) {
        val json = "{\"type\":\"tv_text\",\"text\":" + jsonQuote(text) + "}"
        val payload = json.toByteArray(StandardCharsets.UTF_8)
        // 延迟初始化期调用（hub 先于服务器更新文字）时直接忽略
        if (!started.get()) return
        broadcastRef?.invoke(payload)
    }

    /** 服务器启动后注入广播实现（避免 object 初始化顺序依赖） */
    @Volatile
    internal var broadcastRef: ((ByteArray) -> Unit)? = null
        private set

    fun broadcastAll(payload: ByteArray) {
        broadcastRef?.invoke(payload)
    }

    private fun jsonQuote(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    init {
        broadcastRef = { payload ->
            wsInstance?.broadcastText(payload)
        }
    }

    @Volatile
    private var wsInstance: WsInputServer? = null
}
