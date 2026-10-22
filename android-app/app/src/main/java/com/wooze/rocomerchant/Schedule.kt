package com.wooze.rocomerchant

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 洛克助手时间表计算（北京时间 Asia/Shanghai）。
 * 刷新节点：08:00 / 12:00 / 16:00 / 20:00，每轮 4 小时，00:00–08:00 休息。
 * 移植自已验证的 Python 逻辑。
 */
object Schedule {
    val REFRESH_HOURS = listOf(8, 12, 16, 20)
    const val ROUND_DURATION_HOURS = 4
    private val BJ = ZoneId.of("Asia/Shanghai")

    data class State(
        val status: String,          // "open" / "closed"
        val roundIndex: Int,         // 营业时 1-4
        val endTime: ZonedDateTime?,  // 营业结束
        val nextTime: ZonedDateTime,  // 下一轮刷新
        val nextRoundIndex: Int
    )

    fun nowBeijing(): ZonedDateTime = ZonedDateTime.ofInstant(Instant.now(), BJ)

    fun compute(now: ZonedDateTime = nowBeijing()): State {
        val starts = REFRESH_HOURS.map { h ->
            now.toLocalDate().atTime(h, 0).atZone(BJ)
        }
        for (i in starts.indices) {
            val s = starts[i]
            val e = s.plusHours(ROUND_DURATION_HOURS.toLong())
            if (!now.isBefore(s) && now.isBefore(e)) {
                return State("open", i + 1, e, e, (i + 1) % 4 + 1)
            }
        }
        for (i in starts.indices) {
            if (now.isBefore(starts[i])) {
                return State("closed", -1, null, starts[i], i + 1)
            }
        }
        return State("closed", -1, null, starts[0].plusDays(1), 1)
    }

    fun fmt(z: ZonedDateTime): String =
        String.format("%02d:%02d", z.hour, z.minute)

    /** 当前所属（或即将到来）的刷新点小时 8/12/16/20，用于去重键。 */
    fun pointHour(now: ZonedDateTime = nowBeijing()): Int {
        val st = compute(now)
        return if (st.status == "open") REFRESH_HOURS[st.roundIndex - 1]
        else REFRESH_HOURS[(st.nextRoundIndex - 1).coerceIn(0, REFRESH_HOURS.size - 1)]
    }

    /**
     * 当前轮次去重键：`<日期>-<刷新点小时>`，如 `2026-10-14-8`。
     * 营业中 = 当前轮刷新点；休息中 = 下一轮刷新点（仅占位，不展示商品）。
     * 自动刷新 / 解锁补刷 / 新鲜度判定共用，避免各算各的导致不一致。
     */
    fun roundKey(now: ZonedDateTime = nowBeijing()): String {
        val st = compute(now)
        val hour = if (st.status == "open") REFRESH_HOURS[st.roundIndex - 1]
        else REFRESH_HOURS[(st.nextRoundIndex - 1).coerceIn(0, REFRESH_HOURS.size - 1)]
        return "${now.toLocalDate()}-$hour"
    }
}
