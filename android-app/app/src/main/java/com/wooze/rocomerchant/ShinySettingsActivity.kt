package com.wooze.rocomerchant

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 异色图鉴的设置页。
 *
 * 之前这些功能全塞在异色图鉴底部的按钮 + 各种弹窗里，按钮越加越多。
 * 现在统一收到这一页，按「图鉴 / 图标 / 数据 / 赛季」分组成卡片，
 * 异色图鉴底栏只留高频的「添加图鉴 / 识图添加 / 更多」。
 */
class ShinySettingsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private var root = ShinyData.Root()

    /** 有没有改过数据（返回时按这个决定要不要让异色图鉴重新读盘）。 */
    private var dirty = false

    /** 已选好、等待二次确认 / 执行的导入数据。 */
    private var pendingImport: ShinyData.Root? = null

    /** 导出前拼好的 JSON 文本（CreateDocument 回调里写出）。 */
    private var pendingExportText: String = ""

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use {
                it.write(pendingExportText.toByteArray())
            }
            toast("已导出异色记录")
        } catch (e: Exception) {
            toast("导出失败：${e.message}")
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            if (text.isNullOrBlank()) { toast("文件为空"); return@registerForActivityResult }
            val parsed = ShinyData.fromText(text)
            if (parsed == null) {
                UiDialogs.info(
                    this, "导入失败",
                    "这个文件不是有效的异色记录 JSON（缺少 seasons 字段或格式不对）。"
                )
                return@registerForActivityResult
            }
            pendingImport = parsed
            showImportChoice(parsed)
        } catch (e: Exception) {
            toast("导入失败：${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeBridge.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shiny_settings)

        container = findViewById(R.id.llSetContainer)
        findViewById<TextView>(R.id.btnSetBack).setOnClickListener { finish() }

        root = ShinyData.load(this)

        PaintTheme.decorate(this, findViewById(R.id.headerBar))
        render()
    }

    // 彩绘主题视觉（彩虹顶栏 + 纸纹）改由 PaintTheme.decorate() 在 onCreate 统一处理。

    override fun finish() {
        // 让异色图鉴知道数据可能变了
        if (dirty) setResult(RESULT_OK)
        super.finish()
    }

    // —— 页面渲染 ——

    private fun render() {
        container.removeAllViews()

        buildPokedexSection()
        buildIconSection()
        buildDataSection()
    }

    /** 一张卡片：标题 + 若干行。传入的 lambda 里用 addRow 追加行。 */
    private fun card(title: String, build: CardBuilder.() -> Unit) {
        val isPaint = ThemeBridge.isPaintTheme(this@ShinySettingsActivity)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            background = if (isPaint) {
                PaintTheme.wobbleRect(
                    this@ShinySettingsActivity,
                    this@ShinySettingsActivity.themed(com.google.android.material.R.attr.colorSurface),
                    this@ShinySettingsActivity.themed(com.google.android.material.R.attr.colorOutline),
                    16f, 7
                )
            } else {
                GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(this@ShinySettingsActivity.themed(com.google.android.material.R.attr.colorSurface))
                    setStroke(1, this@ShinySettingsActivity.themed(com.google.android.material.R.attr.colorOutline))
                }
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        box.addView(sectionHeader(title, isPaint))
        container.addView(box)
        CardBuilder(box, isPaint).build()
    }

    /** 卡片内容构建器：一行行往下加，自动插分隔线。 */
    private inner class CardBuilder(private val host: LinearLayout, private val isPaint: Boolean = false) {

        fun addRow(title: String, subtitle: String, accent: Boolean = false, onClick: () -> Unit) {
            if (host.childCount > 1) {
                host.addView(View(this@ShinySettingsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, if (isPaint) dp(3) else 1
                    ).apply { leftMargin = dp(14); rightMargin = dp(14) }
                    background = if (isPaint) {
                        PaintTheme.wobbleLine(
                            this@ShinySettingsActivity,
                            this@ShinySettingsActivity.themed(com.google.android.material.R.attr.colorOutline), 2f
                        )
                    } else {
                        GradientDrawable().apply {
                            setColor(this@ShinySettingsActivity.themed(com.google.android.material.R.attr.colorOutlineVariant))
                        }
                    }
                })
            }
            host.addView(makeRow(title, subtitle, accent, onClick))
        }
    }

    /** 一行设置项：主标题 + 副说明 + 右侧箭头。 */
    private fun makeRow(
        title: String,
        subtitle: String,
        accent: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        val rowBg = this@ShinySettingsActivity.themed(com.google.android.material.R.attr.colorSurface)
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
                ColorStateList.valueOf(
                    resolveColor(
                        this@ShinySettingsActivity, R.color.ripple
                    )
                ),
                GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(rowBg)
                },
                GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(rowBg)
                }
            )
            setOnClickListener { onClick() }
        }

        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@ShinySettingsActivity).apply {
                text = title
                textSize = 14.5f
                setTextColor(
                    resolveColor(
                        this@ShinySettingsActivity,
                        if (accent) R.color.gold else com.google.android.material.R.attr.colorOnSurface
                    )
                )
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            if (subtitle.isNotBlank()) {
                addView(TextView(this@ShinySettingsActivity).apply {
                    text = subtitle
                    textSize = 11.8f
                    setTextColor(this@ShinySettingsActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    setPadding(0, dp(3), 0, 0)
                })
            }
        })

        row.addView(TextView(this).apply {
            text = "›"
            textSize = 18f
            setTextColor(this@ShinySettingsActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(dp(8), 0, dp(2), 0)
        })

        return row
    }

    // —— 图鉴 ——

    private fun buildPokedexSection() {
        val custom = ShinyCatalog.customNames(this)
        card("图鉴") {
            addRow(
                "刷新精灵列表",
                "${ShinyCatalog.size(this@ShinySettingsActivity)} 只 · ${lastRefreshText()}",
                accent = true
            ) { forceRefreshCatalog() }
            addRow(
                "自定义精灵",
                if (custom.isEmpty()) "还没有（图鉴没收录的新精灵可以记在这里）"
                else "${custom.size} 只 · 不会被刷新覆盖"
            ) { showCustomSpirits() }
        }
    }

    private fun lastRefreshText(): String {
        val since = ShinyCatalog.sinceRefresh(this)
        if (since == Long.MAX_VALUE) return "还没联网刷新过"
        val days = since / (24L * 60 * 60 * 1000)
        if (days <= 0) return "今天刷新过"
        return "${days} 天前刷新"
    }

    private fun forceRefreshCatalog() {
        val before = ShinyCatalog.size(this)
        toast("正在联网刷新…")
        Thread {
            val ok = ShinyCatalog.refresh(this, force = true)
            val after = ShinyCatalog.size(this)
            runOnUiThread {
                UiDialogs.info(
                    this,
                    if (ok) "图鉴已刷新" else "刷新失败",
                    if (ok)
                        "刷新前 $before 只 → 刷新后 $after 只。" +
                            if (after > before) "\n\n新增 ${after - before} 只，游戏新出的精灵应该能认出来了。"
                            else "\n\n没有新增。图鉴站目前就这些。"
                    else
                        "没能连上图鉴站（检查网络后重试）。\n\n" +
                            "不影响现有功能：图鉴里没有的精灵可以记为「自定义精灵」。",
                    closeText = "知道了"
                )
                render()
            }
        }.start()
    }

    private fun showCustomSpirits() {
        val names = ShinyCatalog.customNames(this)
        if (names.isEmpty()) {
            UiDialogs.info(
                this, "自定义精灵",
                "还没有手动补录的精灵。\n\n" +
                    "用途：游戏刚出的新精灵，图鉴站还没收录，查不到名字时" +
                    "可以「记为自定义精灵」，存在本机、不会被图鉴刷新覆盖，" +
                    "之后用名字就能正常识别和计数。"
            )
            return
        }
        val msg = buildString {
            append("这些精灵存在本机，不会被图鉴刷新覆盖，用名字就能识别：\n\n  ")
            append(names.joinToString("\n  "))
            append("\n\n共 ").append(names.size).append(" 只。")
        }
        UiDialogs.info(this, "自定义精灵（${names.size} 只）", msg)
    }

    // —— 图标缓存 ——

    private fun buildIconSection() {
        val sizeKb = ShinyIcons.sizeBytes(this) / 1024
        val all = root.seasons.flatMap { s -> s.groups.flatMap { g -> g.items } }
        val owned = all.size
        val realShiny = all.count { ShinyIcons.hasShiny(this, it) }
        card("图标") {
            addRow(
                "图标缓存",
                "${ShinyIcons.count(this@ShinySettingsActivity)} 个文件 · 约 ${sizeKb}KB · 异色图 $realShiny/$owned"
            ) { showIconsDialog() }
        }
    }

    private fun showIconsDialog() {
        val sizeKb = ShinyIcons.sizeBytes(this) / 1024
        val all = root.seasons.flatMap { s -> s.groups.flatMap { g -> g.items } }
        val owned = all.size
        val realShiny = all.count { ShinyIcons.hasShiny(this, it) }
        val (dlg, box) = UiDialogs.panel(
            this, "图标缓存",
            "已缓存 ${ShinyIcons.count(this)} 个文件（约 ${sizeKb}KB）· 异色图 $realShiny/$owned 只"
        )
        box.addView(
            UiDialogs.messageText(
                this,
                "缓存放在应用私有目录（不是系统缓存），不会被「清理缓存」误删；" +
                    "只有在这里手动清空或卸载 App 才会删除，删掉后会自动重新下载。\n\n" +
                    "异色图来自 BWIKI 的精灵立绘；wiki 还没收录异色立绘的精灵（比如部分新赛季精灵），" +
                    "会先用「原皮明亮化」占位，等 wiki 补图后点「补下缺失图标」就能换成真图。"
            )
        )
        box.addView(UiDialogs.row(this, "补下缺失图标", "把还没有的图标 / 异色图补一次") {
            dlg.dismiss()
            Thread {
                val need = all.filter { !ShinyIcons.has(this, it) }
                ShinyIcons.ensureAll(this@ShinySettingsActivity, need)
                // 同样只补 BWIKI 上确实有异色立绘的，避免对没收录的精灵空跑请求
                val needS = all.filter {
                    !ShinyIcons.hasShiny(this, it) && ShinyIcons.worthShinyFetch(this, it)
                }
                for (e in needS) {
                    ShinyIcons.ensureShiny(this@ShinySettingsActivity, e)
                    Thread.sleep(250)
                }
                runOnUiThread {
                    render()
                    toast("补图完成：异色图 ${all.count { ShinyIcons.hasShiny(this, it) }} / ${all.size}")
                }
            }.start()
        })
        box.addView(UiDialogs.row(this, "清空缓存", "释放空间，之后会自动重新下载", danger = true) {
            dlg.dismiss()
            val freed = ShinyIcons.clear(this)
            render()
            toast("已清空图标缓存（释放 ${freed / 1024}KB）")
        })
        box.addView(UiDialogs.row(this, "关闭", soft = true) { dlg.dismiss() })
        dlg.show()
    }

    // —— 数据：导入 / 导出 / 还原备份 ——

    private fun buildDataSection() {
        val (allGot, allTotal) = ShinyData.progress(root)
        val allPieces = ShinyData.pieces(root)
        card("数据") {
            addRow(
                "导出记录",
                "当前 $allGot/$allTotal 种 · 共 $allPieces 只 · 存成 JSON"
            ) { showExportChoice() }
            addRow(
                "导入记录",
                "从 JSON 覆盖 / 补充导入"
            ) { importLauncher.launch(arrayOf("application/json", "text/*")) }
            addRow(
                "还原备份",
                if (ShinyData.hasBackup(this@ShinySettingsActivity)) "上次导入前已自动备份，可找回"
                else "还没有备份（先导入一次就会生成）"
            ) { restoreBackup() }
        }
    }

    // —— 导入 ——

    /** 第一窗口：覆盖 / 补充 二选一。 */
    private fun showImportChoice(parsed: ShinyData.Root) {
        UiDialogs.menuWithSubs(
            this,
            "导入异色记录",
            "从文件读到了 ${countEntries(parsed)} 只精灵",
            listOf(
                "覆盖导入" to "用文件替换全部当前记录",
                "补充导入" to "只添加图鉴里没有的精灵"
            ),
            dangerIndex = 0
        ) { which ->
            if (which == 0) showImportConfirmOverwrite(parsed)
            else showImportConfirmSupplement(parsed)
        }
    }

    /** 第二窗口·覆盖（详细）：说明后果 + 自动备份。返回 / 取消 / 确定(红)。 */
    private fun showImportConfirmOverwrite(parsed: ShinyData.Root) {
        val (dlg, box) = UiDialogs.panel(this, "确认覆盖导入？", null)
        box.addView(
            UiDialogs.messageText(
                this,
                "将替换全部当前记录（${root.seasons.size} 个赛季）；\n" +
                    "数量、备注、蛋数会清空且不可恢复。\n\n" +
                    "开始前会自动备份，可从「还原备份」找回。"
            )
        )
        box.addView(UiDialogs.row(this, "返回") { dlg.dismiss(); showImportChoice(parsed) })
        box.addView(UiDialogs.row(this, "取消", soft = true) { dlg.dismiss() })
        box.addView(UiDialogs.row(this, "确定覆盖", danger = true) {
            dlg.dismiss()
            doImportOverwrite(parsed)
        })
        dlg.show()
    }

    /** 第二窗口·补充（详细）：显示预测 新增/跳过、不改原有。返回 / 取消 / 确定。 */
    private fun showImportConfirmSupplement(parsed: ShinyData.Root) {
        val (add, skip) = countSupplement(parsed)
        val (dlg, box) = UiDialogs.panel(this, "确认补充导入？", null)
        box.addView(
            UiDialogs.messageText(
                this,
                "扫描文件 ${countEntries(parsed)} 只：\n" +
                    "预测 新增 $add · 跳过 $skip\n\n" +
                    "原有精灵的数量 / 备注不受影响；\n" +
                    "仅加入图鉴里没有的（含新赛季）。"
            )
        )
        box.addView(UiDialogs.row(this, "返回") { dlg.dismiss(); showImportChoice(parsed) })
        box.addView(UiDialogs.row(this, "取消", soft = true) { dlg.dismiss() })
        box.addView(UiDialogs.row(this, "确定补充") {
            dlg.dismiss()
            doImportSupplement(parsed)
        })
        dlg.show()
    }

    private fun doImportOverwrite(parsed: ShinyData.Root) {
        ShinyData.saveBackup(this, root)
        root = parsed
        ShinyData.save(this, root)
        dirty = true
        pendingImport = null
        render()
        toast("已覆盖导入，原记录已自动备份")
    }

    private fun doImportSupplement(parsed: ShinyData.Root) {
        ShinyData.saveBackup(this, root)
        var added = 0
        for (ps in parsed.seasons) {
            val target = ShinyData.ensureSeason(root, ps.name)
            for (g in ps.groups) {
                val tg = ShinyData.ensureGroup(target, g.name)
                for (e in g.items) {
                    if (tg.items.any { sameSpirit(it, e) }) continue
                    tg.items.add(
                        ShinyData.Entry(e.name, e.no, e.slug, e.icon, e.count, e.note, e.eggCount)
                    )
                    added++
                }
            }
        }
        ShinyData.save(this, root)
        dirty = true
        pendingImport = null
        render()
        toast("补充导入完成：新增 $added 只")
    }

    // —— 导出 ——

    /** 第一窗口：全部赛季 / 自选赛季 二选一。 */
    private fun showExportChoice() {
        UiDialogs.menuWithSubs(
            this,
            "导出异色记录",
            "选择要导出的范围",
            listOf(
                "全部赛季" to "导出所有赛季的精灵",
                "自选赛季" to "挑选部分赛季导出"
            )
        ) { which ->
            if (which == 0) showExportAll() else showExportCustom()
        }
    }

    /** 全部赛季：只问「是否包含数量」。 */
    private fun showExportAll() {
        val (dlg, box) = UiDialogs.panel(this, "导出全部赛季", "将导出所有赛季的精灵")
        var withQty = false
        box.addView(
            toggleRow("包含数量", "导出时一并写入只数与蛋数", makeSwitch(withQty) { withQty = it })
        )
        box.addView(UiDialogs.row(this, "导出") {
            dlg.dismiss()
            launchExport(root.seasons.map { it.name }, withQty)
        })
        box.addView(UiDialogs.row(this, "取消", soft = true) { dlg.dismiss() })
        dlg.show()
    }

    /** 自选赛季：≤4 用一行 chip 点选；>4 弹大窗口网格多选。 */
    private fun showExportCustom() {
        val names = root.seasons.map { it.name }
        if (names.isEmpty()) { toast("还没有任何赛季"); return }
        if (names.size <= 4) showExportCustomInline(names)
        else showExportCustomGrid(names)
    }

    private fun showExportCustomInline(names: List<String>) {
        val (dlg, box) = UiDialogs.panel(this, "自选赛季", "点选要导出的赛季")
        val selected = BooleanArray(names.size) { true }
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        names.forEachIndexed { i, name ->
            val chip = makeSeasonChip(name, selected[i]) { on -> selected[i] = on }
            chipRow.addView(
                chip,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i > 0) leftMargin = dp(6)
                }
            )
        }
        box.addView(chipRow)
        var withQty = false
        box.addView(
            toggleRow("包含数量", "导出时一并写入只数与蛋数", makeSwitch(withQty) { withQty = it })
        )
        box.addView(UiDialogs.row(this, "导出") {
            val pick = names.filterIndexed { i, _ -> selected[i] }
            if (pick.isEmpty()) { toast("至少选一个赛季"); return@row }
            dlg.dismiss()
            launchExport(pick, withQty)
        })
        box.addView(UiDialogs.row(this, "取消", soft = true) { dlg.dismiss() })
        dlg.show()
    }

    private fun showExportCustomGrid(names: List<String>) {
        val (dlg, box) = UiDialogs.panel(this, "自选赛季", "点选要导出的赛季（可多选）")
        val selected = BooleanArray(names.size) { true }

        // 列数：按屏宽定上限（2~4 列），再用「每行差最小、同分取行数更少」选最匀的列数。
        // 关键：格子等宽（GridLayout 列权重），末行不满就左对齐空着 —— 不再拉伸填满。
        val dm = resources.displayMetrics
        val popupWdp = ((dm.widthPixels / dm.density) * 0.9f).toInt().coerceAtMost(420)
        val gapDp = 8
        val minBtnDp = 76
        val maxCols = ((popupWdp - gapDp) / (minBtnDp + gapDp)).coerceIn(2, 4)
        val cols = bestColumns(names.size, maxCols)

        val grid = android.widget.GridLayout(this).apply {
            columnCount = cols
            setPadding(0, dp(2), 0, dp(2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        names.forEachIndexed { i, name ->
            val chip = makeSeasonChip(name, selected[i]) { on -> selected[i] = on }
            grid.addView(
                chip,
                android.widget.GridLayout.LayoutParams(
                    android.widget.GridLayout.spec(i / cols),
                    android.widget.GridLayout.spec(i % cols, 1f)
                ).apply {
                    width = 0
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
            )
        }
        box.addView(grid)

        var withQty = false
        box.addView(
            toggleRow("包含数量", "导出时一并写入只数与蛋数", makeSwitch(withQty) { withQty = it })
        )
        box.addView(UiDialogs.row(this, "导出") {
            val pick = names.filterIndexed { i, _ -> selected[i] }
            if (pick.isEmpty()) { toast("至少选一个赛季"); return@row }
            dlg.dismiss()
            launchExport(pick, withQty)
        })
        box.addView(UiDialogs.row(this, "取消", soft = true) { dlg.dismiss() })
        dlg.show()
    }

    /**
     * 选列数：让每行数量尽量均匀。
     * score = 2×行数 + 末行空缺，取最小；同分取列数更大（行数更少）。
     * 结果：6→3 列(3+3)、7→4 列(4+3)、5→3 列(3+2)、8→4 列(4+4)、10→4 列(4+4+2)。
     */
    private fun bestColumns(n: Int, maxCols: Int): Int {
        if (n <= 0) return 1
        var best = maxCols.coerceAtMost(n).coerceAtLeast(1)
        var bestScore = Int.MAX_VALUE
        for (c in 2..maxCols) {
            val rows = (n + c - 1) / c
            val last = n - (rows - 1) * c
            val empty = c - last
            val score = rows * 2 + empty
            if (score < bestScore || (score == bestScore && c > best)) {
                bestScore = score
                best = c
            }
        }
        return best
    }

    /** 拼出导出数据并触发系统保存选择器。 */
    private fun launchExport(seasonNames: List<String>, withQty: Boolean) {
        val exportRoot = buildExportRoot(seasonNames)
        pendingExportText = ShinyData.toText(exportRoot, withQty)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        exportLauncher.launch("shiny_export_$date.json")
    }

    private fun buildExportRoot(seasonNames: List<String>): ShinyData.Root {
        val out = ShinyData.Root()
        for (name in seasonNames) {
            val s = root.seasons.firstOrNull { it.name == name } ?: continue
            val ns = ShinyData.Season(s.name)
            for (g in s.groups) {
                val ng = ShinyData.Group(g.name)
                for (it in g.items) {
                    ng.items.add(
                        ShinyData.Entry(it.name, it.no, it.slug, it.icon, it.count, it.note, it.eggCount)
                    )
                }
                ns.groups.add(ng)
            }
            out.seasons.add(ns)
        }
        return out
    }

    // —— 还原备份 ——

    private fun restoreBackup() {
        val b = ShinyData.loadBackup(this)
        if (b == null) { toast("还没有备份"); return }
        UiDialogs.confirm(
            this,
            "还原备份",
            "用上次导入前的备份替换当前记录？\n当前的记录会丢失（可再次导入找回）。",
            confirmText = "还原",
            danger = true
        ) {
            root = b
            ShinyData.save(this, root)
            dirty = true
            render()
            toast("已还原备份")
        }
    }

    // —— 小工具 ——

    /** 同一次导入里，两只算「同一只」：名字同义（含别名）/ slug 相同 / 基础 slug 相同（形态变体）。 */
    private fun sameSpirit(a: ShinyData.Entry, b: ShinyData.Entry): Boolean {
        if (ShinyCatalog.synonymous(a.name, b.name)) return true
        val sa = a.slug.lowercase().trim()
        val sb = b.slug.lowercase().trim()
        if (sa.isNotBlank() && sa == sb) return true
        val ba = sa.substringBefore("-")
        val bb = sb.substringBefore("-")
        if (ba.isNotBlank() && ba == bb) return true
        return false
    }

    private fun countEntries(root: ShinyData.Root): Int =
        root.seasons.sumOf { s -> s.groups.sumOf { g -> g.items.size } }

    /** 补充导入时，预测会有多少只「新增」、多少「跳过」。 */
    private fun countSupplement(parsed: ShinyData.Root): Pair<Int, Int> {
        var add = 0
        var skip = 0
        for (ps in parsed.seasons) {
            val target = root.seasons.firstOrNull { it.name == ps.name }
            if (target == null) {
                add += ps.groups.sumOf { it.items.size }
                continue
            }
            for (g in ps.groups) {
                for (e in g.items) {
                    if (target.groups.any { tg -> tg.items.any { sameSpirit(it, e) } }) skip++
                    else add++
                }
            }
        }
        return add to skip
    }

    /** 一行「标题 + 副说明 + 右侧开关」。 */
    private fun toggleRow(title: String, sub: String, switch: View): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(this@ShinySettingsActivity.themed(com.google.android.material.R.attr.colorSurface))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(4) }
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(this).apply {
            text = title
            textSize = 14.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(this@ShinySettingsActivity.themed(com.google.android.material.R.attr.colorOnSurface))
        })
        texts.addView(TextView(this).apply {
            text = sub
            textSize = 11.5f
            setTextColor(this@ShinySettingsActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(texts)
        row.addView(switch)
        return row
    }

    /** 包含数量的开关（仿图鉴页样式）。 */
    private fun makeSwitch(checked: Boolean, onChange: (Boolean) -> Unit): View {
        var on = checked
        val track = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(26))
            background = GradientDrawable().apply {
                cornerRadius = dp(13).toFloat()
                setColor(resolveColor(this@ShinySettingsActivity, if (on) R.color.gold else com.google.android.material.R.attr.colorOutline))
            }
        }
        val knob = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(20), dp(20)).apply {
                gravity = Gravity.CENTER_VERTICAL
                leftMargin = if (on) dp(22) else dp(3)
                topMargin = dp(3)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(this@ShinySettingsActivity.themed(com.google.android.material.R.attr.colorOnSurface))
            }
        }
        track.addView(knob)
        track.setOnClickListener {
            on = !on
            (track.background as GradientDrawable).setColor(
                resolveColor(this@ShinySettingsActivity, if (on) R.color.gold else com.google.android.material.R.attr.colorOutline)
            )
            val lp = knob.layoutParams as FrameLayout.LayoutParams
            lp.leftMargin = if (on) dp(22) else dp(3)
            knob.layoutParams = lp
            onChange(on)
        }
        return track
    }

    /** 赛季选择 chip（可点选切换选中态）。 */
    private fun makeSeasonChip(name: String, selected: Boolean, onToggle: (Boolean) -> Unit): TextView {
        var on = selected
        val tv = TextView(this).apply {
            text = name
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                on = !on
                styleSeasonChip(this, on)
                onToggle(on)
            }
        }
        styleSeasonChip(tv, on)
        return tv
    }

    /** 给赛季 chip 套选中 / 未选中的配色。 */
    private fun styleSeasonChip(tv: TextView, sel: Boolean) {
        tv.background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(resolveColor(this@ShinySettingsActivity, if (sel) R.color.gold_soft else com.google.android.material.R.attr.colorSurface))
            setStroke(
                if (sel) 2 else 1,
                resolveColor(this@ShinySettingsActivity, if (sel) R.color.gold else com.google.android.material.R.attr.colorOutline)
            )
        }
        tv.setTextColor(
            resolveColor(
                this@ShinySettingsActivity,
                if (sel) R.color.gold_text_deep else com.google.android.material.R.attr.colorOnSurface
            )
        )
    }

    private fun sectionHeader(text: String, isPaint: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(
            if (isPaint) this@ShinySettingsActivity.themed(R.attr.colorPaintAccent)
            else this@ShinySettingsActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant)
        )
        setPadding(dp(14), dp(12), dp(14), dp(6))
        if (isPaint) PaintTheme.tilt(this, -0.9f)
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
