package com.wooze.rocomerchant

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat

/**
 * 总设置页（App 级别，区别于异色图鉴的 ShinySettingsActivity）。
 *
 * 分组：外观 / 提醒 / 关于。
 * 结构照抄异色图鉴设置页那套「卡片 + 行」，新增设置项往对应卡片里加一行即可。
 *
 * 颜色一律走 R.color.*，深色模式由 values-night 自动接管。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private val prefs by lazy { getSharedPreferences("merchant_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeBridge.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        container = findViewById(R.id.llSetContainer)
        findViewById<TextView>(R.id.btnSetBack).setOnClickListener { finish() }

        PaintTheme.decorate(this, findViewById(R.id.headerBar))
        render()
    }

    // 彩绘主题视觉（彩虹顶栏 + 纸纹）改由 PaintTheme.decorate() 在 onCreate 统一处理。

    // —— 页面渲染 ——

    private fun render() {
        container.removeAllViews()
        buildAppearanceSection()
        buildNotificationSection()
        buildAboutSection()
    }

    /** 一张卡片：标题 + 若干行。 */
    private fun card(title: String, build: CardBuilder.() -> Unit) {
        val isPaint = ThemeBridge.isPaintTheme(this@SettingsActivity)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            background = if (isPaint) {
                // 彩绘主题：手绘抖动墨线卡片
                PaintTheme.wobbleRect(
                    this@SettingsActivity,
                    themed(com.google.android.material.R.attr.colorSurface),
                    themed(com.google.android.material.R.attr.colorOutline),
                    16f, 7
                )
            } else {
                GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(themed(com.google.android.material.R.attr.colorSurface))
                    setStroke(1, themed(com.google.android.material.R.attr.colorOutline))
                }
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        box.addView(sectionHeader(title, isPaint))
        container.addView(box)
        CardBuilder(box, isPaint).build()
    }

    /** 卡片外的分组小标题。 */
    private fun sectionHeader(text: String, isPaint: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = 11.5f
        setTextColor(
            if (isPaint) themed(R.attr.colorPaintAccent)
            else themed(com.google.android.material.R.attr.colorOnSurfaceVariant)
        )
        setTypeface(typeface, Typeface.BOLD)
        letterSpacing = 0.06f
        setPadding(dp(10), dp(8), dp(10), dp(6))
        if (isPaint) PaintTheme.tilt(this, -0.9f)
    }

    /** 卡片内容构建器：一行行往下加，自动插分隔线。 */
    private inner class CardBuilder(private val host: LinearLayout, private val isPaint: Boolean = false) {

        private fun paintDividerView(): View = View(this@SettingsActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                if (isPaint) dp(3) else 1
            ).apply { leftMargin = dp(14); rightMargin = dp(14) }
            background = if (isPaint) {
                PaintTheme.wobbleLine(
                    this@SettingsActivity,
                    themed(com.google.android.material.R.attr.colorOutline), 2f
                )
            } else {
                GradientDrawable().apply {
                    setColor(themed(com.google.android.material.R.attr.colorOutlineVariant))
                }
            }
        }

        fun addRow(title: String, subtitle: String, accent: Boolean = false, onClick: () -> Unit) {
            if (host.childCount > 1) host.addView(paintDividerView())
            host.addView(makeRow(title, subtitle, accent, onClick))
        }

        /** 一行里放自定义内容（如分段选择器、开关），右侧不画箭头。 */
        fun addCustom(title: String, subtitle: String, extra: View) {
            if (host.childCount > 1) host.addView(paintDividerView())
            host.addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                addView(TextView(this@SettingsActivity).apply {
                    text = title
                    textSize = 14.5f
                setTextColor(
                    if (isPaint) themed(R.attr.colorPaintAccent)
                    else themed(com.google.android.material.R.attr.colorOnSurface)
                )
                    setTypeface(typeface, Typeface.BOLD)
                })
                if (subtitle.isNotBlank()) {
                    addView(TextView(this@SettingsActivity).apply {
                        text = subtitle
                        textSize = 11.8f
                        setTextColor(themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
                        setPadding(0, dp(3), 0, 0)
                    })
                }
                addView(extra)
            })
        }
    }

    /** 一行设置项：主标题 + 副说明 + 右侧箭头。 */
    private fun makeRow(
        title: String,
        subtitle: String,
        accent: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(12), dp(13))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = RippleDrawable(
                ColorStateList.valueOf(color(R.color.ripple)),
                GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(themed(com.google.android.material.R.attr.colorSurface))
                },
                GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(themed(com.google.android.material.R.attr.colorSurface))
                }
            )
            setOnClickListener { onClick() }
        }

        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@SettingsActivity).apply {
                text = title
                textSize = 14.5f
                setTextColor(color(if (accent) R.color.gold else com.google.android.material.R.attr.colorOnSurface))
                setTypeface(typeface, Typeface.BOLD)
            })
            if (subtitle.isNotBlank()) {
                addView(TextView(this@SettingsActivity).apply {
                    text = subtitle
                    textSize = 11.8f
                    setTextColor(themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    setPadding(0, dp(3), 0, 0)
                })
            }
        })

        row.addView(TextView(this).apply {
            text = "›"
            textSize = 18f
            setTextColor(themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(dp(8), 0, dp(2), 0)
        })

        return row
    }

    // —— 外观 ——

    private fun buildAppearanceSection() {
        card("外观") {
            val modes = listOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)
            val labels = listOf("跟随系统", "浅色", "深色")
            val cur = prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            val idx = modes.indexOf(cur).coerceAtLeast(0)

            addCustom("深色模式", "跟随系统 / 强制浅色 / 强制深色", segmented(labels, idx) { i ->
                prefs.edit().putInt(KEY_NIGHT_MODE, modes[i]).apply()
                AppCompatDelegate.setDefaultNightMode(modes[i])
                render()
            })

            addCustom("主题色彩", "米金 / 糖果 / 彩绘 / 海洋", themeColorPicker())
        }
    }

    /** 主题色四选一：每档一个色点 + 名称，当前选中高亮；点选即换肤并实时刷新本页。 */
    private val THEME_OPTIONS = listOf(
        "gold" to Pair("米金", 0xFFD9A441.toInt()),
        "candy" to Pair("糖果", 0xFFFF7EB3.toInt()),
        "paint" to Pair("彩绘", 0xFFE4573D.toInt()),
        "ocean" to Pair("海洋", 0xFF2E86C1.toInt())
    )

    private fun themeColorPicker(): LinearLayout {
        val cur = prefs.getString(ThemeBridge.KEY_THEME, "gold") ?: "gold"
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(9) }
            setPadding(0, dp(2), 0, dp(2))
        }
        THEME_OPTIONS.forEach { (key, opt) ->
            val (label, color) = opt
            val on = key == cur
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(8), dp(4), dp(8))
                isClickable = true
                isFocusable = true
                if (on) {
                    background = if (ThemeBridge.isPaintTheme(this@SettingsActivity)) {
                        PaintTheme.wobbleRect(
                            this@SettingsActivity,
                            themed(com.google.android.material.R.attr.colorSurface),
                            themed(com.google.android.material.R.attr.colorOutline),
                            12f, 9
                        )
                    } else {
                        GradientDrawable().apply {
                            cornerRadius = dp(12).toFloat()
                            setColor(themed(com.google.android.material.R.attr.colorSurfaceVariant))
                            setStroke(2, themed(com.google.android.material.R.attr.colorPrimary))
                        }
                    }
                }
                setOnClickListener {
                    prefs.edit().putString(ThemeBridge.KEY_THEME, key).apply()
                    recreate()
                }
            }
            cell.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { bottomMargin = dp(6) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (on) setStroke(dp(2), themed(com.google.android.material.R.attr.colorOnSurface))
                }
            })
            cell.addView(TextView(this).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(themed(com.google.android.material.R.attr.colorOnSurface))
            })
            row.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return row
    }

    /** 分段选择器。 */
    private fun segmented(labels: List<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        return if (ThemeBridge.isPaintTheme(this@SettingsActivity)) {
            paintSegmented(labels, selected, onPick)
        } else {
            defaultSegmented(labels, selected, onPick)
        }
    }

    private fun defaultSegmented(labels: List<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(9) }
            background = GradientDrawable().apply {
                cornerRadius = dp(11).toFloat()
                setColor(themed(com.google.android.material.R.attr.colorSurfaceVariant))
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        labels.forEachIndexed { i, label ->
            val on = i == selected
            row.addView(TextView(this).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color(if (on) com.google.android.material.R.attr.colorOnSurface else com.google.android.material.R.attr.colorOnSurfaceVariant))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(0, dp(7), 0, dp(7))
                if (on) {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(9).toFloat()
                        setColor(themed(com.google.android.material.R.attr.colorSurface))
                    }
                }
                setOnClickListener { onPick(i) }
            })
        }
        return row
    }

    /** 彩绘主题专属分段：边框按钮 + 红底选中，模拟手绘 segbox。 */
    private fun paintSegmented(labels: List<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val ink = themed(com.google.android.material.R.attr.colorOutline)
        val selectedBg = themed(com.google.android.material.R.attr.colorPrimary)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(9) }
            background = GradientDrawable().apply {
                cornerRadius = dp(11).toFloat()
                setColor(themed(com.google.android.material.R.attr.colorSurfaceVariant))
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        labels.forEachIndexed { i, label ->
            val on = i == selected
            row.addView(TextView(this).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (on) Color.parseColor("#FFFDF6") else ink)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                setPadding(0, dp(7), 0, dp(7))
                background = PaintTheme.wobbleRect(
                    this@SettingsActivity,
                    if (on) selectedBg else Color.TRANSPARENT,
                    ink, 9f, 5,
                    if (on) -1.5f else 0f   // 选中块向左上错位，模拟手贴歪
                )
                setOnClickListener { onPick(i) }
            })
        }
        return row
    }

    // —— 提醒 ——

    private fun buildNotificationSection() {
        card("提醒") {
            addRow("订阅与提醒", "管理想要关注的商品") {
                startActivity(Intent(this@SettingsActivity, SubscribeActivity::class.java))
            }
            addRow("闹钟与提醒权限", "开启后 8/12/16/20 点更新更准点") {
                openExactAlarmSettings()
            }
        }
    }

    private fun openExactAlarmSettings() {
        try {
            startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {
                toast("无法打开系统设置")
            }
        }
    }

    // —— 关于 ——

    private fun buildAboutSection() {
        card("关于") {
            addRow("使用说明", "怎么看轮次 · 怎么记异色 · 组件怎么加") { showHelp() }
            addRow("创作者声明", "作者 · 数据来源 · 免责") { showCredit() }
            addRow("关于本应用", "版本 ${appVersion()}") { showAbout() }
            addRow("清空本地数据", "仅清空本机记录，不影响云端", accent = true) { confirmClear() }
        }
    }

    private fun showHelp() {
        UiDialogs.info(
            this,
            "使用说明",
            """
            1. 轮次怎么看
               每天 08 / 12 / 16 / 20 点各刷新一轮，一轮持续 4 小时。
               首页顶部显示当前状态与倒计时，休息时段（00:00–08:00）不显示商品。

            2. 商品怎么来的
               数据取自「罗科工具箱」公开数据源，与本 App 背后的微信小程序同源。
               点右上角「刷新」可手动拉取；点商品卡可直接跳到内置图鉴搜索。

            3. 订阅提醒
               在「订阅提醒」里勾选想要的商品，命中时会发通知，
               桌面组件也会多出一行「🔔 订阅命中」。

            4. 今日轮次记录
               每轮商品会自动记一笔，最多保留 4 轮。
               忘了刷新或想手动补，可以进去改。

            5. 异色图鉴
               记录自己拥有的异色精灵与只数，支持导入导出。
               截图认字可以自动识别游戏截图里的精灵名。

            6. 桌面组件
               长按桌面空白处 → 小组件 → 找到「洛克助手」，可选 2×2 或 2×3。
               组件倒计时由桌面自己驱动，实时跳动，不耗电。
            """.trimIndent()
        )
    }

    private fun showCredit() {
        UiDialogs.info(
            this,
            "创作者声明",
            """
            作者：兀泽（wooze）

            数据来源
            商品数据来自「罗科工具箱」公开接口，与本 App 无关的其他第三方数据未做引用。
            精灵图鉴数据来自 BWIKI（罗可图鉴）。

            免责声明
            本 App 为非官方粉丝工具，与游戏官方无任何关联。
            所有数据仅供参考，实际以游戏内为准。
            因网络延迟、数据源异常等导致的信息滞后，本 App 不承担责任。

            版权
            App 内涉及的精灵名称、图像等版权归原权利人所有。
            """.trimIndent()
        )
    }

    private fun showAbout() {
        UiDialogs.info(
            this,
            "关于本应用",
            """
            洛克助手 · 远行商人提醒
            版本 ${appVersion()}

            一个只在需要的时候出现的提醒工具：
            告诉你这一轮卖什么、还有多久换、什么时候刷新。
            """.trimIndent()
        )
    }

    private fun confirmClear() {
        UiDialogs.confirm(
            this,
            "清空本地数据",
            "将清除商品缓存、今日轮次记录、订阅列表等本机数据。\n异色图鉴记录不会动。\n\n确定继续？",
            confirmText = "清空",
            danger = true
        ) {
            prefs.edit().clear().apply()
            toast("已清空本地数据")
            render()
        }
    }

    // —— 工具 ——

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
    } catch (_: Exception) {
        "—"
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun color(resId: Int): Int = resolveColor(this, resId)

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    companion object {
        const val KEY_NIGHT_MODE = "night_mode"
    }
}
