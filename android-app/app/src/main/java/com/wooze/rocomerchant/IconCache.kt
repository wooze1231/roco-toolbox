package com.wooze.rocomerchant

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 商品图标缓存（"新矿"出现时自动联网抓原图并本地缓存，只抓一次，失败即放弃）。
 *
 * 优先级：内置图标（ItemIcons）> 本地缓存文件 > 联网抓取（仅一次）> 占位图。
 * 命中本地/内置时完全不联网，避免重复消耗流量；抓取失败的名字记入失败集合，之后不再重试。
 * 抓取来源移植自已验证的 patchwiki 逻辑（wiki.biligame.com/rocom/<名称> 页的原图直链）。
 */
object IconCache {
    private const val TAG = "IconCache"
    private const val DIR = "icon_cache"
    private const val PREFS = "icon_cache_prefs"
    private const val FAILED = "failed_names"

    private const val WIKI = "https://wiki.biligame.com/rocom/"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36"
    private const val REFERER = "https://wiki.biligame.com/rocom/"

    fun dir(c: Context): File = File(c.filesDir, DIR).also { if (!it.exists()) it.mkdirs() }

    fun cacheFile(c: Context, name: String): File = File(dir(c), "ic_${name.hashCode().toString(36)}.png")

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 是否内置/命中本地缓存（用于决定是否需要联网）。 */
    fun isKnown(c: Context, name: String): Boolean =
        fuzzyBundled(name) || cacheFile(c, name).exists() ||
            (prefs(c).getStringSet(FAILED, emptySet())?.contains(name) == true)

    /** 仅在缺失且从未失败过时才联网抓取一次并缓存；其余情况直接返回。 */
    fun ensure(c: Context, name: String) {
        if (isKnown(c, name)) return
        try {
            val iconUrl = findIconUrl(name)
            if (iconUrl == null) {
                markFailed(c, name); return
            }
            val data = download(iconUrl)
            if (data == null || !isImage(data)) {
                markFailed(c, name); return
            }
            cacheFile(c, name).writeBytes(data)
            Log.i(TAG, "cached icon for $name (${data.size}B)")
        } catch (e: Exception) {
            Log.w(TAG, "ensure $name failed: ${e.message}")
            markFailed(c, name)
        }
    }

    /** 批量：只对缺失项联网，命中内置/缓存的直接跳过（不消耗流量）。 */
    fun ensureMissing(c: Context, items: List<String>) {
        for (n in items) ensure(c, n)
    }

    private fun markFailed(c: Context, name: String) {
        val s = (prefs(c).getStringSet(FAILED, emptySet()) ?: emptySet()).toMutableSet()
        if (s.add(name)) prefs(c).edit().putStringSet(FAILED, s).apply()
    }

    private fun fuzzyBundled(name: String): Boolean {
        if (ItemIcons.MAP.containsKey(name)) return true
        for (k in ItemIcons.MAP.keys) if (name.contains(k) || k.contains(name)) return true
        return false
    }

    private fun findIconUrl(name: String): String? {
        val url = URL(WIKI + URLEncoder.encode(name, "UTF-8"))
        val html = getText(url) ?: return null
        val tags = Regex("""<img\b[^>]*patchwiki\.biligame\.com/images/rocom/[^>]*>""").findAll(html)
        for (m in tags) {
            val tag = m.value
            val src = Regex("""src="([^"]+)""").find(tag)?.groupValues?.get(1) ?: continue
            val alt = Regex("""alt="([^"]*)""").find(tag)?.groupValues?.get(1) ?: ""
            if ("Logo" in alt || "Logo" in src) continue
            if (alt == "图标 物品 来源.png") continue
            if ("/thumb/" in src) continue
            return src
        }
        return null
    }

    private fun isImage(d: ByteArray): Boolean =
        d.size > 8 && (d.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) ||
            (d[0] == 0xFF.toByte() && d[1] == 0xD8.toByte()))

    private fun getText(url: URL): String? = try {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Referer", REFERER)
        conn.connectTimeout = 12000
        conn.readTimeout = 12000
        if (conn.responseCode != 200) { conn.disconnect(); null }
        else conn.inputStream.bufferedReader(StandardCharsets.UTF_8).readText().also { conn.disconnect() }
    } catch (e: Exception) {
        null
    }

    private fun download(url: String): ByteArray? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Referer", REFERER)
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        if (conn.responseCode != 200) { conn.disconnect(); null }
        else conn.inputStream.readBytes().also { conn.disconnect() }
    } catch (e: Exception) {
        null
    }
}
