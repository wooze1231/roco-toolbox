package com.wooze.rocomerchant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 后台刷新广播（由 AlarmManager 在 4 个刷新点 +OFFSET_MIN 触发，或组件上的刷新按钮手动触发）。
 * - 自动触发：同一刷新点（日期+小时）已刷过则跳过，避免重复刷新。
 * - 手动触发（EXTRA_MANUAL）：不受防重复限制，随时刷新。
 * 拉取成功后写入 SharedPreferences 并刷新所有组件实例。
 */
class RefreshReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MANUAL = "manual"

        /** 只重绘组件（重算状态/倒计时目标），不联网。用于 24:05 关店。 */
        const val EXTRA_RENDER_ONLY = "render_only"

        /**
         * 后台保底心跳标记（由 TickReceiver 每 15 分钟发出）。
         * 与手动刷新一样：总是联网拉取、不写「自动刷新防重复」标记，
         * 否则会误伤 4 个精准刷新点的去重（导致到点不刷）。
         */
        const val EXTRA_TICK = "tick"

        /** 精准自动触发才写本标记，用于解锁补刷判断「本轮是否已经自动刷新过」。 */
        internal const val PREF_AUTO_KEY = "auto_refreshed_point"

        /**
         * 本轮「已确认拿到真正新数据」的去重键（`<日期>-<小时>`）。
         * 与 [PREF_AUTO_KEY] 同步写入：只有拉到的商品确实不同于上一轮（服务端真换了数据），
         * 才标记 confirmed；否则判定服务端还没更新、本轮仍属旧数据，不标记、安排顺延重试。
         * 首页/组件「本轮在售」新鲜度以本键为准，而非本地 `updated`（后者在轮次刚开、
         * 服务端延迟时会把旧数据误判成新）。
         */
        internal const val PREF_CONFIRMED_KEY = "confirmed_round_key"

        /** 上一轮已确认的商品快照（\u0001 分隔），用于判定「本轮是否真换了数据」。 */
        internal const val PREF_CONFIRMED_ITEMS = "confirmed_round_items"

        private const val PREF_UNLOCK_LAST = "unlock_refresh_last"
        private const val UNLOCK_DEBOUNCE_MS = 5 * 60_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            val pending = goAsync()
            Thread {
                try {
                    maybeRefreshFromUnlock(context)
                } finally {
                    pending.finish()
                }
            }.start()
            return
        }
        val pending = goAsync()
        Thread {
            try {
                doRefresh(context, intent)
            } finally {
                pending.finish()
            }
        }.start()
    }

    /** 解锁屏幕后：如果本轮还没有真正刷新到新数据，补一次 tick 刷新。 */
    private fun maybeRefreshFromUnlock(c: Context) {
        val prefs = c.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(PREF_UNLOCK_LAST, 0)
        if (now - last < UNLOCK_DEBOUNCE_MS) return

        val bj = Schedule.nowBeijing()
        if (Schedule.compute(bj).status != "open") return

        // 仅当本轮尚未「确认拿到新数据」才补刷（已确认则无需重复）
        if (prefs.getString(PREF_CONFIRMED_KEY, "") == Schedule.roundKey(bj)) return

        prefs.edit().putLong(PREF_UNLOCK_LAST, now).apply()
        val i = Intent(c, RefreshReceiver::class.java).putExtra(EXTRA_TICK, true)
        c.sendBroadcast(i)
    }

    private fun doRefresh(c: Context, intent: Intent) {
        // 关店重绘：不联网，只刷新状态与倒计时
        if (intent.getBooleanExtra(EXTRA_RENDER_ONLY, false)) {
            DailyLog.prune(c)   // 跨天顺手清一次过期记录
            MerchantWidget.updateAll(c)
            WidgetAlarmScheduler.scheduleAll(c)
            WidgetAlarmScheduler.scheduleTicker(c)
            return
        }

        val manual = intent.getBooleanExtra(EXTRA_MANUAL, false)
        val tick = intent.getBooleanExtra(EXTRA_TICK, false)
        val prefs = c.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)
        val now = Schedule.nowBeijing()
        val key = Schedule.roundKey(now)

        // 防重复：仅「精准自动触发」且本轮已确认过 -> 仅刷新组件，不再拉取。
        // 手动刷新 / 心跳刷新不受此限（否则会跳过拉取、无法顺延重试）。
        if (!manual && !tick && prefs.getString(PREF_CONFIRMED_KEY, "") == key) {
            MerchantWidget.updateAll(c)
            WidgetAlarmScheduler.scheduleAll(c)
            WidgetAlarmScheduler.scheduleTicker(c)
            return
        }

        val m = MerchantRepository.fetch()
        if (m != null) {
            // 商品名归一化：别名统一（「炫彩精灵蛋」→「炫彩蛋」），去掉归一产生的重复
            val items = m.items.map { ItemPool.canonical(it) }.distinct()
            // 统一判定：拉到的商品是否与上一轮不同（= 服务端真换了数据）。
            // 不同 -> 确认本轮（记历史/抓图标/比对订阅）；相同 -> 仍是旧数据，安排顺延重试。
            val res = MerchantStore.applyFetch(c, items, m.updateTime, m.settledHour)
            if (res.confirmedNewRound) {
                // 记入今日轮次记录：轮次按本地时间推算、同一轮只补不覆盖。
                DailyLog.log(c, items, m.settledHour)
                // 新商品图标：只对缺失项联网抓一次并本地缓存（命中内置/缓存则不联网）
                IconCache.ensureMissing(c, items)
                // 订阅比对 + 命中提醒（纯本地，按 seen 去重，不会重复弹）
                Subscriptions.rememberSeen(c, items)
                Subscriptions.checkAndNotify(c, items, "round:${key}")
            }
            // 顺延/兜底逻辑已内置于 MerchantStore.applyFetch（安排重试或强制确认）
        }

        MerchantWidget.updateAll(c)
        // 重新排程次日同点闹钟（setExact 为一次性）+ 倒计时心跳
        WidgetAlarmScheduler.scheduleAll(c)
        WidgetAlarmScheduler.scheduleTicker(c)
    }
}
