package com.shorttv.app.direct

import android.util.Log
import com.shorttv.app.data.model.DanmakuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream

// ---------- 手写 protobuf 编解码（模仿 myDV k04：varint + length-delimited，不引第三方库） ----------

private const val WIRE_LEN = 2

private val EMPTY_BYTES = ByteArray(0)

/** 心跳帧（payloadType=hb / method=hb，无 logId/payload），内容固定预编码。 */
private val HB_FRAME = encodePushFrame(PushFrame(payloadType = "hb", method = "hb")).toByteString()

/** PushFrame：field2 varint logId / field6 payloadType / field7 method / field8 payload。 */
private class PushFrame(
    var logId: Long = 0L,
    var payloadType: String = "",
    var method: String = "",
    var payload: ByteArray = EMPTY_BYTES
)

/** Response 内单条消息：field1 method / field2 payload。 */
private class ImMessage(val method: String, val payload: ByteArray)

/** Response：field1 repeated Message / field5 cursor / field9 needAck。 */
private class ImResponse(val messages: List<ImMessage>, val cursor: String, val needAck: Boolean)

/** ChatMessage：field2 user{1 id, 3 nickname} / field3 content / field15 eventTime。 */
private class ChatMsg(val userId: Long, val nickname: String, val content: String, val eventTime: Long)

/** WS 断开信息（httpCode 供日志定位风控/参数问题）。 */
private class WsClose(val httpCode: Int, val error: Throwable?)

private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
    var v = value
    while (true) {
        if ((v and 0x7FL.inv()) == 0L) {
            out.write(v.toInt())
            return
        }
        out.write(((v and 0x7FL) or 0x80L).toInt())
        v = v ushr 7
    }
}

private fun writeBytesField(out: ByteArrayOutputStream, field: Int, data: ByteArray) {
    writeVarint(out, ((field shl 3) or WIRE_LEN).toLong())
    writeVarint(out, data.size.toLong())
    out.write(data)
}

private fun writeStringField(out: ByteArrayOutputStream, field: Int, s: String) =
    writeBytesField(out, field, s.toByteArray(Charsets.UTF_8))

private fun encodePushFrame(f: PushFrame): ByteArray {
    val out = ByteArrayOutputStream()
    if (f.logId != 0L) {
        writeVarint(out, (2 shl 3).toLong())
        writeVarint(out, f.logId)
    }
    if (f.payloadType.isNotEmpty()) writeStringField(out, 6, f.payloadType)
    if (f.method.isNotEmpty()) writeStringField(out, 7, f.method)
    if (f.payload.isNotEmpty()) writeBytesField(out, 8, f.payload)
    return out.toByteArray()
}

/** protobuf 读取器：只支持本协议用到的 varint / length-delimited，其余跳过。 */
private class ProtoReader(private val buf: ByteArray) {
    private var pos = 0

    fun atEnd(): Boolean = pos >= buf.size

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (pos < buf.size) {
            val b = buf[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift >= 64) break
        }
        throw IllegalStateException("bad varint")
    }

    fun readTag(): Int = readVarint().toInt()

    fun readBytes(): ByteArray {
        val len = readVarint().toInt()
        if (len < 0 || pos + len > buf.size) throw IllegalStateException("truncated bytes")
        val r = buf.copyOfRange(pos, pos + len)
        pos += len
        return r
    }

    fun readString(): String = String(readBytes(), Charsets.UTF_8)

    fun skipField(tag: Int) {
        when (tag and 0x7) {
            0 -> readVarint()
            1 -> pos += 8
            2 -> {
                val len = readVarint().toInt()
                if (len < 0 || pos + len > buf.size) throw IllegalStateException("truncated field")
                pos += len
            }
            5 -> pos += 4
            else -> throw IllegalStateException("bad wire type ${tag and 0x7}")
        }
    }
}

private fun parsePushFrame(data: ByteArray): PushFrame {
    val f = PushFrame()
    val r = ProtoReader(data)
    while (!r.atEnd()) {
        val tag = r.readTag()
        if (tag == 0) break
        when (tag ushr 3) {
            2 -> f.logId = r.readVarint()
            6 -> f.payloadType = r.readString()
            7 -> f.method = r.readString()
            8 -> f.payload = r.readBytes()
            else -> r.skipField(tag)
        }
    }
    return f
}

private fun parseResponse(data: ByteArray): ImResponse {
    val messages = ArrayList<ImMessage>()
    var cursor = ""
    var needAck = false
    val r = ProtoReader(data)
    while (!r.atEnd()) {
        val tag = r.readTag()
        if (tag == 0) break
        when (tag ushr 3) {
            1 -> {
                val inner = ProtoReader(r.readBytes())
                var method = ""
                var payload: ByteArray = EMPTY_BYTES
                while (!inner.atEnd()) {
                    val t = inner.readTag()
                    if (t == 0) break
                    when (t ushr 3) {
                        1 -> method = inner.readString()
                        2 -> payload = inner.readBytes()
                        else -> inner.skipField(t)
                    }
                }
                messages.add(ImMessage(method, payload))
            }
            5 -> cursor = r.readString()
            9 -> needAck = r.readVarint() != 0L
            else -> r.skipField(tag)
        }
    }
    return ImResponse(messages, cursor, needAck)
}

private fun parseChat(data: ByteArray): ChatMsg {
    var userId = 0L
    var nickname = ""
    var content = ""
    var eventTime = 0L
    val r = ProtoReader(data)
    while (!r.atEnd()) {
        val tag = r.readTag()
        if (tag == 0) break
        when (tag ushr 3) {
            2 -> {
                val user = ProtoReader(r.readBytes())
                while (!user.atEnd()) {
                    val t = user.readTag()
                    if (t == 0) break
                    when (t ushr 3) {
                        1 -> userId = user.readVarint()
                        3 -> nickname = user.readString()
                        else -> user.skipField(t)
                    }
                }
            }
            3 -> content = r.readString()
            15 -> eventTime = r.readVarint()
            else -> r.skipField(tag)
        }
    }
    return ChatMsg(userId, nickname, content, eventTime)
}

/** payload 为 gzip 压缩；非 gzip（异常场景）按原样返回由上层跳过。 */
private fun gunzip(data: ByteArray): ByteArray =
    runCatching { GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() } }
        .getOrElse { data }

/**
 * 抖音直播弹幕 IM 客户端：webcast IM push v3 WebSocket + protobuf（协议移植自 myDV 逆向）。
 *
 * 流程：enter 取 IM 参数 → wss 连接 → 心跳保持 → PushFrame{msg} 解 gzip →
 * Response → WebcastChatMessage → DanmakuItem；needAck 回 ack cursor。
 *
 * 重连：主 URL 失败立即切备用重试一次；之后最多 5 次（间隔 5s），超出关闭 flow。
 */
class LiveImClient(
    private val http: OkHttpClient,
    private val argsProvider: suspend () -> LiveImArgs?
) {
    companion object {
        private const val TAG = "LiveImClient"
        private const val HEARTBEAT_MS = 10_000L
        private const val RECONNECT_DELAY_MS = 5_000L
        private const val MAX_RECONNECTS = 5
        private const val PRIMARY_HOST = "webcast3-ws-web-lf.douyin.com"
        private const val BACKUP_HOST = "webcast3-ws-web-lq.douyin.com"
        private const val METHOD_CHAT = "WebcastChatMessage"
    }

    private val running = AtomicBoolean(false)
    private val wsRef = AtomicReference<WebSocket?>(null)
    private var heartbeatJob: Job? = null

    /**
     * 启动弹幕流：连接期间持续 emit；失败/重连耗尽 close（带异常），
     * stop() 主动停止则正常关闭；collect 取消时断开 WS。
     */
    fun start(webRid: String): Flow<DanmakuItem> = callbackFlow {
        running.set(true)
        val producer = this
        var lastError: Throwable? = null
        var failed = false
        try {
            var usedBackup = false
            var reconnects = 0
            while (running.get() && isActive) {
                val args = runCatching { argsProvider() }
                    .onFailure { Log.w(TAG, "liveImArgs failed: ${it.message}") }
                    .getOrNull()
                if (args == null || args.roomIdStr.isBlank()) {
                    lastError = IllegalStateException("IM 连接参数不可用")
                    failed = true
                    break
                }
                val url = buildUrl(if (usedBackup) BACKUP_HOST else PRIMARY_HOST, args)
                Log.i(TAG, "connecting: ${url.take(150)}")
                val close = connectOnce(producer, url, args)
                if (!running.get()) break
                if (!usedBackup) {
                    // 主 URL 失败 → 立即换备用重试一次（不计入重连次数）
                    usedBackup = true
                    Log.w(TAG, "primary failed(http=${close?.httpCode}), try backup: ${close?.error?.message}")
                    continue
                }
                if (reconnects < MAX_RECONNECTS) {
                    reconnects++
                    Log.w(TAG, "reconnect $reconnects/$MAX_RECONNECTS in ${RECONNECT_DELAY_MS}ms: ${close?.error?.message}")
                    delay(RECONNECT_DELAY_MS)
                    continue
                }
                lastError = close?.error ?: IllegalStateException("IM 连接关闭")
                Log.w(TAG, "give up after $MAX_RECONNECTS reconnects: ${lastError?.message}")
                failed = true
                break
            }
        } finally {
            // 主动停止 / 失败 / collect 取消统一在此断开 WS 与心跳
            running.set(false)
            stopHeartbeat()
            wsRef.getAndSet(null)?.cancel()
        }
        if (failed) lastError?.let { throw it }
    }.buffer(256)

    /** 关闭 WS 并取消心跳；幂等，可重复调用。 */
    fun stop() {
        running.set(false)
        stopHeartbeat()
        wsRef.getAndSet(null)?.cancel()
    }

    // ---------- 内部 ----------

    /** IM 连接查询参数（对齐抖音 Web 端 push v3）。 */
    private fun buildUrl(host: String, args: LiveImArgs): String {
        val params = linkedMapOf(
            "app_name" to "douyin_web",
            "version_code" to "180800",
            "webcast_sdk_version" to "1.0.14-beta.0",
            "compress" to "gzip",
            "device_platform" to "web",
            "cookie_enabled" to "true",
            "encode_type" to "protobuf",
            "browser_language" to "zh-CN",
            "browser_platform" to "Win32",
            "browser_name" to "Chrome",
            "browser_version" to "147.0.0.0",
            "room_id" to args.roomIdStr,
            "user_unique_id" to args.userUniqueId,
            "internal_stream_id" to args.internalStreamId,
            "cursor" to "0"
        )
        val query = params.entries.joinToString("&") {
            "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
        }
        return "wss://$host/webcast/im/push/v3/?$query"
    }

    /** 建立一次 WS 连接并持续收帧，挂起直到断开/关闭。 */
    private suspend fun connectOnce(
        producer: ProducerScope<DanmakuItem>,
        url: String,
        args: LiveImArgs
    ): WsClose? = suspendCancellableCoroutine { cont ->
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", args.ua)
            .header("Origin", "https://live.douyin.com")
        if (args.cookie.isNotBlank()) builder.header("Cookie", args.cookie)
        val ws = http.newWebSocket(builder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "ws opened")
                startHeartbeat(producer, webSocket)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleBinary(webSocket, bytes.toByteArray(), producer)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code ?: -1
                Log.w(TAG, "ws failure http=$code: ${t.message}")
                stopHeartbeat()
                if (cont.isActive) cont.resume(WsClose(code, t), null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "ws closed code=$code")
                stopHeartbeat()
                if (cont.isActive) cont.resume(WsClose(code, null), null)
            }
        })
        wsRef.set(ws)
        cont.invokeOnCancellation { runCatching { ws.cancel() } }
    }

    /** 心跳：打开后立即一拍，之后每 10s 一次，直到断开/停止。 */
    private fun startHeartbeat(scope: CoroutineScope, ws: WebSocket) {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                runCatching { ws.send(HB_FRAME) }
                delay(HEARTBEAT_MS)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * 二进制帧处理：解 PushFrame，msg → gzip → Response → ChatMessage；
     * hb → 回发心跳；needAck → 回 ack。坏帧 runCatching 跳过不崩。
     */
    private fun handleBinary(ws: WebSocket, data: ByteArray, producer: ProducerScope<DanmakuItem>) {
        Log.d(TAG, "binary frame ${data.size}B")
        runCatching {
            val frame = parsePushFrame(data)
            val isMsg = frame.payloadType == "msg" || frame.method == "msg"
            val isHb = frame.payloadType == "hb" || frame.method == "hb"
            when {
                isMsg -> {
                    val payload = gunzip(frame.payload)
                    val resp = parseResponse(payload)
                    for (m in resp.messages) {
                        if (m.method != METHOD_CHAT) continue
                        runCatching {
                            val chat = parseChat(m.payload)
                            if (chat.content.isNotEmpty()) {
                                val item = DanmakuItem(
                                    id = "live_${frame.logId}_${chat.userId}_${chat.eventTime}",
                                    content = chat.content,
                                    userNickname = chat.nickname,
                                    offsetTimeMs = 0L
                                )
                                Log.d(TAG, "chat: ${chat.nickname}: ${chat.content.take(50)}")
                                producer.trySend(item)
                            }
                        }.onFailure { Log.w(TAG, "chat parse skipped: ${it.message}") }
                    }
                    if (resp.needAck) {
                        val ack = PushFrame(
                            logId = frame.logId,
                            payloadType = "ack",
                            method = "ack",
                            payload = resp.cursor.toByteArray()
                        )
                        ws.send(encodePushFrame(ack).toByteString())
                    }
                }
                isHb -> ws.send(HB_FRAME)
                else -> { /* 其他消息类型忽略 */ }
            }
        }.onFailure { Log.w(TAG, "bad frame skipped: ${it.message}") }
    }
}
