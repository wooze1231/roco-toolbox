package com.wooze.rocomerchant

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

/**
 * 每日轮次记录（纯本地，SharedPreferences "daily_log"，体积极小）。
 *
 * - 键格式：`yyyy-MM-dd|HH`，值为该轮商品名（\u0001 分隔）。
 * - 轮次归属**一律按本地北京时间推算**，不信任服务端 settledHour。
 * - 同一轮**只补不覆盖**：已有记录不会被后来的数据顶掉。
 * - [prune] 只保留「今天」；凌晨 00:00–08:00 额外保留「昨天」供回看，
 *   新一天 08:00 首轮刷新后即自动删除昨日缓存 —— 不会长期占用空间。
 */
object DailyLog {

    private const val PREF = "daily_log"
    private val BJ = ZoneId.of("Asia/Shanghai")

    private fun p(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun today(): LocalDate = LocalDate.now(BJ)

    private fun key(date: LocalDate, hour: Int) = "$date|$hour"

    /**
     * 记录一轮商品。**轮次按本地时间推算**，忽略服务端 settledHour。
     *
     * 为什么不能用 settledHour：数据源返回的 settledHour 常常是错的（实测在
     * 下午第 3 轮时仍返回 8），旧逻辑直接拿它当键，就会把第 1 轮的记录覆盖成
     * 第 3 轮的商品。
     *
     * @param serverHour 服务端声称的轮次小时，仅作参考、不参与定位。
     * @param items      本轮商品（空列表不写）。
     */
    fun log(c: Context, items: List<String>, serverHour: Int = -1) {
        // 归一化：别名统一（如「炫彩精灵蛋」→「炫彩蛋」），并去掉因归一产生的重复
        val list = items.map { ItemPool.canonical(it) }
            .filter { it.isNotBlank() }
            .distinct()
        if (list.isEmpty()) return

        val now = Schedule.nowBeijing()
        val st = Schedule.compute(now)
        // 本轮（或即将到来的那轮）对应的刷新点小时
        val h = if (st.status == "open") Schedule.REFRESH_HOURS[st.roundIndex - 1]
        else Schedule.REFRESH_HOURS[(st.nextRoundIndex - 1).coerceIn(0, Schedule.REFRESH_HOURS.size - 1)]

        // 服务端轮次和本地推算对不上是常态（它经常一直返回 8），记一笔便于排查，
        // 但**不影响记录归属**——归属只认本地时间。
        if (serverHour in Schedule.REFRESH_HOURS && serverHour != h) {
            android.util.Log.i(
                "DailyLog",
                "服务端 settledHour=$serverHour 与本地轮次 $h 不一致，按本地记录"
            )
        }

        // 凌晨 00:00–08:00 拿到的 20 点那一轮属于「昨天」，别记到新一天
        val d = if (now.hour < Schedule.REFRESH_HOURS.first() && h == Schedule.REFRESH_HOURS.last())
            today().minusDays(1) else today()

        val sp = p(c)
        val k = key(d, h)
        // 只补不覆盖：该轮已有记录就保持原样，避免刷新把历史改掉
        if (sp.getString(k, null) == null) {
            sp.edit().putString(k, list.joinToString("\u0001")).apply()
        }
        prune(c)
    }

    /** 读取某天的记录：刷新点小时 -> 商品列表（按 8/12/16/20 有序）。 */
    private fun readDay(c: Context, d: LocalDate): LinkedHashMap<Int, List<String>> {
        val out = linkedMapOf<Int, List<String>>()
        val sp = p(c)
        for (h in Schedule.REFRESH_HOURS) {
            val v = sp.getString(key(d, h), null) ?: continue
            val list = v.split("\u0001").filter { it.isNotBlank() }
            if (list.isNotEmpty()) out[h] = list
        }
        return out
    }

    fun todayRounds(c: Context): LinkedHashMap<Int, List<String>> = readDay(c, today())

    fun yesterdayRounds(c: Context): LinkedHashMap<Int, List<String>> =
        readDay(c, today().minusDays(1))

    /** 今天已记录的轮次数（0-4），用于首页入口显示。 */
    fun recordedCount(c: Context): Int = todayRounds(c).size

    /**
     * 清理过期缓存：只保留今天（凌晨额外保留昨天），其余全部删除。
     * 每次写入 / 读取 / 后台刷新 / 开机 时调用，保证不会堆积。
     */
    fun prune(c: Context) {
        val d = today()
        val keep = mutableSetOf(d.toString())
        // 00:00–08:00 休息期保留昨日供回看，08:00 新一天开市后即被清理
        if (Schedule.nowBeijing().hour < Schedule.REFRESH_HOURS.first()) {
            keep.add(d.minusDays(1).toString())
        }
        val sp = p(c)
        val e = sp.edit()
        var removed = 0
        for (k in sp.all.keys) {
            if (k.substringBefore("|") !in keep) {
                e.remove(k)
                removed++
            }
        }
        if (removed > 0) e.apply()
    }

    /** 手动更正某一轮的商品（覆盖写）。 */
    fun setRound(c: Context, hour: Int, items: List<String>) {
        if (hour !in Schedule.REFRESH_HOURS) return
        val list = items.filter { it.isNotBlank() }
        if (list.isEmpty()) { clearRound(c, hour); return }
        p(c).edit().putString(key(today(), hour), list.joinToString("\u0001")).apply()
        prune(c)
    }

    /** 手动清空某一轮。 */
    fun clearRound(c: Context, hour: Int) {
        p(c).edit().remove(key(today(), hour)).apply()
    }

    /** 清空今天全部记录。 */
    fun clearToday(c: Context) {
        val d = today()
        val e = p(c).edit()
        for (h in Schedule.REFRESH_HOURS) e.remove(key(d, h))
        e.apply()
    }

    /** 清空全部（含昨日回看）。 */
    fun clearAll(c: Context) = p(c).edit().clear().apply()

    /** 估算占用字节数（UTF-16 粗估），用于界面展示「不占容量」。 */
    fun sizeBytes(c: Context): Int {
        var n = 0
        for ((k, v) in p(c).all) {
            n += k.length * 2
            n += (v as? String ?: "").length * 2
        }
        return n
    }
}
