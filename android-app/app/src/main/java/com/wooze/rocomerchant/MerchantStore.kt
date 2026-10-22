package com.wooze.rocomerchant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import java.time.Instant
import java.time.ZoneId

/**
 * 统一的「拉到数据后落库 + 判定本轮是否真的更新了」逻辑（自动刷新与手动刷新共用）。
 *
 * 背景 bug：服务端每轮数据可能延迟发布。轮次刚开始时自动刷新拉到的是**上一轮的旧数据**，
 * 旧逻辑只要 fetch 成功就把旧数据当成新一轮「在售」直接展示，并且把本轮标成「已刷新」，
 * 导致后续不再重试、用户一直看到旧商品。
 *
 * 判定「本轮已真正更新（confirmed）」：
 * - 拉到的商品与上一轮已确认的商品**不同**（服务端确实换了一轮数据）；或
 * - 本轮已开场超过 [TIME_CONFIRM_MS]（覆盖延迟更新窗口 + 心跳周期）：此时即便数据与上一轮
 *   恰巧相同，也应视为本轮数据确认，避免「两轮恰巧一样就永远顺延」的死循环。
 *
 * 若服务端极端延迟、[TIME_CONFIRM_MS] 内还没发布新数据：时间兜底会先确认当前（旧）数据，
 * 但一旦之后迟发的新数据到来且与之不同，仍会重新确认并补记历史/订阅（见 key==confirmedKey 分支）。
 */
object MerchantStore {

    private val BJ = ZoneId.of("Asia/Shanghai")
    private const val RETRY_MS = 5 * 60_000L        // 顺延重试间隔
    private const val TIME_CONFIRM_MS = 15 * 60_000L // 本轮开场超过此时长即确认（兜底）

    data class ApplyResult(
        val confirmedNewRound: Boolean,  // 本次拉取确认了新一轮（调用方应记历史/抓图标/比对订阅）
        val stale: Boolean               // 本次拉到旧数据、本轮尚未确认（已安排顺延重试）
    )

    fun applyFetch(
        c: Context,
        items: List<String>,
        serverUpdateTime: Long,
        settledHour: Int
    ): ApplyResult {
        val p = c.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)
        val now = Schedule.nowBeijing()
        val key = Schedule.roundKey(now)

        // 基础数据总是落库（含本地拉取时刻），供 UI 显示「数据 HH:MM」与组件展示。
        val writeBase: (SharedPreferences.Editor) -> Unit = { e ->
            e.putString("items", items.joinToString("\u0001"))
            e.putInt("settledHour", settledHour)
            e.putLong("serverUpdated", serverUpdateTime)
            e.putLong("updated", System.currentTimeMillis())
        }

        // 休息时段不判定轮次（key 会是「下一轮」的日期-小时，且本就不展示商品），
        // 仅落库基础数据，不动 confirmed 状态，避免夜间把上一轮旧数据误确认到错误日期键。
        if (Schedule.compute(now).status != "open") {
            p.edit().apply { writeBase(this); apply() }
            return ApplyResult(false, false)
        }

        val confirmedKey = p.getString(RefreshReceiver.PREF_CONFIRMED_KEY, "")
        val confirmedItems = p.getString(RefreshReceiver.PREF_CONFIRMED_ITEMS, "")
            ?.takeIf { it.isNotEmpty() }?.split("\u0001") ?: emptyList()

        // 本轮已确认过：若服务端迟发的新数据确实与已确认的不同，重新确认（补记历史/订阅）；
        // 否则仅原地刷新。
        if (key == confirmedKey) {
            return if (items != confirmedItems) {
                p.edit().apply {
                    writeBase(this)
                    putString(RefreshReceiver.PREF_CONFIRMED_ITEMS, items.joinToString("\u0001"))
                    apply()
                }
                ApplyResult(true, false)
            } else {
                p.edit().apply { writeBase(this); apply() }
                ApplyResult(false, false)
            }
        }

        // 本轮尚未确认。
        val isNew = confirmedItems.isEmpty() || items != confirmedItems
        // 时间兜底：本轮开场已超过阈值。覆盖「两轮商品恰巧相同」导致永远顺延的死循环。
        val roundStartMs = now.toLocalDate()
            .atTime(Schedule.REFRESH_HOURS[(Schedule.compute(now).roundIndex - 1).coerceIn(0, 3)], 0)
            .atZone(BJ).toInstant().toEpochMilli()
        val openMs = now.toInstant().toEpochMilli() - roundStartMs
        val timeConfirm = openMs >= TIME_CONFIRM_MS

        if (isNew || timeConfirm) {
            // 真·新数据（或无基线首启），或时间兜底 -> 确认本轮。
            p.edit().apply {
                writeBase(this)
                putString(RefreshReceiver.PREF_CONFIRMED_KEY, key)
                putString(RefreshReceiver.PREF_CONFIRMED_ITEMS, items.joinToString("\u0001"))
                putString(RefreshReceiver.PREF_AUTO_KEY, key)
                apply()
            }
            cancelRetry(c)
            return ApplyResult(true, false)
        }

        // 与上一轮已确认数据相同、且本轮刚开场不久 -> 服务端还没更新 -> 仍是旧数据，顺延重试。
        p.edit().apply { writeBase(this); apply() }
        scheduleRetry(c)
        return ApplyResult(false, true)
    }

    /**
     * 顺延重试：本轮未确认时，[RETRY_MS] 后再拉一次（一次性闹钟，与 15 分钟心跳互补，
     * 加速收敛到真正的新数据）。
     */
    fun scheduleRetry(c: Context) {
        try {
            val am = c.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                c, 7102, Intent(c, TickReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.cancel(pi)
            val at = System.currentTimeMillis() + RETRY_MS
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms())
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 本轮已确认后取消待执行的顺延重试闹钟。 */
    fun cancelRetry(c: Context) {
        try {
            val am = c.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                c, 7102, Intent(c, TickReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.cancel(pi)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
