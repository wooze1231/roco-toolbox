package com.wooze.rocomerchant

import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.R as MR

/**
 * 编辑商品池：分组卡片 + 自绘控件。
 *
 * - 点分类卡头：折叠 / 展开
 * - 行内「分类」药丸：点开换分类（即存）
 * - 行内「改名 / 删除」：改名走输入弹窗；删除的内置商品可恢复
 * - 底部新增卡：输入名称 + 选分类 + 添加；一键恢复默认
 */
class ItemEditActivity : AppCompatActivity() {

    private val cats: List<String> get() = Categories.ALL
    private val prefs by lazy { getSharedPreferences("merchant_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeBridge.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_edit)
        PaintTheme.decorate(this, findViewById(R.id.headerBar))
        findViewById<TextView>(R.id.btnEditBack).setOnClickListener { finish() }
        buildUi()
    }

    // —— 折叠状态 ——

    private fun expandedSet(): MutableSet<String> =
        prefs.getStringSet("pool_expanded", emptySet()).orEmpty().toMutableSet()

    private fun saveExpanded(set: Set<String>) {
        prefs.edit().putStringSet("pool_expanded", set).apply()
    }

    // —— 渲染 ——

    private fun buildUi() {
        val container = findViewById<LinearLayout>(R.id.editContainer)
        container.removeAllViews()

        val names = ItemCatalog.allNames(this)
        val catOf = ItemCatalog.categoryMap(this, names)
        val groups = linkedMapOf<String, MutableList<String>>()
        for (n in names) groups.getOrPut(catOf[n] ?: cats.last()) { mutableListOf() }.add(n)
        val ordered = cats.filter { groups.containsKey(it) } + groups.keys.filter { it !in cats }
        val custom = ItemCatalog.customMap(this).keys
        val hidden = ItemCatalog.hidden(this)
        val expanded = expandedSet()
        val allKeys = ordered + if (hidden.isNotEmpty()) listOf("已删除") else emptyList()

        // 统计条（点右侧可全部展开 / 收起）
        val summary = CardStyle.summary(
            this,
            "当前 ${names.size} 项 · 自定义 ${custom.size}" +
                if (hidden.isEmpty()) "" else " · 已删除 ${hidden.size}",
            if (expanded.containsAll(allKeys)) "全部收起" else "全部展开"
        )
        summary.isClickable = true
        summary.setOnClickListener {
            if (expandedSet().containsAll(allKeys)) saveExpanded(emptySet())
            else saveExpanded(allKeys.toSet())
            buildUi()
        }
        container.addView(summary)

        // 各分类一张卡
        for (cat in ordered) {
            val groupNames = groups[cat] ?: continue
            val open = cat in expanded
            val card = CardStyle.card(this)

            val head = CardStyle.groupHeader(this, cat, "${groupNames.size} 项", open)
            head.isClickable = true
            head.setOnClickListener {
                val s = expandedSet()
                if (!s.remove(cat)) s.add(cat)
                saveExpanded(s)
                buildUi()
            }
            card.addView(head)

            if (open) {
                for (name in groupNames) {
                    card.addView(CardStyle.divider(this))
                    card.addView(makeRow(name, cat, custom.contains(name), hiddenMode = false))
                }
            }
            container.addView(card)
        }

        // 已删除（可恢复）
        if (hidden.isNotEmpty()) {
            val open = "已删除" in expanded
            val card = CardStyle.card(this)
            val head = CardStyle.groupHeader(this, "已删除", "${hidden.size} 项（点右侧可恢复）", open, muted = true)
            head.isClickable = true
            head.setOnClickListener {
                val s = expandedSet()
                if (!s.remove("已删除")) s.add("已删除")
                saveExpanded(s)
                buildUi()
            }
            card.addView(head)
            if (open) {
                for (name in hidden.sorted()) {
                    card.addView(CardStyle.divider(this))
                    card.addView(makeRow(name, ItemCatalog.category(this, name), false, hiddenMode = true))
                }
            }
            container.addView(card)
        }

        container.addView(buildAddCard())
    }

    /** 一行：名称 + 分类药丸 + 改名 + 删除/恢复。 */
    private fun makeRow(name: String, cat: String, isCustom: Boolean, hiddenMode: Boolean): View {
        val row = CardStyle.row(this)

        row.addView(TextView(this).apply {
            text = if (isCustom) "$name（自定义）" else name
            textSize = 14f
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(themed(if (hiddenMode) MR.attr.colorOnSurfaceVariant else MR.attr.colorOnSurface))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        if (!hiddenMode) {
            row.addView(CardStyle.pill(this, cat, "cat").apply {
                isClickable = true
                setOnClickListener {
                    UiDialogs.menu(this@ItemEditActivity, "改分类", "「$name」现在归在【$cat】", cats) { i ->
                        ItemCatalog.setCatOverride(this@ItemEditActivity, name, cats[i])
                        toast("$name → ${cats[i]}")
                        buildUi()
                    }
                }
            })
            row.addView(CardStyle.pill(this, "改名", "edit").apply {
                isClickable = true
                setOnClickListener {
                    UiDialogs.input(
                        this@ItemEditActivity, "改名字", "新名称", initial = name,
                        message = "「$name」现在归在【$cat】。改名后分类跟着走，订阅勾选也会一起迁移。"
                    ) { newName ->
                        if (ItemCatalog.rename(this@ItemEditActivity, name, newName)) {
                            toast("已改名：$name → $newName")
                        } else {
                            toast("改名失败：新名字为空或已存在")
                        }
                        buildUi()
                    }
                }
            })
        }

        row.addView(CardStyle.pill(this, if (hiddenMode) "恢复" else "删除", if (hiddenMode) "restore" else "del").apply {
            isClickable = true
            setOnClickListener {
                if (hiddenMode) {
                    ItemCatalog.restore(this@ItemEditActivity, name)
                    toast("已恢复：$name")
                } else {
                    ItemCatalog.remove(this@ItemEditActivity, name)
                    toast("已删除：$name")
                }
                buildUi()
            }
        })
        return row
    }

    /** 底部新增卡：输入名 + 选分类 + 添加；恢复默认。 */
    private fun buildAddCard(): View {
        val card = CardStyle.card(this)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(2))
        }
        val et = EditText(this).apply {
            hint = "商品名，如 黄石榴石"
            textSize = 14f
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = CardStyle.inputBg(this@ItemEditActivity)
            setTextColor(themed(MR.attr.colorOnSurface))
            setHintTextColor(themed(MR.attr.colorOnSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sp = Spinner(this).apply {
            adapter = ArrayAdapter(this@ItemEditActivity, android.R.layout.simple_spinner_dropdown_item, cats)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(8) }
        }
        row.addView(et)
        row.addView(sp)
        card.addView(row)

        val btn = CardStyle.primaryButton(this, "添加").apply {
            isClickable = true
            setOnClickListener {
                val n = et.text.toString().trim()
                if (n.isEmpty()) {
                    toast("先填商品名")
                    return@setOnClickListener
                }
                val sel = cats.getOrElse(sp.selectedItemPosition) { cats.last() }
                ItemCatalog.addCustom(this@ItemEditActivity, n, sel)
                et.setText("")
                toast("已添加：$n → $sel")
                buildUi()
            }
        }
        btn.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
        ).apply { setMargins(dp(14), dp(10), dp(14), dp(4)) }
        card.addView(btn)

        card.addView(TextView(this).apply {
            text = "恢复默认商品池"
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTextColor(themed(MR.attr.colorPrimary))
            setPadding(dp(14), dp(8), dp(14), dp(14))
            isClickable = true
            setOnClickListener {
                ItemCatalog.reset(this@ItemEditActivity)
                toast("已恢复默认商品池")
                buildUi()
            }
        })
        return card
    }

    private fun themed(attr: Int): Int = resolveColor(this, attr)

    private fun dp(v: Int) = CardStyle.dp(this, v)

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
