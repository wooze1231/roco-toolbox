package com.wooze.rocomerchant

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * 精灵「原皮 / 异色」贴图来源。
 *
 * 内置表：assets/shiny_icons.json
 *   { "<图鉴slug>": { "n":"中文名", "p":"原皮直链", "y":"异色直链" } }
 *   直链来自 wiki 的图片 CDN（patchwiki.biligame.com）——**不走 WAF**，手机上能正常下载；
 *   而 wiki 页面本身有 WAF（567），所以直链要预先查好，App 只负责按直链下载缓存。
 *
 * 表里没有的（版本更新后的新精灵）走 [fetchArt] 实时解析一次 wiki 页面拿直链，
 * 成功就写回内存表并下载缓存；失败（wiki 没收录 / 被限流）返回 null，
 * 界面降级成「原皮 + 明亮化」占位，下次打开会再试。
 */
object ShinyArt {

    private const val ASSET = "shiny_icons.json"
    private const val NRC_API = "https://wiki.biligame.com/nrc/api.php"
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val PET = Pattern.compile("data-view=\"pet\"[^>]*>\\s*<img[^>]+src=\"([^\"]+)\"")
    private val SHINY = Pattern.compile("data-view=\"shiny\"[^>]*>\\s*<img[^>]+src=\"([^\"]+)\"")

    @Volatile private var table: JSONObject? = null

    @Volatile private var bySlug: HashMap<String, JSONObject> = HashMap()
    @Volatile private var byName: HashMap<String, JSONObject> = HashMap()

    private fun load(c: Context) {
        if (table != null) return
        val t = try {
            JSONObject(c.assets.open(ASSET).bufferedReader().readText())
        } catch (e: Exception) {
            e.printStackTrace()
            JSONObject()
        }
        val s = HashMap<String, JSONObject>()
        val n = HashMap<String, JSONObject>()
        val it = t.keys()
        while (it.hasNext()) {
            val k = it.next()
            val o = t.optJSONObject(k) ?: continue
            s[k] = o
            o.optString("n", "").takeIf { it.isNotBlank() }?.let { n[it] = o }
        }
        table = t
        bySlug = s
        byName = n
    }

    private fun find(c: Context, slug: String, name: String): JSONObject? {
        load(c)
        if (slug.isNotBlank()) bySlug[slug]?.let { return it }
        if (name.isNotBlank()) byName[name]?.let { return it }
        return null
    }

    /** 异色立绘直链；没有返回 null。 */
    fun yiseUrl(c: Context, slug: String, name: String = ""): String? =
        find(c, slug, name)?.optString("y", "")?.takeIf { it.isNotBlank() }

    /** 原皮立绘直链；没有返回 null。 */
    fun normalUrl(c: Context, slug: String, name: String = ""): String? =
        find(c, slug, name)?.optString("p", "")?.takeIf { it.isNotBlank() }

    /** 收录了多少只有异色立绘的精灵。 */
    fun yiseCount(c: Context): Int {
        load(c)
        return bySlug.count { it.value.optString("y", "").isNotBlank() }
    }

    /**
     * 联网补查（子线程）：解析 wiki 页面里的 data-view="pet" / data-view="shiny" 立绘直链。
     * 返回 Pair(原皮, 异色)，可能为 null；查到异色就写回内存表。
     */
    fun fetchArt(c: Context, name: String, slug: String = ""): Pair<String?, String?> {
        if (name.isBlank()) return null to null
        val url = NRC_API + "?action=parse&page=" + URLEncoder.encode(name, "UTF-8") +
            "&prop=text&format=json&formatversion=2"
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", UA)
                connectTimeout = 15000
                readTimeout = 20000
            }
            val txt = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val html = JSONObject(txt).optJSONObject("parse")?.optString("text", "") ?: ""
            val pet = PET.matcher(html).takeIf { it.find() }?.group(1)
            val shiny = SHINY.matcher(html).takeIf { it.find() }?.group(1)
            if (shiny != null) {
                load(c)
                val key = slug.ifBlank { name }
                val o = bySlug[key] ?: JSONObject()
                o.put("n", name)
                if (pet != null) o.put("p", pet)
                o.put("y", shiny)
                bySlug[key] = o
                byName[name] = o
                table?.put(key, o)
            }
            pet to shiny
        } catch (e: Exception) {
            e.printStackTrace()
            null to null
        }
    }
}
