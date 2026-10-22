package com.wooze.rocomerchant

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.time.LocalDate

/**
 * 今日（+ 凌晨回看昨日）4 个刷新点的商品记录。
 * 数据来源：每次成功拉取后由 [DailyLog] 写入；纯本地，跨天自动清理。
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var llRounds: LinearLayout
    private lateinit var tvMeta: TextView

    private val itemBgs = listOf(
        R.drawable.bg_item_1, R.drawable.bg_item_2, R.drawable.bg_item_3,
        R.drawable.bg_item_4, R.drawable.bg_item_5, R.drawable.bg_item_6
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeBridge.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        llRounds = findViewById(R.id.llRounds)
        tvMeta = findViewById(R.id.tvHistoryMeta)

        findViewById<TextView>(R.id.btnClearToday).setOnClickListener {
            DailyLog.clearToday(this)
            DailyLog.clearAll(this)
            render()
            Toast.makeText(this, "已清空记录", Toast.LENGTH_SHORT).show()
        }
        // 主题呼应：清空按钮也跟着主题
        CardStyle.themeButton(this, findViewById(R.id.btnClearToday), 0f, 6, 18, 12)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        DailyLog.prune(this)
        llRounds.removeAllViews()

        val now = Schedule.nowBeijing()
        val todayMap = DailyLog.todayRounds(this)
        tvMeta.text = buildString {
            append(DailyLog.today().toString())
            append(" · 已记录 ${todayMap.size}/4 轮")
            append(" · 占用约 ${DailyLog.sizeBytes(this@HistoryActivity)} B")
        }
        addDaySection("今天", DailyLog.today(), todayMap, editableHint = true)

        // 凌晨 00:00–08:00 休息期：今天还没开市，回看昨日记录
        if (todayMap.isEmpty() && now.hour < Schedule.REFRESH_HOURS.first()) {
            val yMap = DailyLog.yesterdayRounds(this)
            if (yMap.isNotEmpty()) {
                addDaySection("昨天（凌晨回看，08:00 后自动清除）",
                    DailyLog.today().minusDays(1), yMap, editableHint = false)
            }
        }

        // 页脚：记录规则说明 + 更正入口
        llRounds.addView(TextView(this).apply {
            text = "说明：每轮商品由 App 自动记录，同一轮只补不覆盖；" +
                "长按某轮卡片可以手动更正或清空那一轮。"
            textSize = 11.5f
            setTextColor(this@HistoryActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(6, 10, 6, 20)
        })
    }

    private fun addDaySection(
        label: String, date: LocalDate,
        map: Map<Int, List<String>>, editableHint: Boolean
    ) {
        llRounds.addView(TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(this@HistoryActivity.themed(com.google.android.material.R.attr.colorOnSurface))
            setPadding(4, 12, 0, 6)
        })

        val now = Schedule.nowBeijing()
        Schedule.REFRESH_HOURS.forEachIndexed { idx, h ->
            val end = h + Schedule.ROUND_DURATION_HOURS
            val status = when {
                now.hour >= end -> "已结束"
                now.hour >= h && date == DailyLog.today() -> "进行中"
                else -> "未开始"
            }
            val items = map[h]
            val cardView = roundCard(idx + 1, h, end, status, items, editableHint)
            if (editableHint) {
                cardView.isClickable = true
                cardView.isFocusable = true
                cardView.setOnLongClickListener {
                    showRoundMenu(idx + 1, h, items)
                    true
                }
            }
            llRounds.addView(cardView)
        }
    }

    /** 长按某一轮卡片：手动更正 / 清空这一轮。 */
    private fun showRoundMenu(round: Int, hour: Int, items: List<String>?) {
        val has = !items.isNullOrEmpty()
        // 用「动作」配对选项，避免靠字符串前缀反查
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (has) {
            actions.add("更正这一轮的商品" to { askEditRound(round, hour, items) })
            actions.add("清空这一轮" to {
                DailyLog.clearRound(this, hour)
                render()
                Toast.makeText(this, "已清空第 $round 轮", Toast.LENGTH_SHORT).show()
            })
        } else {
            actions.add("手动补录这一轮" to { askEditRound(round, hour, items) })
        }
        UiDialogs.menu(
            this,
            "第 $round 轮",
            String.format("%02d:00 – %02d:00", hour, (hour + Schedule.ROUND_DURATION_HOURS) % 24),
            actions.map { it.first },
            dangerIndex = if (has) 1 else -1
        ) { which -> actions[which].second() }
    }

    private fun askEditRound(round: Int, hour: Int, items: List<String>?) {
        val (dlg, box) = UiDialogs.panel(
            this, "第 $round 轮的商品",
            "多个商品用「、」或「,」分隔；留空 = 清空这一轮"
        )
        val et = android.widget.EditText(this).apply {
            setText(items?.joinToString("、") ?: "")
            hint = "商品名，用「、」或「,」分隔"
            setSingleLine(false)
            maxLines = 4
            setPadding(40, 34, 40, 34)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 36f
                setColor(this@HistoryActivity.themed(com.google.android.material.R.attr.colorSurfaceVariant))
                setStroke(1, this@HistoryActivity.themed(com.google.android.material.R.attr.colorOutline))
            }
            setTextColor(this@HistoryActivity.themed(com.google.android.material.R.attr.colorOnSurface))
            setHintTextColor(this@HistoryActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
            textSize = 15f
        }
        box.addView(et)

        // —— 商品联想：取光标前最后一个没打完的词，去商品池找，点一下就补全 ——
        val listBox = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        box.addView(listBox)

        fun currentWord(): String {
            val t = et.text.toString()
            val pos = et.selectionEnd.coerceIn(0, t.length)
            return t.substring(0, pos).split('、', ',', '，', '\n').lastOrNull()?.trim() ?: ""
        }

        fun refreshSuggest() {
            listBox.removeAllViews()
            val q = currentWord()
            if (q.isEmpty()) return
            val hits = (ItemPool.ALL_NAMES.filter { it.startsWith(q) } +
                    ItemPool.ALL_NAMES.filter { q in it && !it.startsWith(q) })
                .distinct().take(6)
            hits.forEach { name ->
                listBox.addView(UiDialogs.row(this, name, soft = true) {
                    val t = et.text.toString()
                    val pos = et.selectionEnd.coerceIn(0, t.length)
                    val head = t.substring(0, pos)
                    val cut = head.lastIndexOfAny(charArrayOf('、', ',', '，', '\n'))
                    val newHead = if (cut >= 0) head.substring(0, cut + 1) else ""
                    et.setText("$newHead$name、")
                    et.setSelection(et.text.length)
                })
            }
        }

        et.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = refreshSuggest()
        })

        box.addView(UiDialogs.row(this, "保存") {
            val list = et.text.toString()
                .split('、', ',', '，', '\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            dlg.dismiss()
            if (list.isEmpty()) DailyLog.clearRound(this, hour)
            else DailyLog.setRound(this, hour, list.map { ItemPool.canonical(it) }.distinct())
            render()
            Toast.makeText(this, "第 $round 轮已保存", Toast.LENGTH_SHORT).show()
        })
        box.addView(UiDialogs.row(this, "取消", soft = true) { dlg.dismiss() })
        dlg.show()
    }

    private fun roundCard(
        round: Int, hour: Int, end: Int, status: String,
        items: List<String>?, editableHint: Boolean
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = CardStyle.cardBg(this@HistoryActivity, 16f)
            setPadding(14, 12, 14, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "第 $round 轮"
            textSize = 15f
            setTextColor(this@HistoryActivity.themed(com.google.android.material.R.attr.colorOnSurface))
        })
        header.addView(TextView(this).apply {
            text = String.format("  %02d:00 – %02d:00", hour, end % 24)
            textSize = 12f
            setTextColor(this@HistoryActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = status
            textSize = 12f
            setTextColor(
                resolveColor(
                    this@HistoryActivity,
                    if (status == "进行中") R.color.green else com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )
        })
        card.addView(header)

        if (items.isNullOrEmpty()) {
            card.addView(TextView(this).apply {
                text = if (editableHint) "暂无记录（该轮开市后会自动记录）" else "暂无记录"
                textSize = 12f
                setTextColor(this@HistoryActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, 10, 0, 2)
            })
            return card
        }

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }
        val inflater = LayoutInflater.from(this)
        var row: LinearLayout? = null
        items.forEachIndexed { i, name ->
            if (i % 3 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    weightSum = 3f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                grid.addView(row)
            }
            val cell = inflater.inflate(R.layout.item_card, row, false) as FrameLayout
            cell.background = ContextCompat.getDrawable(this, itemBgs[i % itemBgs.size])
            cell.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            cell.findViewById<TextView>(R.id.tvItemName).text = name
            val iv = cell.findViewById<ImageView>(R.id.ivItemIcon)
            when (val r = IconResolver.resolve(this, name)) {
                is IconResult.ResId -> iv.setImageResource(r.id)
                is IconResult.LocalFile -> {
                    val bmp = BitmapFactory.decodeFile(r.file.absolutePath)
                    if (bmp != null) iv.setImageBitmap(bmp)
                    else iv.setImageResource(R.drawable.widget_icon_placeholder)
                }
                IconResult.None -> iv.setImageResource(R.drawable.widget_icon_placeholder)
            }
            row?.addView(cell)
        }
        val remainder = items.size % 3
        if (remainder != 0) {
            repeat(3 - remainder) {
                row?.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                })
            }
        }
        card.addView(grid)
        return card
    }
}
