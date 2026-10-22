package com.wooze.rocomerchant

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.URLEncoder

/**
 * App 内置 WIKI 浏览器（初版简单 WebView 方案）。
 * 站点：BWIKI（wiki.biligame.com/rocom）+ 罗可图鉴（rocokingdomworld.org/zh，无风控）。
 * 站内链接在本页打开，站外链接交给系统浏览器。
 */
class WikiActivity : AppCompatActivity() {

    private data class Site(
        val name: String,
        val home: String,
        val search: (String) -> String,
        /** true = 该站的搜索框走 MediaWiki API（不走页面搜索，避免 301 循环） */
        val useApi: Boolean = false
    )

    private val sites = listOf(
        Site(
            name = "BWIKI",
            home = "https://wiki.biligame.com/rocom/",
            search = { q -> q },
            useApi = true
        ),
        Site(
            name = "罗可图鉴",
            home = "https://rocokingdomworld.org/zh",
            // 站内搜索交给站点的 pokedex 检索；没有搜索参数就落到图鉴列表
            search = { _ -> "https://rocokingdomworld.org/zh/pokedex" }
        )
    )

    /** 允许在 App 内直接打开的域名（其余交给系统浏览器）。 */
    private val allowedHosts = listOf(
        "wiki.biligame.com", "biligame.com", "hdslb.com",
        "rocokingdomworld.org"
    )

    /**
     * BWIKI 的 index.php?... 搜索会被 301 甩到 searchwiki.biligame.com，
     * 而那个页面里的 JS 又会跳回 wiki.biligame.com，来回 301 → 无限闪跳。
     *
     * 因此不走页面搜索，直接用 MediaWiki 官方 API 拿结果标题，
     * 再 loadUrl 到 wiki.biligame.com/rocom/<标题>，全程只在主域。
     */
    private fun apiSearchUrl(q: String): String {
        val params = mapOf(
            "action" to "query",
            "list" to "search",
            "srsearch" to q,
            "srlimit" to "10",
            "format" to "json"
        )
        val qs = params.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        return "https://wiki.biligame.com/rocom/api.php?$qs"
    }

    /** 单篇文章的稳定地址（主域，不经过 searchwiki）。 */
    private fun pageUrl(title: String): String =
        "https://wiki.biligame.com/rocom/" + URLEncoder.encode(title, "UTF-8").replace("+", "%20")

    /** 用 API 搜索 → 取第一条 → 加载该条目页。失败则退回收首页。 */
    private fun searchViaApi(q: String) {
        progress.visibility = View.VISIBLE
        tvTitle.text = "搜索中…"
        Thread {
            val hit = try {
                val conn = java.net.URL(apiSearchUrl(q)).openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("User-Agent", web.settings.userAgentString)
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body)
                    .optJSONObject("query")
                    ?.optJSONArray("search")
                    ?.takeIf { it.length() > 0 }
                    ?.optJSONObject(0)
                    ?.optString("title")
                    ?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (hit != null) load(pageUrl(hit))
                else {
                    progress.visibility = View.GONE
                    Toast.makeText(this, "没搜到「$q」，已回首页", Toast.LENGTH_SHORT).show()
                    load(sites[0].home)
                }
            }
        }.start()
    }

    private var current = 0
    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var etSearch: EditText
    private lateinit var tvTitle: TextView

    /** 防闪跳：记录上一次导航，用于识别域名往返。 */
    private var lastNavAt = 0L
    private var lastNavUrl = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeBridge.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wiki)

        web = findViewById(R.id.webWiki)
        progress = findViewById(R.id.pbWiki)
        etSearch = findViewById(R.id.etWikiSearch)
        tvTitle = findViewById(R.id.tvWikiTitle)

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.mediaPlaybackRequiresUserGesture = false
        // 手机 UA，拿移动版页面
        s.userAgentString =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleUrl(url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, r: WebResourceRequest): Boolean {
                return handleUrl(r.url.toString())
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
                tvTitle.text = url?.let { shortHost(it) } ?: "WIKI"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                tvTitle.text = view?.title?.takeIf { it.isNotBlank() } ?: "WIKI"
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                if (newProgress >= 100) progress.visibility = View.GONE
            }
        }

        // 站点切换 chips
        val chipBar = findViewById<android.widget.LinearLayout>(R.id.llWikiSites)
        sites.forEachIndexed { i, site ->
            val chip = TextView(this).apply {
                text = site.name
                textSize = 13f
                setPadding(22, 10, 22, 10)
                background = resources.getDrawable(
                    if (i == 0) R.drawable.bg_chip_site_on else R.drawable.bg_chip_site, this@WikiActivity.theme
                )
                setTextColor(
                    resolveColor(
                        this@WikiActivity,
                        if (i == 0) android.R.color.white else com.google.android.material.R.attr.colorOnSurface
                    )
                )
                setOnClickListener { selectSite(i) }
            }
            chipBar.addView(chip)
        }
        chipBar.visibility = View.VISIBLE

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else false
        }
        findViewById<View>(R.id.btnWikiGo).setOnClickListener { doSearch() }

        // 底部导航
        findViewById<View>(R.id.btnWikiBack).setOnClickListener {
            if (web.canGoBack()) web.goBack()
        }
        findViewById<View>(R.id.btnWikiForward).setOnClickListener {
            if (web.canGoForward()) web.goForward()
        }
        findViewById<View>(R.id.btnWikiReload).setOnClickListener { web.reload() }
        findViewById<View>(R.id.btnWikiHome).setOnClickListener { load(sites[current].home) }
        findViewById<View>(R.id.btnWikiBrowser).setOnClickListener {
            val url = web.url ?: sites[current].home
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
            }
        }

        val q = intent.getStringExtra(EXTRA_QUERY)
        val startUrl = savedInstanceState?.getString(STATE_URL)
        when {
            // 注意：savedInstanceState 非空说明是「重建」，优先恢复用户当时的页面，
            // 不要因为 intent 里还留着 EXTRA_QUERY 就重新搜一遍（那正是闪跳的来源之一）。
            startUrl != null -> web.restoreState(savedInstanceState!!)
            !q.isNullOrBlank() -> {
                etSearch.setText(q)
                searchViaApi(q)
            }
            else -> load(sites[0].home)
        }
        addHomeFab()
    }

    /**
     * 悬浮「回主页」按钮：常驻右下，按住可拖动换位（位置记住），
     * 轻点一下回洛克助手主页（清栈，回到已有的 MainActivity）。
     */
    private fun addHomeFab() {
        val fab = TextView(this).apply {
            text = "回主页"
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(this@WikiActivity.themed(com.google.android.material.R.attr.colorOnPrimaryContainer))
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(this@WikiActivity.themed(com.google.android.material.R.attr.colorPrimary))
                setStroke(1, this@WikiActivity.themed(com.google.android.material.R.attr.colorOutline))
            }
            isClickable = true
            isFocusable = true
            elevation = dp(6).toFloat()
        }

        val prefs = getSharedPreferences("merchant_prefs", MODE_PRIVATE)
        val savedX = prefs.getInt("wiki_fab_x", Int.MIN_VALUE)
        val savedY = prefs.getInt("wiki_fab_y", Int.MIN_VALUE)
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        if (savedX != Int.MIN_VALUE && savedY != Int.MIN_VALUE) {
            lp.gravity = Gravity.NO_GRAVITY
            lp.leftMargin = savedX
            lp.topMargin = savedY
        } else {
            lp.gravity = Gravity.BOTTOM or Gravity.END
            lp.bottomMargin = dp(78)
            lp.rightMargin = dp(16)
        }

        val root = window.decorView as android.view.ViewGroup
        root.addView(fab, lp)

        var downX = 0f
        var downY = 0f
        var baseL = 0
        var baseT = 0
        var dragging = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop

        fab.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && (kotlin.math.abs(ev.rawX - downX) > slop ||
                                kotlin.math.abs(ev.rawY - downY) > slop)
                    ) {
                        // 首次拖动：从 gravity 定位切换到绝对边距定位，先取当前屏幕坐标
                        val loc = IntArray(2)
                        v.getLocationOnScreen(loc)
                        val mlp = v.layoutParams as FrameLayout.LayoutParams
                        mlp.gravity = Gravity.NO_GRAVITY
                        mlp.leftMargin = loc[0]
                        mlp.topMargin = loc[1]
                        v.layoutParams = mlp
                        baseL = loc[0]
                        baseT = loc[1]
                        dragging = true
                    }
                    if (dragging) {
                        val mlp = v.layoutParams as FrameLayout.LayoutParams
                        mlp.leftMargin = (baseL + (ev.rawX - downX)).toInt()
                        mlp.topMargin = (baseT + (ev.rawY - downY)).toInt()
                        v.layoutParams = mlp
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        val mlp = v.layoutParams as FrameLayout.LayoutParams
                        val maxL = (root.width - v.width).coerceAtLeast(0)
                        val maxT = (root.height - v.height).coerceAtLeast(0)
                        mlp.leftMargin = mlp.leftMargin.coerceIn(0, maxL)
                        mlp.topMargin = mlp.topMargin.coerceIn(0, maxT)
                        v.layoutParams = mlp
                        prefs.edit()
                            .putInt("wiki_fab_x", mlp.leftMargin)
                            .putInt("wiki_fab_y", mlp.topMargin)
                            .apply()
                        dragging = false
                    } else {
                        // 视作点击：回主页
                        startActivity(
                            Intent(this@WikiActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                        )
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun selectSite(i: Int) {
        current = i
        val bar = findViewById<android.widget.LinearLayout>(R.id.llWikiSites)
        for (k in 0 until bar.childCount) {
            val chip = bar.getChildAt(k) as TextView
            chip.background = resources.getDrawable(
                if (k == i) R.drawable.bg_chip_site_on else R.drawable.bg_chip_site, theme
            )
            chip.setTextColor(
                resolveColor(
                    this@WikiActivity,
                    if (k == i) android.R.color.white else com.google.android.material.R.attr.colorOnSurface
                )
            )
        }
        load(sites[i].home)
    }

    private fun doSearch() {
        val q = etSearch.text.toString().trim()
        if (q.isEmpty()) {
            load(sites[current].home)
            return
        }
        if (sites[current].useApi) searchViaApi(q)
        else load(sites[current].search(URLEncoder.encode(q, "UTF-8")))
    }

    private fun load(url: String) {
        progress.visibility = View.VISIBLE
        // 用户主动发起的加载，重置防闪跳计数，避免误拦
        lastNavAt = 0L
        lastNavUrl = ""
        web.loadUrl(url)
    }

    /** true 表示已处理（拦截），false 表示让 WebView 自己加载。 */
    private fun handleUrl(url: String): Boolean {
        // 防闪跳死循环：短时间内在 BWIKI 两域间反复横跳时，拦掉并停在当前页
        val now = System.currentTimeMillis()
        if (now - lastNavAt < 700 && url != lastNavUrl) {
            val a = Uri.parse(url).host ?: ""
            val b = Uri.parse(lastNavUrl).host ?: ""
            if (a.contains("biligame.com") && b.contains("biligame.com") && a != b) {
                return true   // 拦下这次跳转，避免 301 往返刷屏
            }
        }
        lastNavAt = now
        lastNavUrl = url

        if (url.startsWith("http://") || url.startsWith("https://")) {
            val host = Uri.parse(url).host ?: return false
            // searchwiki 是搜索专用域，它的页面会跳回主域造成循环 —— 一律拦下
            if (host.startsWith("searchwiki.")) return true
            if (allowedHosts.any { host == it || host.endsWith(".$it") }) return false
        }
        // 站外 / 非 http scheme（微信、应用商店等）交给系统
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
        return true
    }

    private fun shortHost(url: String): String =
        Uri.parse(url).host?.removePrefix("www.") ?: "WIKI"

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
        outState.putString(STATE_URL, web.url)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_QUERY = "wiki_query"
        private const val STATE_URL = "wiki_url"
    }
}
