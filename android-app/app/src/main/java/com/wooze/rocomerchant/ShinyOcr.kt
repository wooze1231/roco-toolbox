package com.wooze.rocomerchant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject

/**
 * 截图「认字」——纯本地，不联网、不用任何在线视觉大模型、也不依赖 ML Kit。
 *
 * 流程：
 *  1. 灰度 + 长边压到 1600px 以内。
 *  2. 局部自适应二值化（积分图算均值/标准差，带绝对余量防止平坦背景出噪点）。
 *  3. 先按行投影切出文本行，再按列投影切出单个字；汉字近似方块，
 *     所以把过窄的碎片往左并回去，解决「脑」被切成「月+吉」这类问题。
 *  4. 每个字块拉伸归一化到 32×32，跟内置子集字体现场渲染出的模板做
 *     归一化互相关（NCC），取加权分数最高的字。
 *  5. 识别出的文字 / 数字交给 ShinyCatalog 去匹配精灵。
 *
 * 内置字体 assets/fonts/ocr_subset.ttf 是 simhei 的子集，共 ~3600 字：
 * 图鉴字（含生僻字）∪《通用规范汉字表》一级字表 3500 ∪ 数字。
 *
 * **三档权重**（assets/fonts/ocr_char_tiers.json）：
 * 候选集不再是扁平的，每个字带一个「先验权重」——
 *   图鉴字（含自定义精灵字）  ×1.00，外加 +0.02 偏置  ← 最可能是它
 *   常用字 3500               ×0.85，无偏置
 *   其余（数字等）            ×0.70，−0.02 偏置
 * 最终打分 = NCC × 权重 + 偏置。这样字形相近时优先判给图鉴里真实存在的字，
 * 既保住了「只会是精灵名」的先验，又不会因为新精灵没进图鉴就认不出来。
 */
object ShinyOcr {

    /** 识别出的一个字。 */
    class Glyph(val ch: Char, val score: Float)

    /** 一整行文本。 */
    class Line(val text: String, val score: Float, val box: android.graphics.Rect)

    /** 识别结果：按版面从上到下的所有行。 */
    class Result(val lines: List<Line>, val reason: String = "") {
        /** 全部文字（行之间用空格连接）。 */
        val text: String get() = lines.joinToString(" ") { it.text }

        /** 平均置信度（0~1，用于界面上分档提示）。 */
        val confidence: Float
            get() = if (lines.isEmpty()) 0f else lines.sumOf { it.score.toDouble() }.toFloat() / lines.size

        /** 这一堆文字里最像「编号」的数字串。 */
        val number: Int?
            get() {
                var best: Int? = null
                for (l in lines) {
                    for (m in Regex("\\d{1,4}").findAll(l.text)) {
                        val v = m.value.toIntOrNull() ?: continue
                        if (best == null || m.value.length > best.toString().length) best = v
                    }
                }
                return best
            }
    }

    private const val TW = 32
    private const val TH = 32
    private const val WIN = 24
    private const val K = 0.15f
    private const val MARGIN = 28

    // —— 「宁可认不出，也不吐垃圾」的过滤阈值 ——
    /** 行带（≈字高）下限：低于这个高度的字笔画已糊，直接不认。实测这张 850×803 的图字高仅 14~18。 */
    private const val MIN_LINE_H = 20
    /**
     * 行带高度上限：超过这个高度不可能是「一行字」，而是立绘 / 图标行。
     *
     * 这是实测出来的关键规则 —— 在游戏截图里：
     *   行带高 14~80px  = 名字行（要认）
     *   行带高 100px+   = 立绘 / 图标行（跳过）
     * 尤其当精灵名是**半透明灰白字压在立绘上**时，横投影会把「立绘+名字」黏成
     * 一整行（实测 162~172px），名字根本没被单独切出来过 —— 这种行必须整行跳过，
     * 否则会把立绘纹理当字认（就是「兽兽兽」「蛇蛇」那类噪音的来源）。
     */
    private const val MAX_LINE_H = 96
    /**
     * 「一行字」的高度上限。超过它说明这个行带里**粘了不止一行**：
     * 图鉴里带形态说明的格子，上排是精灵名（实测 22px），下排小字是
     * 「（火山附近的样子）」，两排合成一个 47~48px 的行带。
     * 这时要在带内再切子行分别识别，不能整带当一个字块。
     */
    private const val SINGLE_LINE_H = 34
    /** 单个字块最小宽度（像素），太窄的是笔画碎片。 */
    private const val MIN_SEG_W = 6
    /** 字块宽 / 行高 的上限，超过说明是横向长条（边框、分隔线、图标底座）。 */
    private const val MAX_SEG_AR = 1.9f
    /**
     * **单个字**的最低分：低于此值这个字直接丢掉，**不牵连整行**。
     *
     * 这是第二轮迭代的关键改动。第一版是「整行均分低于 0.45 就整行废弃」，
     * 结果把「小鼓象」「音碟机」这种「名字认出来了、只是旁边混了属性图标」的行
     * 一起误杀了（实测 2.jpg 整行被废、置信度只有 28%）。
     * 改成逐字判定后：图标/分隔线认出来的低分字被丢掉，真名保留。
     *
     * **阈值 0.40 是拿真机实测数据标定的**（不是 PC 镜像的分数！）：
     * 设备侧（Android Canvas 渲染模板 + 抗锯齿，与 PC 光栅化有系统性差异）
     *   · 真名字字符：0.444 ~ 0.630，中位 0.504
     *   · 明显错字/垃圾：0.218 ~ 0.389
     * 谷底在 0.39~0.44 之间，取 0.40。
     *
     * 教训：PC 离线镜像的分数**整体比真机高 ~0.08**，拿镜像分数定阈值会偏高。
     */
    private const val MIN_CHAR_SCORE = 0.40f
    /** 一行里同一个字的最大占比，超过判定为「同一图形被切碎」。 */
    private const val MAX_DUP_RATIO = 0.5f
    /** 逐字过滤后一行至少剩几个字才算有效（少于这个数多半是杂图案）。 */
    private const val MIN_KEPT_CHARS = 2

    // —— 三档权重参数 ——
    /** 图鉴字（含自定义精灵字）：权重最高，并给一点正向偏置。 */
    private const val W_DEX = 1.00f
    private const val B_DEX = 0.02f
    /** 常用字 3500：中等权重，持平。 */
    private const val W_COMMON = 0.85f
    private const val B_COMMON = 0f
    /** 其余（数字、符号）：最低，略微压一点，避免抢走汉字。 */
    private const val W_OTHER = 0.70f
    private const val B_OTHER = -0.02f

    private var templateCache: LruCache<Char, ByteArray>? = null
    private var typeface: Typeface? = null

    /** 候选字 + 其所属档次（0=图鉴 / 1=常用 / 2=其余）。 */
    private class Pool(val chars: CharArray, val tier: ByteArray)

    @Volatile private var poolCache: Pool? = null

    /**
     * 作废候选池缓存。
     *
     * **图鉴刷新后必须调用**：刷新会往 `ShinyCatalog.entries()` 里塞进新精灵，
     * 它们的名字里可能有「分层表还没收录的新字」。如果不清缓存，池子永远是旧的一份，
     * 新精灵的字就进不了最高权重档 —— 这正是「刷新了图鉴却还是认不出新精灵」的原因。
     *
     * 这是 assets 只读约束下能做到的**等价提权**：
     * 字体文件改不了，但候选池与权重档是运行时可重建的。
     */
    fun invalidatePool() {
        poolCache = null
        templateCache?.evictAll()
    }

    /**
     * 候选字符集：图鉴字（最高权重）+ 常用字 3500（兜底）+ 数字。
     *
     * 分层表来自 assets/fonts/ocr_char_tiers.json（由 build_subset_font.py 随字体一起导出），
     * 保证「认得出图鉴精灵名」的同时，新精灵的常见字也有字可用。
     */
    private fun pool(c: Context): Pool {
        poolCache?.let { return it }
        // 1) 打底：常用字（含数字）——万一分层表读不到，至少还有这一层
        val common = LinkedHashSet<Char>()
        val dex = LinkedHashSet<Char>()
        try {
            val txt = c.assets.open("fonts/ocr_char_tiers.json").bufferedReader().readText()
            val tiers = JSONObject(txt).optJSONObject("tiers")
            tiers?.optString("dex", "")?.let { for (ch in it) dex.add(ch) }
            tiers?.optString("common", "")?.let { for (ch in it) common.add(ch) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // 2) 图鉴里的字强制并入最高档（分层表可能滞后于图鉴更新）
        try {
            val txt = c.assets.open("shiny_catalog.json").bufferedReader().readText()
            val arr = JSONArray(txt)
            for (i in 0 until arr.length()) {
                val n = arr.getJSONObject(i).optString("n", "")
                for (ch in n) if (isCjk(ch)) dex.add(ch)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // 2a) ★ 联网刷新过 / 用户补录过的图鉴同样计入最高档。
        //     早先只读 assets，导致「刷新图鉴拿到新精灵，新精灵的字却不在候选池里」，
        //     认不出新精灵的字。改读 ShinyCatalog.entries()（已含 assets + 刷新 + 自定义三层合并）。
        try {
            for (e in ShinyCatalog.entries(c)) for (ch in e.name) if (isCjk(ch)) dex.add(ch)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // 2b) 玩家习惯叫法（别名）里的字也算最高档——截图里可能写的就是通称
        try {
            for (ch in ShinyCatalog.aliasChars()) if (isCjk(ch)) dex.add(ch)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // 2c) 自定义精灵（用户手动补录的）同样进最高档
        try {
            for (name in ShinyCatalog.customNames(c)) for (ch in name) if (isCjk(ch)) dex.add(ch)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // 2d) ★ BWIKI 形态词（首领/异敌/蓄水/蜕皮/彩玉球…）也进最高档。
        //     截图里写的是「丢丢（火山附近的样子）」这类带形态的全名，
        //     形态词认不出来就等于整个名字认不出来。
        try {
            for (w in ShinyCatalog.bwikiFormWords(c)) for (ch in w) if (isCjk(ch)) dex.add(ch)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (dex.isEmpty() && common.isEmpty()) {
            for (ch in "0123456789的一是不了在人有我") { dex.add(ch) }
        }
        // 3) 组装：图鉴档优先，其次常用档，最后数字/符号档
        //    注意：标点 / 括号 / 引号一律不进候选集 —— 它们只会出现在 UI 装饰里，
        //    被认出来纯属噪音（旧版会把「」「（）」当成识别结果）。
        val chars = LinkedHashSet<Char>()
        for (ch in "0123456789") chars.add(ch)
        for (ch in dex) if (isCjk(ch)) chars.add(ch)
        for (ch in common) if (isCjk(ch)) chars.add(ch)
        val arr = chars.toCharArray()
        val tier = ByteArray(arr.size)
        for (i in arr.indices) {
            val ch = arr[i]
            tier[i] = when {
                dex.contains(ch) -> 0
                common.contains(ch) -> 1
                else -> 2
            }
        }
        val p = Pool(arr, tier)
        poolCache = p
        return p
    }

    private fun isCjk(ch: Char): Boolean =
        ch.code in 0x3400..0x9fff || ch.code in 0xff00..0xffef

    private fun face(c: Context): Typeface? {
        typeface?.let { return it }
        val f = try {
            Typeface.createFromAsset(c.assets, "fonts/ocr_subset.ttf")
        } catch (e: Exception) {
            null
        }
        typeface = f
        return f
    }

    /**
     * 识别截图里的文字。必须在子线程调用（要渲染几百个模板，一般几百毫秒）。
     *
     * 过滤策略（v1.0.0 迭代过两轮，**逐字过滤 > 整行废弃**）：
     *  ① 行高区间 —— 太矮（笔画糊）或太高（立绘/图标行）整行跳过；
     *  ② 字块长宽比 —— 只有近似方块的块才可能是汉字，图标/边框/分隔线丢掉；
     *  ③ **逐字丢弃低分字** —— 每个字单独看 NCC，低于阈值的那个字直接扔掉，
     *     **不牵连整行**。这是关键：像「小鼓象」这种名字旁边混了属性图标，
     *     只该丢掉图标那几块，不该把「小鼓象」一起废掉；
     *  ④ 整行复读率 —— 同字占比过高（「蛇蛇」「兽兽兽」）说明同一块图形被切碎，整行废；
     *  ⑤ 整行有效字下限 —— 逐字丢完还剩几个字？太少说明这行基本是图案，整行废。
     *
     * 注意：行高区间过滤在 [textLines] 里就做了（早退，省掉切字与模板匹配），
     * 这里只统计原因，避免对「立绘行」白跑一遍全字符集匹配。
     */
    fun recognize(c: Context, src: Bitmap): Result {
        val face = face(c) ?: return Result(emptyList())
        val gray = toGray(src, 1600)
        val mask = binarize(gray.px, gray.w, gray.h)
        val rows = textLines(mask, gray.w, gray.h)
        val pool = pool(c)
        val lines = mutableListOf<Line>()
        var noLine = 0   // 行带太矮被丢
        var bigLine = 0  // 行带太高（立绘/图标行）被丢
        var noSeg = 0    // 逐字过滤后一个字都不剩
        var dupLine = 0  // 复读率过高被丢
        var fewLine = 0  // 有效字太少被丢
        for (row in rows) {
            // ① 行高过滤：太矮（笔画糊）或太高（立绘/图标行）都不是「一行字」
            val rowH = row.y1 - row.y0
            if (rowH < MIN_LINE_H) { noLine++; continue }
            if (rowH > MAX_LINE_H) { bigLine++; continue }

            val kept = mutableListOf<Char>()   // 通过了逐字过滤的字
            var sum = 0f
            var minX = Int.MAX_VALUE; var maxX = 0
            var minY = Int.MAX_VALUE; var maxY = 0
            for (seg in row.segs) {
                val w0 = seg[1] - seg[0]
                // ② 字块长宽比：太瘦/太扁的块不是汉字（汉字是方块字）
                if (w0 < MIN_SEG_W || w0 > rowH * MAX_SEG_AR) continue
                val sample = normalize(mask, gray.w, seg[0], seg[1], row.y0, row.y1)
                val g = bestMatch(face, pool, sample) ?: continue
                // ③ 逐字过滤：这个字本身认得够清楚才留下，不牵连整行
                if (g.score < MIN_CHAR_SCORE) continue
                kept.add(g.ch)
                sum += g.score
                minX = minOf(minX, seg[0]); maxX = maxOf(maxX, seg[1])
                minY = minOf(minY, row.y0); maxY = maxOf(maxY, row.y1)
            }
            if (kept.isEmpty()) { noSeg++; continue }

            val text = kept.joinToString("")
            // ④ 整行复读率：同一个字反复出现 = 把同一块图形切碎了，整行不可信
            val dup = kept.groupingBy { it }.eachCount().values.maxOrNull() ?: 0
            if (kept.size >= 2 && dup.toFloat() / kept.size > MAX_DUP_RATIO) { dupLine++; continue }

            // ⑤ 有效字下限：逐字丢完只剩一两个字，多半是杂图案而不是名前缀
            if (kept.size < MIN_KEPT_CHARS) { fewLine++; continue }

            lines.add(
                Line(text, sum / kept.size, android.graphics.Rect(minX, minY, maxX, maxY))
            )
        }
        val reason = when {
            lines.isNotEmpty() -> ""
            rows.isEmpty() -> "没找到任何像文字的横向笔画带（图里可能根本没有字）。"
            bigLine >= rows.size -> "图里的横带都是「立绘 / 图标行」（高度超 $MAX_LINE_H px），" +
                "说明精灵名是压在立绘上的、没被单独切出来，认不了。"
            noLine > 0 && noLine >= rows.size -> "找到 ${rows.size} 处疑似文字，但都太小了" +
                "（字高不足 $MIN_LINE_H px），笔画已经糊在一起，认出来也全是错的。"
            dupLine > 0 -> "找到文字带，但里面的字被切碎了（同一个字重复出现），无法判定。"
            fewLine > 0 || noSeg > 0 -> "找到文字带，但认准的字太少（多半是图标、分隔线），" +
                "拼不出完整的名字。"
            else -> "没找到可用的文字行。"
        }
        return Result(lines, reason)
    }

    // ——————————————————————————————————————————————
    // 图像处理
    // ——————————————————————————————————————————————

    private class Gray(val px: IntArray, val w: Int, val h: Int)

    private fun toGray(src: Bitmap, maxSide: Int): Gray {
        val scale = if (maxOf(src.width, src.height) > maxSide)
            maxSide.toFloat() / maxOf(src.width, src.height) else 1f
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val bmp = if (w == src.width && h == src.height) src
        else Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        if (bmp !== src) bmp.recycle()
        val g = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            g[i] = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)).toInt()
        }
        return Gray(g, w, h)
    }

    /**
     * 局部自适应二值化：true = 笔画（前景比局部背景暗）。
     * 用积分图算局部均值与平方均值，O(n)；阈值取 min(均值 - K*标准差, 均值 - MARGIN)，
     * 后者保证平坦背景不会把噪声判成笔画。
     */
    private fun binarize(px: IntArray, w: Int, h: Int): BooleanArray {
        // 标准二维前缀和，下标 (y*(w+1)+x)，行列从 1 开始
        val sum = LongArray((w + 1) * (h + 1))
        val sum2 = LongArray((w + 1) * (h + 1))
        for (y in 1..h) {
            for (x in 1..w) {
                val v = px[(y - 1) * w + (x - 1)].toLong()
                val i = y * (w + 1) + x
                val a = (y - 1) * (w + 1) + x
                val b = y * (w + 1) + (x - 1)
                val cc = (y - 1) * (w + 1) + (x - 1)
                sum[i] = sum[a] + sum[b] - sum[cc] + v
                sum2[i] = sum2[a] + sum2[b] - sum2[cc] + v * v
            }
        }

        fun rect(s: LongArray, x0: Int, y0: Int, x1: Int, y1: Int): Long =
            s[y1 * (w + 1) + x1] - s[y0 * (w + 1) + x1] - s[y1 * (w + 1) + x0] + s[y0 * (w + 1) + x0]

        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            val y0 = (y - WIN).coerceAtLeast(0)
            val y1 = (y + WIN + 1).coerceAtMost(h)
            for (x in 0 until w) {
                val x0 = (x - WIN).coerceAtLeast(0)
                val x1 = (x + WIN + 1).coerceAtMost(w)
                val cnt = (x1 - x0).toDouble() * (y1 - y0)
                val s = rect(sum, x0, y0, x1, y1).toDouble()
                val s2 = rect(sum2, x0, y0, x1, y1).toDouble()
                val mean = s / cnt
                val varv = (s2 / cnt) - mean * mean
                val std = if (varv > 0) Math.sqrt(varv) else 0.0
                val byStd = mean - K * std
                val floor = mean - MARGIN
                val thr = if (byStd < floor) byStd else floor
                out[y * w + x] = px[y * w + x] < thr
            }
        }
        return out
    }

    /** 文本行：行范围 + 该行内切出的字块 [x0, x1)。 */
    private class Row(val y0: Int, val y1: Int, val segs: List<IntArray>)

    private fun textLines(mask: BooleanArray, w: Int, h: Int): List<Row> {
        val rowInk = IntArray(h)
        for (y in 0 until h) {
            var c = 0
            val base = y * w
            for (x in 0 until w) if (mask[base + x]) c++
            rowInk[y] = c
        }
        val rowThr = maxOf(2, (w * 0.008f).toInt())
        val bands = mutableListOf<IntArray>()
        var y = 0
        while (y < h) {
            if (rowInk[y] > rowThr) {
                val y0 = y
                while (y < h && rowInk[y] > rowThr) y++
                bands.add(intArrayOf(y0, y))
            } else {
                y++
            }
        }
        val out = mutableListOf<Row>()
        for (b in bands) {
            val y0 = b[0]; val y1 = b[1]
            // 行带高 = 字高基准。整行跳过太矮/太高的带（名字行 ≈ 14~80px，立绘行 ≥100px）
            val rowH = y1 - y0
            if (rowH < MIN_LINE_H || rowH > MAX_LINE_H) continue

            // 行带里可能是**两行字**：图鉴中带形态说明的单元格，
            // 上面一行是精灵名（≈22px），下面一行小字是「（火山附近的样子）」，
            // 两行粘成一个 47px 的带。直接按整带切字会把两行压进一个样本，必错。
            // 所以高过单行上限时，在带内再按横投影切子行，各自单独识别。
            if (rowH > SINGLE_LINE_H) {
                for (sub in splitSubLines(rowInk, y0, y1)) {
                    val (s0, s1) = sub
                    if (s1 - s0 < MIN_LINE_H) continue
                    val segs = segsFor(mask, w, s0, s1)
                    if (segs.isNotEmpty()) out.add(Row(s0, s1, segs))
                }
                continue
            }

            val segs = segsFor(mask, w, y0, y1)
            if (segs.isNotEmpty()) out.add(Row(y0, y1, segs))
        }
        return out
    }

    /** 按 x 方向投影切字，供行带 / 子行共用。 */
    private fun segsFor(mask: BooleanArray, w: Int, y0: Int, y1: Int): List<IntArray> {
        val colInk = IntArray(w)
        for (x in 0 until w) {
            var c = 0
            for (yy in y0 until y1) if (mask[yy * w + x]) c++
            colInk[x] = c
        }
        return segments(colInk, w, y1 - y0)
    }

    /**
     * 在一个行带内部再切子行：找带内「几乎没笔画」的横缝当分隔。
     * 找不到横缝就整带返回（说明本来就是一行，只是字比较大）。
     */
    private fun splitSubLines(rowInk: IntArray, y0: Int, y1: Int): List<IntArray> {
        val peak = (y0 until y1).maxOfOrNull { rowInk[it] } ?: 0
        if (peak <= 0) return listOf(intArrayOf(y0, y1))
        // 横缝阈值：带内最低谷 + 峰值的 12%，但不超过峰值的 35%（免得整带都算成缝）
        val minInk = (y0 until y1).minOfOrNull { rowInk[it] } ?: 0
        val thr = minOf(minInk + maxOf(1, (peak * 0.12f).toInt()), (peak * 0.35f).toInt())

        val gaps = mutableListOf<Int>()
        var runStart = -1
        for (y in y0 until y1) {
            if (rowInk[y] <= thr) {
                if (runStart < 0) runStart = y
            } else {
                // 连续低谷够厚才算「行间的缝」（至少 2px）
                if (runStart >= 0 && y - runStart >= 2) gaps.add((runStart + y) / 2)
                runStart = -1
            }
        }

        val cuts = mutableListOf<Int>()
        for (g in gaps) {
            // 切出来的子行都得够一行字高，否则这个缝不采信
            if (g - y0 >= MIN_LINE_H && y1 - g >= MIN_LINE_H) cuts.add(g)
        }
        if (cuts.isEmpty()) return listOf(intArrayOf(y0, y1))

        val out = mutableListOf<IntArray>()
        var s = y0
        for (c in cuts) {
            if (c > s) out.add(intArrayOf(s, c))
            s = c
        }
        if (y1 > s) out.add(intArrayOf(s, y1))
        return out
    }

    /**
     * 竖投影切字 + 合并碎片。
     * 汉字近似方块、字宽≈行高，所以把明显过窄的段往邻居并回去，
     * 解决「脑」被竖投影切成「月+吉」这种问题。
     */
    private fun segments(colInk: IntArray, w: Int, lineH: Int): List<IntArray> {
        val raw = mutableListOf<IntArray>()
        var x = 0
        while (x < w) {
            if (colInk[x] > 0) {
                val x0 = x
                while (x < w && colInk[x] > 0) x++
                raw.add(intArrayOf(x0, x))
            } else {
                x++
            }
        }
        if (raw.isEmpty()) return raw
        // 字宽基准：行高与最宽段取大者（最宽段通常就是一个完整的字）
        var widest = 0
        for (s in raw) widest = maxOf(widest, s[1] - s[0])
        val base = maxOf(lineH, widest).coerceAtLeast(6)

        val merged = mutableListOf<IntArray>()
        for (s in raw) {
            if (merged.isEmpty()) { merged.add(s); continue }
            val prev = merged.last()
            val prevW = prev[1] - prev[0]
            val combined = s[1] - prev[0]
            if (prevW < base * 0.62f && combined <= base * 1.25f) {
                prev[1] = s[1]
            } else {
                merged.add(s)
            }
        }
        // 再扫一遍：开头/结尾仍有孤立窄段，就并给它唯一的邻居
        val out = mutableListOf<IntArray>()
        var i = 0
        while (i < merged.size) {
            val s = merged[i]
            if (s[1] - s[0] < base * 0.5f && i + 1 < merged.size) {
                merged[i + 1][0] = s[0]
                i++
                continue
            }
            out.add(s)
            i++
        }
        return out
    }

    /**
     * 把 [x0,x1) × [y0,y1) 里的笔画抠出来，拉伸归一化到 32×32。
     * 汉字是方块字，直接拉伸到正方（不保持长宽比）判别力最强。
     */
    private fun normalize(
        mask: BooleanArray, w: Int, x0: Int, x1: Int, y0: Int, y1: Int
    ): ByteArray {
        var minY = Int.MAX_VALUE
        var maxY = -1
        for (y in y0 until y1) {
            val base = y * w
            for (x in x0 until x1) {
                if (mask[base + x]) {
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        val out = ByteArray(TW * TH)
        if (maxY < 0) return out
        val sw = x1 - x0
        val sh = maxY - minY + 1
        for (ty in 0 until TH) {
            var sy = minY + ty * sh / TH
            if (sy > maxY) sy = maxY
            val sbase = sy * w
            for (tx in 0 until TW) {
                var sx = x0 + tx * sw / TW
                if (sx >= x1) sx = x1 - 1
                if (mask[sbase + sx]) out[ty * TW + tx] = 1
            }
        }
        return out
    }

    /** 渲染候选字符的模板（带缓存）。 */
    private fun template(face: Typeface, ch: Char): ByteArray {
        val cache = templateCache ?: LruCache<Char, ByteArray>(800).also { templateCache = it }
        cache.get(ch)?.let { return it }
        val side = 96
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = side * 0.88f
            typeface = face
            textAlign = Paint.Align.CENTER
        }
        val m = p.fontMetrics
        val baseline = side / 2f - (m.ascent + m.descent) / 2f
        cv.drawText(ch.toString(), side / 2f, baseline, p)

        val px = IntArray(side * side)
        bmp.getPixels(px, 0, side, 0, 0, side, side)
        bmp.recycle()
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE
        var maxX = -1; var maxY = -1
        for (y in 0 until side) {
            val base = y * side
            for (x in 0 until side) {
                if (Color.red(px[base + x]) < 128) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        val out = ByteArray(TW * TH)
        if (maxX >= 0) {
            val sw = maxX - minX + 1
            val sh = maxY - minY + 1
            for (ty in 0 until TH) {
                var sy = minY + ty * sh / TH
                if (sy > maxY) sy = maxY
                val sbase = sy * side
                for (tx in 0 until TW) {
                    var sx = minX + tx * sw / TW
                    if (sx > maxX) sx = maxX
                    if (Color.red(px[sbase + sx]) < 128) out[ty * TW + tx] = 1
                }
            }
        }
        cache.put(ch, out)
        return out
    }

    /** 归一化互相关：对二值笔画来说比直接重合率稳，不受笔画粗细影响。 */
    private fun ncc(a: ByteArray, b: ByteArray): Float {
        var sa = 0; var sb = 0
        for (i in a.indices) { sa += a[i]; sb += b[i] }
        val ma = sa.toFloat() / a.size
        val mb = sb.toFloat() / b.size
        var num = 0f; var da = 0f; var db = 0f
        for (i in a.indices) {
            val xa = a[i] - ma
            val xb = b[i] - mb
            num += xa * xb
            da += xa * xa
            db += xb * xb
        }
        val den = Math.sqrt((da * db).toDouble()).toFloat()
        return if (den > 1e-6f) num / den else 0f
    }

    private fun bestMatch(face: Typeface, pool: Pool, sample: ByteArray): Glyph? {
        var bestCh = '?'
        var best = -2f
        var bestRaw = 0f
        val chars = pool.chars
        for (i in chars.indices) {
            val ch = chars[i]
            val raw = ncc(sample, template(face, ch))
            // 三档加权：字形相近时，优先判给图鉴里真实存在的字
            val s = when (pool.tier[i].toInt()) {
                0 -> raw * W_DEX + B_DEX
                1 -> raw * W_COMMON + B_COMMON
                else -> raw * W_OTHER + B_OTHER
            }
            if (s > best) {
                best = s
                bestCh = ch
                bestRaw = raw
            }
        }
        // 对外一律还原成「原始 NCC」口径，避免权重参数泄漏到界面文案里
        return if (best <= 0f) null else Glyph(bestCh, bestRaw.coerceIn(0f, 1f))
    }

    /**
     * 从相册/文件选到的 Uri 读一张位图，并缩放（否则大图容易 OOM）。
     * 只读一遍 bounds，再按 2 的幂采样。
     */
    fun loadScaled(c: Context, uri: android.net.Uri, maxPx: Int = 1600): Bitmap? {
        return try {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, opts)
            }
            var scale = 1
            while (opts.outWidth / scale > maxPx || opts.outHeight / scale > maxPx) scale *= 2
            val o2 = android.graphics.BitmapFactory.Options().apply { inSampleSize = scale }
            c.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, o2)
            }
        } catch (e: Exception) {
            null
        }
    }
}
