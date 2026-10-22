package com.wooze.rocomerchant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 每日 4 个刷新点的后台闹钟：8 / 12 / 16 / 20 点各 +OFFSET_MIN 分钟触发，
 * 由 RefreshReceiver 在后台拉取最新商品并刷新组件（无需弹出 APP）。
 * 用 setExactAndAllowWhileIdle 保证到点唤醒；每个点只刷一次（防重复见 RefreshReceiver）。
 */
object WidgetAlarmScheduler {
    const val EXTRA_POINT = "point_hour"
    private const val OFFSET_MIN = 5 // 到点后延迟 5 分钟再刷（本轮首次更新迟到 5 分钟，给服务端发布数据留余量）

    /**
     * 后台保底刷新的心跳间隔：每 15 分钟唤醒一次联网拉取最新商品。
     * 这是「App 不挂后台」时组件仍能自动刷新的关键（进程死后只能靠刷新点 + 本心跳）。
     * 15 分钟是耗电与「能明显感知到刷新」之间的折中；游戏数据每 4 小时才更新一轮，
     * 中间拉到的相同数据靠去重保护，开销极小。
     */
    private const val TICK_MS = 15 * 60_000L

    /** 数据刷新点：8 / 12 / 16 / 20 各 +OFFSET_MIN 分钟，联网拉最新商品。 */
    private val REFRESH_POINTS = Schedule.REFRESH_HOURS

    /**
     * 状态切换点（整点 +2 秒，只重绘不联网）：8 / 12 / 16 / 20 / 24(次日 0 点)。
     * 上一版只在 00:05 用非唤醒闹钟重绘，夜间设备休眠时不会触发 ——
     * 于是第 4 轮结束后组件一直停在负数倒计时、商品也一直是第 4 轮的。
     * 这里改成「整点 + RTC_WAKEUP + allowWhileIdle」，休眠也能切到下一状态。
     */
    private val BOUNDARY_HOURS = listOf(8, 12, 16, 20, 0)

    fun scheduleAll(c: Context) {
        for (h in REFRESH_POINTS) scheduleOne(c, h)
        for (h in BOUNDARY_HOURS) scheduleBoundary(c, h)
    }

    /**
     * 状态切换重绘：到点把组件从「营业中」切到「休息中/下一轮」并重设倒计时目标，
     * 不联网。用 RTC_WAKEUP + allowWhileIdle，保证夜间 Doze 下也能唤醒一次。
     */
    private fun scheduleBoundary(c: Context, hour: Int) {
        try {
            val am = c.getSystemService(AlarmManager::class.java) ?: return
            val i = Intent(c, RefreshReceiver::class.java)
                .putExtra(RefreshReceiver.EXTRA_RENDER_ONLY, true)
            val pi = PendingIntent.getBroadcast(
                c, 9100 + hour, i,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.cancel(pi)
            val at = nextTriggerAt(hour, 0) + 2_000L
            if (canExact(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 排下一次后台保底刷新心跳：每 TICK_MS 唤醒一次联网拉取（RTC_WAKEUP）。
     * 进程死后也能由系统唤醒执行，保证组件在 App 不挂后台时仍自动刷新。
     */
    fun scheduleTicker(c: Context) {
        try {
            val am = c.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                c, 7001, Intent(c, TickReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.cancel(pi)
            val at = System.currentTimeMillis() + TICK_MS
            if (canExact(am)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                // 未授予精确闹钟权限：用不精确闹钟，绝不抛 SecurityException
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun canExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun scheduleOne(c: Context, hour: Int) {
        try {
            val am = c.getSystemService(AlarmManager::class.java) ?: return
            val pi = pi(c, hour)
            am.cancel(pi)
            val at = nextTriggerAt(hour, OFFSET_MIN)
            if (canExact(am)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                // 未授予精确闹钟权限时退化：仍后台唤醒，但可能略有延迟（已留 +3 分钟余量）
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 是否已获得「闹钟与提醒」精确闹钟权限（Android 12+ 需要）。 */
    fun hasExactAlarmPermission(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = c.getSystemService(AlarmManager::class.java) ?: return true
        return am.canScheduleExactAlarms()
    }

    private fun pi(c: Context, hour: Int): PendingIntent {
        val i = Intent(c, RefreshReceiver::class.java).putExtra(EXTRA_POINT, hour)
        return PendingIntent.getBroadcast(
            c, hour, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun nextTriggerAt(hour: Int, minute: Int): Long {
        val bj = ZoneId.of("Asia/Shanghai")
        var z: ZonedDateTime = LocalDate.now(bj).atTime(hour, minute, 0).atZone(bj)
        if (z.toInstant().isBefore(Instant.now())) z = z.plusDays(1)
        return z.toInstant().toEpochMilli()
    }
}
