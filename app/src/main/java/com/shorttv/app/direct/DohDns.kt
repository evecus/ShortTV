package com.shorttv.app.direct

import okhttp3.Dns
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import org.json.JSONObject

/**
 * 系统优先 + DoH 备用 DNS。
 *
 * 部分电视盒子的路由 DNS 会解析失败某些域名（如实测 mssdk.bytedance.com
 * 在安卓 9 电视上不可达，而 douyin.com 正常），导致 mssdk 无法签发真实
 * msToken。系统解析失败时回退公共 DoH（阿里 / 腾讯，直连 IP 无需再解析）。
 */
object DohDns : Dns {

    private val PROVIDERS = listOf(
        "https://223.5.5.5/resolve?name=%s&type=A",      // AliDNS
        "https://120.53.53.53/dns-query?name=%s&type=A", // DNSPod
    )

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            resolveViaDoh(hostname) ?: throw e
        }
    }

    private fun resolveViaDoh(hostname: String): List<InetAddress>? {
        for (template in PROVIDERS) {
            try {
                val url = template.replace("%s", URLEncoder.encode(hostname, "UTF-8"))
                val conn = URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Accept", "application/dns-json")
                val body = conn.inputStream.use { it.readBytes().decodeToString() }
                val answers = JSONObject(body).optJSONArray("Answer") ?: continue
                val addresses = mutableListOf<InetAddress>()
                for (i in 0 until answers.length()) {
                    val item = answers.getJSONObject(i)
                    if (item.optInt("type") != 1) continue // 只取 A 记录
                    runCatching { InetAddress.getByName(item.getString("data")) }
                        .getOrNull()?.let { addresses.add(it) }
                }
                if (addresses.isNotEmpty()) return addresses
            } catch (_: Exception) {
                // 尝试下一个 DoH 服务商
            }
        }
        return null
    }
}
