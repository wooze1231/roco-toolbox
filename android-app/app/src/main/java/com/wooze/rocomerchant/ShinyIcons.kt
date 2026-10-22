package com.wooze.rocomerchant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 异色精灵图标缓存。
 *
 * 目录：`filesDir/shiny/icons/`
 * 注意：**不用 cacheDir** —— 系统「清理缓存」/ 存储紧张时会把 cacheDir 清掉，
 * 这里放在 filesDir（应用私有数据区），只有卸载 App 才会没；
 * 同时 App 内提供「清空图标缓存」按钮，需要时手动释放空间（缓存清掉后会自动重新下载）。
 */
object ShinyIcons {

    private const val HOST = "https://rocokingdomworld.org"
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun dir(c: Context): File = File(c.filesDir, "shiny/icons").apply { mkdirs() }

    private fun keyOf(e: ShinyData.Entry): String =
        (e.slug.ifBlank { e.name }).replace(Regex("[^A-Za-z0-9_\\-一-龥]"), "_")

    /** 本地文件（可能不存在）。 */
    fun local(c: Context, e: ShinyData.Entry): File = File(dir(c), keyOf(e) + ".img")

    /** 异色贴图本地文件（可能不存在）。 */
    fun localShiny(c: Context, e: ShinyData.Entry): File = File(dir(c), keyOf(e) + "_yise.img")

    fun has(c: Context, e: ShinyData.Entry): Boolean {
        val f = local(c, e)
        return f.exists() && f.length() > 0
    }

    /** 有没有真正的异色贴图（下载到本地的才算）。 */
    fun hasShiny(c: Context, e: ShinyData.Entry): Boolean {
        val f = localShiny(c, e)
        return f.exists() && f.length() > 0
    }

    /** 下载普通（原皮）图；已在本地就跳过。子线程调用。 */
    private fun fetch(c: Context, url: String, out: File): Boolean {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", UA)
                connectTimeout = 15000
                readTimeout = 20000
                instanceFollowRedirects = true
            }
            conn.inputStream.use { input ->
                FileOutputStream(out).use { o -> input.copyTo(o) }
            }
            conn.disconnect()
            out.length() > 0
        } catch (ex: Exception) {
            ex.printStackTrace()
            out.delete()
            false
        }
    }

    /**
     * 取异色贴图：本地有直接用；没有就查表/联网找直链再下载缓存。
     * 找不到（wiki 还没收录这只的异色立绘，或被限流）返回 false，由界面降级成「原皮明亮化」。
     * 子线程调用。
     */
    fun ensureShiny(c: Context, e: ShinyData.Entry, force: Boolean = false): Boolean {
        val f = localShiny(c, e)
        if (!force && f.exists() && f.length() > 0) return true
        // 先查内置直链表；表里没有就实时解析一次 wiki 页面（新精灵 / wiki 后来补的图）
        val url = ShinyArt.yiseUrl(c, e.slug, e.name)
            ?: ShinyArt.fetchArt(c, e.name, e.slug).second
            ?: return false
        return fetch(c, url, f)
    }

    /** 缺图才下载；返回是否本地已存在（含本次下载成功的）。在子线程调用。 */
    fun ensure(c: Context, e: ShinyData.Entry, force: Boolean = false): Boolean {
        val f = local(c, e)
        if (!force && f.exists() && f.length() > 0) return true
        // 原皮优先用 wiki 立绘（和异色图同风格），没有再实时查一次，最后用图鉴站头像
        val url = ShinyArt.normalUrl(c, e.slug, e.name)
            ?: ShinyArt.fetchArt(c, e.name, e.slug).first
            ?: e.icon.takeIf { it.isNotBlank() }?.let { if (it.startsWith("http")) it else HOST + it }
            ?: return false
        return fetch(c, url, f)
    }

    /** 批量下载缺失图标（子线程调用）。返回 (成功数, 总数)。 */
    fun ensureAll(c: Context, items: List<ShinyData.Entry>): Pair<Int, Int> {
        var ok = 0
        items.forEach { if (ensure(c, it)) ok++ }
        return ok to items.size
    }

    /**
     * 这一只**值不值得联网去补异色图**。
     *
     * 补图要对每只发一次 wiki 请求（还要 250ms 限速），641 只全试一遍很慢。
     * 但其中大部分精灵 BWIKI 根本没收异色立绘，请求注定失败。
     * 这里用两张本地表先筛一遍：**任一数据源说有 → 才去联网**。
     *
     * - `ShinyArt.yiseUrl()`：本地立绘直链表（44 条手工收录）
     * - `ShinyCatalog.hasBwikiShiny()`：BWIKI 异色清单（图鉴范围内命中 128 只）
     *
     * 两张表都没有 → 跳过，省掉无效请求。保守策略：只要有一张表认，就补，
     * 所以不会因为表不全而漏补。
     */
    fun worthShinyFetch(c: Context, e: ShinyData.Entry): Boolean {
        if (ShinyArt.yiseUrl(c, e.slug, e.name) != null) return true
        return ShinyCatalog.hasBwikiShiny(c, e.slug)
    }

    /** 清空图标缓存（下次用到会自动重新下载）。返回删除的字节数。 */
    fun clear(c: Context): Long {
        var bytes = 0L
        dir(c).listFiles()?.forEach {
            bytes += it.length()
            it.delete()
        }
        return bytes
    }

    fun sizeBytes(c: Context): Long {
        var bytes = 0L
        dir(c).listFiles()?.forEach { bytes += it.length() }
        return bytes
    }

    /**
     * 取出图标位图。
     *
     * [shiny] = true 时优先用**真正的异色立绘**（本地已缓存的）；
     * 如果这只精灵的异色立绘还没拿到（wiki 未收录 / 还没下载），
     * 就退回「明亮化」处理（提饱和 + 提亮 + 轻微色相偏移）先占位，
     * 等能下载到真图后自动替换。
     */
    fun bitmap(c: Context, e: ShinyData.Entry, shiny: Boolean): Bitmap? {
        if (shiny) {
            val sf = localShiny(c, e)
            if (sf.exists() && sf.length() > 0) {
                val real = BitmapFactory.decodeFile(sf.absolutePath)
                if (real != null) return real
            }
        }
        val f = local(c, e)
        if (!f.exists() || f.length() == 0L) return null
        val src = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        if (!shiny) return src
        return try {
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(shinyMatrix())
            }
            Canvas(out).drawBitmap(src, 0f, 0f, p)
            out
        } catch (ex: Exception) {
            src
        }
    }

    /** 异色明亮化矩阵：饱和度 1.7、亮度 ×1.18 再抬一点、色相轻微偏移。 */
    private fun shinyMatrix(): ColorMatrix {
        val sat = ColorMatrix().apply { setSaturation(1.7f) }
        val bright = ColorMatrix(
            floatArrayOf(
                1.18f, 0f, 0f, 0f, 12f,
                0f, 1.18f, 0f, 0f, 12f,
                0f, 0f, 1.18f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val hue = ColorMatrix().apply { setRotate(1, 10f) } // 绿轴轻微偏移，制造配色差异
        val out = ColorMatrix()
        out.setConcat(sat, bright)
        out.postConcat(hue)
        return out
    }

    fun count(c: Context): Int = dir(c).listFiles()?.size ?: 0

    /**
     * 给「联想搜索」这类只拿到**图鉴条目**（[ShinyCatalog.Entry]）的地方取图标。
     *
     * 只查本地缓存，**不联网**：联想列表是边打字边刷新的，联网会让输入卡顿。
     * 本地没有就返回 null，由调用方显示占位图；用户真正选中后，
     * 再由 `ensure()` 在后台把图标补下来。
     */
    fun bitmapForCatalog(c: Context, e: ShinyCatalog.Entry, shiny: Boolean = true): Bitmap? {
        val de = ShinyData.Entry(
            name = e.name, no = e.no, slug = e.slug, icon = e.icon, count = 0
        )
        return bitmap(c, de, shiny)
    }
}
