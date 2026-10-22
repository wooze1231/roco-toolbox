package com.wooze.rocomerchant

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.R as MR

/**
 * 订阅提醒设置页（卡片化）。
 *
 * 列表来源 = 内置商品池 + 历史出现 + 已订阅项 + 当前在售（被删掉的一律不出现）。
 *
 * 联动规则（三态）：
 *   - 勾「订阅整个分类」→ 该类细分商品全勾上，并记住分类订阅
 *   - 取消「整个分类」  → 该类细分商品全取消
 *   - 手动取消某细分项  → 「整个分类」自动取消
 *   - 细分子项全勾上    → 「整个分类」自动勾上
 */
class SubscribeActivity : AppCompatActivity() {

    /** 重入保护：批量勾选时不触发监听回调里的联动逻辑。 */
    private var suppress = false

    /** 从「编辑商品池」返回后需要重建列表。 */
    private var firstResume = true

    private val prefs by lazy { getSharedPreferences("merchant_prefs", MODE_PRIVATE) }

    override fun onResume() {
        super.onResume()
        if (firstResume) firstResume = false else recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeBridge.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscribe)
        PaintTheme.decorate(this, findViewById(R.id.headerBar))
        findViewById<TextView>(R.id.btnSubBack).setOnClickListener { finish() }
        render()
    }

    private fun render() {
        val container = findViewById<LinearLayout>(R.id.subContainer)
        container.removeAllViews()

        val hidden = ItemCatalog.hidden(this)
        val current = prefs.getString("items", "")?.split("\u0001").orEmpty()
        val all = LinkedHashSet<String>().apply {
            addAll(ItemCatalog.allNames(this@SubscribeActivity))
            addAll(Subscriptions.seen(this@SubscribeActivity))
            addAll(Subscriptions.items(this@SubscribeActivity))
            addAll(ItemIcons.MAP.keys)
            addAll(current.filter { it.isNotBlank() })
        }.filter { it !in hidden }.toSortedSet()

        if (all.isEmpty()) {
            val card = CardStyle.card(this)
            card.addView(TextView(this).apply {
                text = "暂无数据，先打开首页刷新一次商品即可。"
                textSize = 13f
                setTextColor(themed(MR.attr.colorOnSurfaceVariant))
                setPadding(dp(14), dp(16), dp(14), dp(16))
            })
            container.addView(card)
            return
        }

        val catOf = ItemCatalog.categoryMap(this, all)
        val groups = linkedMapOf<String, MutableList<String>>()
        for (n in all) groups.getOrPut(catOf[n] ?: Categories.ALL.last()) { mutableListOf() }.add(n)
        val ordered = Categories.ALL.filter { groups.containsKey(it) } +
            groups.keys.filter { it !in Categories.ALL }

        val catSet = Subscriptions.cats(this)
        val itemSet = Subscriptions.items(this)
        val savedExpanded = prefs.getStringSet("sub_expanded", emptySet()).orEmpty()
        val expanded = linkedMapOf<String, Boolean>().apply {
            ordered.forEach { put(it, it in savedExpanded) }
        }

        // —— 顶部统计条：已选计数 + 编辑商品池 + 清空 ——
        val leftTv = TextView(this).apply {
            textSize = 12.5f
            setTextColor(themed(MR.attr.colorOnSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val editTv = TextView(this).apply {
            text = "编辑商品池"
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(themed(MR.attr.colorPrimary))
            isClickable = true
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                startActivity(Intent(this@SubscribeActivity, ItemEditActivity::class.java))
            }
        }
        val clearTv = TextView(this).apply {
            text = "清空"
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(themed(MR.attr.colorError))
            isClickable = true
            setPadding(dp(8), dp(4), dp(2), dp(4))
            setOnClickListener {
                Subscriptions.clearAll(this@SubscribeActivity)
                recreate()
            }
        }
        container.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(10))
            addView(leftTv)
            addView(editTv)
            addView(clearTv)
        })

        // —— 全选卡 ——
        val allSwitch = SwitchCompat(this)
        val allCard = CardStyle.card(this)
        val allRow = CardStyle.row(this)
        val allTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        allTexts.addView(TextView(this).apply {
            text = "全选"
            textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(themed(MR.attr.colorOnSurface))
        })
        allTexts.addView(TextView(this).apply {
            text = "任一商品在售即提醒"
            textSize = 11.5f
            setPadding(0, dp(3), 0, 0)
            setTextColor(themed(MR.attr.colorOnSurfaceVariant))
        })
        allRow.addView(allTexts)
        allRow.addView(allSwitch)
        allCard.addView(allRow)
        container.addView(allCard)

        // 每个分类的细分勾选框（供全选/整类联动批量设置）
        val childBoxes = linkedMapOf<String, MutableList<CheckBox>>()

        fun refreshCount() {
            val its = Subscriptions.items(this)
            val cs = Subscriptions.cats(this)
            val effective = all.count { its.contains(it) || cs.contains(catOf[it]) }
            leftTv.text = "已选 $effective 项 / ${cs.size} 个分类"
            suppress = true
            allSwitch.isChecked = effective >= all.size
            suppress = false
        }

        var rebuild: () -> Unit = {}

        for (cat in ordered) {
            val names = groups[cat] ?: continue
            val catOn = catSet.contains(cat)
            val card = CardStyle.card(this)
            val boxes = mutableListOf<CheckBox>()
            val open = expanded[cat] == true
            val picked = names.count { catOn || itemSet.contains(it) }

            val head = CardStyle.groupHeader(
                this, cat,
                "${names.size} 项" + if (picked > 0) " · 已选 $picked" else "",
                open
            )
            head.isClickable = true
            head.setOnClickListener {
                expanded[cat] = expanded[cat] != true
                rebuild()
            }
            card.addView(head)

            if (open) {
                val parent = CheckBox(this).apply {
                    text = "订阅整个分类"
                    textSize = 14f
                    setTextColor(themed(MR.attr.colorOnSurface))
                    buttonDrawable = checkDrawable()
                    buttonTintList = null
                    setPadding(dp(14), dp(9), dp(14), dp(9))
                    isChecked = catOn
                    setOnCheckedChangeListener { _, on ->
                        if (suppress) return@setOnCheckedChangeListener
                        suppress = true
                        boxes.forEach { it.isChecked = on }
                        suppress = false
                        Subscriptions.setItems(this@SubscribeActivity, names, on)
                        Subscriptions.setCat(this@SubscribeActivity, cat, on)
                        refreshCount()
                    }
                }
                card.addView(CardStyle.divider(this))
                card.addView(parent)

                for (name in names) {
                    val box = CheckBox(this).apply {
                        text = name
                        textSize = 14f
                        setTextColor(themed(MR.attr.colorOnSurface))
                        buttonDrawable = checkDrawable()
                        buttonTintList = null
                        setPadding(dp(36), dp(9), dp(14), dp(9))
                        isChecked = catOn || itemSet.contains(name)
                        setOnCheckedChangeListener { _, on ->
                            if (suppress) return@setOnCheckedChangeListener
                            Subscriptions.setItem(this@SubscribeActivity, name, on)
                            val allOn = boxes.all { it.isChecked }
                            suppress = true
                            parent.isChecked = allOn
                            suppress = false
                            Subscriptions.setCat(this@SubscribeActivity, cat, allOn)
                            refreshCount()
                        }
                    }
                    boxes.add(box)
                    card.addView(CardStyle.divider(this))
                    card.addView(box)
                }
            }
            childBoxes[cat] = boxes
            container.addView(card)
        }

        rebuild = {
            prefs.edit()
                .putStringSet("sub_expanded", expanded.filterValues { it }.keys)
                .apply()
            recreate()
        }

        allSwitch.setOnCheckedChangeListener { _, on ->
            if (suppress) return@setOnCheckedChangeListener
            suppress = true
            for ((cat, boxes) in childBoxes) {
                boxes.forEach { it.isChecked = on }
                Subscriptions.setCat(this, cat, on)
            }
            Subscriptions.setItems(this, all, on)
            suppress = false
            refreshCount()
        }

        refreshCount()
    }

    // —— 工具 ——

    private fun checkDrawable(): Drawable = CheckDrawable(
        sizePx = dp(22),
        onColor = themed(MR.attr.colorPrimary),
        borderColor = themed(MR.attr.colorOutline),
        borderPx = dp(2).toFloat(),
        radiusPx = dp(7).toFloat()
    )

    private fun themed(attr: Int): Int = resolveColor(this, attr)

    private fun dp(v: Int) = CardStyle.dp(this, v)
}
