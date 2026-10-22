package com.wooze.rocomerchant

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/**
 * 统一风格的弹窗。
 *
 * 之前到处是 `AlertDialog.Builder.setMessage/setItems`，走的是系统默认样式：
 * 方角白底、左对齐文字、灰分隔线、橙色按钮 —— 和异色图鉴那套米白圆角卡片完全不搭。
 *
 * 这里收口成一套自绘弹窗：
 * - 面板：米白 + 22dp 圆角，窗口背景透明；
 * - 标题：居中加粗，下面一条 1px 分隔线；
 * - 选项：圆角卡片行（danger = 粉底红字，soft = 米底灰字），带水波纹；
 * - 说明：居中小字，可滚动（内容长时不撑爆屏幕）。
 *
 * 所有界面都走这几个入口，样式就不会再各长各的。
 *
 * 颜色一律走 R.color.*（而非硬编码），这样 values-night 能自动接管深色模式。
 */
object UiDialogs {

    // —— dp 换算 ——

    private fun dp(c: Context, v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), c.resources.displayMetrics
    ).toInt()

    /** 取色（随日夜模式自动切换）。 */
    private fun col(c: Context, resId: Int): Int = resolveColor(c, resId)

    // —— 骨架 ——

    /** 创建一个空的美化弹窗面板，自己往里加内容。返回 (dialog, 面板容器)。 */
    fun panel(
        c: Context,
        title: String? = null,
        subtitle: String? = null
    ): Pair<AlertDialog, LinearLayout> {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 8))
            background = if (ThemeBridge.isPaintTheme(c)) {
                PaintTheme.wobbleRect(
                    c,
                    c.themed(com.google.android.material.R.attr.colorSurface),
                    c.themed(com.google.android.material.R.attr.colorOutline),
                    22f, 7
                )
            } else {
                GradientDrawable().apply {
                    cornerRadius = dp(c, 22).toFloat()
                    setColor(c.themed(com.google.android.material.R.attr.colorSurface))
                }
            }
        }

        if (!title.isNullOrBlank()) {
            box.addView(TextView(c).apply {
                text = title
                textSize = 19f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(c.themed(com.google.android.material.R.attr.colorOnSurface))
            })
        }
        if (!subtitle.isNullOrBlank()) {
            box.addView(TextView(c).apply {
                text = subtitle
                textSize = 12.5f
                gravity = Gravity.CENTER
                setTextColor(c.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, dp(c, 5), 0, 0)
            })
        }
        if (!title.isNullOrBlank() || !subtitle.isNullOrBlank()) {
            box.addView(divider(c, topMargin = 14, bottomMargin = 10))
        }

        // 内容装进「限高滚动容器」：内容少 → 高度自适应；内容多 → 内部滚动，
        // 最高不超过屏幕 62%，弹窗永远不会撑满/溢出屏幕。
        // 返回给调用方的 box 实为 content，调用方照常 addView 即可。
        val content = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
        }
        val maxH = (c.resources.displayMetrics.heightPixels * 0.62f).toInt()
        val scroll = object : ScrollView(c) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                if (measuredHeight > maxH) setMeasuredDimension(measuredWidth, maxH)
            }
        }.apply { addView(content) }
        box.addView(scroll)

        val dlg = AlertDialog.Builder(c).setView(box).create()
        dlg.setOnShowListener {
            dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val screenW = c.resources.displayMetrics.widthPixels
            val maxW = (420f * c.resources.displayMetrics.density).toInt()
            dlg.window?.setLayout(
                (screenW * 0.9f).toInt().coerceAtMost(maxW),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return dlg to content
    }

    /** 一行选项，供外部自行拼装弹窗时使用。 */
    fun row(
        c: Context,
        title: String,
        sub: String = "",
        danger: Boolean = false,
        soft: Boolean = false,
        onClick: () -> Unit
    ): View = optionRow(c, title, sub, danger, soft, onClick)

    private fun divider(c: Context, topMargin: Int = 0, bottomMargin: Int = 0): View =
        View(c).apply {
            val paint = ThemeBridge.isPaintTheme(c)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, if (paint) dp(c, 3) else 1
            ).apply {
                this.topMargin = dp(c, topMargin)
                this.bottomMargin = dp(c, bottomMargin)
            }
            if (paint) {
                background = PaintTheme.wobbleLine(
                    c, c.themed(com.google.android.material.R.attr.colorOutline), 2f
                )
            } else {
                setBackgroundColor(c.themed(com.google.android.material.R.attr.colorOutline))
            }
        }

    /** 一行选项：白色圆角卡片，可带副标题。 */
    private fun optionRow(
        c: Context,
        title: String,
        sub: String = "",
        danger: Boolean = false,
        soft: Boolean = false,
        onClick: () -> Unit
    ): View {
        val base = when {
            danger -> com.google.android.material.R.attr.colorErrorContainer
            soft -> com.google.android.material.R.attr.colorSurfaceVariant
            else -> com.google.android.material.R.attr.colorSurface
        }
        val fg = when {
            danger -> com.google.android.material.R.attr.colorError
            soft -> com.google.android.material.R.attr.colorOnSurfaceVariant
            else -> com.google.android.material.R.attr.colorOnSurface
        }
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(col(c, if (danger) com.google.android.material.R.attr.colorError else com.google.android.material.R.attr.colorOutline)),
                GradientDrawable().apply {
                    cornerRadius = dp(c, 13).toFloat()
                    setColor(col(c, base))
                    setStroke(PaintTheme.cardStrokePx(c), c.themed(com.google.android.material.R.attr.colorOutline))
                },
                GradientDrawable().apply {
                    cornerRadius = dp(c, 13).toFloat()
                    setColor(col(c, base))
                }
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(c, 6) }
            setOnClickListener { onClick() }
        }
        if (!soft && !danger && ThemeBridge.isPaintTheme(c)) PaintTheme.tilt(row, -0.5f)
        row.addView(TextView(c).apply {
            text = title
            textSize = 14.5f
            setTextColor(col(c, fg))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        if (sub.isNotBlank()) {
            row.addView(TextView(c).apply {
                text = sub
                textSize = 11.5f
                setTextColor(c.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, dp(c, 3), 0, 0)
            })
        }
        return row
    }

    /** 底部「关闭 / 取消」这类弱化的整行按钮。 */
    private fun closeRow(c: Context, text: String, onClick: () -> Unit): View =
        optionRow(c, text, soft = true, onClick = onClick)

    // —— 多选 ——

    /**
     * 一行**可勾选**的选项：左侧一个方块勾选框，右侧标题 + 副标题。
     * 点整行即切换勾选状态，回调返回最新状态。
     */
    private fun checkRow(
        c: Context,
        title: String,
        sub: String = "",
        checked: Boolean,
        onToggle: (Boolean) -> Unit
    ): View {
        var on = checked
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(c, 6) }
        }
        val box = TextView(c).apply {
            text = if (on) "☑" else "☐"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(col(c, if (on) com.google.android.material.R.attr.colorOnSurface else com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        box.layoutParams = LinearLayout.LayoutParams(dp(c, 30), dp(c, 30))
        val texts = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvTitle = TextView(c).apply {
            text = title
            textSize = 14.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val tvSub = TextView(c).apply {
            text = sub
            textSize = 11.5f
            setPadding(0, dp(c, 2), 0, 0)
            visibility = if (sub.isBlank()) View.GONE else View.VISIBLE
        }
        texts.addView(tvTitle)
        texts.addView(tvSub)
        row.addView(box)
        row.addView(texts)

        // 勾选态用「背景色 + 勾选框字符 + 文字色」三重反馈，避免只变一个方块不够明显
        fun paint() {
            val bg = if (on) com.google.android.material.R.attr.colorPrimaryContainer else com.google.android.material.R.attr.colorSurface
            box.text = if (on) "☑" else "☐"
            box.setTextColor(col(c, if (on) com.google.android.material.R.attr.colorOnSurface else com.google.android.material.R.attr.colorOnSurfaceVariant))
            tvTitle.setTextColor(c.themed(com.google.android.material.R.attr.colorOnSurface))
            row.background = RippleDrawable(
                ColorStateList.valueOf(c.themed(com.google.android.material.R.attr.colorOutline)),
                GradientDrawable().apply {
                    cornerRadius = dp(c, 13).toFloat()
                    setColor(col(c, bg))
                    setStroke(if (on) 2 else 1, col(c, if (on) com.google.android.material.R.attr.colorOnSurface else com.google.android.material.R.attr.colorOutline))
                },
                GradientDrawable().apply {
                    cornerRadius = dp(c, 13).toFloat()
                    setColor(col(c, bg))
                }
            )
        }
        paint()
        row.setOnClickListener {
            on = !on
            paint()
            onToggle(on)
        }
        return row
    }

    /**
     * 多选面板：候选列表带勾选框，底部一个「加入选中的 N 只」主按钮。
     *
     * 与 [menuWithSubs] 的区别：点选项**不关窗**，可以连续勾多只，最后一次提交。
     * 用于「一屏截图里有好几只精灵」的场景。
     *
     * @param items  每项 (主标题, 副说明)
     * @param preselect 初始勾选的下标集合（比如按置信度预勾最像的那几个）
     * @param confirmText 主按钮文案模板，`%d` 会被替换成已勾数量
     * @param onConfirm 提交回调，参数是**已勾选的原始下标列表**（保持候选顺序）
     */
    fun multiPick(
        c: Context,
        title: String,
        subtitle: String? = null,
        items: List<Pair<String, String>>,
        preselect: Set<Int> = emptySet(),
        confirmText: String = "加入选中的 %d 只",
        extraRows: List<View> = emptyList(),
        onConfirm: (List<Int>) -> Unit
    ) {
        val (dlg, box) = panel(c, title, subtitle)
        val picked = LinkedHashSet<Int>().apply { addAll(preselect.filter { it in items.indices }) }

        // 主按钮要随勾选数量实时更新文案与可用状态，所以先建引用
        var confirmBtn: View? = null

        fun refresh() {
            confirmBtn?.let { v ->
                val tv = (v as? android.view.ViewGroup)?.getChildAt(0) as? TextView
                tv?.text = if (picked.isEmpty()) "勾选要加入的精灵"
                else confirmText.replace("%d", picked.size.toString())
                tv?.setTextColor(
                    col(c, if (picked.isEmpty()) com.google.android.material.R.attr.colorOnSurfaceVariant else com.google.android.material.R.attr.colorOnSurface)
                )
                v.isEnabled = picked.isNotEmpty()
            }
        }

        items.forEachIndexed { i, (label, sub) ->
            box.addView(checkRow(c, label, sub, checked = i in picked) { on ->
                if (on) picked.add(i) else picked.remove(i)
                refresh()
            })
        }
        extraRows.forEach { box.addView(it) }

        val btn = optionRow(c, confirmText.replace("%d", "0"), "") {
            if (picked.isEmpty()) return@optionRow
            val sel = picked.toList().sorted()
            dlg.dismiss()
            onConfirm(sel)
        }
        confirmBtn = btn
        box.addView(btn)
        refresh()
        dlg.show()
    }

    // —— 对外入口 ——

    /**
     * 选项菜单（替代 setItems）。
     * 点任意一项后自动关闭弹窗。
     */
    fun menu(
        c: Context,
        title: String,
        subtitle: String? = null,
        items: List<String>,
        dangerIndex: Int = -1,
        onPick: (Int) -> Unit
    ) {
        val (dlg, box) = panel(c, title, subtitle)
        items.forEachIndexed { i, label ->
            box.addView(optionRow(c, label, danger = i == dangerIndex) {
                dlg.dismiss()
                onPick(i)
            })
        }
        box.addView(closeRow(c, "关闭") { dlg.dismiss() })
        dlg.show()
    }

    /**
     * 联想输入：一边打字，一边在下面列出匹配的条目（图标 → 名字 → 编号）。
     *
     * 用于「手打精灵名」的所有入口。用户打错一个字时，靠联想列表直接点一下就能纠正，
     * 不用记住图鉴里的正式写法。
     *
     * @param suggest 给一个查询词，返回候选（调用方接 `ShinyCatalog.suggest`）
     * @param iconOf  取某个候选的图标；返回 null 就用占位图
     * @param onPick  点中某个候选（大多数场景直接就定了，不必再输入）
     * @param onFree  用户不想选候选，就用自己打的原话（可能图鉴没有，需要走自定义）
     */
    fun suggestInput(
        c: Context,
        title: String,
        hint: String,
        initial: String = "",
        suggest: (String) -> List<ShinyCatalog.Entry>,
        iconOf: (ShinyCatalog.Entry) -> android.graphics.Bitmap?,
        pickText: String = "就选这一只",
        freeText: String = "就用我打的字",
        onPick: (ShinyCatalog.Entry) -> Unit,
        onFree: (String) -> Unit
    ) {
        val (dlg, box) = panel(c, title, "边打边找，点一下就选中")

        val et = EditText(c).apply {
            this.hint = hint
            if (initial.isNotBlank()) setText(initial)
            setSingleLine(true)
            setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14))
            background = GradientDrawable().apply {
                cornerRadius = dp(c, 14).toFloat()
                setColor(c.themed(com.google.android.material.R.attr.colorSurfaceVariant))
                setStroke(PaintTheme.cardStrokePx(c), c.themed(com.google.android.material.R.attr.colorOutline))
            }
            setTextColor(c.themed(com.google.android.material.R.attr.colorOnSurface))
            setHintTextColor(c.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
            textSize = 15f
        }
        box.addView(et)

        // 候选列表容器：每次输入变化就重建内容
        val listBox = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        // 用 ScrollView 包住，候选多时不会把弹窗撑爆
        val sv = ScrollView(c).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 200)
            ).apply { topMargin = dp(c, 8) }
            isVerticalScrollBarEnabled = true
        }
        sv.addView(listBox)
        box.addView(sv)

        var currentTop: ShinyCatalog.Entry? = null

        fun refresh() {
            val q = et.text.toString().trim()
            listBox.removeAllViews()
            currentTop = null
            val hits = try { suggest(q) } catch (e: Exception) { emptyList() }
            if (hits.isEmpty()) {
                listBox.addView(TextView(c).apply {
                    text = if (q.isEmpty()) "还没有匹配的精灵" else "图鉴里没有「$q」"
                    textSize = 12.5f
                    setTextColor(c.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    gravity = Gravity.CENTER
                    setPadding(0, dp(c, 20), 0, dp(c, 20))
                })
                return
            }
            currentTop = hits.first()
            for ((i, e) in hits.withIndex()) {
                listBox.addView(suggestRow(c, e, i == 0, iconOf) {
                    dlg.dismiss()
                    onPick(e)
                })
            }
        }

        et.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { refresh() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
        })

        box.addView(optionRow(c, pickText) {
            val top = currentTop
            if (top != null) { dlg.dismiss(); onPick(top) }
            else {
                val v = et.text.toString().trim()
                if (v.isEmpty()) return@optionRow
                dlg.dismiss(); onFree(v)
            }
        })
        box.addView(closeRow(c, "取消") { dlg.dismiss() })

        dlg.show()
        refresh()
        et.requestFocus()
        et.post {
            val imm = c.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** 联想候选行：左侧图标（48dp）+ 中间名字/编号 + 右侧「首选」标记。 */
    private fun suggestRow(
        c: Context,
        e: ShinyCatalog.Entry,
        first: Boolean,
        iconOf: (ShinyCatalog.Entry) -> android.graphics.Bitmap?,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(c, 12), dp(c, 9), dp(c, 12), dp(c, 9))
            isClickable = true
            isFocusable = true
        }
        val bmp = try { iconOf(e) } catch (ex: Exception) { null }
        val box = android.widget.FrameLayout(c).apply {
            layoutParams = LinearLayout.LayoutParams(dp(c, 48), dp(c, 48))
        }
        box.addView(android.widget.ImageView(c).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            if (bmp != null) setImageBitmap(bmp) else setImageResource(R.drawable.widget_icon_placeholder)
        })
        row.addView(box)

        val texts = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(c, 11), 0, 0, 0)
        }
        texts.addView(TextView(c).apply {
            text = e.name
            textSize = 14.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(c.themed(com.google.android.material.R.attr.colorOnSurface))
        })
        val sub = buildString {
            if (e.no > 0) append("#${e.no}")
            if (e.py.isNotBlank()) append(if (isNotEmpty()) " · " else "").append(e.py)
        }
        if (sub.isNotEmpty()) texts.addView(TextView(c).apply {
            text = sub
            textSize = 11.5f
            setTextColor(c.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, dp(c, 2), 0, 0)
        })
        row.addView(texts)

        if (first) {
            row.addView(TextView(c).apply {
                text = "首选"
                textSize = 10.5f
                setTextColor(c.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(dp(c, 8), 0, 0, 0)
            })
        }

        row.background = RippleDrawable(
            ColorStateList.valueOf(c.themed(com.google.android.material.R.attr.colorOutline)),
            GradientDrawable().apply {
                cornerRadius = dp(c, 12).toFloat()
                setColor(col(c, if (first) com.google.android.material.R.attr.colorPrimaryContainer else com.google.android.material.R.attr.colorSurface))
                if (first) setStroke(PaintTheme.cardStrokePx(c), c.themed(com.google.android.material.R.attr.colorOutline))
            },
            GradientDrawable().apply {
                cornerRadius = dp(c, 12).toFloat()
                setColor(c.themed(com.google.android.material.R.attr.colorSurface))
            }
        )
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(c, 5) }
        row.setOnClickListener { onClick() }
        return row
    }

    /**
     * 多行文本输入（高版，8 行起，内部可滚）。
     * 适合「一次粘贴/敲一堆名字，每行一个」的场景——[input] 的 `multiLine` 只有 5 行不够用。
     *
     * @param onOk 回调收到的是**原样文本**（未拆分、未 trim），由调用方决定怎么解析
     */
    fun inputLines(
        c: Context,
        title: String,
        hint: String,
        message: String? = null,
        initial: String = "",
        minLines: Int = 8,
        confirmText: String = "确定",
        onOk: (String) -> Unit
    ) {
        val (dlg, box) = panel(c, title)
        if (!message.isNullOrBlank()) box.addView(scrollText(c, message, false))

        val rows = minLines
        val et = EditText(c).apply {
            this.hint = hint
            if (initial.isNotBlank()) setText(initial)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false)
            this.minLines = rows
            maxLines = rows
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14))
            background = GradientDrawable().apply {
                cornerRadius = dp(c, 14).toFloat()
                setColor(c.themed(com.google.android.material.R.attr.colorSurfaceVariant))
                setStroke(PaintTheme.cardStrokePx(c), c.themed(com.google.android.material.R.attr.colorOutline))
            }
            setTextColor(c.themed(com.google.android.material.R.attr.colorOnSurface))
            setHintTextColor(c.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
            textSize = 15f
            // 让输入框自己能滚，别撑爆弹窗
            isVerticalScrollBarEnabled = true
        }
        box.addView(et)
        box.addView(View(c).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 10)
            )
        })

        box.addView(optionRow(c, confirmText) {
            val v = et.text.toString()
            if (v.isBlank()) {
                et.background = GradientDrawable().apply {
                    cornerRadius = dp(c, 14).toFloat()
                    setColor(c.themed(com.google.android.material.R.attr.colorErrorContainer))
                    setStroke(dp(c, 2), c.themed(com.google.android.material.R.attr.colorError))
                }
                return@optionRow
            }
            dlg.dismiss()
            onOk(v)
        })
        box.addView(closeRow(c, "取消") { dlg.dismiss() })
        dlg.show()
        et.requestFocus()
        et.post {
            val imm = c.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** 带副标题的选项菜单：每项 (主标题, 副说明)。 */
    fun menuWithSubs(
        c: Context,
        title: String,
        subtitle: String? = null,
        items: List<Pair<String, String>>,
        dangerIndex: Int = -1,
        onPick: (Int) -> Unit
    ) {
        val (dlg, box) = panel(c, title, subtitle)
        items.forEachIndexed { i, (label, sub) ->
            box.addView(optionRow(c, label, sub, danger = i == dangerIndex) {
                dlg.dismiss()
                onPick(i)
            })
        }
        box.addView(closeRow(c, "关闭") { dlg.dismiss() })
        dlg.show()
    }

    /**
     * 确认框（替代 setMessage + 两个按钮）。
     * @param danger 确认按钮是否画成危险样式
     */
    fun confirm(
        c: Context,
        title: String,
        message: String,
        confirmText: String = "确定",
        cancelText: String = "取消",
        danger: Boolean = false,
        onConfirm: () -> Unit
    ) {
        val (dlg, box) = panel(c, title)
        box.addView(scrollText(c, message, scroll = message.length > 160))
        box.addView(optionRow(c, confirmText, danger = danger) {
            dlg.dismiss()
            onConfirm()
        })
        box.addView(closeRow(c, cancelText) { dlg.dismiss() })
        dlg.show()
    }

    /** 纯信息框（只有一个关闭）。 */
    fun info(c: Context, title: String, message: String, closeText: String = "知道了") {
        val (dlg, box) = panel(c, title)
        box.addView(scrollText(c, message, scroll = message.length > 160))
        box.addView(closeRow(c, closeText) { dlg.dismiss() })
        dlg.show()
    }

    /**
     * 加载中提示（不可取消），返回 dialog 供调用方完成后 dismiss。
     * 统一样式，避免各处自建「XX中…」弹窗长得不一样。
     */
    fun loading(c: Context, title: String, message: String): AlertDialog {
        val (dlg, box) = panel(c, title)
        box.addView(scrollText(c, message, false))
        dlg.setCancelable(false)
        dlg.show()
        return dlg
    }

    /** 顶部一段说明文字（用于内嵌在别的弹窗里，不套滚动）。 */
    fun messageText(c: Context, message: String): View = scrollText(c, message, false)

    /** 带输入框的弹窗。校验失败时不会关闭。 */
    fun input(
        c: Context,
        title: String,
        hint: String,
        initial: String = "",
        message: String? = null,
        multiLine: Boolean = false,
        confirmText: String = "确定",
        allowBlank: Boolean = false,
        onOk: (String) -> Unit
    ) {
        val (dlg, box) = panel(c, title)
        if (!message.isNullOrBlank()) box.addView(scrollText(c, message, false))

        val et = EditText(c).apply {
            this.hint = hint
            if (initial.isNotBlank()) setText(initial)
            setSingleLine(!multiLine)
            if (multiLine) maxLines = 5
            gravity = if (multiLine) Gravity.TOP or Gravity.START else Gravity.CENTER_VERTICAL
            setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14))
            background = GradientDrawable().apply {
                cornerRadius = dp(c, 14).toFloat()
                setColor(c.themed(com.google.android.material.R.attr.colorSurfaceVariant))
                setStroke(PaintTheme.cardStrokePx(c), c.themed(com.google.android.material.R.attr.colorOutline))
            }
            setTextColor(c.themed(com.google.android.material.R.attr.colorOnSurface))
            setHintTextColor(c.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
            textSize = 15f
        }
        box.addView(et)
        box.addView(View(c).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 10)
            )
        })

        box.addView(optionRow(c, confirmText) {
            val v = et.text.toString().trim()
            if (v.isBlank() && !allowBlank) {
                // 就地抖动提示，不关窗
                et.background = GradientDrawable().apply {
                    cornerRadius = dp(c, 14).toFloat()
                    setColor(c.themed(com.google.android.material.R.attr.colorErrorContainer))
                    setStroke(dp(c, 2), c.themed(com.google.android.material.R.attr.colorError))
                }
                return@optionRow
            }
            dlg.dismiss()
            onOk(v)
        })
        box.addView(closeRow(c, "取消") { dlg.dismiss() })
        dlg.show()
        // 自动弹键盘
        et.requestFocus()
        et.post {
            val imm = c.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    // —— 内部小工具 ——

    /**
     * 说明文字。内容长时套一层限高滚动容器，避免弹窗顶出屏幕。
     * 注意返回值类型是 View（可能是 TextView，也可能是 ScrollView）。
     */
    private fun scrollText(c: Context, text: String, scroll: Boolean): View {
        val tv = TextView(c).apply {
            this.text = text
            textSize = 13.5f
            setTextColor(c.themed(com.google.android.material.R.attr.colorOnSurface))
            setLineSpacing(dp(c, 3).toFloat(), 1f)
            setPadding(dp(c, 2), dp(c, 6), dp(c, 2), dp(c, 10))
        }
        if (!scroll) return tv
        return ScrollView(c).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 340)
            )
            addView(tv)
        }
    }
}
