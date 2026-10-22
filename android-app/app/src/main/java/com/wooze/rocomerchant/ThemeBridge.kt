package com.wooze.rocomerchant

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.R as MR

/**
 * 主题桥：把「主题色(米金/糖果/彩绘/海洋) × 夜间模式(浅/深)」两维偏好，
 * 解析成一套 Material3 主题 style，在 Activity.onCreate 前 setTheme。
 *
 * 设计要点（业内标准的依赖倒置）：
 * - 所有布局/代码只引用语义 attr（?attr/colorSurface 等），具体色只在 theme 里出现一次；
 * - 换主题 = 换 theme style，业务代码零改动；新增主题只加一个 <style>，零业务改动。
 */
object ThemeBridge {

    const val KEY_THEME = "theme_key"
    const val KEY_NIGHT_MODE = SettingsActivity.KEY_NIGHT_MODE

    private val LIGHT = mapOf(
        "gold" to R.style.Theme_RocoMerchant_Gold,
        "candy" to R.style.Theme_RocoMerchant_Candy,
        "paint" to R.style.Theme_RocoMerchant_Paint,
        "ocean" to R.style.Theme_RocoMerchant_Ocean
    )
    private val DARK = mapOf(
        "gold" to R.style.Theme_RocoMerchant_GoldNight,
        "candy" to R.style.Theme_RocoMerchant_CandyNight,
        "paint" to R.style.Theme_RocoMerchant_PaintNight,
        "ocean" to R.style.Theme_RocoMerchant_OceanNight
    )

    /** 在 Activity.onCreate(savedInstanceState) 之前调用。 */
    fun apply(activity: AppCompatActivity) {
        val prefs = activity.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)
        val theme = prefs.getString(KEY_THEME, "gold") ?: "gold"
        val mode = prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val night = isNight(mode, activity)
        val map = if (night) DARK else LIGHT
        val fallback = if (night) R.style.Theme_RocoMerchant_GoldNight else R.style.Theme_RocoMerchant_Gold
        activity.setTheme(map[theme] ?: fallback)
        appliedMap[activity] = "$theme|$mode"
    }

    /** 判断当前是否应走暗夜主题。 */
    fun isNight(mode: Int, ctx: Context): Boolean = when (mode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> isSystemDark(ctx)
    }

    /** 当前生效的主题色 key（gold/candy/paint/ocean）。 */
    fun currentThemeKey(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, "gold") ?: "gold"
    }

    /** 当前主题是否为「彩绘」（手绘风需要特殊视觉处理）。 */
    fun isPaintTheme(ctx: Context): Boolean = currentThemeKey(ctx) == "paint"

    /** 记录每个 Activity 实例实际套用的「主题色|夜间模式」，用于判断是否需要换肤刷新。 */
    private val appliedMap = java.util.WeakHashMap<AppCompatActivity, String>()

    /**
     * 该 Activity 当前套用的主题是否与最新偏好不一致。
     * 在 onResume 时调用：不一致说明用户在别处切了主题/夜间模式，需要 recreate 换肤。
     * apply() 在 onCreate 已写入 appliedMap，故 onResume 时必然已存在，不会误触发。
     */
    fun shouldRecreate(activity: AppCompatActivity): Boolean {
        // 没在 appliedMap 里的 Activity（未走本桥接、不归我们管）一律不碰，避免误触发重建。
        val applied = appliedMap[activity] ?: return false
        val prefs = activity.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)
        val theme = prefs.getString(KEY_THEME, "gold") ?: "gold"
        val mode = prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        return applied != "$theme|$mode"
    }

    private fun isSystemDark(ctx: Context): Boolean {
        val uiMode = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }
}

/**
 * 从当前主题解析语义 attr 颜色（用于代码里动态创建 Drawable / 设背景）。
 * 作为 Context 扩展，调用点的 receiver 是 Activity / View（都是 Context），
 * 在内部类/lambda 里也能沿 receiver 链解析到外层 Context。
 */
fun Context.themed(attrRes: Int): Int = resolveColor(this, attrRes)

/**
 * 统一的颜色解析入口：既能解析「语义 attr id」(?attr/xxx)，也能解析「color 资源 id」(R.color.xxx)。
 *
 * 框架色全部改为 ?attr/ 引用后，代码中仍存在混合场景 —— 例如「金标记 R.color.gold（固定资源）
 * 或 文字色 ?attr/colorOnSurface」写在同一处三目/变量里。此函数两种 id 都能正确还原，
 * 避免把 attr id 误当 color 资源丢给 getColor 而崩。
 */
fun resolveColor(ctx: Context, spec: Int): Int {
    val tv = TypedValue()
    return if (ctx.theme.resolveAttribute(spec, tv, true)) tv.data
    else ContextCompat.getColor(ctx, spec)
}

/**
 * 彩绘主题专属视觉：纸纹颗粒 + 彩虹顶栏 + 墨线描边。
 * 仅当主题为 paint 时生效，其它主题调用零副作用。
 * 对应设计稿 ui_plan_candy.html「彩绘（抖动墨线 + 蜡笔溢出 + 纸纹）」——
 * 原生 View 没有 SVG 抖动滤镜，用「纸纹 PNG(multiply) + 2px 墨线描边 + 彩虹渐变顶栏」组合还原手绘感。
 */
object PaintTheme {

    /** 当前是否暗夜（与 ThemeBridge 同口径）。 */
    fun isNight(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getInt(ThemeBridge.KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        return ThemeBridge.isNight(mode, ctx)
    }

    /** 彩绘卡片描边宽度（px）：彩绘 2dp（墨线感），其它 1dp。 */
    fun cardStrokePx(ctx: Context): Int {
        val d = ctx.resources.displayMetrics.density
        val dp = if (ThemeBridge.isPaintTheme(ctx)) 2f else 1f
        return (dp * d + 0.5f).toInt()
    }

    private fun dpx(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    /** 彩绘底：填充 + 抖动墨线描边。offDp 为整体错位量（「手贴歪」的错位感）。 */
    fun wobbleRect(
        ctx: Context,
        fill: Int,
        stroke: Int,
        radiusDp: Float,
        seed: Int = 7,
        offDp: Float = 0f
    ): WobbleRectDrawable = WobbleRectDrawable(
        fill, stroke, cardStrokePx(ctx).toFloat(), dpx(ctx, radiusDp), seed, -1f,
        dpx(ctx, offDp), dpx(ctx, offDp)
    )

    /** 彩绘分隔线：手绘抖动的横线。 */
    fun wobbleLine(ctx: Context, color: Int, thicknessDp: Float = 2f): WobbleLineDrawable =
        WobbleLineDrawable(color, dpx(ctx, thicknessDp), 3)

    /** 组件轻微倾斜（手绘错位感）。只在尚未旋转时设置，避免叠加。 */
    fun tilt(v: View?, deg: Float) {
        if (v != null && v.rotation == 0f) v.rotation = deg
    }

    /**
     * Activity 级装饰（仅彩绘主题生效）：
     * - 全屏铺纸纹颗粒；
     * - 顶栏换成方案 A：纸底 + 底边抖动墨线 + 标题下波浪下划线 + 标题轻旋。
     */
    fun decorate(activity: AppCompatActivity, headerView: View?) {
        if (!ThemeBridge.isPaintTheme(activity)) return

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = content?.getChildAt(0) as? ViewGroup
        if (root != null) {
            val grain = ContextCompat.getDrawable(activity, R.drawable.paint_grain)?.mutate()
            if (grain != null) {
                grain.alpha = if (isNight(activity)) 42 else 30
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    root.foreground = grain
                }
            }
        }

        if (headerView != null) {
            val surface = activity.themed(MR.attr.colorSurface)
            val ink = activity.themed(MR.attr.colorOutline)
            val accent = activity.themed(R.attr.colorPaintAccent)
            val bg = WobbleHeaderDrawable(surface, ink, cardStrokePx(activity).toFloat(), accent)
            headerView.background = bg
            val title = activity.findViewById<View>(R.id.headerTitle)
            headerView.post {
                if (title != null) {
                    val l = title.left.toFloat()
                    val w = title.width.toFloat().coerceAtMost(dpx(activity, 78f))
                    bg.setWaveRange(l, l + w)
                    tilt(title, -0.7f)
                }
            }
        }
    }
}
