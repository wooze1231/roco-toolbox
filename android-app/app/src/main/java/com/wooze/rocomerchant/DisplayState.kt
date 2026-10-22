package com.wooze.rocomerchant

import android.content.Context
import java.time.ZonedDateTime

/**
 * 「当前该显示什么商品」的统一判定（组件与 App 共用，避免两处逻辑不一致）。
 *
 * 规则：
 * 1. 休息时段（00:00–08:00，或非营业时段）：一律不显示商品，替换为占位提示。
 *    这样第 4 轮结束后不会残留上一轮商品。
 * 2. 营业时段：只有「本轮已真正确认拿到新数据」（`confirmed_round_key` == 当前轮键）
 *    才展示商品；否则显示「第 N 轮刷新中…」，避免服务端延迟时把上一轮旧商品当本轮。
 *
 * ★ 新鲜度以 [RefreshReceiver.PREF_CONFIRMED_KEY] 为准（由 MerchantStore 在「拉到的商品
 *   确实不同于上一轮」时才置位），而**不是**本地 `updated`——否则轮次刚开、服务端还没发布
 *   新数据时，旧逻辑会把拉到的上一轮旧数据直接当成新一轮「在售」展示。
 */
object DisplayState {

    data class Snapshot(
        val status: String,        // "open" / "closed"
        val roundIndex: Int,       // 营业时 1-4
        val items: List<String>,   // 可展示的商品（不新鲜则为空）
        val placeholder: String,   // 无商品时的提示文案
        val fresh: Boolean         // 数据是否属于当前轮次
    )

    fun resolve(c: Context, now: ZonedDateTime = Schedule.nowBeijing()): Snapshot {
        val st = Schedule.compute(now)
        val p = c.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)

        // 休息中：商品一律清空
        if (st.status != "open") {
            return Snapshot(
                "closed", st.roundIndex, emptyList(),
                "休息中 · ${Schedule.fmt(st.nextTime)} 开张", false
            )
        }

        val raw = p.getString("items", "")
            ?.takeIf { it.isNotEmpty() }?.split("\u0001") ?: emptyList()

        // 本轮键（日期-小时）；与刷新逻辑共用，确保「确认的是哪一轮」口径一致。
        val key = Schedule.roundKey(now)
        val confirmedKey = p.getString(RefreshReceiver.PREF_CONFIRMED_KEY, "")

        // 只有「本轮已确认拿到真正新数据」才展示，否则显示刷新中占位。
        val fresh = raw.isNotEmpty() && confirmedKey == key

        if (!fresh) {
            return Snapshot(
                "open", st.roundIndex, emptyList(),
                if (raw.isEmpty()) "打开APP加载商品" else "第 ${st.roundIndex} 轮刷新中…",
                false
            )
        }
        return Snapshot("open", st.roundIndex, raw, "", true)
    }
}
