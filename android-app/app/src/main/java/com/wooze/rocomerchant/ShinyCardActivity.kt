package com.wooze.rocomerchant

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.R as MR

/**
 * 炫彩色卡展示页（仅测试渠道使用）。
 *
 * 四个筛选：底色 / 混色 / 粒子 / 特殊；排序：最近 / 底色 / 混色 / 粒子；支持搜索精灵名。
 * 点卡片看详情，可改名、删除。
 */
class ShinyCardActivity : AppCompatActivity() {

    private val cards = mutableListOf<CardRec>()
    private var fBase = "全部"
    private var fMix = "全部"
    private var fPart = ALL
    private var sortBy = "recent"
    private var q = ""
    private var openDim = ""

    private lateinit var grid: GridLayout
    private lateinit var chipsBox: GridLayout
    private lateinit var countTv: TextView
    private lateinit var selectors: List<TextView>
    private var sortViews: List<TextView> = emptyList()

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.openOutputStream(uri)?.use {
                    it.write(ShinyCardStore.toJson(cards).toByteArray(Charsets.UTF_8))
                }
                toast("已导出 ${cards.size} 张")
            } catch (t: Throwable) {
                toast("导出失败：${t.message}")
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val text = contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: return@registerForActivityResult
                val recs = ShinyCardStore.fromJson(text)
                if (recs.isEmpty()) {
                    toast("文件里没有可导入的色卡")
                    return@registerForActivityResult
                }
                ShinyCardStore.add(this, recs)
                cards.clear()
                cards.addAll(ShinyCardStore.load(this))
                render()
                toast("已导入 ${recs.size} 张")
            } catch (t: Throwable) {
                toast("导入失败：${t.message}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeBridge.apply(this)
        super.onCreate(savedInstanceState)
        ShinyCardStore.seedForBetaIfEmpty(this)
        ShinyCardStore.migrateToPalette(this)
        cards.clear()
        cards.addAll(ShinyCardStore.load(this))
        setContentView(buildRoot())
        render()
    }

    override fun onResume() {
        super.onResume()
        // 从扫描页回来后刷新（数量变了才重建）
        val fresh = ShinyCardStore.load(this)
        if (fresh.size != cards.size) {
            cards.clear()
            cards.addAll(fresh)
            render()
        }
    }

    // ——— 骨架 ———

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themed(R.attr.colorBg))
        }

        // 顶栏
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(themed(MR.attr.colorSurface))
        }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 20f
            setTextColor(themed(MR.attr.colorOnSurface))
            setPadding(0, 0, dp(10), 0)
            setOnClickListener { finish() }
        })
        header.addView(TextView(this).apply {
            text = "炫彩色卡"
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(themed(MR.attr.colorOnSurface))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        countTv = TextView(this).apply {
            textSize = 12f
            setTextColor(themed(MR.attr.colorPrimary))
            setPadding(dp(8), dp(3), dp(10), dp(3))
            setBackgroundColor(themed(MR.attr.colorSurfaceVariant))
        }
        header.addView(TextView(this).apply {
            text = "⋯"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(themed(MR.attr.colorOnSurface))
            setPadding(dp(10), dp(2), dp(10), dp(2))
            setOnClickListener { showMoreMenu() }
        })
        header.addView(View(this), LinearLayout.LayoutParams(dp(4), 1))
        header.addView(TextView(this).apply {
            text = "＋ 扫描"
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(themed(MR.attr.colorOnSurface))
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(themed(MR.attr.colorPrimaryContainer))
            }
            setOnClickListener {
                startActivity(android.content.Intent(this@ShinyCardActivity, CardScanActivity::class.java))
            }
        })
        header.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        header.addView(countTv)
        root.addView(header)

        // 内容
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), 0)
        }
        root.addView(body, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // 筛选卡
        val filterCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), dp(9), dp(9), dp(9))
        }
        baseCard(filterCard)
        body.addView(filterCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        selectors = listOf("底色", "混色", "粒子").mapIndexed { i, label ->
            TextView(this).apply {
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(8), dp(6), dp(8))
                setOnClickListener {
                    openDim = if (openDim == dimKey(i)) "" else dimKey(i)
                    render()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
            }
        }
        selectors.forEach { row.addView(it) }
        filterCard.addView(row)

        chipsBox = GridLayout(this).apply {
            columnCount = 4
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        filterCard.addView(chipsBox)

        // 排序
        val sortRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        val sortList = mutableListOf<TextView>()
        listOf("recent" to "最近", "base" to "底色", "mix" to "混色", "part" to "粒子").forEach { (k, label) ->
            sortList.add(TextView(this).apply {
                text = label
                textSize = 10.5f
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(6))
                setOnClickListener { sortBy = k; render() }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                tag = k
            })
        }
        sortList.forEach { sortRow.addView(it) }
        sortViews = sortList
        filterCard.addView(sortRow)

        body.addView(View(this), LinearLayout.LayoutParams(0, dp(9)))

        // 搜索
        body.addView(EditText(this).apply {
            hint = "搜索精灵名称"
            textSize = 12.5f
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setTextColor(themed(MR.attr.colorOnSurface))
            setHintTextColor(themed(MR.attr.colorOnSurfaceVariant))
            baseCard(this)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { q = s?.toString().orEmpty(); renderGrid() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(9) })

        // 网格
        val sv = android.widget.ScrollView(this)
        grid = GridLayout(this).apply {
            columnCount = 2
            setPadding(0, 0, 0, dp(16))
        }
        sv.addView(grid)
        body.addView(sv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { bottomMargin = dp(8) })

        return root
    }

    private fun dimKey(i: Int) = when (i) { 0 -> "base"; 1 -> "mix"; else -> "part" }

    private fun baseCard(v: View) {
        v.setBackgroundColor(themed(MR.attr.colorSurface))
        v.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(13).toFloat()
            setColor(themed(MR.attr.colorSurface))
            setStroke(1, themed(MR.attr.colorOutline))
        }
    }

    // ——— 渲染 ———

    private fun render() {
        countTv.text = "${cards.size} 张"

        val labels = listOf(
            "底色 " + fBase,
            "混色 " + fMix,
            "粒子 " + if (fPart == ALL) "全部" else PART_NAMES[fPart]
        )
        selectors.forEachIndexed { i, tv ->
            tv.text = labels[i]
            val on = when (i) {
                0 -> fBase != "全部"
                1 -> fMix != "全部"
                else -> fPart != ALL
            }
            tv.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(themed(if (on) MR.attr.colorPrimaryContainer else MR.attr.colorSurfaceVariant))
            }
            tv.setTextColor(themed(if (on) MR.attr.colorOnPrimaryContainer else MR.attr.colorOnSurfaceVariant))
        }

        // 排序按钮
        sortViews.forEachIndexed { i, tv ->
            val k = tv.tag as String
            val on = k == sortBy
            tv.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(themed(if (on) MR.attr.colorPrimaryContainer else MR.attr.colorSurfaceVariant))
            }
            tv.setTextColor(themed(if (on) MR.attr.colorOnPrimaryContainer else MR.attr.colorOnSurfaceVariant))
        }
        renderChips()
        renderGrid()
    }

    private fun renderChips() {
        chipsBox.visibility = if (openDim.isEmpty()) View.GONE else View.VISIBLE
        if (openDim.isEmpty()) return
        chipsBox.removeAllViews()
        val items: List<Pair<String, String>> = when (openDim) {
            "base" -> listOf("全部" to "全部") + CardCats.BASE.map { it to it }
            "mix" -> listOf("全部" to "全部") + CardCats.MIX.map { it to it }
            else -> listOf("全部" to "全部") + PART_NAMES.mapIndexed { i, n -> "$i" to n }
        }
        val cur = when (openDim) {
            "base" -> fBase
            "mix" -> fMix
            else -> if (fPart == ALL) "全部" else fPart.toString()
        }
        items.forEach { (key, label) ->
            chipsBox.addView(TextView(this).apply {
                text = label
                textSize = 10.5f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(6))
                val on = key == cur
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(themed(if (on) MR.attr.colorPrimaryContainer else MR.attr.colorSurfaceVariant))
                }
                setTextColor(themed(if (on) MR.attr.colorOnPrimaryContainer else MR.attr.colorOnSurfaceVariant))
                setOnClickListener {
                    when (openDim) {
                        "base" -> fBase = key
                        "mix" -> fMix = key
                        else -> fPart = if (key == "全部") ALL else key.toInt()
                    }
                    render()
                }
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
            })
        }
    }

    private fun visible(): List<CardRec> {
        val out = cards.filter { c ->
            if (fBase != "全部" && CardCats.baseOf(c) != fBase) return@filter false
            if (fMix != "全部" && CardCats.mixOf(c) != fMix) return@filter false
            if (fPart != ALL && c.particle != fPart) return@filter false
            if (q.isNotBlank() && !c.spirit.contains(q, true)) return@filter false
            true
        }
        return when (sortBy) {
            "recent" -> out.sortedByDescending { it.ts }
            "base" -> out.sortedWith(compareBy { if (it.special) 99 else CardCats.BASE.indexOf(CardCats.baseOf(it)) })
            "mix" -> out.sortedWith(compareBy { if (it.special) 99 else CardCats.MIX.indexOf(CardCats.mixOf(it)) })
            else -> out.sortedBy { it.particle }
        }
    }

    /** 列数随窗口自适应：每列至少 ~150dp，最少 2 列、最多 4 列。 */
    private fun columns(): Int {
        val wDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return (wDp / 150f).toInt().coerceIn(2, 4)
    }

    private fun renderGrid() {
        grid.removeAllViews()
        val list = visible()
        val cols = columns()
        grid.columnCount = cols
        // 固定单元格宽度：否则「只剩一张」时权重列会把整行撑满，卡片被拉得巨大
        val cellW = ((resources.displayMetrics.widthPixels - dp(24) - dp(6) * cols) / cols)
            .coerceAtLeast(dp(80))
        if (list.isEmpty()) {
            grid.addView(TextView(this).apply {
                text = "当前筛选没有色卡"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(themed(MR.attr.colorOnSurfaceVariant))
                setPadding(0, dp(30), 0, dp(30))
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, cols.toFloat())
                ).apply { width = 0 }
            })
            return
        }
        list.forEach { c ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(6), dp(6), dp(6), dp(7))
                baseCard(this)
                setOnClickListener { showDetail(c) }
            }
            cell.addView(CardSwatchView(this).apply {
                bind(c.base, c.mix, c.particle)
                art = c.art
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            cell.addView(TextView(this).apply {
                text = c.spirit.ifBlank { "未命名" }
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(themed(MR.attr.colorOnSurface))
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(5), 0, 0)
            })
            cell.addView(TextView(this).apply {
                text = if (c.special) "特殊色卡" else "${CardCats.baseOf(c)}底 · ${CardCats.mixOf(c)}混"
                textSize = 9.5f
                setTextColor(themed(MR.attr.colorOnSurfaceVariant))
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(2), 0, 0)
            })
            grid.addView(cell, GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED)
            ).apply {
                width = cellW
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
    }

    private fun showDetail(c: CardRec) {
        val (dlg, box) = UiDialogs.panel(this, c.spirit.ifBlank { "未命名" })
        box.addView(CardSwatchView(this).apply {
            bind(c.base, c.mix, c.particle)
            art = c.art
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(120)
            ).apply { bottomMargin = dp(10) }
        })
        box.addView(infoRow("底色", "#%06X".format(c.base and 0xFFFFFF) + "  " + CardCats.baseOf(c)))
        box.addView(infoRow("混色", "#%06X".format(c.mix and 0xFFFFFF) + "  " + CardCats.mixOf(c)))
        box.addView(infoRow("粒子", PART_NAMES.getOrElse(c.particle) { "星芒" }))
        box.addView(UiDialogs.row(this, "改名字", "点这里改成实际的精灵名") {
            dlg.dismiss()
            UiDialogs.input(this, "改名字", "精灵名称", initial = c.spirit) { name ->
                val idx = cards.indexOfFirst { it.id == c.id }
                if (idx >= 0) {
                    cards[idx] = cards[idx].copy(spirit = name)
                    ShinyCardStore.save(this, cards)
                    render()
                }
            }
        })
        box.addView(UiDialogs.row(this, "删除这张色卡", danger = true) {
            dlg.dismiss()
            ShinyCardStore.remove(this, c.id)
            cards.clear(); cards.addAll(ShinyCardStore.load(this))
            render()
            toast("已删除")
        })
        box.addView(UiDialogs.row(this, "关闭", soft = true) { dlg.dismiss() })
        dlg.show()
    }

    private fun showMoreMenu() {
        UiDialogs.menu(
            this, "炫彩色卡", "共 ${cards.size} 张",
            listOf("导出 JSON", "导入 JSON", "分享色卡清单")
        ) { which ->
            when (which) {
                0 -> exportLauncher.launch("shiny_cards_${System.currentTimeMillis()}.json")
                1 -> importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                else -> shareList()
            }
        }
    }

    private fun shareList() {
        if (cards.isEmpty()) {
            toast("还没有色卡")
            return
        }
        val sb = StringBuilder()
        sb.append("我的炫彩色卡（").append(cards.size).append(" 张）\n")
        cards.sortedByDescending { it.ts }.forEach { c ->
            sb.append("· ").append(c.spirit.ifBlank { "未命名" }).append("  ")
                .append(if (c.special) "特殊" else "${CardCats.baseOf(c)}底 / ${CardCats.mixOf(c)}混")
                .append('\n')
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(send, "分享色卡"))
    }

    private fun infoRow(label: String, value: String): View =
        TextView(this).apply {
            text = "$label：$value"
            textSize = 12.5f
            setTextColor(themed(MR.attr.colorOnSurfaceVariant))
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }

    // ——— 工具 ———

    private fun themed(attr: Int): Int = resolveColor(this, attr)
    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        const val ALL = -2
        val PART_NAMES = arrayOf("星芒", "爱心", "菱形", "五角星", "特殊")
    }
}
