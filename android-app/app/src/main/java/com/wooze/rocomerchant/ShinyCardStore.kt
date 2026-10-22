package com.wooze.rocomerchant

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 一张炫彩色卡：精灵名 + 底色 + 混色 + 粒子 + 是否特殊。 */
data class CardRec(
    val id: Long,
    val spirit: String,
    val base: Int,      // 底色（下方大块）
    val mix: Int,       // 混色（上方波浪）
    val particle: Int,  // 0 星芒 / 1 爱心 / 2 菱形 / 3 五角星 / 4 特殊
    val special: Boolean,
    val ts: Long,
    /** 特殊外观（黑白/赛季）用整张成品图：assets/cards/<art>，空串表示用配色渲染 */
    val art: String = ""
)

/** 色卡本地存储（私有目录，卸载即清）。 */
object ShinyCardStore {
    private const val NAME = "shiny_cards.json"

    fun file(c: Context): File = File(c.filesDir, NAME)

    fun load(c: Context): MutableList<CardRec> {
        val f = file(c)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                CardRec(
                    id = o.optLong("id"),
                    spirit = o.optString("spirit"),
                    base = o.optInt("base"),
                    mix = o.optInt("mix"),
                    particle = o.optInt("particle"),
                    special = o.optBoolean("special"),
                    ts = o.optLong("ts"),
                    art = o.optString("art")
                )
            }
        } catch (_: Throwable) {
            mutableListOf()
        }
    }

    fun save(c: Context, list: List<CardRec>) {
        try {
            val arr = JSONArray()
            list.forEach {
                arr.put(JSONObject().apply {
                    put("id", it.id)
                    put("spirit", it.spirit)
                    put("base", it.base)
                    put("mix", it.mix)
                    put("particle", it.particle)
                    put("special", it.special)
                    put("ts", it.ts)
                    put("art", it.art)
                })
            }
            file(c).writeText(arr.toString())
        } catch (_: Throwable) {
        }
    }

    fun add(c: Context, recs: List<CardRec>) {
        if (recs.isEmpty()) return
        val all = load(c)
        // 入库前把颜色吸附到游戏固定色板（特殊卡保持原色）
        all.addAll(recs.map {
            if (it.special) it
            else it.copy(base = CardCats.snap(it.base), mix = CardCats.snap(it.mix))
        })
        save(c, all)
    }

    fun remove(c: Context, id: Long) {
        val all = load(c)
        all.removeAll { it.id == id }
        save(c, all)
    }

    /** 一次性迁移：把已有色卡的颜色吸附到 wiki 标准色板。 */
    fun migrateToPalette(c: Context) {
        val prefs = c.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("card_palette_v1", false)) return
        val all = load(c)
        if (all.isNotEmpty()) {
            save(c, all.map {
                if (it.special) it
                else it.copy(base = CardCats.snap(it.base), mix = CardCats.snap(it.mix))
            })
        }
        prefs.edit().putBoolean("card_palette_v1", true).apply()
    }

    /** 导出成 JSON 文本（分享/备份用）。 */
    fun toJson(list: List<CardRec>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("spirit", c.spirit)
                put("base", c.base)
                put("mix", c.mix)
                put("particle", c.particle)
                put("special", c.special)
                put("ts", c.ts)
            })
        }
        return arr.toString()
    }

    /** 从 JSON 文本导入；id 重新分配，避免和现有冲突。 */
    fun fromJson(text: String): List<CardRec> {
        val out = mutableListOf<CardRec>()
        try {
            val arr = JSONArray(text)
            val now = System.currentTimeMillis()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    CardRec(
                        id = now + i,
                        spirit = o.optString("spirit"),
                        base = o.optInt("base"),
                        mix = o.optInt("mix"),
                        particle = o.optInt("particle"),
                        special = o.optBoolean("special"),
                        ts = o.optLong("ts", now + i),
                        art = o.optString("art")
                    )
                )
            }
        } catch (_: Throwable) {
        }
        return out
    }

    /**
     * 测试包首次打开时灌一批示例色卡，方便直接看分类/筛选效果；
     * 正式包（applicationId 不以 .test 结尾）永远不会走这里。
     */
    fun seedForBetaIfEmpty(c: Context) {
        if (!c.packageName.endsWith(".test")) return
        val prefs = c.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)
        if (prefs.getInt("card_seed_v", 0) >= 4) return
        val cur = load(c)
        // 用户已经扫到过真实色卡（id 是时间戳，很大）→ 不覆盖
        if (cur.any { it.id >= 1000 }) return
        val now = System.currentTimeMillis()
        val demo = listOf(
            Triple("雪绒鸟", 0xFFE5576F.toInt(), 0xFF3DD6D6.toInt()) to 0,
            Triple("鸭吉吉", 0xFFF7C722.toInt(), 0xFF3DD6D6.toInt()) to 1,
            Triple("水灵", 0xFFA7E4E4.toInt(), 0xFFE4566E.toInt()) to 2,
            Triple("板板壳", 0xFF83D256.toInt(), 0xFFE5576F.toInt()) to 0,
            Triple("焰火", 0xFF2F6FB5.toInt(), 0xFFBFE6F7.toInt()) to 0,
            Triple("火神", 0xFF3E7CC1.toInt(), 0xFFF7C722.toInt()) to 2,
            Triple("波波拉", 0xFF57A15A.toInt(), 0xFFC8E6C9.toInt()) to 1,
            Triple("咔咔壳", 0xFFE0A93B.toInt(), 0xFFF6C7D5.toInt()) to 0,
            Triple("水蓝蓝", 0xFF2F6FB5.toInt(), 0xFFBFE6F7.toInt()) to 0
        ).mapIndexed { i, (t, part) ->
            CardRec(i + 1L, t.first, t.second, t.third, part, false, now + i)
        }.toMutableList()
        demo.add(CardRec(90, "觅觅蝠", 0xFF3ED7D7.toInt(), 0xFFDBBCEB.toInt(), 3, true, now + 90))
        demo.add(CardRec(91, "黑白", 0xFFBFBFB9.toInt(), 0xFF2A2826.toInt(), 4, true, now + 91))
        // 特殊外观（黑白 + 4 个赛季）——直接用 wiki 的成品图
        listOf(
            "blackwhite.png" to "黑白",
            "s1_darklight.png" to "暗夜拾光",
            "s2_carnival.png" to "狂欢怪谈",
            "s3_type.png" to "铅字幻梦",
            "s4_moon.png" to "月涌狂想"
        ).forEachIndexed { i, (img, title) ->
            demo.add(CardRec(200L + i, title, 0, 0, 4, true, now + 200 + i, img))
        }
        save(c, demo.map {
            if (it.special) it
            else it.copy(base = CardCats.snap(it.base), mix = CardCats.snap(it.mix))
        })
        prefs.edit().putInt("card_seed_v", 4).apply()
    }
}

/**
 * 颜色分类：底色 / 混色 是两套不同的类目（混色多一批"浅X"）。
 * 用「代表色 + 最近色」归类；特殊卡不参与颜色分类。
 */
object CardCats {
    /** 深色 6 个：**底色只用这 6 个**（wiki 数据里底色无例外）。 */
    private val DEEP = linkedMapOf(
        "蓝色" to 0xFF3C32CF.toInt(),
        "绿色" to 0xFF83D256.toInt(),
        "青色" to 0xFF3ED7D7.toInt(),
        "紫色" to 0xFFB869ED.toInt(),
        "红色" to 0xFFE5576F.toInt(),
        "橙色" to 0xFFF9C924.toInt()
    )

    /** 浅色 6 个：只出现在混色。 */
    private val LIGHT = linkedMapOf(
        "浅蓝" to 0xFFBAC8FB.toInt(),
        "浅绿" to 0xFFAEDFAE.toInt(),
        "浅青" to 0xFFA8E5E5.toInt(),
        "浅紫" to 0xFFDBBCEB.toInt(),
        "浅红" to 0xFFFDCDD3.toInt(),
        "浅橙" to 0xFFF1D397.toInt()
    )

    /** wiki 里另外 4 个颜色（特殊卡用）：不归类目，但参与吸色。 */
    private val EXTRA = intArrayOf(
        0xFFCD4579.toInt(),
        0xFF772478.toInt(),
        0xFF08090F.toInt(),
        0xFF22232E.toInt()
    )

    val BASE: List<String> = DEEP.keys.toList()
    val MIX: List<String> = (DEEP.keys + LIGHT.keys).toList()

    private val BASE_HEX: Map<String, Int> = DEEP
    private val MIX_HEX: Map<String, Int> = DEEP + LIGHT

    fun dot(cat: String): Int =
        BASE_HEX[cat] ?: MIX_HEX[cat] ?: 0xFFD8D2C4.toInt()

    fun nearest(color: Int, table: Map<String, Int>): String {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        var best = table.keys.first(); var bd = Double.MAX_VALUE
        table.forEach { (k, v) ->
            val dr = (r - Color.red(v)).toDouble()
            val dg = (g - Color.green(v)).toDouble()
            val db = (b - Color.blue(v)).toDouble()
            val d = dr * dr + dg * dg + db * db
            if (d < bd) { bd = d; best = k }
        }
        return best
    }

    private fun hsv(color: Int): FloatArray =
        FloatArray(3).also { Color.colorToHSV(color, it) }

    /**
     * 游戏固定色板：同一档颜色在游戏里只有一个值。
     * 识别/导入时先把颜色「吸附」到最近的标准色，避免相机偏色或手填色号导致同色不同值。
     */
    /** 吸色板 = wiki 的 16 个颜色（深 6 + 浅 6 + 特殊 4）。 */
    val PALETTE: IntArray = (DEEP.values + LIGHT.values).toIntArray() + EXTRA

    /** 距离色板足够近才吸附，太远（特殊卡）保持原色。 */
    fun snap(color: Int): Int {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        var best = color
        var bd = 70 * 70.0
        PALETTE.forEach { p ->
            val dr = (r - Color.red(p)).toDouble()
            val dg = (g - Color.green(p)).toDouble()
            val db = (b - Color.blue(p)).toDouble()
            val d = dr * dr + dg * dg + db * db
            if (d < bd) { bd = d; best = p }
        }
        return best
    }

    /**
     * 底色：只有 6 类（按色相）。淡色也归到同一色系 —— 浅色系只属于「混色」那一套。
     * ⚠️ 不能用 RGB 最近色：浅色之间距离都很近，淡蓝会被判成紫色。
     */
    /** 颜色入库前已吸附到色板，所以直接取最近的标准色名即可。 */
    fun baseOf(c: CardRec): String =
        if (c.special) "特殊" else nearest(c.base, DEEP)

    /** 混色 = 深 6 + 浅 6。 */
    fun mixOf(c: CardRec): String =
        if (c.special) "特殊" else nearest(c.mix, DEEP + LIGHT)
}
