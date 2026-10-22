package com.wooze.rocomerchant

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * 异色精灵「告知」识别服务。
 *
 * 识别顺序：内置库（assets/shiny_catalog.json，离线可用）
 *          -> 联网刷新罗可图鉴（游戏更新后的新精灵也能认出来）
 * 三态结果：
 *  - Ok：唯一命中
 *  - Ambiguous：多个候选 -> 回退，把候选和原因告知用户，由用户确认
 *  - NotFound：库里没有 -> 回退并告知原因（已联网刷新过 / 网络失败）
 */
object ShinyCatalog {

    class Entry(val name: String, val slug: String, val py: String, val icon: String, val no: Int)

    sealed class Result {
        class Ok(val hit: Entry, val fromNetwork: Boolean) : Result()
        class Ambiguous(val query: String, val hits: List<Entry>, val reason: String) : Result()
        class NotFound(val query: String, val reason: String) : Result()
    }

    /** 玩家习惯叫法 -> 罗可图鉴正式名（手动整理的别名）。 */
    private val ALIAS = mapOf(
        "拿脑丸" to "章脑丸",
        "芽眼魇" to "芽眼魔",
        "叮叮卵" to "叮叮卯",
        "刺轮砣（上弦）" to "刺轮砣（上弦的样子）",
        "刺轮砣（下弦）" to "刺轮砣（下弦的样子）"
    )

    /** 反向别名：图鉴正式名 -> 玩家更常用的叫法（用于把记录里的名字改回通称）。 */
    private val ALIAS_REVERSE: Map<String, String> = ALIAS.entries.associate { (k, v) -> v to k }

    // —— BWIKI 形态（第 6 步·标准名接入运行时）——

    /**
     * BWIKI 立绘文件名里带的「形态」信息（assets/bwiki_forms.json）。
     *
     * BWIKI 用拼音后缀区分形态：`JL_diudiu_huoshan.png` = 丢丢(火山)、
     * `JL_bengbenghua_shouling.png` = 蹦蹦花(首领)。图鉴站则写成
     * 「丢丢（火山附近的样子）」这种带括号的全名。
     *
     * 两者的**基础名一致、形态词一致，只是写法不同**。这里把 BWIKI 的形态词
     * 建成候选名，让下面这些写法都能对上同一只：
     *   · 截图/输入写「丢丢（火山附近的样子）」→ 图鉴原名，本来就对
     *   · 写「丢丢火山」/「丢丢 火山」→ 靠形态别名补上括号与「附近的样子」
     *   · 写「蹦蹦花首领」→ 同样成立
     */
    private class Forms(
        val suffixCn: Map<String, String>,   // 拼音后缀 -> 中文形态词
        val forms: Map<String, List<String>>, // 基础拼音 -> 该精灵有哪些形态词
        val yise: Set<String>                 // BWIKI 有异色立绘的拼音
    )

    @Volatile private var formsCache: Forms? = null

    private fun forms(c: Context): Forms {
        formsCache?.let { return it }
        val suffix = HashMap<String, String>()
        val fm = HashMap<String, List<String>>()
        val ys = HashSet<String>()
        try {
            val txt = c.assets.open("bwiki_forms.json").bufferedReader().readText()
            val o = org.json.JSONObject(txt)
            o.optJSONObject("suffix")?.let { s ->
                for (k in s.keys()) suffix[k] = s.optString(k, "")
            }
            o.optJSONObject("forms")?.let { f ->
                for (k in f.keys()) {
                    val a = f.optJSONArray(k) ?: continue
                    fm[k] = (0 until a.length()).map { a.optString(it, "") }.filter { it.isNotBlank() }
                }
            }
            o.optJSONArray("yise")?.let { a ->
                for (i in 0 until a.length()) ys.add(a.optString(i, "").lowercase())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val f = Forms(suffix, fm, ys)
        formsCache = f
        return f
    }

    /** BWIKI 的所有形态词（首领/异敌/蓄水…），给 OCR 补字符集用。 */
    fun bwikiFormWords(c: Context): Collection<String> = forms(c).suffixCn.values.toSet()

    /**
     * 这只精灵在 BWIKI 上有没有异色立绘（决定界面要不要显示「异色预览」）。
     *
     * 形态变体要**回退到基础拼音**再查：图鉴里 `地鼠（储水时的样子）` 的 slug 是
     * `dishu-chushuishideyangzi`，而 BWIKI 的异色表只有 `dishu`。
     * 少了这步会漏掉 8 只形态变体（120 → 128）。
     */
    fun hasBwikiShiny(c: Context, slug: String, py: String = ""): Boolean {
        val f = forms(c)
        fun inYise(k: String) = k.isNotBlank() && f.yise.contains(k)
        val s = slug.lowercase().trim()
        val p = py.lowercase().trim()
        if (inYise(s) || inYise(p)) return true
        // 回退：切掉形态后缀（`dishu-chushuishideyangzi` → `dishu`）
        return inYise(baseSlug(s)) || inYise(baseSlug(p))
    }

    /** 从 slug / 拼音里切出基础段（第一个 `-` 之前）。 */
    private fun baseSlug(s: String): String {
        val d = s.indexOf('-')
        return if (d > 0) s.substring(0, d) else s
    }

    /**
     * 别名里出现过的所有汉字（含正式名一侧）。
     * 给 OCR 的候选集分层用——别名也是玩家可能截到的字，得算进最高权重档。
     */
    fun aliasChars(): Set<Char> {
        val s = HashSet<Char>()
        for ((k, v) in ALIAS) {
            for (ch in k) s.add(ch)
            for (ch in v) s.add(ch)
        }
        return s
    }

    /** 同一只精灵的「其他写法」（双向都算同义），用于重名合并与查重。 */
    fun synonymous(a: String, b: String): Boolean {
        if (a == b) return true
        return ALIAS[a] == b || ALIAS[b] == a
    }

    /** 把图鉴正式名转成玩家常用名（如 章脑丸 -> 拿脑丸）；没有反向别名就原样返回。 */
    fun commonName(official: String): String = ALIAS_REVERSE[official] ?: official

    /**
     * 识图（OCR）出来的文字 -> 最像的图鉴条目。
     *
     * OCR 不可避免会有个别字认错（比如 荆棘 → 芽棘），所以这里不做精确匹配，
     * 而是用「最长公共子序列比例 + 长度接近度」打分，返回若干候选让用户确认。
     * 同时把出现的数字当作编号来试。
     *
     * **关键：必须逐行比，不能把整段拼成一个长串比。**
     * OCR 的每一行对应一只精灵的名字（2.jpg 这种图里一屏有 3~4 行名字）。
     * 早先的实现把 `「叮花架 平鸽贝小夜小独角鲁 丑公小象音碟花伯」` 拼成 20 字再
     * 跟「小独角兽」比，LCS 分母被 20 撑大，**再好的匹配也被稀释到 0.16**，
     * 全都跌破 0.35 门槛 → 返回空 → 明明认出了名字却弹「没能识别」。
     * 逐行比时同一条匹配是 0.361，能正常进候选。所以这里对每一行分别打分取最好。
     *
     * @param raw OCR 原文（多行，每行一只精灵名，可能夹着杂字）
     * @param topN 返回多少个候选
     */
    fun bestMatches(c: Context, raw: String, topN: Int = 6): List<Entry> {
        val list = entries(c)
        if (raw.isBlank()) return emptyList()
        // 按行拆开；一行 = 一只精灵的名字（OCR 用空格/换行分行）
        val lines = raw.split(' ', '\n', '\t', '　')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()
        // 整段（去空格）也保留一份，用于「名字被切碎跨行」的兜底
        val compactAll = lines.joinToString("")

        val scored = mutableListOf<Pair<Entry, Float>>()
        for (e in list) {
            val n = e.name
            if (n.length < 2) continue
            val real = ALIAS[n] ?: n
            var best = 0f
            // 逐行打分：取「某一行最像这只」的分数
            for (line in lines) {
                best = maxOf(best, scoreOne(line, n, real))
            }
            // 兜底：整段拼起来再试一次（应对名字跨行、或整段就是一只的情况）
            best = maxOf(best, scoreOne(compactAll, n, real) * 0.9f)
            // 编号命中直接给高分
            if (e.no > 0 && Regex("(^|\\D)${e.no}(\\D|$)").containsMatchIn(compactAll)) {
                best = maxOf(best, 0.98f)
            }
            if (best > MATCH_THRESHOLD) scored.add(e to best)
        }
        scored.sortByDescending { it.second }
        return scored.take(topN).map { it.first }
    }

    /**
     * 一屏多只：把 OCR 结果**按行**各自出候选。
     *
     * 一张截图里常有 3~4 只异色精灵，OCR 也正好按行输出（一行一只）。
     * 这个方法对每一行独立调用 [bestMatchesForLine]，返回 `行原文 -> 该行候选`。
     * 识别不到的行直接跳过，不占位置。
     *
     * @param maxPerLine 每行最多给几个候选（多了用户挑花眼）
     */
    fun bestMatchesByLine(c: Context, raw: String, maxPerLine: Int = 4): List<Pair<String, List<Entry>>> {
        val lines = raw.split(' ', '\n', '\t', '　')
            .map { it.trim() }
            .filter { it.length >= 2 }
        if (lines.isEmpty()) return emptyList()
        val list = entries(c)
        val out = mutableListOf<Pair<String, List<Entry>>>()
        for (line in lines) {
            val hits = bestMatchesForLine(list, line, maxPerLine)
            if (hits.isNotEmpty()) out.add(line to hits)
        }
        return out
    }

    /** 单行 -> 候选列表（供 [bestMatchesByLine] 用；内部复用同一套打分）。 */
    private fun bestMatchesForLine(list: List<Entry>, line: String, topN: Int): List<Entry> {
        val scored = mutableListOf<Pair<Entry, Float>>()
        for (e in list) {
            val n = e.name
            if (n.length < 2) continue
            val real = ALIAS[n] ?: n
            val s = scoreOne(line, n, real)
            if (s > MATCH_THRESHOLD) scored.add(e to s)
        }
        scored.sortByDescending { it.second }
        return scored.take(topN).map { it.first }
    }

    /**
     * 联想搜索：边打边出候选。
     *
     * 与 [matchQueries]（提交后的正式对应）不同，这里追求**快速、宽松、按相关度排序**：
     * 如果首字都没打，就按编号给出前几只（方便直接翻）；
     * 打得越多，越倾向「名字以它开头」的条目（前缀匹配 > 包含 > 拼音）。
     *
     * @param limit 最多返回几条
     */
    fun suggest(c: Context, query: String, limit: Int = 20): List<Entry> {
        val q = query.trim().removePrefix("#").removePrefix("No.").removePrefix("no.").trim()
        val all = entries(c)
        if (q.isEmpty()) {
            // 空输入：按编号给前几只，让用户能直接翻
            return all.filter { it.no > 0 }.sortedBy { it.no }.take(limit)
        }
        // 纯数字优先当编号
        if (q.all { it.isDigit() }) {
            val byNo = all.filter { it.no.toString().startsWith(q) }.sortedBy { it.no }
            if (byNo.isNotEmpty()) return byNo.take(limit)
        }
        val lq = q.lowercase()
        // 打分：前缀命中 3 分 > 包含 2 分 > 拼音 1 分；越短的名字排越前（更可能是用户想找的）
        val scored = mutableListOf<Pair<Entry, Int>>()
        for (e in all) {
            val n = e.name
            val real = ALIAS[n] ?: n
            val hit = when {
                n.startsWith(q) || real.startsWith(q) -> 3
                n.contains(q) || real.contains(q) -> 2
                e.py.lowercase().startsWith(lq) || e.slug.lowercase().startsWith(lq) -> 1
                else -> 0
            }
            if (hit > 0) scored.add(e to hit)
        }
        // ★ BWIKI 形态写法也进联想（打「丢丢火山」时要能带出「丢丢（火山附近的样子）」）
        for (e in formsAliasMatch(all, q)) {
            if (scored.none { it.first === e }) scored.add(e to 3)
        }
        scored.sortWith(compareByDescending<Pair<Entry, Int>> { it.second }.thenBy { it.first.name.length })
        return scored.take(limit).map { it.first }
    }

    /**
     * 批量文本输入时，把用户**手打**的一个名字/编号对应到图鉴条目。
     *
     * 与 OCR 用的 [bestMatchesForLine] 有本质区别：
     * **手打是准确的**，所以要「精确优先」，而不是一上来就模糊打分 ——
     * 否则用户打「小夜」，模糊匹配可能把「小夜」和「小夜莺」并列，反而添乱。
     *
     * 顺序：编号 → 精确名（含别名）→ 拼音/slug → 子串 → 模糊兜底。
     * 前面的阶段一旦有结果就立即返回，不再往下走。
     */
    fun matchQueries(list: List<Entry>, q: String): List<Entry> {
        val query = q.trim().removePrefix("#").removePrefix("No.").removePrefix("no.").trim()
        if (query.isBlank()) return emptyList()
        // ① 编号（支持 "226" / "#226" / "No.226"）
        query.toIntOrNull()?.let { n ->
            val byNo = list.filter { it.no == n }
            if (byNo.isNotEmpty()) return byNo
        }
        // ② 精确名 / 别名
        val real = ALIAS[query] ?: query
        list.firstOrNull { it.name == query }?.let { return listOf(it) }
        if (real != query) list.firstOrNull { it.name == real }?.let { return listOf(it) }
        // ③ 拼音 / slug
        list.firstOrNull { it.py.equals(real, true) || (it.slug.isNotBlank() && it.slug.equals(real, true)) }
            ?.let { return listOf(it) }
        // ④ 子串（"小独角" -> "小独角兽"）
        val sub = list.filter { it.name.contains(real) || real.contains(it.name) }
        if (sub.isNotEmpty()) return sub.take(6)
        // ④b ★ BWIKI 形态：把「丢丢火山」「蹦蹦花首领」这类写法对上带括号的图鉴全名。
        //     做法是把查询词拆成「基础名 + 形态词」，在图鉴里找「基础名 + （形态…）」的条目。
        formsAliasMatch(list, query).let { if (it.isNotEmpty()) return it }
        // ⑤ 模糊兜底（打错一两个字也能救回来）
        return bestMatchesForLine(list, query, 4)
    }

    /**
     * BWIKI 形态写法 -> 图鉴带括号全名。
     *
     * `丢丢火山` / `丢丢 火山` / `丢丢（火山）` 都应该能对上
     * 图鉴的「丢丢（火山附近的样子）」。判据是：图鉴名以基础名开头，
     * 且形态词出现在名字里。
     */
    private fun formsAliasMatch(list: List<Entry>, query: String): List<Entry> {
        val q = query.replace("（", "").replace("）", "").replace("(", "").replace(")", "")
            .replace(" ", "").replace("的", "").replace("样子", "").trim()
        if (q.length < 3) return emptyList()
        val hits = list.filter { e ->
            val nm = e.name
            // 基础名要在开头（避免「小夜」误配到别的含「夜」的名字）
            val base = nm.substringBefore("（").substringBefore("(")
            if (base.isEmpty() || !q.startsWith(base)) return@filter false
            val form = nm.substringAfter("（", "").substringBefore("）")
            if (form.isEmpty()) return@filter false
            val formKey = form.replace("附近", "").replace("的", "").replace("样子", "").trim()
            // 名字里必须真的带这个形态词，且查询词里也提到了它
            form.isNotBlank() && q.contains(formKey) && formKey.length >= 2
        }
        return hits.take(6)
    }

    /**
     * 候选门槛。**换用召回率指标后必须调高**（旧值 0.35 是配旧公式的）。
     *
     * 实测 2.jpg（OCR 输出 `叮花架 平鸽贝小夜小独角鲁 丑公小象音碟花伯`）：
     *   0.55 → 命中小夜/小独角兽/小鼓象/音碟吼/公平鸽/小丑公爵，**全是真货**
     *   0.50 → 多一个「小丑兔」噪音
     *   0.60 → 丢掉「小丑公爵」
     * 取 0.55。
     */
    private const val MATCH_THRESHOLD = 0.55f

    /**
     * 单个字符串 vs 条目名（含别名）的打分。
     *
     * **核心是「召回率」视角：名字里的字，有多少在 OCR 文本里出现过。**
     *
     * `score = LCS(文本, 名字) / len(名字)`
     *
     * 早先分母用的是 `max(len(文本), len(名字))`，这是错的 —— 一屏 OCR 会输出
     * 一整行十来个字（`平鸽贝小夜小独角鲁`），分母被撑大后，**即便名字被完整识别**
     * 分数也只有 0.33（`小独角兽` 的 LCS=3 / 9 行字）。而分母换成 `len(名字)` 后：
     *
     *   · `小独角兽` → 3/4 = 0.750   （OCR 把最后一个「兽」错认成「鲁」）
     *   · `小夜`    → 2/2 = 1.000
     *   · `小独角鲁` → 4/4 = 1.000   （若图鉴里真有这个名字）
     *
     * 同时乘一个轻量的长度惩罚，避免「只截到名字里 1 个字」的短匹配霸榜：
     * 目标长度 = 名字长度，差得越多分越低（但**不改变主项量级**）。
     */
    private fun scoreOne(text: String, name: String, realName: String): Float {
        var v = recallScore(text, name)
        if (realName != name) v = maxOf(v, recallScore(text, realName) * 0.95f)
        return v
    }

    /** 名字的字符有多少按顺序出现在 [text] 里（LCS / 名字长度），再乘长度惩罚。 */
    private fun recallScore(text: String, name: String): Float {
        val l = lcsLen(text, name)
        val recall = l.toFloat() / name.length
        // 轻量长度惩罚：OCR 文本远长于名字时略降（整行里只有一部分是这只的名字）
        val lenPen = minOf(1f, (name.length + 3f) / maxOf(name.length, text.length).toFloat())
        return recall * (0.8f + 0.2f * lenPen)
    }

    /** 最长公共子序列的**长度**（不归一化）。 */
    private fun lcsLen(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1] + 1
                else maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }
        return dp[a.length][b.length]
    }

    /** 所有已知写法（含别名），用于按名字反查图鉴条目。 */
    fun lookupByName(c: Context, name: String): Entry? {
        val list = entries(c)
        list.firstOrNull { it.name == name }?.let { return it }
        val real = ALIAS[name]
        if (real != null) list.firstOrNull { it.name == real }?.let { return it }
        return list.firstOrNull { it.name.contains(name) || name.contains(it.name) }
    }

    private const val LIST_URL = "https://rocokingdomworld.org/zh/pokedex"
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    @Volatile private var cache: List<Entry>? = null

    private fun localFile(c: Context) = File(File(c.filesDir, "shiny"), "shiny_catalog.json")

    /** 用户在本地手动补录的精灵（图鉴没有收录的），单独存一份，不会被刷新覆盖。 */
    private fun customFile(c: Context) = File(File(c.filesDir, "shiny"), "shiny_custom.json")

    /** 上次成功联网刷新的时间（毫秒）。 */
    private fun stampFile(c: Context) = File(File(c.filesDir, "shiny"), "catalog_stamp.txt")

    /** 刷新缓存有效期：3 天。期间不再重复联网。 */
    private const val REFRESH_TTL_MS = 3L * 24 * 60 * 60 * 1000

    /** 内置库 + 联网刷新过的本地副本 + 手动补录的精灵。 */
    fun entries(c: Context): List<Entry> {
        cache?.let { return it }
        val list = mutableListOf<Entry>()
        try {
            val txt = c.assets.open("shiny_catalog.json").bufferedReader().readText()
            list.addAll(fromJson(txt))
        } catch (e: Exception) { e.printStackTrace() }
        val f = localFile(c)
        if (f.exists()) {
            try { list.addAll(fromJson(f.readText())) } catch (e: Exception) { e.printStackTrace() }
        }
        // 手动补录的放最后，distinctBy 保留先出现的，所以图鉴优先
        val cf = customFile(c)
        if (cf.exists()) {
            try { list.addAll(fromJson(cf.readText())) } catch (e: Exception) { e.printStackTrace() }
        }
        val merged = list.distinctBy { it.slug.ifBlank { it.name } }
        cache = merged
        return merged
    }

    /** 把图鉴里没有的精灵记到本地，之后就能像正常图鉴条目一样被识别到。 */
    fun addCustom(c: Context, name: String, no: Int = 0): Entry? {
        val n = name.trim()
        if (n.isBlank()) return null
        // 已经在库里（含别名）就不重复加
        lookupByName(c, n)?.let { return it }
        val e = Entry(n, "", "", "", no)
        val arr = try {
            if (customFile(c).exists()) JSONArray(customFile(c).readText()) else JSONArray()
        } catch (ex: Exception) { JSONArray() }
        arr.put(org.json.JSONObject().apply {
            put("n", e.name); put("s", e.slug); put("p", e.py); put("i", e.icon); put("no", e.no)
        })
        try {
            customFile(c).parentFile?.mkdirs()
            customFile(c).writeText(arr.toString())
        } catch (ex: Exception) { ex.printStackTrace() }
        cache = null
        // 自定义精灵的名字也要进 OCR 候选池最高档（第 3 步·自定义反哺）
        ShinyOcr.invalidatePool()
        return e
    }

    /** 自定义精灵有哪些（给界面展示 / 管理用）。 */
    fun customNames(c: Context): List<String> {
        val cf = customFile(c)
        if (!cf.exists()) return emptyList()
        return try { fromJson(cf.readText()).map { it.name } } catch (e: Exception) { emptyList() }
    }

    /** 距离上次成功刷新过去了多久（毫秒）；从没刷新过返回 Long.MAX_VALUE。 */
    fun sinceRefresh(c: Context): Long {
        val sf = stampFile(c)
        if (!sf.exists()) return Long.MAX_VALUE
        val t = try { sf.readText().trim().toLong() } catch (e: Exception) { return Long.MAX_VALUE }
        return System.currentTimeMillis() - t
    }

    /**
     * 联网刷新罗可图鉴列表（游戏更新后新增的精灵也能进库）。
     * @param force 忽略缓存有效期，强制刷新
     * @return 是否成功（缓存还在有效期内时直接返回 true，不发请求）
     */
    fun refresh(c: Context, force: Boolean = false): Boolean {
        if (!force && sinceRefresh(c) < REFRESH_TTL_MS) return true
        val html = try { httpGet(LIST_URL) } catch (e: Exception) { null }
        if (html.isNullOrBlank()) return false
        val list = parse(html)
        if (list.isEmpty()) return false
        try {
            localFile(c).parentFile?.mkdirs()
            localFile(c).writeText(toJsonArray(list))
            stampFile(c).writeText(System.currentTimeMillis().toString())
        } catch (e: Exception) { e.printStackTrace() }
        val merged = (list + entries(c)).distinctBy { it.slug.ifBlank { it.name } }
        cache = merged
        // 图鉴变了 -> OCR 候选池必须重建，新精灵的字才能进最高权重档（第 3 步·刷新提权）
        ShinyOcr.invalidatePool()
        return true
    }

    /** 图鉴条目数（用于界面上显示「图鉴 641 只」这类信息）。 */
    fun size(c: Context): Int = entries(c).size

    private fun fromJson(txt: String): List<Entry> {
        val out = mutableListOf<Entry>()
        val arr = JSONArray(txt)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Entry(
                    name = o.optString("n", ""),
                    slug = o.optString("s", ""),
                    py = o.optString("p", ""),
                    icon = o.optString("i", ""),
                    no = o.optInt("no", 0)
                )
            )
        }
        return out
    }

    /** 解析 /zh/pokedex 列表页：中文名 + 拼音 + slug + 头像路径。 */
    fun parse(html: String): List<Entry> {
        val out = LinkedHashMap<String, Entry>()
        val re = Regex(
            "<a[^>]+href=\"(/zh/pokedex/[^\"]+)\"[^>]*>([\\s\\S]{0,1500}?)</a>"
        )
        var idx = 0
        for (m in re.findAll(html)) {
            val href = m.groupValues[1]
            if (href.contains("/type/")) continue
            val body = m.groupValues[2]
            var name: String? = null
            var py = ""
            for (a in Regex("alt=\"([^\"]{1,24})\"").findAll(body)) {
                val s = a.groupValues[1].trim()
                if (Regex("^[一-龥]").containsMatchIn(s)) {
                    val mm = Regex("^([一-龥（）()·]+)\\s*\\(([A-Za-z]+)\\)\\s*$").find(s)
                    if (mm != null) { name = mm.groupValues[1]; py = mm.groupValues[2].lowercase() }
                    else name = s
                    break
                }
            }
            if (name == null) {
                for (t in Regex(">([^<>{}]{2,18})<").findAll(body)) {
                    val s = t.groupValues[1].trim()
                    if (Regex("^[一-龥（）()·]+$").matches(s)) { name = s; break }
                }
            }
            val nm = name ?: continue
            val slug = href.substringAfterLast("/")
            val imgM = Regex("src=\"(/img/[^\"]+\\.(?:webp|png|jpg|jpeg))\"").find(body)
            val img = if (imgM != null) imgM.groupValues[1] else ""
            if (!out.containsKey(slug)) {
                idx++
                out[slug] = Entry(nm, slug, py, img, idx)
            }
        }
        return out.values.toList()
    }

    private fun toJsonArray(list: List<Entry>): String {
        val arr = JSONArray()
        for (e in list) {
            arr.put(org.json.JSONObject().apply {
                put("n", e.name); put("s", e.slug); put("p", e.py)
                put("i", e.icon); put("no", e.no)
            })
        }
        return arr.toString()
    }

    /**
     * 识别「名字或编号」。
     * 必须在子线程调用（可能联网）。
     */
    fun resolve(c: Context, raw: String): Result {
        val q = raw.trim().removePrefix("#").removePrefix("No.").removePrefix("no.").trim()
        if (q.isBlank()) return Result.NotFound(raw, "输入为空")

        var fromNetwork = false
        var hits = match(entries(c), q)
        if (hits.isEmpty()) {
            // 找不到就刷新一次图鉴（游戏更新后新增的精灵也能捞到）。
            // 缓存有效期内 refresh 不会真的发请求，直接返回 true，
            // 所以要记下「这次到底有没有重新联网」，否则文案会骗人。
            val stamp = sinceRefresh(c)
            fromNetwork = refresh(c) && stamp >= REFRESH_TTL_MS
            hits = match(entries(c), q)
        }

        return when {
            hits.size == 1 -> Result.Ok(hits[0], fromNetwork)
            hits.size > 1 -> Result.Ambiguous(
                q, hits.take(12),
                "「$q」在图鉴里对应 ${hits.size} 只，无法自动判定，请选择正确的那一只"
            )
            else -> Result.NotFound(
                q,
                if (fromNetwork)
                    "刚联网刷新过图鉴（共 ${size(c)} 只），仍然没有「$q」。\n\n" +
                        "如果这是游戏新出的精灵，可能是图鉴站还没收录；" +
                        "你可以把它作为「自定义精灵」直接记下来。"
                else if (sinceRefresh(c) < REFRESH_TTL_MS)
                    "图鉴里没有「$q」（最近刚刷新过，共 ${size(c)} 只，暂不重复联网）。\n\n" +
                        "如果是新精灵，图鉴站可能还没收录，可以记为「自定义精灵」。"
                else
                    "图鉴里没有「$q」，而且联网刷新失败了（检查网络后重试）。\n\n" +
                        "也可以先把它作为「自定义精灵」记下来。"
            )
        }
    }

    private fun match(list: List<Entry>, q: String): List<Entry> {
        val real = ALIAS[q] ?: q
        // 编号
        val num = q.toIntOrNull()
        if (num != null) {
            val byNo = list.filter { it.no == num }
            if (byNo.isNotEmpty()) return byNo
        }
        // 精确名
        list.firstOrNull { it.name == real }?.let { return listOf(it) }
        // 拼音精确
        list.firstOrNull { it.py.equals(real, true) || it.slug.equals(real, true) }?.let { return listOf(it) }
        // 子串（含别名反查：正式名包含查询词）
        val sub = list.filter { it.name.contains(real) || real.contains(it.name) }
        return sub
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept-Encoding", "gzip")
            connectTimeout = 20000
            readTimeout = 30000
            instanceFollowRedirects = true
        }
        return try {
            val stream = if (conn.contentEncoding.equals("gzip", true))
                GZIPInputStream(conn.inputStream) else conn.inputStream
            stream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }
}
