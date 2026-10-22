package com.wooze.rocomerchant

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

open class MerchantWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // 尺寸变化时重排（列数 / 降级为紧凑或纯文字布局）
        updateWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    /**
     * 系统切换日/夜模式时，Launcher 会广播到组件。
     * 这里必须重绘一遍，否则组件会停在旧配色（RemoteViews 不自动跟随主题）。
     */
    override fun onReceive(context: Context, intent: Intent) {
        // 先做 id 归属校验：非法的直接丢弃，连 super 都不进
        val safe = sanitizeWidgetIds(context, intent) ?: return
        super.onReceive(context, safe)
        if (safe.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            updateAll(context)
        }
    }

    /**
     * 组件 receiver 必须 `exported=true`（AppWidget 硬性要求，关不掉），
     * 理论上别的 App 能伪造 `APPWIDGET_UPDATE`、塞进**不属于本组件**的 widgetId。
     * 这里只对「APPWIDGET_UPDATE」这一个 action 做 id 归属过滤 —— 它正是唯一能被伪造用来触发重绘的入口。
     *
     * ⚠️ **其它 action 一律放行**（`OPTIONS_CHANGED` 尺寸变化、`DELETED` 删除组件、`ENABLED` 等），
     * 那些都是系统发的生命周期广播，拦了会导致组件尺寸变了不重排、删了不清理数据。
     * ⚠️ 拿不到自家组件列表时（`own` 为空，可能是时序问题）**也放行**，宁可放过不可误伤。
     */
    private fun sanitizeWidgetIds(context: Context, intent: Intent): Intent? {
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_UPDATE) return intent

        val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: return intent
        if (ids.isEmpty()) return intent

        val own = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, this::class.java))
            .toHashSet()
        if (own.isEmpty()) return intent

        val kept = ids.filter { it in own }
        if (kept.isEmpty()) return null          // 全是别人的 id → 确定是伪造，丢弃

        return Intent(intent).also {
            it.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, kept.toIntArray())
        }
    }

    companion object {
        /**
         * 组件所属「包」= 当前 applicationId，类名固定为 Kotlin 包名。
         * ⚠️ 不能硬编码包名：测试渠道包（applicationId 不同）会去查**另一个 App** 的组件，
         *    系统抛 SecurityException，表现为开机/启动后立刻崩溃。
         */
        private fun providersFor(context: Context): List<ComponentName> = listOf(
            ComponentName(context.packageName, "com.wooze.rocomerchant.MerchantWidget"),
            ComponentName(context.packageName, "com.wooze.rocomerchant.MerchantWidgetWide")
        )

        private const val MAX_ROWS = 6

        // —— 自适应降级阈值 ——
        private const val MINI_H = 80     // 高度不足 → 纯文字（丢掉图片）
        // ⚠️ 必须是 ≤110：标准 2×2 组件的宽度就是 110dp（Android 里 2 格 = 110dp），
        //    原来写 120 会把 2×2 误判成「极简纯文字」，连带「+N」角标一起没了。
        private const val MINI_W = 100
        private const val COMPACT_H = 150 // 高度不足 → 紧凑横向（倒计时 + 一排小图标）

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            for (p in providersFor(context)) {
                val ids = try {
                    mgr.getAppWidgetIds(p)
                } catch (_: Throwable) {
                    continue
                }
                for (id in ids) {
                    try {
                        updateWidget(context, mgr, id)
                    } catch (_: Throwable) {
                    }
                }
            }
        }

        private fun prefs(c: Context) =
            c.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)

        /**
         * 是否处于系统深色模式。
         *
         * 桌面组件是跨进程渲染，Configuration 由 Launcher 传入，
         * 不会跟随 Activity 的 AppCompatDelegate 主题 —— 所以必须主动判断。
         * 用户在设置页强制指定了浅色/深色时以用户选择为准。
         */
        private fun isNight(c: Context): Boolean {
            val forced = prefs(c).getInt(SettingsActivity.KEY_NIGHT_MODE, -100)
            if (forced == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) return true
            if (forced == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) return false
            return (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }

        private fun textPrimary(c: Context): Int =
            ContextCompat.getColor(c, R.color.text_primary)

        private fun textSecondary(c: Context): Int =
            ContextCompat.getColor(c, R.color.text_secondary)

        private fun columnsFor(widthDp: Int): Int = when {
            widthDp >= 250 -> 4
            widthDp >= 180 -> 3
            else -> 2 // 最小 1 行 2 列（横向 2 格宽）
        }

        /** 自适应行数：组件多高就显示几行（顶部信息区约 80dp，每行物品约 86dp）。 */
        private fun rowsFor(heightDp: Int): Int = when {
            heightDp >= 330 -> 4
            heightDp >= 250 -> 3
            heightDp >= 170 -> 2
            else -> 1
        }

        fun updateWidget(context: Context, mgr: AppWidgetManager, id: Int) {
            val opts = mgr.getAppWidgetOptions(id)
            val widthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
            val heightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

            // 休息时段 / 数据过期时，商品一律置空（不再残留上一轮）
            val now = Schedule.nowBeijing()
            val st = Schedule.compute(now)
            val snap = DisplayState.resolve(context, now)
            val items = snap.items
            val placeholder = snap.placeholder

            // 只有一套自适应网格逻辑：组件多大就显示多少格（最小 1 行 2 列），
            // 太小放不下网格才降级成纯文字。不再区分 compact / full 两套。
            when {
                heightDp < MINI_H || widthDp < MINI_W ->
                    renderMini(context, mgr, id, widthDp, items, placeholder, now, st)
                else ->
                    renderFull(context, mgr, id, widthDp, items, placeholder, now, st)
            }

            // 每次渲染都顺手排一次「状态切换」闹钟（8/12/16/20/24 点整重绘一次），
            // 保证轮次结束那一刻组件会切到下一状态，不会停在负数倒计时。
            try {
                WidgetAlarmScheduler.scheduleAll(context)
            } catch (_: Exception) {}
        }

        /** 距离下一次状态切换（本轮结束 / 下一轮开张）的剩余毫秒。 */
        private fun remainMillis(now: ZonedDateTime, st: Schedule.State): Long =
            (if (st.status == "open")
                Duration.between(now, st.endTime) else Duration.between(now, st.nextTime)).toMillis()

        /**
         * 用桌面自己的 Chronometer 走倒计时——由 Launcher 进程每秒驱动，
         * 不需要闹钟/唤醒/精确闹钟权限，真正实时（1 秒一跳）。
         * 注意：必须先设 setChronometerCountDown 再 setChronometer，否则 countDown 标记会失效。
         * 兜底：若已越过边界却还没重绘（后台被系统限制时），停表显示 00:00:00，绝不出现负数。
         */
        private fun applyCountdown(
            views: RemoteViews, viewId: Int, sizeSp: Float,
            now: ZonedDateTime, st: Schedule.State
        ) {
            val remain = remainMillis(now, st)
            views.setChronometerCountDown(viewId, true)
            if (remain <= 0) {
                views.setChronometer(viewId, SystemClock.elapsedRealtime() + 1_000L, null, false)
                views.setTextViewText(viewId, "00 : 00 : 00")
            } else {
                views.setChronometer(viewId, SystemClock.elapsedRealtime() + remain, null, true)
            }
            views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, sizeSp)
        }

        /** L3 极简：不显示图片，倒计时缩小，只列商品名（自动换行）。 */
        /**
         * 自适应商品网格（唯一一套渲染逻辑）：
         * - 列数 = columnsFor(widthDp)，行数 = rowsFor(heightDp)，组件多大就显示多少格；
         *   最小基准为 1 行 2 列，用户拉伸组件会自然显示更多行/列；
         * - 物品从左到右、从上到下依次填格，**先装满全部 capacity 格**；
         * - 只有当所有格都填满却还有剩的物品时，才在「最后一个显示出来的真实物品」**右上角**
         *   叠加一枚「+N」角标（角标贴在该物品格上，不是独立虚化格、也不是被挤掉的那个）；
         * - 末行不满（且无溢出）时用隐形占位格补齐，保证孤项**左对齐**（和 App 内 renderItems 一致）。
         */
        private fun buildItemGrid(
            context: Context,
            views: RemoteViews,
            containerId: Int,
            cellRes: Int,
            iconViewId: Int,
            nameViewId: Int,
            badgeViewId: Int,
            items: List<String>,
            cols: Int,
            rows: Int
        ) {
            val pkg = context.packageName
            views.removeAllViews(containerId)
            val capacity = rows * cols
            val visibleIcons = items.size.coerceAtMost(capacity)
            val overflow = (items.size - visibleIcons).coerceAtLeast(0)
            // 角标贴在「最后一个显示出来的真实物品」右上角
            val badgeIndex = if (overflow > 0) visibleIcons - 1 else -1
            val badgeText = if (overflow > 0) "+$overflow" else ""
            // 溢出时也补满占位格，让网格整齐；无溢出时末行补成整行
            val gridCount = if (overflow > 0) capacity
                            else ((visibleIcons + cols - 1) / cols) * cols

            var k = 0
            while (k < gridCount) {
                val row = RemoteViews(pkg, R.layout.widget_row)
                repeat(cols) {
                    if (k < visibleIcons) {
                        val cell = RemoteViews(pkg, cellRes)
                        IconResolver.applyToRemote(context, cell, iconViewId, items[k])
                        if (nameViewId != 0) cell.setTextViewText(nameViewId, items[k])
                        if (k == badgeIndex) {
                            cell.setTextViewText(badgeViewId, badgeText)
                            cell.setViewVisibility(badgeViewId, View.VISIBLE)
                        }
                        row.addView(R.id.llRow, cell)
                    } else {
                        row.addView(R.id.llRow, RemoteViews(pkg, R.layout.widget_item_spacer))
                    }
                    k++
                }
                views.addView(containerId, row)
            }
        }

        private fun renderMini(
            context: Context, mgr: AppWidgetManager, id: Int, widthDp: Int,
            items: List<String>, placeholder: String,
            now: ZonedDateTime, st: Schedule.State
        ) {
            val pkg = context.packageName
            val views = RemoteViews(pkg, R.layout.widget_merchant_mini)

            // 空间越小，倒计时字号越收（秒数由 Chronometer 自己跳）
            applyCountdown(
                views, R.id.chronoCountdownM,
                if (widthDp < 160) 10f else 11f, now, st
            )

            if (items.isEmpty()) {
                views.setTextViewText(R.id.tvWidgetGoodsM, placeholder)
            } else {
                // 极简布局没有图标，放不下角标；件数直接写进文案里
                val limit = 6
                val shown = items.take(limit).joinToString(" · ")
                views.setTextViewText(
                    R.id.tvWidgetGoodsM,
                    if (items.size > limit) "$shown 等 ${items.size} 件" else shown
                )
            }
            if (isNight(context)) {
                views.setInt(R.id.widgetRootM, "setBackgroundResource", R.drawable.bg_widget_night)
                views.setTextColor(R.id.tvWidgetGoodsM, textPrimary(context))
            }

            views.setOnClickPendingIntent(R.id.widgetRootM, openAppIntent(context))
            views.setOnClickPendingIntent(R.id.btnWidgetRefreshM, refreshIntent(context))
            mgr.updateAppWidget(id, views)
        }

        /** L1 完整布局：状态 + 倒计时胶囊 + 刷新时间 + 订阅提示 + 自适应图标网格（按尺寸换行/换列）。 */
        private fun renderFull(
            context: Context, mgr: AppWidgetManager, id: Int, widthDp: Int,
            items: List<String>, placeholder: String,
            now: ZonedDateTime, st: Schedule.State
        ) {
            val pkg = context.packageName
            val views = RemoteViews(pkg, R.layout.widget_merchant)
            val cols = columnsFor(widthDp)

            val opts = mgr.getAppWidgetOptions(id)
            val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 240)

            // 高度很紧时省掉状态行，把空间让给商品网格
            if (hDp < 200) {
                views.setViewVisibility(R.id.tvWidgetStatus, View.GONE)
            } else {
                views.setViewVisibility(R.id.tvWidgetStatus, View.VISIBLE)
                views.setTextViewText(
                    R.id.tvWidgetStatus,
                    if (st.status == "open") "营业中 · 第 ${st.roundIndex} 轮"
                    else "休息中 · 下一轮 ${Schedule.fmt(st.nextTime)}"
                )
            }

            // 宽度不足时倒计时自动调小字号（秒数仍由 Chronometer 实时跳）
            applyCountdown(
                views, R.id.chronoCountdown,
                if (widthDp < 200) 13f else 16f, now, st
            )

            if (items.isNotEmpty()) {
                views.setViewVisibility(R.id.tvWidgetGoods, View.GONE)
                val rows = rowsFor(hDp)
                // 自适应网格：cols 列 × rows 行，左对齐占位 + 溢出时在最后真实物品右上角「+N」角标
                buildItemGrid(
                    context, views, R.id.llWidgetItems,
                    R.layout.widget_item_cell, R.id.ivItemIcon, R.id.tvItemName, R.id.tvBadge,
                    items, cols, rows
                )
            } else {
                views.setViewVisibility(R.id.tvWidgetGoods, View.VISIBLE)
                views.setTextViewText(R.id.tvWidgetGoods, placeholder)
                views.removeAllViews(R.id.llWidgetItems)
            }

            val updated = prefs(context).getLong("updated", 0)
            if (updated > 0) {
                val t = DateTimeFormatter.ofPattern("HH:mm").format(
                    Instant.ofEpochMilli(updated).atZone(ZoneId.of("Asia/Shanghai"))
                )
                views.setTextViewText(R.id.tvWidgetUpdated, "已刷新 $t")
            } else {
                views.setTextViewText(R.id.tvWidgetUpdated, "")
            }

            val hit = Subscriptions.match(context, items)
            if (hit.isNotEmpty()) {
                views.setViewVisibility(R.id.tvWidgetSub, View.VISIBLE)
                views.setTextViewText(R.id.tvWidgetSub, "🔔 订阅命中：${hit.joinToString("、")}")
            } else {
                views.setViewVisibility(R.id.tvWidgetSub, View.GONE)
            }

            views.setTextViewText(R.id.tvWidgetTitle, context.getString(R.string.app_name))
            if (isNight(context)) {
                views.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.bg_widget_night)
                views.setTextColor(R.id.tvWidgetStatus, textSecondary(context))
                views.setTextColor(R.id.tvWidgetUpdated, textSecondary(context))
            }
            views.setOnClickPendingIntent(R.id.widgetRoot, openAppIntent(context))
            views.setOnClickPendingIntent(R.id.btnWidgetRefresh, refreshIntent(context))
            mgr.updateAppWidget(id, views)
        }

        private fun openAppIntent(c: Context): PendingIntent = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        private fun refreshIntent(c: Context): PendingIntent {
            val i = Intent(c, RefreshReceiver::class.java)
                .putExtra(RefreshReceiver.EXTRA_MANUAL, true)
            return PendingIntent.getBroadcast(
                c, 9001, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}

/** 2×3（三列）尺寸的组件实例，复用同一套渲染逻辑 */
class MerchantWidgetWide : MerchantWidget()
