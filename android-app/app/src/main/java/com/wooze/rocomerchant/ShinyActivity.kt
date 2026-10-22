package com.wooze.rocomerchant

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import java.util.Collections
/**
 * 异色图鉴 / 异色记录。
 *
 * - 分赛季（Tab），赛季内可再分组（分类不强制，组名留空即「未分类」）。
 * - 点格子切换「已获得 / 未获得」；长按可删除。
 * - 「告知异色」：输入名字或编号 -> ShinyCatalog 识别；
 *   唯一命中直接加入；有歧义列候选让你选；找不到就回退并说明原因。
 * - 「截图认字加入」：选一张截图 -> ShinyOcr 本地认字（不联网）-> 拿名字/编号找候选，
 *   点一下即加入；认错了可以手动输入。
 * - 数据落在 filesDir（不是 cache），支持导出 / 导入 JSON。
 */
class ShinyActivity : AppCompatActivity() {

    private lateinit var btnSeason: TextView
    private lateinit var tvSeasonCount: TextView
    private lateinit var tvProgress: TextView
    private lateinit var container: LinearLayout
    private var root = ShinyData.Root()
    private var seasonIndex = 0
    /** 当前赛季名（持久化，避免新赛季加入后丢失上次选中的赛季）。 */
    private var selectedSeasonName: String = ""
    /** 显示模式：false = 原皮，true = 异色（图标做明亮化处理表示异色形态）。 */
    private var shinyMode = false

    /** 编辑模式：卡片右上出现红色 ×，点卡片改精灵/只数，底栏换成「完成」。 */
    private var editMode = false

    /** 设置页：改完数据回来重新读盘。 */
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) {
            root = ShinyData.load(this)
            val saved = getSharedPreferences("merchant_prefs", MODE_PRIVATE)
                .getString("shiny_season", "")
            selectedSeasonName = if (!saved.isNullOrBlank() &&
                root.seasons.any { it.name == saved }
            ) saved else root.seasons.firstOrNull()?.name ?: ""
            seasonIndex = root.seasons.indexOfFirst { it.name == selectedSeasonName }
                .coerceAtLeast(0)
            setupSeasonButton()
            render()
        }
    }

    /** 识图：从相册/文件选一张截图。 */
    private val visionLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        startVision(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeBridge.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shiny)
        PaintTheme.decorate(this, findViewById(R.id.headerBar))

        // 主题呼应：顶栏 / 底栏按钮底色跟随主题
        CardStyle.themeButton(this, findViewById(R.id.btnShinySeason), -12f, 0, 18, 9)
        CardStyle.themeButton(this, findViewById(R.id.btnShinyMode), 6f, 1, 12, 10, radiusDp = 18f)
        CardStyle.themeButton(this, findViewById(R.id.btnEditDone), 0f, 2, 14, 7, radiusDp = 18f)
        CardStyle.themeButton(this, findViewById(R.id.btnShinyAdd), -12f, 3, 18, 11)
        CardStyle.themeButton(this, findViewById(R.id.btnShinyOcr), 0f, 4, 18, 11)
        CardStyle.themeButton(this, findViewById(R.id.btnShinySettings), 12f, 5, 18, 11)
        CardStyle.themeButton(this, findViewById(R.id.btnShinyEdit), 0f, 6, 18, 11, strength = 0.30f)

        btnSeason = findViewById(R.id.btnShinySeason)
        tvSeasonCount = findViewById(R.id.tvSeasonCount)
        tvProgress = findViewById(R.id.tvShinyProgress)
        container = findViewById(R.id.llShinyContainer)

        root = ShinyData.load(this)
        shinyMode = getSharedPreferences("merchant_prefs", MODE_PRIVATE)
            .getBoolean("shiny_mode", false)

        // 还原上次选中的赛季（不存在则回退到第一个）
        val savedSeason = getSharedPreferences("merchant_prefs", MODE_PRIVATE)
            .getString("shiny_season", "")
        selectedSeasonName = if (!savedSeason.isNullOrBlank() &&
            root.seasons.any { it.name == savedSeason }
        ) savedSeason else root.seasons.firstOrNull()?.name ?: ""

        findViewById<TextView>(R.id.btnShinyAdd).setOnClickListener { showAddDialog() }
        findViewById<TextView>(R.id.btnShinyOcr).setOnClickListener { pickForVision() }
        btnSeason.setOnClickListener { showSeasonPicker() }
        findViewById<TextView>(R.id.btnShinySettings).setOnClickListener {
            settingsLauncher.launch(Intent(this, ShinySettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.btnShinyMode).setOnClickListener {
            shinyMode = !shinyMode
            getSharedPreferences("merchant_prefs", MODE_PRIVATE).edit()
                .putBoolean("shiny_mode", shinyMode).apply()
            updateModeButton()
            render()
            toast(if (shinyMode) "全部显示异色形态" else "全部显示原皮形态")
        }

        findViewById<TextView>(R.id.btnShinyEdit).setOnClickListener { setEditMode(true) }
        findViewById<TextView>(R.id.btnEditDone).setOnClickListener { setEditMode(false) }

        setupSeasonButton()
        render()
        // 缺的图标后台补下（不阻塞）
        downloadMissing()
    }

    /** 切换编辑 / 浏览两态。进编辑自动存一次，退出再存一次兜底。 */
    private fun setEditMode(on: Boolean) {
        editMode = on
        ShinyData.save(this, root)
        render()
        toast(if (on) "编辑模式：点卡片改 · 拖动排序 / 换分类 · 点 × 移除" else "已退出编辑，改动已保存")
    }

    /** 离屏时停掉呼吸动画（省电）。 */
    override fun onPause() {
        super.onPause()
        for (i in 0 until container.childCount) stopWiggle(container.getChildAt(i))
    }

    /** 编辑态回到前台时把动画重新播上（上面 onPause 停掉了）。 */
    override fun onResume() {
        super.onResume()
        if (editMode) render()
    }

    private fun setupSeasonButton() {
        val season = currentSeason()
        btnSeason.text = "赛季：${season.name} ▾"
        tvSeasonCount.text = "共 ${root.seasons.size} 个"
    }

    /** 选中某个赛季（按名字定位，避免新赛季加入后位置错位）。 */
    private fun selectSeason(name: String) {
        val idx = root.seasons.indexOfFirst { it.name == name }
        if (idx < 0) return
        selectedSeasonName = name
        seasonIndex = idx
        getSharedPreferences("merchant_prefs", MODE_PRIVATE).edit()
            .putString("shiny_season", name).apply()
        setupSeasonButton()
        render()
    }

    private fun currentSeason(): ShinyData.Season {
        return root.seasons.firstOrNull { it.name == selectedSeasonName }
            ?: root.seasons.getOrNull(seasonIndex)
            ?: root.seasons[0]
    }

    /**
     * 赛季选择器：点顶部「赛季：XXX ▾」弹出。
     * 赛季越来越多，用按钮选比横向拖动 Tab 更稳；支持正序 / 倒序切换。
     * 列表项支持「长按拖动」调整赛季先后顺序（顺序会记住）。
     */
    private fun showSeasonPicker() {
        var asc = getSharedPreferences("merchant_prefs", MODE_PRIVATE)
            .getString("shiny_season_order", "asc") == "asc"

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorSurface))
            }
        }

        // 头部：标题 + 排序切换
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 0, 4, dp(12))
        }
        header.addView(TextView(this).apply {
            text = "选择赛季"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorOnSurface))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val sortBtn = TextView(this).apply {
            text = if (asc) "排序：正序 ▲" else "排序：倒序 ▼"
            textSize = 13f
            setTextColor(resolveColor(this@ShinyActivity, R.color.gold))
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(resolveColor(this@ShinyActivity, R.color.gold_soft))
                setStroke(1, resolveColor(this@ShinyActivity, R.color.gold_line))
            }
            isClickable = true
            isFocusable = true
        }
        header.addView(sortBtn)
        box.addView(header)

        // 提示：长按可拖动排序
        box.addView(TextView(this).apply {
            text = "长按卡片可上下拖动排序"
            textSize = 11.5f
            setTextColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(6, 2, 6, dp(10))
        })

        // 列表（RecyclerView，支持长按拖动排序）
        val rv = RecyclerView(this@ShinyActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(360)
            )
            layoutManager = LinearLayoutManager(this@ShinyActivity)
        }
        box.addView(rv)

        val dlg = AlertDialog.Builder(this).setView(box).create()

        lateinit var adapter: SeasonAdapter

        fun rebuild() {
            val display = if (asc) root.seasons.toMutableList()
            else root.seasons.reversed().toMutableList()
            adapter = SeasonAdapter(display, asc) { s ->
                dlg.dismiss()
                selectSeason(s.name)
            }
            rv.adapter = adapter
            val touch = ItemTouchHelper(object : ItemTouchHelper.Callback() {
                override fun getMovementFlags(
                    rv: RecyclerView, vh: RecyclerView.ViewHolder
                ) = makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

                override fun onMove(
                    rv: RecyclerView, a: RecyclerView.ViewHolder, b: RecyclerView.ViewHolder
                ): Boolean {
                    val from = a.adapterPosition
                    val to = b.adapterPosition
                    if (from < 0 || to < 0) return false
                    Collections.swap(adapter.list, from, to)
                    adapter.notifyItemMoved(from, to)
                    return true
                }

                override fun onSwiped(vh: RecyclerView.ViewHolder, d: Int) {}

                override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, state: Int) {
                    super.onSelectedChanged(vh, state)
                    vh?.itemView?.alpha = if (state == ItemTouchHelper.ACTION_STATE_DRAG) 0.55f else 1f
                }

                override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                    super.clearView(rv, vh)
                    vh.itemView.alpha = 1f
                    // 拖动结束：把当前显示顺序写回真实赛季顺序
                    val newOrder = if (asc) adapter.list else adapter.list.reversed()
                    root.seasons.clear()
                    root.seasons.addAll(newOrder)
                    ShinyData.save(this@ShinyActivity, root)
                    toast("赛季顺序已保存")
                }
            })
            touch.attachToRecyclerView(rv)
        }

        sortBtn.setOnClickListener {
            asc = !asc
            sortBtn.text = if (asc) "排序：正序 ▲" else "排序：倒序 ▼"
            getSharedPreferences("merchant_prefs", MODE_PRIVATE).edit()
                .putString("shiny_season_order", if (asc) "asc" else "desc").apply()
            rebuild()
        }
        rebuild()

        // 底部两个入口：赛季/分类管理、校正名字
        // （原底栏按钮已收进这里，避免底部按钮越堆越多）
        val foot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        foot.addView(footerButton("管理赛季 / 分类") {
            dlg.dismiss()
            showManageDialog()
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { rightMargin = dp(5) })
        foot.addView(footerButton("校正名字") {
            dlg.dismiss()
            showFixNamesDialog()
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { leftMargin = dp(5) })
        box.addView(foot)

        dlg.setOnShowListener {
            dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dlg.show()
    }

    /** 赛季选择器底部的小按钮（浅金色胶囊）。 */
    private fun footerButton(text: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(resolveColor(this@ShinyActivity, R.color.gold))
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(resolveColor(this@ShinyActivity, R.color.gold_soft))
                setStroke(1, resolveColor(this@ShinyActivity, R.color.gold_line))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    /** 赛季选择器的列表适配器，每项是一张赛季卡片，支持长按拖动重排。 */
    private inner class SeasonAdapter(
        val list: MutableList<ShinyData.Season>,
        val asc: Boolean,
        private val onPick: (ShinyData.Season) -> Unit
    ) : RecyclerView.Adapter<SeasonAdapter.VH>() {

        inner class VH(val container: FrameLayout) : RecyclerView.ViewHolder(container)

        override fun getItemCount(): Int = list.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val c = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
            }
            return VH(c)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val s = list[pos]
            val (got, total) = ShinyData.progress(root, s)
            val pieces = ShinyData.pieces(root, s)
            val selected = s.name == currentSeason().name
            h.container.removeAllViews()
            h.container.addView(
                seasonRow(s.name, got, total, pieces, selected) { onPick(s) }
            )
        }
    }

    /** 选择器里的一行赛季卡片。 */
    private fun seasonRow(
        name: String, got: Int, total: Int, pieces: Int,
        selected: Boolean, onClick: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = RippleDrawable(
                ColorStateList.valueOf(this@ShinyActivity.themed(com.google.android.material.R.attr.colorOutline)),
                GradientDrawable().apply {
                    cornerRadius = dp(13).toFloat()
                    setColor(resolveColor(this@ShinyActivity, if (selected) R.color.gold_soft else com.google.android.material.R.attr.colorSurface))
                    setStroke(
                        if (selected) 2 else 1,
                        resolveColor(this@ShinyActivity, if (selected) R.color.gold else com.google.android.material.R.attr.colorOutline)
                    )
                },
                null
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) }
            isClickable = true
            isFocusable = true
        }
        row.addView(TextView(this).apply {
            text = name
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(resolveColor(this@ShinyActivity, if (selected) R.color.gold_text_deep else com.google.android.material.R.attr.colorOnSurface))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = "$got/$total 种 · ${pieces} 只" + if (selected) "  ✓" else ""
            textSize = 12.5f
            setTextColor(resolveColor(this@ShinyActivity, if (selected) R.color.gold_text_deep else com.google.android.material.R.attr.colorOnSurfaceVariant))
        })
        row.setOnClickListener { onClick() }
        return row
    }

    private fun render() {
        // 重建前先停掉卡片上的呼吸动画，否则脱离视图树的动画会一直空跑
        for (i in 0 until container.childCount) stopWiggle(container.getChildAt(i))
        container.removeAllViews()
        updateModeButton()
        // 编辑态：顶栏常驻提示 + 底栏收起（避免误触加只数）
        findViewById<LinearLayout>(R.id.llEditBar).visibility =
            if (editMode) View.VISIBLE else View.GONE
        findViewById<LinearLayout>(R.id.llShinyFoot).visibility =
            if (editMode) View.GONE else View.VISIBLE
        val season = currentSeason()
        val (got, total) = ShinyData.progress(root, season)
        val (allGot, allTotal) = ShinyData.progress(root)
        val seasonPieces = ShinyData.pieces(root, season)
        val allPieces = ShinyData.pieces(root)
        val pct = if (total == 0) 0 else got * 100 / total
        tvProgress.text =
            "我共有异色 $allPieces 只（已收集种类 $allGot / $allTotal）\n" +
                "${season.name}：$got / $total 种（$pct%）· 共 $seasonPieces 只\n" +
                if (editMode) "✥ 编辑中 · 点卡片改 · 拖动排序 / 换分类 · 点 × 移除"
                else "点一下 +1 只并点亮 · 长按调只数 / 写备注"
        tvProgress.setTextColor(this.themed(com.google.android.material.R.attr.colorOnSurface))
        tvProgress.textSize = 13f

        if (season.groups.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "这个赛季还没有内容，点「添加图鉴」添加吧"
                setTextColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
                textSize = 13f
                setPadding(8, 24, 8, 24)
            })
            return
        }

        // 空分类不渲染（设计稿：挪走最后一只后分组自动消失，不留空标题）
        val shown = season.groups.filter { it.items.isNotEmpty() }
        if (shown.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "这个赛季还没有内容，点「添加图鉴」添加吧"
                setTextColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
                textSize = 13f
                setPadding(8, 24, 8, 24)
            })
            return
        }

        shown.forEachIndexed { gi, group ->
            val gGot = group.items.count { it.owned }
            val gPieces = ShinyData.piecesOf(group)
            val titleView = TextView(this).apply {
                text = "${if (group.name.isBlank()) "未分类" else group.name}　$gGot/${group.items.size} 种" +
                    if (gPieces > 0) "　共 $gPieces 只" else ""
                setTextColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorOnSurface))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(4, 16, 4, 8)
            }
            // 编辑态：标题本身也是投放目标（拖到哪个标题下面就归哪组）
            if (editMode) attachDropTarget(titleView, group)
            container.addView(titleView)

            val grid = GridLayout(this).apply {
                columnCount = 4
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            // 编辑态：拖到该组的网格区域同样判定为归入本组
            if (editMode) attachDropTarget(grid, group)
            group.items.forEach { entry ->
                grid.addView(
                    cellView(
                        entry,
                        onClick = {
                            if (editMode) {
                                // 编辑态：点卡片进「改精灵 / 改只数 / 移动 / 移除」
                                showEditEntryMenu(season, group, entry)
                            } else {
                                // 点一下 +1 只并点亮（0 -> 1 -> 2 ...），想减用长按菜单
                                entry.count += 1
                                ShinyData.save(this, root)
                                render()
                                toast("${entry.name} ×${entry.count}")
                            }
                        },
                        onLongClick = { showEntryMenu(season, group, entry) },
                        onRemove = if (editMode) {
                            { removeEntry(group, entry) }
                        } else null,
                        dragEnabled = editMode
                    )
                )
            }
            container.addView(grid)

            if (gi == shown.size - 1) {
                // 末尾留白，避免被底部按钮挡住
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 12
                    )
                })
            }
        }
    }

    /**
     * 长按格子：弹出这只精灵的操作菜单（只数 / 备注 / 补图 / 删除）。
     * 自己拼的卡片式菜单，比系统默认列表好看也更好点。
     */
    private fun showEntryMenu(
        season: ShinyData.Season, group: ShinyData.Group, entry: ShinyData.Entry
    ) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorSurface))
            }
        }

        // 关联蛋数开关（不记忆，弹窗打开默认关闭）
        var eggOn = false
        // 数量段标题（随开关切换文字），直接用 TextView 以便动态改 text
        val capCount = TextView(this).apply {
            text = "数量"
            textSize = 11.5f
            setTextColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(dp(2), dp(2), 0, dp(5))
        }

        // 自绘蛋开关：圆角轨 + 圆形钮（与精灵名字同一行平齐，无需额外文字）
        fun makeEggSwitch(initial: Boolean, onChange: (Boolean) -> Unit): View {
            var checked = initial
            val track = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(20))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (checked) Color.rgb(0x5B, 0xB9, 0x7E) else Color.rgb(0xD8, 0xD2, 0xC6))
                }
            }
            val knob = View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
                layoutParams = FrameLayout.LayoutParams(dp(16), dp(16)).apply { gravity = Gravity.CENTER_VERTICAL }
            }
            fun place() {
                (knob.layoutParams as FrameLayout.LayoutParams).leftMargin = if (checked) dp(16) else dp(2)
                knob.requestLayout()
                (track.background as GradientDrawable).setColor(
                    if (checked) Color.rgb(0x5B, 0xB9, 0x7E) else Color.rgb(0xD8, 0xD2, 0xC6)
                )
            }
            track.addView(knob)
            place()
            track.setOnClickListener {
                checked = !checked
                place()
                onChange(checked)
            }
            return track
        }

        // 头部：精灵名字占满居中；蛋图标+开关作为独立图层浮在左侧，互不挤压
        val eggToggle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@ShinyActivity).apply {
                setImageResource(R.drawable.egg_icon)
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                scaleType = ImageView.ScaleType.FIT_CENTER
            })
            addView(makeEggSwitch(eggOn) { on ->
                eggOn = on
                capCount.text = if (on) "数量（精灵蛋）" else "数量"
            }.apply { (layoutParams as LinearLayout.LayoutParams).setMargins(dp(5), 0, 0, 0) })
        }
        val head = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        head.addView(TextView(this).apply {
            text = entry.name
            textSize = 20f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorOnSurface))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        })
        head.addView(eggToggle.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL or Gravity.START
            )
        })
        box.addView(head)

        // 副标题：当前精灵只数
        box.addView(TextView(this).apply {
            text = (if (entry.no > 0) "#${entry.no} · " else "") +
                (if (entry.count > 0) "异色 ${entry.count} 只" else "未拥有")
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(
                resolveColor(
                    this@ShinyActivity,
                    if (entry.count > 0) R.color.gold else com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )
            setPadding(0, 4, 0, 0)
        })
        box.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, dp(14), 0, dp(10)) }
            setBackgroundColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorOutline))
        })

        val dlg = AlertDialog.Builder(this).setView(box).create()

        // ── 第 1 段 · 数量（蛋开关关=精灵只数，开=精灵蛋数量）──
        box.addView(capCount)
        box.addView(
            grid2(
                mBtn("－ 减 1") {
                    dlg.dismiss()
                    if (eggOn) {
                        if (entry.eggCount > 0) entry.eggCount -= 1
                        toast("${entry.name} 蛋 ×${entry.eggCount}")
                    } else {
                        if (entry.count > 0) entry.count -= 1
                        toast(if (entry.count == 0) "已标记「${entry.name}」未拥有" else "${entry.name} ×${entry.count}")
                    }
                    ShinyData.save(this, root); render()
                },
                mBtn("＋ 加 1") {
                    dlg.dismiss()
                    if (eggOn) {
                        entry.eggCount += 1
                        toast("${entry.name} 蛋 ×${entry.eggCount}")
                    } else {
                        entry.count += 1
                        toast("${entry.name} ×${entry.count}")
                    }
                    ShinyData.save(this, root); render()
                }
            )
        )
        box.addView(
            mBtn("手动输入只数") {
                dlg.dismiss()
                val isEgg = eggOn
                inputDialog(
                    if (isEgg) "输入蛋数" else "输入只数",
                    if (isEgg) "例如 3，表示有 3 个${entry.name}的蛋" else "例如 3，表示有 3 只异色${entry.name}"
                ) { v ->
                    val n = v.toIntOrNull()
                    if (n == null || n < 0) { toast("请输入 0 或正整数"); return@inputDialog }
                    if (isEgg) entry.eggCount = n else entry.count = n
                    ShinyData.save(this, root); render()
                    toast(
                        if (isEgg) "${entry.name} 蛋 ×$n"
                        else if (n == 0) "已标记「${entry.name}」未拥有"
                        else "${entry.name} ×$n"
                    )
                }
            }
        )

        // ── 第 2 段 · 其它操作 ──
        box.addView(capLabel("其它操作"))
        box.addView(
            grid2(
                mBtn("补下异色图") {
                    dlg.dismiss()
                    val list = siblingsOf(entry)
                    Thread {
                        // 统一 slug：同名条目共用同一份缓存，补一次全生效
                        val slug = list.firstOrNull { it.slug.isNotBlank() }?.slug
                        if (slug != null && list.any { it.slug.isBlank() }) {
                            list.forEach { if (it.slug.isBlank()) it.slug = slug }
                            ShinyData.save(this@ShinyActivity, root)
                        }
                        var ok = false
                        for (e in list) {
                            if (ShinyIcons.ensureShiny(this@ShinyActivity, e, force = true)) ok = true
                        }
                        runOnUiThread {
                            render()
                            toast(
                                if (ok) "已更新「${entry.name}」的异色图（${list.size} 处记录）"
                                else "wiki 还没有这只的异色图"
                            )
                        }
                    }.start()
                },
                mBtn("清零") {
                    dlg.dismiss()
                    entry.count = 0
                    ShinyData.save(this, root); render()
                    toast("已标记「${entry.name}」未拥有")
                }
            )
        )

        // ── 第 3 段 · 备注（写过就直接露出内容当预览）──
        box.addView(
            menuRow(
                if (entry.note.isBlank()) "添加备注" else "修改备注",
                if (entry.note.isBlank()) "记点什么，比如 3阶 攻资31" else entry.note
            ) { dlg.dismiss(); showNoteDialog(entry) }
        )

        // ── 第 4 段 · 删除 / 关闭 ──
        box.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, dp(4), 0, dp(10)) }
            setBackgroundColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorOutline))
        })
        box.addView(
            grid2(
                mBtn("删除", danger = true) {
                    dlg.dismiss()
                    UiDialogs.confirm(
                        this,
                        "删除「${entry.name}」？",
                        "从${season.name}${
                            if (group.name.isBlank()) "" else " · ${group.name}"
                        }中移除这条异色记录。",
                        confirmText = "删除",
                        danger = true
                    ) {
                        group.items.remove(entry)
                        ShinyData.save(this, root); render()
                    }
                },
                mBtn("关闭", soft = true) { dlg.dismiss() },
                bottomMargin = 0
            )
        )

        dlg.setOnShowListener {
            dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dlg.show()
    }

    /** 弹窗里的分组小标题（如「数量」「其它操作」）。 */
    private fun capLabel(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 11.5f
        setTextColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
        setPadding(dp(2), dp(2), 0, dp(5))
    }

    /** 弹窗里的单个按钮：圆角卡片 + 水波纹，内容居中，无副标题。 */
    private fun mBtn(
        title: String, danger: Boolean = false, soft: Boolean = false,
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
        return TextView(this).apply {
            text = title
            textSize = 14.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(resolveColor(this@ShinyActivity, fg))
            setPadding(dp(10), dp(12), dp(10), dp(12))
            background = RippleDrawable(
                ColorStateList.valueOf(
                    resolveColor(
                        this@ShinyActivity,
                        if (danger) com.google.android.material.R.attr.colorError else com.google.android.material.R.attr.colorOutline
                    )
                ),
                GradientDrawable().apply {
                    cornerRadius = dp(13).toFloat()
                    setColor(resolveColor(this@ShinyActivity, base))
                    setStroke(
                        1,
                        resolveColor(
                            this@ShinyActivity,
                            if (soft) com.google.android.material.R.attr.colorOutline else com.google.android.material.R.attr.colorOutline
                        )
                    )
                },
                null
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(7)) }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    /** 两列并排的按钮行（等宽平分）。 */
    private fun grid2(left: View, right: View, bottomMargin: Int = 11): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(bottomMargin)) }
        }
        listOf(left, right).forEachIndexed { i, v ->
            v.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { if (i == 0) marginEnd = dp(7) }
            row.addView(v)
        }
        return row
    }

    /**
     * 编辑态点卡片：改精灵 / 改只数 / 移动分类 / 移除。
     * （设计稿原本是「拖卡片换分类」，改用菜单选目标分类——同样一步到位，且不会误拖。）
     */
    private fun showEditEntryMenu(
        season: ShinyData.Season, group: ShinyData.Group, entry: ShinyData.Entry
    ) {
        val (dlg, box) = UiDialogs.panel(
            this, entry.name,
            (if (entry.no > 0) "#${entry.no} · " else "") +
                (if (entry.count > 0) "异色 ${entry.count} 只" else "未拥有")
        )
        box.addView(mBtn("改精灵") {
            dlg.dismiss()
            pickSpiritName("换成哪只？", entry.name) { n ->
                val hit = ShinyCatalog.lookupByName(this, n)
                if (hit != null) {
                    mergeEntry(entry, hit, hit.name)
                    ShinyData.save(this, root); render()
                    // 新精灵的图标大概率还没缓存，后台默默补一下
                    Thread { ShinyIcons.ensure(this, entry) }.start()
                    toast("已改成「${hit.name}」")
                } else {
                    entry.name = n
                    ShinyData.save(this, root); render()
                    toast("已改成「$n」")
                }
            }
        })
        box.addView(mBtn("改只数") {
            dlg.dismiss()
            inputDialog("输入只数", "例如 3，表示有 3 只异色${entry.name}") { v ->
                val n = v.toIntOrNull()
                if (n == null || n < 0) { toast("请输入 0 或正整数"); return@inputDialog }
                entry.count = n
                ShinyData.save(this, root); render()
                toast(if (n == 0) "已标记「${entry.name}」未拥有" else "${entry.name} ×$n")
            }
        })
        box.addView(mBtn("移动到其他分类") { dlg.dismiss(); showMoveGroup(season, group, entry) })
        box.addView(mBtn("移除", danger = true) { dlg.dismiss(); removeEntry(group, entry) })
        box.addView(mBtn("关闭", soft = true) { dlg.dismiss() })
        dlg.show()
    }

    /**
     * 移除一条记录。若这个分类被搬空，顺手把它删掉（设计稿「空分组自动消失」）。
     */
    private fun removeEntry(group: ShinyData.Group, entry: ShinyData.Entry) {
        UiDialogs.confirm(
            this,
            "移除「${entry.name}」？",
            "从${group.name.ifBlank { "未分类" }}里移除这条异色记录。",
            confirmText = "移除",
            danger = true
        ) {
            group.items.remove(entry)
            if (group.items.isEmpty() && group.name.isNotBlank()) {
                currentSeason().groups.remove(group)
            }
            ShinyData.save(this, root); render()
        }
    }

    /** 把一条记录挪到同赛季的其它分类。 */
    private fun showMoveGroup(
        season: ShinyData.Season, from: ShinyData.Group, entry: ShinyData.Entry
    ) {
        val targets = season.groups.filter { it !== from }
        if (targets.isEmpty()) {
            toast("这个赛季只有这一个分类，先新建一个")
            return
        }
        UiDialogs.menu(
            this, "移动到哪个分类",
            subtitle = "当前在「${from.name.ifBlank { "未分类" }}」",
            items = targets.map { it.name.ifBlank { "未分类" } }
        ) { which ->
            val tgt = targets[which]
            from.items.remove(entry)
            tgt.items.add(entry)
            if (from.items.isEmpty() && from.name.isNotBlank()) season.groups.remove(from)
            ShinyData.save(this, root); render()
            toast("已移到「${tgt.name.ifBlank { "未分类" }}」")
        }
    }

    /** 所有赛季 / 分类里，同一只精灵的条目（按名字或 slug 匹配）。 */
    private fun siblingsOf(entry: ShinyData.Entry): List<ShinyData.Entry> {
        val all = root.seasons.flatMap { s -> s.groups.flatMap { g -> g.items } }
        return all.filter {
            it === entry || it.name == entry.name ||
                (entry.slug.isNotBlank() && it.slug == entry.slug) ||
                (entry.slug.isBlank() && it.slug.isNotBlank() && it.name == entry.name)
        }.distinct()
    }

    /** 菜单里的一行：圆角卡片 + 水波纹按压效果。 */
    private fun menuRow(
        title: String, sub: String = "", danger: Boolean = false, soft: Boolean = false,
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
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = RippleDrawable(
                ColorStateList.valueOf(resolveColor(this@ShinyActivity, if (danger) com.google.android.material.R.attr.colorError else com.google.android.material.R.attr.colorOutline)),
                GradientDrawable().apply {
                    cornerRadius = dp(13).toFloat()
                    setColor(resolveColor(this@ShinyActivity, base))
                    setStroke(1, resolveColor(this@ShinyActivity, if (soft) com.google.android.material.R.attr.colorOutline else com.google.android.material.R.attr.colorOutline))
                },
                null
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(7)) }
            isClickable = true
            isFocusable = true
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(resolveColor(this@ShinyActivity, fg))
        })
        if (sub.isNotBlank()) {
            row.addView(TextView(this).apply {
                text = sub
                textSize = 11.5f
                maxLines = 1
                setTextColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, 3, 0, 0)
            })
        }
        row.setOnClickListener { onClick() }
        return row
    }

    /** 给这条异色记录写备注（比如「3阶，攻资31，还可以」），会跟着导出/导入走。 */
    private fun showNoteDialog(entry: ShinyData.Entry) {
        UiDialogs.input(
            this,
            "备注 · ${entry.name}",
            "例如：3阶，攻资31，还可以",
            initial = entry.note,
            multiLine = true,
            confirmText = "保存",
            allowBlank = true
        ) { text ->
            entry.note = text
            ShinyData.save(this, root)
            render()
            toast("备注已保存")
        }
    }

    private fun cellView(
        entry: ShinyData.Entry,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        onRemove: (() -> Unit)? = null,
        dragEnabled: Boolean = false
    ): View {
        // 根容器用 FrameLayout：角标 / 编辑× 作为独立图层浮在内容左上角/右上角，不受内层 box 裁剪
        val root = FrameLayout(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(5, 5, 5, 5)
            }
        }
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(6, 8, 6, 8)
            background = cellBg(entry.owned)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(cell)
        // 图标 + 右下角数量角标
        val box = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
        }
        val iv = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        // 显示形态由顶部切换按钮**全局**决定：异色模式全部显示异色，原皮模式全部回原皮。
        // 「点亮」（有没有异色）体现在卡片金边 + 图标是否压暗，不再绑架显示形态。
        val wantShiny = shinyMode
        val bmp = ShinyIcons.bitmap(this, entry, wantShiny)
        if (bmp != null) iv.setImageBitmap(bmp)
        else iv.setImageResource(R.drawable.widget_icon_placeholder)
        iv.alpha = if (entry.owned) 1f else 0.35f
        box.addView(iv)

        if (entry.count > 0) {
            box.addView(TextView(this).apply {
                text = "×${entry.count}"
                textSize = 11f
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(1), dp(5), dp(1))
                background = badgeBg()
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END
                )
            })
        }

        cell.addView(box)

        // 编辑态：右上角红色 × ，点一下移除这条记录（挂 root 层，避免被 box 裁剪）
        if (onRemove != null) {
            root.addView(TextView(this).apply {
                text = "×"
                textSize = 13f
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(resolveColor(this@ShinyActivity, R.color.red))
                    setStroke(dp(2), Color.WHITE)
                }
                layoutParams = FrameLayout.LayoutParams(
                    dp(19), dp(19), Gravity.TOP or Gravity.END
                )
                isClickable = true
                setOnClickListener { onRemove() }
            })
        }

        // 左上角蛋角标：蛋数独立计数、本地留存；蛋数 > 0 即显示（与右下角精灵只数角标互不冲突）
        // 挂在 root 层并带负 margin，真正贴到卡片背景左上角
        if (entry.eggCount > 0) {
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(0), dp(0), dp(3), dp(0))
                background = GradientDrawable().apply {
                    cornerRadius = dp(7).toFloat()
                    setColor(Color.rgb(0xE8, 0x92, 0x3B))
                }
                addView(ImageView(this@ShinyActivity).apply {
                    setImageResource(R.drawable.egg_icon)
                    layoutParams = LinearLayout.LayoutParams(dp(13), dp(13))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                })
                addView(TextView(this@ShinyActivity).apply {
                    text = entry.eggCount.toString()
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(dp(1), 0, 0, 0)
                })
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply {
                    topMargin = dp(2)
                    marginStart = dp(3)
                }
            })
        }

        cell.addView(TextView(this).apply {
            text = entry.name
            textSize = 11f
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(
                if (entry.owned) this@ShinyActivity.themed(com.google.android.material.R.attr.colorOnSurface)
                else this@ShinyActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
            setPadding(0, 4, 0, 0)
        })

        // 有备注就在格子下面露一行，不用点开也看得到
        if (entry.note.isNotBlank()) {
            cell.addView(TextView(this).apply {
                text = entry.note
                textSize = 9f
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(2, 2, 2, 0)
            })
        }

        if (entry.owned) {
            // 点亮：边框 / 背景走金色，图标本身不压暗
            cell.background = cellBg(true)
        }

        root.setOnClickListener { onClick() }
        root.setOnLongClickListener {
            if (dragEnabled) {
                // 抓起来：停掉呼吸动画并复位，避免拖影还在抖
                stopWiggle(root)
                root.scaleX = 1f
                root.scaleY = 1f
                // 编辑态：长按抓起卡片，拖到别的分组标题/网格上即换分类
                val ok = root.startDragAndDrop(
                    ClipData.newPlainText("shiny-entry", entry.name),
                    View.DragShadowBuilder(root),
                    entry,
                    0
                )
                if (ok) toast("松开手放到目标分组即可移动")
            } else {
                onLongClick()
            }
            true
        }
        if (dragEnabled) {
            // 呼吸动画：暗示这张卡片可以拖（错开起始，避免整屏整齐划一）
            attachWiggle(root, entry.name)

            // 卡片本身也是投放目标：拖到哪张卡上，就插到那张卡前面
            // → 同组内拖动 = 调顺序；跨组拖动 = 换分类并指定位置
            attachCellDropTarget(root, entry)

            // ★ 编辑态：按住卡片**一移动**就进入拖拽，不要求长按。
            //   原因：MuMu/鼠标环境下长按经常不触发 Android 的 long click（按住不动收不到持续事件），
            //   而「按下 + 移动」必然产生 ACTION_MOVE，绕开这个坑。
            //   同时在按下瞬间锁住外层 ScrollView 的拦截，否则纵向移动会被判成滚动、拖拽立刻取消。
            val slop = ViewConfiguration.get(this).scaledTouchSlop
            var downX = 0f
            var downY = 0f
            var dragStarted = false

            fun beginDrag() {
                if (dragStarted) return
                dragStarted = true
                stopWiggle(root)
                root.scaleX = 1f
                root.scaleY = 1f
                val ok = root.startDragAndDrop(
                    ClipData.newPlainText("shiny-entry", entry.name),
                    View.DragShadowBuilder(root),
                    entry,
                    0
                )
                if (ok) toast("松开手放到目标分组即可移动")
            }

            root.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX
                        downY = ev.rawY
                        dragStarted = false
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragStarted &&
                            (kotlin.math.abs(ev.rawX - downX) > slop ||
                                kotlin.math.abs(ev.rawY - downY) > slop)
                        ) {
                            beginDrag()
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    else -> {}
                }
                false   // 返回 false：点击（弹菜单）/ 长按逻辑照常走
            }
        }
        return root
    }

    /**
     * 编辑态的「可以拖我」呼吸动画：轻微缩放往返 + 错开起始时间。
     * 动画对象挂在 `view.tag` 上，便于拖拽开始时 / 重建视图时取消。
     */
    private fun attachWiggle(cell: View, key: String) {
        val anim = ObjectAnimator.ofPropertyValuesHolder(
            cell,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.95f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.95f)
        ).apply {
            duration = 820
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = (kotlin.math.abs(key.hashCode()) % 600).toLong()
            start()
        }
        cell.tag = anim
    }

    /** 取消呼吸动画。重建视图前必须调用，否则脱离视图树的动画会一直空跑。 */
    private fun stopWiggle(v: View) {
        (v.tag as? Animator)?.cancel()
        v.tag = null
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) stopWiggle(v.getChildAt(i))
        }
    }

    /**
     * 把一个 View 变成拖拽投放目标：卡片拖到它上面 = 移入对应分组。
     * 进入高亮浅金，离开/结束还原。
     */
    private fun attachDropTarget(v: View, target: ShinyData.Group) {
        v.setOnDragListener { view, e ->
            when (e.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.setBackgroundColor(resolveColor(this, R.color.gold_soft))
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    view.setBackgroundColor(Color.TRANSPARENT)
                    true
                }
                DragEvent.ACTION_DROP -> {
                    val entry = e.localState as? ShinyData.Entry
                    if (entry != null) {
                        view.setBackgroundColor(Color.TRANSPARENT)
                        // 拖拽结束后再重建视图，避免在 drag 会话中销毁源视图
                        if (moveEntryTo(target, entry)) view.post { render() }
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    view.setBackgroundColor(Color.TRANSPARENT)
                    true
                }
                else -> false
            }
        }
    }

    /** 把一条记录移到目标分组（追加到末尾）。移出后空分组自动消失。 */
    private fun moveEntryTo(target: ShinyData.Group, entry: ShinyData.Entry): Boolean {
        val season = currentSeason()
        val from = season.groups.firstOrNull { g -> g.items.any { it === entry } } ?: return false
        if (from === target) return false
        from.items.remove(entry)
        target.items.add(entry)
        if (from.items.isEmpty() && from.name.isNotBlank()) season.groups.remove(from)
        ShinyData.save(this, root)
        toast("已移到「${target.name.ifBlank { "未分类" }}」")
        return true
    }

    /**
     * 把一张卡片变成投放目标：拖到它上面 = 插到它的位置之前。
     * 于是同组内拖动 = 调顺序，跨组拖动 = 换分类兼指定位置。
     * 被拖到的那张会半透明高亮，提示「会插在这里」。
     */
    private fun attachCellDropTarget(v: View, anchor: ShinyData.Entry) {
        v.setOnDragListener { view, e ->
            when (e.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.alpha = 0.5f
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    view.alpha = 1f
                    true
                }
                DragEvent.ACTION_DROP -> {
                    view.alpha = 1f
                    val dragged = e.localState as? ShinyData.Entry
                    if (dragged != null && moveEntryBefore(dragged, anchor)) {
                        view.post { render() }
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    view.alpha = 1f
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 把 dragged 插到 anchor 前面。
     * 同组 → 只调顺序；跨组 → 移过去并插在该位置，源分组空了就删掉。
     */
    private fun moveEntryBefore(dragged: ShinyData.Entry, anchor: ShinyData.Entry): Boolean {
        if (dragged === anchor) return false
        val season = currentSeason()
        val from = season.groups.firstOrNull { g -> g.items.any { it === dragged } } ?: return false
        val target = season.groups.firstOrNull { g -> g.items.any { it === anchor } } ?: return false
        val toIdx = target.items.indexOf(anchor)
        if (toIdx < 0) return false

        if (from === target) {
            val fromIdx = from.items.indexOf(dragged)
            if (fromIdx == toIdx) return false          // 原位，不算改动
            from.items.removeAt(fromIdx)
            var ins = toIdx
            if (fromIdx < toIdx) ins--                   // 抽走自己后，后面的下标整体前移
            from.items.add(ins.coerceIn(0, from.items.size), dragged)
            ShinyData.save(this, root)
            toast("已调整顺序：第 ${from.items.indexOf(dragged) + 1} 位")
            return true
        }

        from.items.remove(dragged)
        val ins = toIdx.coerceIn(0, target.items.size)
        target.items.add(ins, dragged)
        if (from.items.isEmpty() && from.name.isNotBlank()) season.groups.remove(from)
        ShinyData.save(this, root)
        toast("已移到「${target.name.ifBlank { "未分类" }}」第 ${ins + 1} 位")
        return true
    }

    private fun updateModeButton() {
        findViewById<TextView>(R.id.btnShinyMode)?.apply {
            setCompoundDrawablesWithIntrinsicBounds(
                if (shinyMode) R.drawable.ic_shiny_on else R.drawable.ic_shiny_off,
                0, 0, 0
            )
        }
    }

    private fun badgeBg(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(9).toFloat()
        setColor(resolveColor(this@ShinyActivity, R.color.gold))
        setStroke(1, Color.WHITE)
    }

    private fun cellBg(owned: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        setColor(resolveColor(this@ShinyActivity, if (owned) com.google.android.material.R.attr.colorSurface else com.google.android.material.R.attr.colorSurfaceVariant))
        setStroke(
            if (owned) 2 else 1,
            resolveColor(this@ShinyActivity, if (owned) R.color.gold else com.google.android.material.R.attr.colorOutline)
        )
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    /** 圆角卡片样式的输入框，统一异色图鉴里所有弹窗的输入外观。 */
    private fun styledInput(hint: String, text: String = "", multiLine: Boolean = false): EditText {
        return EditText(this).apply {
            this.hint = hint
            if (text.isNotBlank()) setText(text)
            setSingleLine(!multiLine)
            if (multiLine) maxLines = 5
            gravity = if (multiLine) Gravity.TOP or Gravity.START else Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorSurfaceVariant))
                setStroke(1, this@ShinyActivity.themed(com.google.android.material.R.attr.colorOutline))
            }
            setTextColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorOnSurface))
            setHintTextColor(this@ShinyActivity.themed(com.google.android.material.R.attr.colorOnSurfaceVariant))
            textSize = 15f
        }
    }

    // —— 告知异色 ——
    /** @param seed 预填的查询词（识图认错了，把手动输入带过来的原文字串填上）。 */
    /**
     * 「告知异色」入口。
     *
     * 默认直接进**联想输入**（最快路径：边打边找、点一下就加入未分类）。
     * 需要填分类、或一次录多只的，走下面的「更多方式」。
     */
    private fun showAddDialog(seed: String = "") {
        if (seed.isNotBlank()) {
            // 带预填词（多半是识图认错了带过来的）就直接进联想，少点一层
            pickSpiritName("添加图鉴", seed) { addEntryByName(it) }
            return
        }
        val season = currentSeason()
        UiDialogs.menuWithSubs(
            this,
            "添加图鉴 · ${season.name}",
            null,
            listOf(
                "找一只加入" to "边打边找，点一下就加入未分类",
                "批量添加多只" to "每行一个名字，一次全部加入未分类",
                "带分类批量添加" to "每行一个分类：精灵1，精灵2"
            )
        ) { which ->
            when (which) {
                0 -> pickSpiritName("找一只加入") { addEntryByName(it) }
                1 -> showBatchInput()
                2 -> showBatchInputWithGroups()
            }
        }
    }

    /** 用名字加入：先按图鉴条目（有 slug/编号/图标），找不到就按自定义精灵记。 */
    private fun addEntryByName(name: String) {
        val n = name.trim()
        if (n.isBlank()) return
        Thread {
            val hit = ShinyCatalog.lookupByName(this, n)
            runOnUiThread {
                if (hit != null) addEntry(hit, "")
                else addEntries(listOf(ShinyCatalog.Entry(n, "", "", "", 0)))
            }
        }.start()
    }

    /** 单个名字的高级入口（要填分类、或批量）。 */
    private fun showAddDialogAdvanced(seed: String = "") {
        val et = styledInput("精灵名字或编号，例如：拿脑丸 / 幽铃 / 226", seed)
        val etGroup = styledInput("分类（可留空，例如：赤月奇遇精灵）")
        val season = currentSeason()
        val (dlg, box) = UiDialogs.panel(
            this, "添加图鉴 · ${season.name}",
            "名字或编号都行，会自动去图鉴里对应"
        )
        box.addView(et)
        box.addView(View(this@ShinyActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(10)
            )
        })
        box.addView(etGroup)
        box.addView(UiDialogs.row(this, "识别并添加") {
            val q = et.text.toString().trim()
            if (q.isBlank()) { et.error = "请输入名字或编号"; return@row }
            dlg.dismiss()
            doResolve(q, etGroup.text.toString().trim())
        })
        // 一屏好几只时，一只一只来太慢：走批量
        box.addView(UiDialogs.row(this, "批量添加多只", "每行一个名字，一次全部加入") {
            dlg.dismiss()
            showBatchInput(et.text.toString().trim())
        })
        box.addView(UiDialogs.row(this, "取消", soft = true) { dlg.dismiss() })
        dlg.show()
        et.requestFocus()
    }

    // —— 批量添加：一次输入多个名字 ——

    /**
     * 批量输入：用户每行写一个精灵名（也支持逗号/顿号/空格分隔），
     * 逐行去图鉴对应，然后**走与识图同一套多选面板**确认后一次性加入。
     *
     * 这样「打字录入」和「截图识别」的确认体验完全一致，不用学两套。
     */
    private fun showBatchInput(seed: String = "") {
        UiDialogs.inputLines(
            this,
            "批量添加",
            "小独角兽\n公平鸽\n小夜",
            message = "每行一个精灵名字（也可用逗号、顿号、空格分隔）。\n" +
                "会自动逐行去图鉴对应，确认后一次性全部加入「未分类」。",
            initial = seed,
            confirmText = "下一步：对应图鉴"
        ) { raw ->
            resolveBatch(raw)
        }
    }

    /** 把用户输入拆成一个个查询词。以换行为主，同时兼容常见的分隔符。 */
    private fun splitQueries(raw: String): List<String> {
        return raw.split('\n', '，', ',', '、', '；', ';', ' ', '\t', '　')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun resolveBatch(raw: String) {
        val queries = splitQueries(raw)
        if (queries.isEmpty()) {
            toast("没有可识别的名字")
            return
        }
        val dlg = UiDialogs.loading(this, "对应中…", "正在查图鉴：${queries.size} 个名字")
        Thread {
            val list = ShinyCatalog.entries(this)
            // 逐个查询词做精确/模糊对应，不联网（尽量快，且批量场景下网络请求会很慢）
            val byLine = mutableListOf<Pair<String, List<ShinyCatalog.Entry>>>()
            val missed = mutableListOf<String>()
            for (q in queries) {
                val hits = ShinyCatalog.matchQueries(list, q)
                if (hits.isEmpty()) missed.add(q) else byLine.add(q to hits)
            }
            runOnUiThread {
                dlg.dismiss()
                if (byLine.isEmpty()) {
                    showBatchAllMissed(missed)
                } else {
                    showBatchCandidates(byLine, missed)
                }
            }
        }.start()
    }

    /** 全部都没对上：给手动指定 / 记为自定义的出口。 */
    private fun showBatchAllMissed(missed: List<String>) {
        val sb = StringBuilder()
        sb.append("这 ${missed.size} 个名字在图鉴里都找不到：\n")
        sb.append(missed.joinToString("、").take(200)).append("\n\n")
        sb.append("可能是拼错了，或者图鉴站还没收录。")
        val (dlg, box) = UiDialogs.panel(this, "没能对应上", sb.toString())
        box.addView(UiDialogs.row(this, "改一改再试") { dlg.dismiss(); showBatchInput(missed.joinToString("\n")) })
        box.addView(UiDialogs.row(this, "全部记为自定义精灵") {
            dlg.dismiss()
            addEntries(missed.map { ShinyCatalog.Entry(it, "", "", "", 0) })
        })
        box.addView(UiDialogs.row(this, "取消", soft = true) { dlg.dismiss() })
        dlg.show()
    }

    /**
     * 批量对应结果：复用识图的多选面板。
     * 每行默认勾头号候选，没对上的名字单独列在最后提示用户。
     */
    private fun showBatchCandidates(
        byLine: List<Pair<String, List<ShinyCatalog.Entry>>>,
        missed: List<String>
    ) {
        val flat = mutableListOf<Pair<Int, ShinyCatalog.Entry>>()
        val items = mutableListOf<Pair<String, String>>()
        val preselect = mutableSetOf<Int>()
        byLine.forEachIndexed { li, (line, cands) ->
            cands.forEachIndexed { ci, e ->
                val idx = flat.size
                flat.add(li to e)
                items.add(
                    "${if (ci == 0) "① " else "　"}${e.name}${if (e.no > 0) "  #${e.no}" else ""}" to
                        "你输入「$line」${if (e.py.isNotBlank()) " · ${e.py}" else ""}"
                )
                if (ci == 0) preselect.add(idx)
            }
        }
        val head = StringBuilder()
        head.append("输入 ${byLine.size + missed.size} 个名字，对应上 ${byLine.size} 个。\n")
        head.append("已预选最像的，改好勾选后一次加入：")
        if (missed.isNotEmpty()) {
            head.append("\n\n没对上的 ${missed.size} 个：")
            head.append(missed.joinToString("、").take(80))
            head.append("\n（加入后可单独再处理）")
        }

        val extras = mutableListOf<View>()
        if (missed.isNotEmpty()) {
            extras.add(UiDialogs.row(this, "把没对上的记为自定义精灵", missed.joinToString("、").take(30)) {
                addEntries(missed.map { ShinyCatalog.Entry(it, "", "", "", 0) })
            })
        }
        extras.add(UiDialogs.row(this, "重新输入", soft = true) {
            showBatchInput(byLine.joinToString("\n") { it.first })
        })

        UiDialogs.multiPick(
            this,
            "批量添加 · 勾选加入",
            head.toString(),
            items,
            preselect = preselect,
            confirmText = "加入选中的 %d 只",
            extraRows = extras
        ) { picked ->
            val hits = picked.mapNotNull { flat.getOrNull(it)?.second }
            if (hits.isNotEmpty()) addEntries(hits)
        }
    }

    // —— 带分类批量添加 ——

    /**
     * 带分类批量输入。
     * 每行格式：分类名：精灵1，精灵2，精灵3
     * 「未分类」「无」或空分类 → 加入未分类（groupName = ""）。
     */
    private fun showBatchInputWithGroups(seed: String = "") {
        UiDialogs.inputLines(
            this,
            "带分类批量添加",
            "赛季奇遇：卡波，稻草人\n未分类：斑斑，豆丁鱼",
            message = "每行一个分类。格式：分类名：精灵1，精灵2\n" +
                "分类名为「未分类」「无」或留空时，精灵会加入未分类。",
            initial = seed,
            confirmText = "下一步：对应图鉴"
        ) { raw -> resolveBatchWithGroups(raw) }
    }

    private fun resolveBatchWithGroups(raw: String) {
        val lines = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) {
            toast("没有可识别的内容")
            return
        }
        val dlg = UiDialogs.loading(this, "对应中…", "正在按分类查图鉴")
        Thread {
            val list = ShinyCatalog.entries(this)
            // groupName -> [(输入原文, 头号候选)]
            val groupMap = mutableMapOf<String, MutableList<Pair<String, ShinyCatalog.Entry>>>()
            val missed = mutableListOf<Pair<String, String>>() // (groupName, query)
            for (line in lines) {
                val (groupPart, namesPart) = when {
                    line.contains("：") -> line.split("：", limit = 2)
                    line.contains(":") -> line.split(":", limit = 2)
                    else -> listOf("", line)
                }.let { it[0] to it.getOrElse(1) { "" } }
                val groupName = when (groupPart.trim()) {
                    "", "未分类", "无" -> ""
                    else -> groupPart.trim()
                }
                val names = namesPart.split(Regex("[,，、]"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                for (name in names) {
                    val hits = ShinyCatalog.matchQueries(list, name)
                    if (hits.isEmpty()) {
                        missed.add(groupName to name)
                    } else {
                        groupMap.getOrPut(groupName) { mutableListOf() }
                            .add(name to hits.first())
                    }
                }
            }
            runOnUiThread {
                dlg.dismiss()
                if (groupMap.isEmpty()) {
                    showBatchAllMissed(missed.map { it.second })
                } else {
                    showBatchCandidatesWithGroups(groupMap, missed)
                }
            }
        }.start()
    }

    /**
     * 带分类的批量候选确认面板。
     * 每个候选前面带上分类标签，让用户知道会进哪个分组。
     */
    private fun showBatchCandidatesWithGroups(
        groupMap: Map<String, List<Pair<String, ShinyCatalog.Entry>>>,
        missed: List<Pair<String, String>>
    ) {
        val flat = mutableListOf<Triple<String, String, ShinyCatalog.Entry>>()
        val items = mutableListOf<Pair<String, String>>()
        val preselect = mutableSetOf<Int>()
        val sortedGroups = groupMap.toSortedMap(compareBy { it.isEmpty().toString() + it })
        sortedGroups.forEach { (groupName, pairs) ->
            pairs.forEach { (query, e) ->
                val idx = flat.size
                flat.add(Triple(groupName, query, e))
                val tag = if (groupName.isBlank()) "【未分类】" else "【$groupName】"
                items.add(
                    "$tag ${e.name}${if (e.no > 0) "  #${e.no}" else ""}" to
                        "你输入「$query」${if (e.py.isNotBlank()) " · ${e.py}" else ""}"
                )
                preselect.add(idx)
            }
        }

        val totalInput = groupMap.values.sumOf { it.size } + missed.size
        val head = StringBuilder()
        head.append("共 $totalInput 个名字，对应上 ${groupMap.values.sumOf { it.size }} 个。\n")
        head.append("已按分类预选，改好勾选后一次加入：")
        if (missed.isNotEmpty()) {
            head.append("\n\n没对上的 ${missed.size} 个：")
            head.append(missed.joinToString("、") { it.second }.take(80))
            head.append("\n（加入后可单独再处理）")
        }

        val extras = mutableListOf<View>()
        if (missed.isNotEmpty()) {
            extras.add(UiDialogs.row(this, "把没对上的记为自定义精灵（未分类）",
                missed.joinToString("、") { it.second }.take(30)) {
                addEntries(missed.map { ShinyCatalog.Entry(it.second, "", "", "", 0) })
            })
        }
        extras.add(UiDialogs.row(this, "重新输入", soft = true) {
            showBatchInputWithGroups(groupMap.values.flatten().joinToString("\n") { it.first })
        })

        UiDialogs.multiPick(
            this,
            "带分类批量添加 · 勾选加入",
            head.toString(),
            items,
            preselect = preselect,
            confirmText = "加入选中的 %d 只",
            extraRows = extras
        ) { picked ->
            val byGroup = mutableMapOf<String, MutableList<ShinyCatalog.Entry>>()
            for (idx in picked) {
                val (groupName, _, e) = flat[idx]
                byGroup.getOrPut(groupName) { mutableListOf() }.add(e)
            }
            if (byGroup.isNotEmpty()) addEntriesWithGroups(byGroup)
        }
    }

    /**
     * 按分类批量加入。每个分类内的逻辑与 addEntries 一致：已有 +1，没有就新增。
     */
    private fun addEntriesWithGroups(hitsByGroup: Map<String, List<ShinyCatalog.Entry>>) {
        val season = currentSeason()
        var added = 0
        var bumped = 0
        val newlyAdded = mutableListOf<ShinyData.Entry>()
        for ((groupName, hits) in hitsByGroup) {
            val group = ShinyData.ensureGroup(season, groupName)
            for (hit in hits) {
                val exist = group.items.firstOrNull { it.name == hit.name }
                if (exist != null) {
                    exist.count += 1
                    bumped++
                } else {
                    val ne = ShinyData.Entry(
                        name = hit.name, no = hit.no, slug = hit.slug,
                        icon = hit.icon, count = 1
                    )
                    group.items.add(ne)
                    newlyAdded.add(ne)
                    added++
                }
            }
        }
        ShinyData.save(this, root)
        render()
        val msg = buildString {
            append("已加入 ")
            if (added > 0) append("$added 只")
            if (bumped > 0) append(if (added > 0) "，" else "").append("$bumped 只 +1")
        }
        toast(msg)
        for (ne in newlyAdded) Thread { ShinyIcons.ensure(this, ne) }.start()
    }

    private fun doResolve(query: String, groupName: String) {
        val dlg = UiDialogs.loading(this, "识别中…", "正在查图鉴：「$query」")
        Thread {
            val res = ShinyCatalog.resolve(this, query)
            val total = ShinyCatalog.size(this)
            runOnUiThread {
                dlg.dismiss()
                when (res) {
                    is ShinyCatalog.Result.Ok -> addEntry(res.hit, groupName)
                    is ShinyCatalog.Result.Ambiguous -> showAmbiguous(res, groupName)
                    is ShinyCatalog.Result.NotFound ->
                        showNotFound(res, groupName, total)
                }
            }
        }.start()
    }

    /** 图鉴里找不到：可能是新精灵，给「自定义精灵」和「重新联网刷新」两条出路。 */
    private fun showNotFound(
        res: ShinyCatalog.Result.NotFound, groupName: String, total: Int
    ) {
        val partials = ShinyCatalog.bestMatches(this, res.query, 6)
        val msg = buildString {
            append(res.reason)
            append("\n\n图鉴目前 ").append(total).append(" 只。")
            if (partials.isNotEmpty()) {
                append("名字接近的有：\n  ")
                append(partials.joinToString("\n  ") {
                    it.name + if (it.no > 0) "  #${it.no}" else ""
                })
            }
            append("\n\n如果这是游戏新出的精灵（图鉴站还没收录），可以记为「自定义精灵」，")
            append("存本机、不会被之后的图鉴刷新覆盖。")
        }
        val (dlg, box) = UiDialogs.panel(this, "图鉴里没有「${res.query}」", msg)
        box.addView(UiDialogs.row(this, "记为自定义精灵", "存本机，不会被图鉴刷新覆盖") {
            dlg.dismiss(); askCustomName(res.query, groupName)
        })
        box.addView(UiDialogs.row(this, "联网刷新图鉴", "看看图鉴站是不是刚收录了它") {
            dlg.dismiss(); forceRefreshCatalog()
        })
        box.addView(UiDialogs.row(this, "取消", soft = true) { dlg.dismiss() })
        dlg.show()
    }

    // —— 赛季 / 分类管理 ——
    /**
     * 赛季 / 分类管理。
     * 注意：图鉴刷新、自定义精灵属于「设置」，不放在这里（v1.10.21 起）。
     */
    private fun showManageDialog() {
        val season = currentSeason()
        val (got, total) = ShinyData.progress(root, season)
        val items = listOf(
            "新建赛季" to "赛季名，例如 S5",
            "在当前赛季新建分类" to groupSummary(season),
            "重命名当前赛季" to "现在是「${season.name}」（$got/$total 种）",
            "删除当前赛季" to "该赛季下所有记录都会被删除"
        )
        UiDialogs.menuWithSubs(
            this,
            "赛季 / 分类",
            "当前 ${season.name} · 共 ${root.seasons.size} 个赛季",
            items,
            dangerIndex = 3
        ) { which ->
            when (which) {
                0 -> inputDialog("新建赛季", "赛季名，例如 S5") { name ->
                    val ns = ShinyData.ensureSeason(root, name)
                    ShinyData.save(this, root)
                    selectSeason(ns.name)
                }
                1 -> inputDialog(
                    "新建分类",
                    "分类名，例如 赤月奇遇精灵（可留空 = 未分类）"
                ) { name ->
                    ShinyData.ensureGroup(currentSeason(), name)
                    ShinyData.save(this, root)
                    render()
                }
                2 -> inputDialog("重命名赛季", "新的赛季名") { name ->
                    val cur = currentSeason()
                    cur.name = name
                    selectedSeasonName = name
                    getSharedPreferences("merchant_prefs", MODE_PRIVATE).edit()
                        .putString("shiny_season", name).apply()
                    ShinyData.save(this, root)
                    setupSeasonButton(); render()
                }
                3 -> {
                    if (root.seasons.size <= 1) { toast("至少要留一个赛季"); return@menuWithSubs }
                    UiDialogs.confirm(
                        this,
                        "删除「${currentSeason().name}」？",
                        "该赛季下所有异色记录都会被删除。",
                        confirmText = "删除",
                        danger = true
                    ) {
                        val idx = root.seasons.indexOfFirst { it.name == selectedSeasonName }
                        if (idx >= 0) root.seasons.removeAt(idx)
                        selectedSeasonName = root.seasons.firstOrNull()?.name ?: ""
                        seasonIndex = 0
                        ShinyData.save(this, root)
                        setupSeasonButton(); render()
                    }
                }
            }
        }
    }

    private fun groupSummary(season: ShinyData.Season): String {
        val gs = season.groups.filter { it.name.isNotBlank() }
        if (gs.isEmpty()) return "还没有分类（可留空 = 未分类）"
        return "已有：${gs.joinToString("、") { it.name }}"
    }

    /** 手动录入一只图鉴没有的精灵，之后它就能被识别到。 */
    private fun askCustomName(hint: String, groupName: String) {
        val etName = styledInput("精灵名字，例如：XXX", hint)
        val etNo = styledInput("编号（可留空）")
        val (dlg, box) = UiDialogs.panel(
            this, "记为自定义精灵",
            "图鉴站还没收录的精灵会存在本机，不会被之后的图鉴刷新覆盖，以后用名字就能直接识别。"
        )
        box.addView(etName)
        box.addView(View(this@ShinyActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(10)
            )
        })
        box.addView(etNo)
        box.addView(UiDialogs.row(this, "保存并加入") {
            val name = etName.text.toString().trim()
            if (name.isBlank()) { toast("名字不能为空"); return@row }
            dlg.dismiss()
            val no = etNo.text.toString().trim().toIntOrNull() ?: 0
            val e = ShinyCatalog.addCustom(this, name, no)
            if (e == null) toast("保存失败") else addEntry(e, groupName)
        })
        box.addView(UiDialogs.row(this, "取消", soft = true) { dlg.dismiss() })
        dlg.show()
    }

    /** 强制联网刷新图鉴（忽略 3 天缓存有效期）。 */
    private fun forceRefreshCatalog() {
        val dlg = UiDialogs.loading(this, "刷新中…", "正在从图鉴站拉取最新的精灵列表")
        Thread {
            val before = ShinyCatalog.size(this)
            val ok = ShinyCatalog.refresh(this, force = true)
            val after = ShinyCatalog.size(this)
            runOnUiThread {
                dlg.dismiss()
                UiDialogs.info(
                    this,
                    if (ok) "刷新完成" else "刷新失败",
                    if (ok)
                        "图鉴：$before → $after 只" +
                            if (after > before) "（新增 ${after - before} 只）"
                            else "（没有新增）"
                    else "联网失败，请检查网络后重试。\n（内置图鉴仍可正常使用）"
                )
            }
        }.start()
    }

    private fun showAmbiguous(res: ShinyCatalog.Result.Ambiguous, groupName: String) {
        UiDialogs.menuWithSubs(
            this,
            "有歧义，请确认",
            res.reason,
            res.hits.map { "#${it.no} ${it.name}" to it.py }
        ) { which -> addEntry(res.hits[which], groupName) }
    }

    private fun addEntry(hit: ShinyCatalog.Entry, groupName: String) {
        val season = currentSeason()
        val group = ShinyData.ensureGroup(season, groupName)
        val exist = group.items.firstOrNull { it.name == hit.name }
        if (exist != null) {
            // 已经有了就再 +1 只（比如第二只异色小黑猫）
            exist.count += 1
            ShinyData.save(this, root)
            render()
            toast("${hit.name} 已有 ${exist.count} 只（+1）")
            return
        }
        group.items.add(
            ShinyData.Entry(
                name = hit.name, no = hit.no, slug = hit.slug, icon = hit.icon, count = 1
            )
        )
        ShinyData.save(this, root)
        render()
        toast("已记录：${hit.name}${if (hit.no > 0) " #${hit.no}" else ""}")
        Thread { ShinyIcons.ensure(this, group.items.last()) }.start()
    }

    // —— 识图加入 ——
    /**
     * 选一张游戏截图 -> 本地认字（不联网）-> 拿认出的名字/编号去图鉴里找候选。
     * 完全离线，不需要预先缓存任何立绘。
     */
    private fun pickForVision() {
        UiDialogs.confirm(
            this,
            "识图加入异色",
            "选一张截图，App 在本地把图里的字认出来（不联网、不上传），" +
                "再拿去图鉴里找最像的精灵，列出来给你点选。\n\n" +
                "建议截图里精灵名字清晰完整；认错了不要紧，可以手动输入。",
            confirmText = "选择截图"
        ) { visionLauncher.launch(arrayOf("image/*")) }
    }

    private fun startVision(uri: Uri) {
        val dlg = UiDialogs.loading(this, "识别中…", "正在本地识别截图里的文字")
        Thread {
            val bmp = ShinyOcr.loadScaled(this, uri, 1600)
            if (bmp == null) {
                runOnUiThread {
                    dlg.dismiss()
                    toast("读不到这张图片")
                }
                return@Thread
            }
            val ocr = try {
                ShinyOcr.recognize(this, bmp)
            } catch (e: Exception) {
                e.printStackTrace()
                ShinyOcr.Result(emptyList(), "识别过程出错了。")
            }
            val text = ocr.text
            val num = ocr.number
            val conf = ocr.confidence
            val reason = ocr.reason
            // 一屏可能有多只精灵：按行各自出候选，再一次性多选加入
            val byLine = if (text.isBlank() && num == null) emptyList()
            else ShinyCatalog.bestMatchesByLine(this, text)
            runOnUiThread {
                dlg.dismiss()
                bmp.recycle()
                if (byLine.isEmpty()) {
                    showVisionFailed(text, num, conf, reason)
                } else {
                    showVisionCandidates(byLine, text, num, conf)
                }
            }
        }.start()
    }

    // —— 置信度分档 ——
    /**
     * 把 OCR 的平均 NCC 分数分成三档，给用户一个「要不要信」的直觉。
     *
     * **阈值必须用「真机分数」标定，不能用 PC 离线镜像的分数。**
     * 实测两者有系统性差异：Android 用 `Canvas.drawText` 渲染模板（带 hinting
     * 与抗锯齿），PC 镜像用纯 Python 光栅化，**镜像分数整体比真机高约 0.08**。
     * 我第一版就是拿镜像数据定的阈值，结果真机上每行好字都被判成「低置信度」。
     *
     * 真机实测（2.jpg，逐字过滤后整行均分）：
     *   · 三行有效行：0.448 / 0.489 / 0.496
     *   · 真名字单字：0.444 ~ 0.630，中位 0.504
     *   · 明显错字/垃圾：0.218 ~ 0.389
     *
     * 所以 0.50 / 0.43 才贴真机分布（旧值 0.86/0.72 完全失真，
     * 中间的 0.56/0.48 也偏高）。
     */
    private enum class Conf(val label: String, val advice: String) {
        High("高", "认得很清楚，基本可以直接选"),
        Mid("中", "可能有 1~2 个字认错了，扫一眼再点"),
        Low("低", "认错的可能性不小，建议核对或手动输入")
    }

    private fun confOf(score: Float): Conf = when {
        score >= 0.50f -> Conf.High
        score >= 0.43f -> Conf.Mid
        else -> Conf.Low
    }

    /** 置信度一行文案，例如「识别置信度：高 · 认得很清楚，基本可以直接选」。 */
    private fun confLine(score: Float): String {
        val c = confOf(score)
        return "识别置信度：${c.label}（${(score * 100).toInt()}%）· ${c.advice}"
    }

    /** 没认出任何候选时的兜底：说清为什么认不出，并给手动输入 / 自定义精灵两条出路。 */
    private fun showVisionFailed(
        text: String, num: Int?, conf: Float = 0f, reason: String = ""
    ) {
        val sb = StringBuilder()
        if (text.isBlank()) {
            // 认不出：把原因摊开说，别只说「没找到」
            sb.append(if (reason.isNotBlank()) reason else "这张图里没找到像文字的内容。")
            sb.append("\n\n")
            sb.append("好消息：这次没有硬塞给你一堆认错的字。\n\n")
            sb.append("怎么办：\n")
            sb.append("· 精灵名压在立绘上、字还很小时，基本认不了 —— 把截图放大，")
            sb.append("让名字行单独占满屏幕宽度再截一张\n")
            sb.append("· 名字被图标 / 星星装饰压住 —— 换一张干净点的截图\n")
            sb.append("· 图里压根没有精灵名 —— 换一张带名字的\n")
            sb.append("· 实在不好截 —— 直接用「手动输入名字」，图鉴匹配一样准")
        } else {
            sb.append("认出来的字：\n「$text」\n\n")
            if (num != null) sb.append("其中编号：$num\n\n")
            sb.append(confLine(conf)).append("\n\n")
            sb.append("但图鉴里没有匹配得上的精灵——可能是新精灵，或者认错了几个字。")
        }
        val seed = if (text.isNotBlank()) text.take(12) else ""
        val (dlg, box) = UiDialogs.panel(this, "没能识别", sb.toString())
        box.addView(UiDialogs.row(this, "手动输入名字") { dlg.dismiss(); showAddDialog(seed) })
        box.addView(UiDialogs.row(this, "记为自定义精灵", "图鉴站还没收录的新精灵") {
            dlg.dismiss(); askCustomName(seed, "")
        })
        box.addView(UiDialogs.row(this, "重选一张", soft = true) { dlg.dismiss(); pickForVision() })
        dlg.show()
    }

    /**
     * 识别结果：**按行多选**。
     *
     * 一张截图里常有 3~4 只异色精灵，OCR 也按行输出。所以这里不是「选一只就关窗」，
     * 而是把每一行的候选都列出来，勾选后**一次全部加入**。
     * 默认把每行的头号候选预先勾上（它就是最像的那只），用户只需取消认错的、补勾漏的。
     *
     * 分类统一走「未分类」，一批加入后想归类再自己去整理——多选场景下逐只问分类太啰嗦。
     */
    private fun showVisionCandidates(
        byLine: List<Pair<String, List<ShinyCatalog.Entry>>>,
        text: String, num: Int?, conf: Float = 0f
    ) {
        val level = confOf(conf)

        // 展平成 (行下标, 候选) 的扁平表，勾选状态用扁平下标表示
        val flat = mutableListOf<Pair<Int, ShinyCatalog.Entry>>()
        val head = StringBuilder()
        if (text.isNotBlank()) head.append("认出的字：「$text」")
        if (num != null) head.append(if (head.isNotEmpty()) "\n" else "").append("编号：$num")
        if (head.isNotEmpty()) head.append("\n")
        head.append(confLine(conf)).append("\n\n")
        head.append(
            when (level) {
                Conf.High -> "识别到 ${byLine.size} 处名字。已预选最像的一只，确认或改一下，然后一次加入："
                Conf.Mid -> "识别到 ${byLine.size} 处名字。已预选最像的，认错了就改勾选："
                Conf.Low -> "认字可能不太准。已预选最像的，请逐项核对后再加入；拿不准就用手动输入。"
            }
        )

        val items = mutableListOf<Pair<String, String>>()
        val preselect = mutableSetOf<Int>()
        byLine.forEachIndexed { li, (line, cands) ->
            cands.forEachIndexed { ci, e ->
                val idx = flat.size
                flat.add(li to e)
                val mark = if (ci == 0) "① " else "　"   // 视觉上标出首选
                items.add(
                    "$mark${e.name}${if (e.no > 0) "  #${e.no}" else ""}" to
                        "第 ${li + 1} 行「$line」${if (e.py.isNotBlank()) " · ${e.py}" else ""}"
                )
                if (ci == 0) preselect.add(idx)   // 每行默认勾头号候选
            }
        }

        // 额外出口：手动输入 / 重选，放在候选下面
        val extras = mutableListOf<View>()
        extras.add(UiDialogs.row(this, "手动输入名字", "识别不对就用这个") {
            showAddDialog(text.take(12))
        })
        extras.add(UiDialogs.row(this, "重新选一张图", "换张更清楚的截图", soft = true) {
            pickForVision()
        })

        UiDialogs.multiPick(
            this,
            "识别结果 · 勾选加入",
            head.toString(),
            items,
            preselect = preselect,
            confirmText = "加入选中的 %d 只",
            extraRows = extras
        ) { picked ->
            val hits = picked.mapNotNull { flat.getOrNull(it)?.second }
            if (hits.isEmpty()) return@multiPick
            addEntries(hits)
        }
    }

    /**
     * 一次加入多只（多选场景）。统一进「未分类」，只存一次盘、只重绘一次。
     * 已有记录的就 +1，这与单只加入的行为保持一致。
     */
    private fun addEntries(hits: List<ShinyCatalog.Entry>) {
        val season = currentSeason()
        val group = ShinyData.ensureGroup(season, "")
        var added = 0
        var bumped = 0
        val newlyAdded = mutableListOf<ShinyData.Entry>()
        for (hit in hits) {
            val exist = group.items.firstOrNull { it.name == hit.name }
            if (exist != null) {
                exist.count += 1
                bumped++
            } else {
                val ne = ShinyData.Entry(
                    name = hit.name, no = hit.no, slug = hit.slug, icon = hit.icon, count = 1
                )
                group.items.add(ne)
                newlyAdded.add(ne)
                added++
            }
        }
        ShinyData.save(this, root)
        render()
        val msg = buildString {
            append("已加入 ")
            if (added > 0) append("$added 只")
            if (bumped > 0) append(if (added > 0) "，" else "").append("$bumped 只 +1")
        }
        toast(msg)
        // 立绘异步补齐
        for (ne in newlyAdded) Thread { ShinyIcons.ensure(this, ne) }.start()
    }

    // —— 校正名字 ——
    /**
     * 扫描所有记录，把「名字写法有问题」的条目列出来：
     *  - 与图鉴正式名不一致（比如 章脑丸 vs 拿脑丸）
     *  - 图鉴里找不到（脏名字）
     * 每一项都可选择：改成官方名 / 改成通称 / 手动输入。
     */
    private fun showFixNamesDialog() {
        val dlg = UiDialogs.loading(this, "扫描中…", "正在对照图鉴检查记录里的名字")
        Thread {
            val all = root.seasons.flatMap { s -> s.groups.flatMap { g -> g.items } }
            val issues = mutableListOf<Triple<ShinyData.Entry, ShinyCatalog.Entry, String>>()
            for (it in all) {
                if (it.name.isBlank()) continue
                val hit = ShinyCatalog.lookupByName(this, it.name)
                if (hit == null) {
                    issues.add(Triple(it, ShinyCatalog.Entry(it.name, it.slug, "", it.icon, it.no), ""))
                } else if (hit.name != it.name) {
                    issues.add(Triple(it, hit, ShinyCatalog.commonName(hit.name)))
                }
            }
            runOnUiThread {
                dlg.dismiss()
                if (issues.isEmpty()) {
                    UiDialogs.info(
                        this, "名字都没问题",
                        "当前 ${all.size} 条记录的名字都能对上图鉴，无需校正。"
                    )
                    return@runOnUiThread
                }
                showFixList(issues)
            }
        }.start()
    }

    private fun showFixList(issues: List<Triple<ShinyData.Entry, ShinyCatalog.Entry, String>>) {
        // 列表项 + 末尾一个批量操作
        val subs = issues.map { (old, hit, common) ->
            val target = if (common.isNotBlank() && common != hit.name)
                "${hit.name}（通称 $common）" else hit.name
            if (old.name == hit.name) "⚠ ${old.name}" to "图鉴里没找到这个写法"
            else old.name to "→ $target"
        }.toMutableList()
        subs.add("全部改成官方名" to "一次把上面能自动改的都改成图鉴名")

        UiDialogs.menuWithSubs(
            this,
            "需要校正的 ${issues.size} 条",
            "点一条手动改，或直接全部改成官方名",
            subs
        ) { which ->
            if (which == issues.size) fixAll(issues, false) else askFixOne(issues[which])
        }
    }

    private fun askFixOne(item: Triple<ShinyData.Entry, ShinyCatalog.Entry, String>) {
        val (old, hit, common) = item
        val found = old.name != hit.name || hit.no > 0
        // 用「动作」配对选项，避免靠前缀字符串反查（列表顺序变了就会错）
        val opts = mutableListOf<Pair<String, () -> Unit>>()
        if (found) opts.add("改成图鉴名：${hit.name}" to { applyFix(old, hit, hit.name) })
        if (found && common.isNotBlank() && common != hit.name) {
            opts.add("改成通称：$common" to { applyFix(old, hit, common) })
        }
        opts.add("手动输入名字" to { pickSpiritName("新的名字", old.name) { n -> applyFix(old, hit, n) } })
        UiDialogs.menu(this, "校正「${old.name}」", items = opts.map { it.first }) { which ->
            opts[which].second()
        }
    }

    /**
     * 带**联想**的名字选择：边打边列候选（图标 + 名字 + 编号），点一下即选定。
     *
     * 三处手打精灵名的入口（告知异色 / 图鉴改精灵 / 每日记录）统一走这里，
     * 避免用户还要记住图鉴里的正式写法、或打错字后无从发现。
     *
     * @param initial 预填文本（比如识别错的原文、或原来的名字）
     * @param onDone  选定（或坚持用自己打的字）后回调
     */
    private fun pickSpiritName(title: String, initial: String = "", onDone: (String) -> Unit) {
        UiDialogs.suggestInput(
            this,
            title,
            "精灵名字，例如：小独角兽",
            initial = initial,
            suggest = { q -> ShinyCatalog.suggest(this, q) },
            iconOf = { e -> ShinyIcons.bitmapForCatalog(this, e) },
            pickText = "就用这一只",
            freeText = "就用我打的字",
            onPick = { e -> onDone(e.name) },
            onFree = { raw -> onDone(raw) }
        )
    }

    private fun fixAll(issues: List<Triple<ShinyData.Entry, ShinyCatalog.Entry, String>>, toCommon: Boolean) {
        var n = 0
        for ((old, hit, common) in issues) {
            if (hit.no <= 0 && hit.slug.isBlank()) continue   // 图鉴里找不到的，不动
            val target = if (toCommon && common.isNotBlank()) common else hit.name
            if (target == old.name) continue
            mergeEntry(old, hit, target)
            n++
        }
        if (n == 0) { toast("没有可自动校正的条目"); return }
        ShinyData.save(this, root)
        render()
        toast("已校正 $n 条名字")
    }

    private fun applyFix(old: ShinyData.Entry, hit: ShinyCatalog.Entry, newName: String) {
        if (newName.isBlank()) { toast("名字不能为空"); return }
        if (newName == old.name) { toast("名字没变化"); return }
        mergeEntry(old, hit, newName)
        ShinyData.save(this, root)
        render()
        toast("已改成「$newName」")
    }

    /**
     * 把 old 改名为 newName，并补齐 slug/编号/图标。
     * 如果同一分组里已经有同名条目，则把数量合并过去、删掉本条，避免出现两条一样的。
     *
     * ★ 手动「改精灵」意味着要完整替换为另一只精灵，因此 slug/no/icon 必须强制覆盖，
     *   不能只「空时才补」；否则旧 slug 的缓存图会一直在，用户看到的是原精灵贴图。
     */
    private fun mergeEntry(old: ShinyData.Entry, hit: ShinyCatalog.Entry, newName: String) {
        val season = currentSeason()
        val group = season.groups.firstOrNull { g -> g.items.any { it === old } } ?: return
        val twin = group.items.firstOrNull { it !== old && it.name == newName }
        if (twin != null) {
            twin.count += old.count
            if (twin.note.isBlank()) twin.note = old.note
            // 保留 twin 原有 slug 只有当 hit 没有提供时；否则用 hit 的最新数据
            twin.slug = hit.slug.ifBlank { twin.slug.ifBlank { old.slug } }
            twin.no = if (hit.no > 0) hit.no else if (old.no > 0) old.no else twin.no
            twin.icon = hit.icon.ifBlank { twin.icon.ifBlank { old.icon } }
            group.items.remove(old)
        } else {
            old.name = newName
            old.slug = hit.slug
            old.no = hit.no
            old.icon = hit.icon
        }
    }

    private fun inputDialog(title: String, hint: String, onOk: (String) -> Unit) {
        UiDialogs.input(this, title, hint, onOk = onOk)
    }

    private fun downloadMissing() {
        Thread {
            val all = root.seasons.flatMap { s -> s.groups.flatMap { g -> g.items } }
            val needNormal = all.filter {
                (ShinyArt.normalUrl(this, it.slug, it.name) != null || it.icon.isNotBlank()) &&
                    !ShinyIcons.has(this, it)
            }
            if (needNormal.isNotEmpty()) ShinyIcons.ensureAll(this, needNormal)

            // 只补「BWIKI 上确实有异色立绘」的：本地表或异色清单认了才联网。
            // 641 只里约 128 只有，剩下 513 只跳过 → 省掉大量注定失败的请求。
            val needShiny = all.filter { !ShinyIcons.hasShiny(this, it) && ShinyIcons.worthShinyFetch(this, it) }
            for (e in needShiny) {
                ShinyIcons.ensureShiny(this, e)
                Thread.sleep(250) // 别把 wiki 打限流
            }
            runOnUiThread { render() }
        }.start()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
