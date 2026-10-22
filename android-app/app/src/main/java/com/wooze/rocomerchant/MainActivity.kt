package com.wooze.rocomerchant

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.view.Gravity
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import java.time.Duration

class MainActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var tvRound: TextView
    private lateinit var tvTimeRange: TextView
    private lateinit var tvUpdated: TextView
    private lateinit var tvItemCount: TextView
    private lateinit var itemsContainer: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var merchant: MerchantRepository.Merchant? = null
    private var lastPointKey: String = ""

    /**
     * 监听 SharedPreferences 变化：后台 RefreshReceiver 写入新商品后，
     * 前台 Activity 即使正在运行也能立刻重绘，不用等用户手动刷新或切回。
     */
    private val prefListener = OnSharedPreferenceChangeListener { _, key ->
        if (key in listOf("items", "updated", "settledHour")) {
            runOnUiThread { syncUiFromPrefs() }
        }
    }

    private val itemBgs = listOf(
        R.drawable.bg_item_1, R.drawable.bg_item_2, R.drawable.bg_item_3,
        R.drawable.bg_item_4, R.drawable.bg_item_5, R.drawable.bg_item_6
    )

    // 商品图标映射统一放在 ItemIcons 对象中（组件与 Activity 共用）

    private val tick = object : Runnable {
        override fun run() {
            updateUi()
            // 跨过刷新点（含 24:00 关店）时自动处理一次：
            // 营业中 -> 重新拉数据；休息中 -> 立刻清空商品并重置倒计时（不再负数）
            val now = Schedule.nowBeijing()
            val key = "${now.toLocalDate()}-${Schedule.pointHour(now)}"
            if (key != lastPointKey) {
                val first = lastPointKey.isEmpty()
                lastPointKey = key
                if (!first) {
                    if (Schedule.compute(now).status == "open") loadMerchant()
                    else {
                        renderItems()
                        MerchantWidget.updateAll(this@MainActivity)
                    }
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeBridge.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        PaintTheme.decorate(this, findViewById(R.id.headerBar))

        tvStatus = findViewById(R.id.tvStatus)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvRound = findViewById(R.id.tvRound)
        tvTimeRange = findViewById(R.id.tvTimeRange)
        tvUpdated = findViewById(R.id.tvUpdated)
        tvItemCount = findViewById(R.id.tvItemCount)
        itemsContainer = findViewById(R.id.itemsContainer)

        findViewById<TextView>(R.id.btnRefresh).setOnClickListener {
            Snackbar.make(findViewById(android.R.id.content), "正在刷新商品…", Snackbar.LENGTH_SHORT).show()
            loadMerchant()
        }

        findViewById<TextView>(R.id.btnCalendar).setOnClickListener {
            startActivity(Intent(this, ItemEditActivity::class.java))
        }
        findViewById<TextView>(R.id.btnSubscribe).setOnClickListener {
            startActivity(Intent(this, SubscribeActivity::class.java))
        }
        findViewById<View>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<View>(R.id.btnWiki).setOnClickListener {
            startActivity(Intent(this, WikiActivity::class.java))
        }
        findViewById<View>(R.id.btnShiny).setOnClickListener {
            startActivity(Intent(this, ShinyActivity::class.java))
        }
        // 功能入口卡：底色呼应主题，三张各不相同（色相微偏 → 张张有变化，仍贴着主题色）
        findViewById<View>(R.id.btnHistory).background = CardStyle.entryBg(this, -18f, 0)
        findViewById<View>(R.id.btnWiki).background = CardStyle.entryBg(this, 0f, 1)
        findViewById<View>(R.id.btnShiny).background = CardStyle.entryBg(this, 18f, 2)
        // 底部三个按钮：同样呼应主题
        CardStyle.themeButton(this, findViewById(R.id.btnCalendar), -14f, 3, 18, 11)
        CardStyle.themeButton(this, findViewById(R.id.btnSubscribe), 0f, 4, 18, 11)
        CardStyle.themeButton(this, findViewById(R.id.btnSettings), 14f, 5, 18, 11)

        // 测试渠道专属入口：炫彩色卡（正式版不显示，逻辑完全不变）
        if (packageName.endsWith(".test")) addColorCardEntry()
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        requestNotifPermission()
        maybeAskExactAlarm()

        prefs().registerOnSharedPreferenceChangeListener(prefListener)

        loadMerchant()
        handler.post(tick)

        // 部署每日 4 个刷新点的后台闹钟（8/12/16/20 各 +3 分钟）+ 倒计时心跳
        try {
            WidgetAlarmScheduler.scheduleAll(this)
            WidgetAlarmScheduler.scheduleTicker(this)
        } catch (_: Exception) {}
    }

    /** 仅在测试渠道插入「炫彩色卡」入口，插在「异色图鉴记录」下面。 */
    private fun addColorCardEntry() {
        val anchor = findViewById<View>(R.id.btnShiny) ?: return
        val parent = anchor.parent as? LinearLayout ?: return
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d + 0.5f).toInt()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(14), px(12), px(14), px(12))
            background = CardStyle.entryBg(this@MainActivity, 36f, 6)
            isClickable = true
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ShinyCardActivity::class.java))
            }
        }
        row.addView(TextView(this).apply {
            text = "🎨"
            textSize = 17f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(px(30), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = px(9)
            }
        }
        texts.addView(TextView(this).apply {
            text = "炫彩色卡"
            textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(resolveColor(this@MainActivity, com.google.android.material.R.attr.colorOnSurface))
        })
        texts.addView(TextView(this).apply {
            text = "扫色卡 · 分类查看"
            textSize = 11f
            setTextColor(resolveColor(this@MainActivity, com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, px(2), 0, 0)
        })
        row.addView(texts)
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 17f
            setTextColor(resolveColor(this@MainActivity, com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(px(6), 0, 0, 0)
        })

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = px(9)
        parent.addView(row, parent.indexOfChild(anchor) + 1, lp)
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
            )
        }
        NotificationHelper.ensureChannel(this)
    }

    /**
     * 倒计时已由桌面 Chronometer 自己每秒驱动，不需要这个权限。
     * 这里只是提示用户开启「闹钟与提醒」，让 8/12/16/20 的后台刷新点尽量准点。
     */
    private fun maybeAskExactAlarm() {
        if (WidgetAlarmScheduler.hasExactAlarmPermission(this)) return
        if (prefs().getBoolean("asked_exact_alarm", false)) return
        prefs().edit().putBoolean("asked_exact_alarm", true).apply()
        Snackbar.make(
            findViewById(android.R.id.content),
            "组件倒计时已实时跳动（无需设置）。开启「闹钟与提醒」可让 8/12/16/20 的后台更新更准点。",
            Snackbar.LENGTH_LONG
        ).setAction("去开启") {
            try {
                startActivity(
                    Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(android.net.Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {
                try {
                    startActivity(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:$packageName"))
                    )
                } catch (_: Exception) {
                }
            }
        }.show()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        prefs().unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        updateHistoryCount()
    }

    private fun updateHistoryCount() {
        DailyLog.prune(this)
        findViewById<TextView>(R.id.tvHistoryCount)?.text =
            "已记录 ${DailyLog.recordedCount(this)}/4 轮"
        val root = ShinyData.load(this)
        val (got, total) = ShinyData.progress(root)
        val pieces = ShinyData.pieces(root)
        findViewById<TextView>(R.id.tvShinyCount)?.text =
            "拥有异色 $pieces 只 · 种类 $got/$total"
    }

    private fun prefs(): SharedPreferences = getSharedPreferences("merchant_prefs", MODE_PRIVATE)

    private fun loadMerchant() {
        Thread {
            val m = MerchantRepository.fetch()
            merchant = m
            m?.let {
                // 商品名归一化：别名统一（「炫彩精灵蛋」→「炫彩蛋」），去掉归一产生的重复
                val items = it.items.map { ItemPool.canonical(it) }.distinct()
                // 与自动刷新共用同一套「是否本轮真更新」判定。
                // 手动刷新的历史记录策略：只要**不是**「服务端尚未更新、拉到的是上一轮旧数据」
                // （即 res.stale = false），就写入历史 / 补图 / 比对订阅——恢复旧版「手动刷就进历史」
                // 的体验（同一轮重复刷也补充，靠 DailyLog 只补不覆盖保证不污染）。
                // 仅在本轮刚开场、服务端还没发布新数据的极短窗口内（stale）跳过，避免把旧数据记成本轮。
                val res = MerchantStore.applyFetch(this@MainActivity, items, it.updateTime, it.settledHour)
                val st = Schedule.compute(Schedule.nowBeijing())
                if (res.confirmedNewRound || (!res.stale && st.status == "open")) {
                    DailyLog.log(this@MainActivity, items, it.settledHour)
                    IconCache.ensureMissing(this@MainActivity, items)
                    Subscriptions.rememberSeen(this@MainActivity, items)
                    // 去重键按「当天-轮次」算，与自动刷新一致，避免手动/自动重复提醒
                    Subscriptions.checkAndNotify(
                        this@MainActivity, items, Schedule.roundKey(Schedule.nowBeijing())
                    )
                }
            }
            runOnUiThread {
                tvUpdated.text = if (m != null) {
                    "数据 ${dataTimeShort()}"
                } else {
                    "拉取失败 · 检查网络"
                }
                renderItems()
                updateUi()
                updateHistoryCount()
                MerchantWidget.updateAll(this@MainActivity)
            }
        }.start()
    }

    /** 从 SharedPreferences 同步顶部状态、商品网格、历史/异色计数。 */
    private fun syncUiFromPrefs() {
        tvUpdated.text = "数据 ${dataTimeShort()}"
        renderItems()
        updateUi()
        updateHistoryCount()
    }

    /** 顶部 chip 用的短时间文案，如「19:08」。 */
    private fun currentTimeShort(): String {
        val t = Schedule.nowBeijing()
        return String.format("%02d:%02d", t.hour, t.minute)
    }

    /**
     * 顶部 chip 显示的是**数据拉取时间**，不是当前钟点。
     * 用 nowBeijing() 会误导：数据其实是 12:05 拉的、16:30 打开 App 也会显示「数据 16:30」，
     * 看着像刚拉过。这里读 prefs 里的 `updated`（最后一次成功拉取的时刻）。
     */
    private fun dataTimeShort(): String {
        val t = prefs().getLong("updated", 0L)
        if (t <= 0L) return currentTimeShort()
        val z = java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.of("Asia/Shanghai"))
        return String.format("%02d:%02d", z.hour, z.minute)
    }

    /** 只展示「本轮新鲜」的商品：休息中 / 数据不是本轮 -> 置空。 */
    private fun items(): List<String> = DisplayState.resolve(this).items

    private fun renderItems() {
        itemsContainer.removeAllViews()
        val snap = DisplayState.resolve(this)
        val list = snap.items
        // 商品件数（0 件时留空，避免「0 件」看着别扭）
        tvItemCount.text = if (list.isEmpty()) "" else "${list.size} 件"
        if (list.isEmpty()) {
            val empty = TextView(this).apply {
                text = snap.placeholder
                setTextColor(this@MainActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
                textSize = 14f
            }
            itemsContainer.addView(empty)
            return
        }

        val inflater = LayoutInflater.from(this)
        var currentRow: LinearLayout? = null
        list.forEachIndexed { index, name ->
            if (index % 3 == 0) {
                currentRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    weightSum = 3f
                }
                itemsContainer.addView(currentRow)
            }

            val card = inflater.inflate(R.layout.item_card, currentRow, false) as FrameLayout
            card.background = ContextCompat.getDrawable(this, itemBgs[index % itemBgs.size])
            card.findViewById<TextView>(R.id.tvItemName).text = name
            val iv = card.findViewById<ImageView>(R.id.ivItemIcon)
            when (val r = IconResolver.resolve(this, name)) {
                is IconResult.ResId -> iv.setImageResource(r.id)
                is IconResult.LocalFile -> {
                    val bmp = BitmapFactory.decodeFile(r.file.absolutePath)
                    if (bmp != null) iv.setImageBitmap(bmp)
                    else iv.setImageResource(R.drawable.widget_icon_placeholder)
                }
                IconResult.None -> iv.setImageResource(R.drawable.widget_icon_placeholder)
            }

            // 点商品卡 -> App 内置 WIKI 直接搜这个名字（不跳浏览器）
            card.setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, WikiActivity::class.java)
                        .putExtra(WikiActivity.EXTRA_QUERY, name)
                )
            }

            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            card.layoutParams = params
            currentRow?.addView(card)
        }

        // 补齐最后一行空白占位，保持对齐
        val remainder = list.size % 3
        if (remainder != 0) {
            repeat(3 - remainder) {
                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                }
                currentRow?.addView(spacer)
            }
        }
    }

    private fun updateUi() {
        val now = Schedule.nowBeijing()
        val st = Schedule.compute(now)
        if (st.status == "open") {
            tvStatus.text = "营业中"
            tvStatus.setTextColor(getColor(R.color.green))
            tvCountdown.text = fmtPill(Duration.between(now, st.endTime))
            tvRound.text = "第 ${st.roundIndex} 轮 · 下一轮 ${Schedule.fmt(st.nextTime)}"
        } else {
            tvStatus.text = "休息中"
            tvStatus.setTextColor(getColor(R.color.red))
            tvCountdown.text = fmtPill(Duration.between(now, st.nextTime))
            tvRound.text = "下一轮 ${Schedule.fmt(st.nextTime)} · 第 ${st.nextRoundIndex} 轮"
        }
        tvTimeRange.text = timeRangeText(st)
    }

    private fun timeRangeText(st: Schedule.State): String {
        return if (st.status == "open" && st.endTime != null) {
            val s = st.endTime.minusHours(Schedule.ROUND_DURATION_HOURS.toLong())
            String.format("%02d:00:00 ~ %02d:00:00", s.hour, st.endTime.hour)
        } else {
            val next = st.nextTime
            val prev = next.minusHours(Schedule.ROUND_DURATION_HOURS.toLong())
            String.format("%02d:00:00 ~ %02d:00:00", prev.hour, next.hour)
        }
    }

    private fun fmtPill(d: Duration): String {
        val s = d.seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return String.format("%02d : %02d : %02d", h, m, sec)
    }
}
