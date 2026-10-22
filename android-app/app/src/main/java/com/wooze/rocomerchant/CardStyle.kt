package com.wooze.rocomerchant

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.R as MR

/**
 * 商品池 / 订阅提醒两页共用的「卡片化」控件。
 *
 * 默认主题（米金/糖果/海洋）下是「白卡片 + 1px 细描边」；
 * 彩绘主题下自动换成手绘抖动底（[PaintTheme.wobbleRect]）+ 抖动分隔线。
 * 这样两页只写一套布局代码，却能在四个主题下都成立。
 */
object CardStyle {

    fun dp(c: Context, v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), c.resources.displayMetrics
    ).toInt()

    private fun dpf(c: Context, v: Float): Float = v * c.resources.displayMetrics.density

    private fun col(c: Context, attr: Int): Int = c.themed(attr)

    private fun rect(c: Context, color: Int, radiusDp: Int, strokeDp: Int = 0, strokeColor: Int = 0): Drawable =
        GradientDrawable().apply {
            cornerRadius = dp(c, radiusDp).toFloat()
            setColor(color)
            if (strokeDp > 0) setStroke(dp(c, strokeDp), strokeColor)
        }

    /** 卡片底。 */
    fun cardBg(c: Context, radiusDp: Float = 15f): Drawable =
        if (ThemeBridge.isPaintTheme(c))
            PaintTheme.wobbleRect(c, col(c, MR.attr.colorSurface), col(c, MR.attr.colorOutline), radiusDp)
        else rect(c, col(c, MR.attr.colorSurface), radiusDp.toInt(), 1, col(c, MR.attr.colorOutline))

    /** 输入框底。 */
    fun inputBg(c: Context): Drawable =
        rect(c, col(c, MR.attr.colorSurfaceVariant), 12, 1, col(c, MR.attr.colorOutline))

    /** 一张卡片容器（垂直，自带卡片底）。 */
    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(c, 12) }
        background = cardBg(c)
    }

    /** 行间分隔线（彩绘为抖动线，其余 1px 细线）。 */
    fun divider(c: Context): View = View(c).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            if (ThemeBridge.isPaintTheme(c)) dp(c, 3) else dp(c, 1)
        ).apply { leftMargin = dp(c, 14); rightMargin = dp(c, 14) }
        background = if (ThemeBridge.isPaintTheme(c))
            PaintTheme.wobbleLine(c, col(c, MR.attr.colorOutline), 2f)
        else rect(c, col(c, MR.attr.colorOutlineVariant), 0)
    }

    /** 卡片内的一行（横向、垂直居中、统一内边距）。 */
    fun row(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(c, 14), dp(c, 11), dp(c, 14), dp(c, 11))
    }

    /** 分组标题行：色点 + 名称 + 计数 + 折叠箭头。 */
    fun groupHeader(c: Context, title: String, count: String, open: Boolean, muted: Boolean = false): LinearLayout {
        val h = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13))
        }
        h.addView(View(c).apply {
            layoutParams = LinearLayout.LayoutParams(dp(c, 9), dp(c, 9)).apply { rightMargin = dp(c, 9) }
            background = rect(c, if (muted) col(c, MR.attr.colorOnSurfaceVariant) else col(c, MR.attr.colorPrimary), 3)
        })
        h.addView(TextView(c).apply {
            text = title
            textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(col(c, if (muted) MR.attr.colorOnSurfaceVariant else MR.attr.colorOnSurface))
        })
        h.addView(TextView(c).apply {
            text = count
            textSize = 11.5f
            setTextColor(col(c, MR.attr.colorOnSurfaceVariant))
            setPadding(dp(c, 7), 0, 0, 0)
        })
        h.addView(TextView(c).apply {
            text = if (open) "▾" else "▸"
            textSize = 13f
            setTextColor(col(c, MR.attr.colorOnSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(c, 8) }
        })
        return h
    }

    /** 自绘方形勾选框（勾选=主色填充 + 白勾）。 */
    fun checkbox(c: Context, on: Boolean): TextView = TextView(c).apply {
        text = if (on) "✓" else ""
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(dp(c, 22), dp(c, 22))
        background = if (on) rect(c, col(c, MR.attr.colorPrimary), 7)
        else rect(c, Color.TRANSPARENT, 7, 2, col(c, MR.attr.colorOutline))
    }

    /** 右侧小药丸按钮。kind: edit / del / restore / cat。 */
    fun pill(c: Context, text: String, kind: String): TextView = TextView(c).apply {
        this.text = text
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(c, 10), dp(c, 5), dp(c, 10), dp(c, 5))
        val fg: Int
        val bg: Int
        when (kind) {
            "del" -> { fg = col(c, MR.attr.colorError); bg = col(c, MR.attr.colorErrorContainer) }
            "restore" -> { fg = Color.parseColor("#2E7D32"); bg = Color.parseColor("#E8F5E9") }
            "cat" -> { fg = col(c, MR.attr.colorOnSurfaceVariant); bg = col(c, MR.attr.colorSurfaceVariant) }
            else -> { fg = col(c, MR.attr.colorPrimary); bg = col(c, MR.attr.colorSurfaceVariant) }
        }
        setTextColor(fg)
        background = rect(c, bg, 9)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(c, 7) }
    }

    /** 顶部统计条：左说明 + 右强调文字。 */
    fun summary(c: Context, left: String, right: String): LinearLayout {
        val s = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(c, 4), dp(c, 2), dp(c, 4), dp(c, 12))
        }
        s.addView(TextView(c).apply {
            text = left
            textSize = 12.5f
            setTextColor(col(c, MR.attr.colorOnSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (right.isNotBlank()) s.addView(TextView(c).apply {
            text = right
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(col(c, MR.attr.colorPrimary))
        })
        return s
    }

    /** 整行主按钮。 */
    fun primaryButton(c: Context, text: String): TextView = TextView(c).apply {
        this.text = text
        textSize = 14.5f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setTextColor(if (ThemeBridge.isPaintTheme(c)) col(c, MR.attr.colorOnPrimary) else Color.WHITE)
        background = rect(c, col(c, MR.attr.colorPrimary), 12)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 44)
        )
    }

    /** 给一个 View 加上四边间距（dp）。 */
    fun pad(c: Context, v: View, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b))
        v.layoutParams = lp
    }

    // —— 主题呼应底色（首页功能入口卡） ——

    /** 把颜色做色相偏移（度）。 */
    private fun shiftHue(color: Int, deg: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + deg + 360f) % 360f
        return Color.HSVToColor(hsv)
    }

    /** 按比例 t 把 tint 混进 base。 */
    private fun blend(base: Int, tint: Int, t: Float): Int {
        val r = (Color.red(base) + (Color.red(tint) - Color.red(base)) * t).toInt()
        val g = (Color.green(base) + (Color.green(tint) - Color.green(base)) * t).toInt()
        val b = (Color.blue(base) + (Color.blue(tint) - Color.blue(base)) * t).toInt()
        return Color.rgb(r, g, b)
    }

    /**
     * 通用「主题呼应柔和底」：把 [tint] 按 [strength] 融进卡片底色，出一层浅渐变。
     * 彩绘主题下自动换成手绘抖动底。
     */
    fun softBg(c: Context, tint: Int, strength: Float, variant: Int, radiusDp: Float = 14f): Drawable {
        val surface = col(c, MR.attr.colorSurface)
        val top = blend(surface, tint, strength)
        val bottom = blend(surface, tint, strength * 0.38f)
        return if (ThemeBridge.isPaintTheme(c)) {
            PaintTheme.wobbleRect(c, top, col(c, MR.attr.colorOutline), radiusDp, 7 + variant)
        } else {
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(top, bottom)).apply {
                cornerRadius = dpf(c, radiusDp)
                setStroke(dp(c, 1), col(c, MR.attr.colorOutline))
            }
        }
    }

    /**
     * 首页功能入口卡的底色：主题主色做轻微色相偏移后融进卡片底。
     * 不同 [hueShiftDeg] → 三张卡各不相同的柔和主题色，自动跟随米金/糖果/彩绘/海洋与日夜。
     */
    fun entryBg(c: Context, hueShiftDeg: Float, variant: Int): Drawable =
        softBg(c, shiftHue(col(c, MR.attr.colorPrimary), hueShiftDeg), 0.26f, variant, 14f)

    /**
     * 给按钮换上主题呼应底（并保持原内边距，避免背景换掉后内边距丢失）。
     * [tintOverride] 用于危险/特殊动作（如传 colorError 做软红底）。
     */
    fun themeButton(
        c: Context, v: View?, hueShiftDeg: Float, variant: Int,
        padH: Int, padV: Int, strength: Float = 0.20f,
        tintOverride: Int? = null, radiusDp: Float = 24f
    ) {
        v ?: return
        val tint = tintOverride ?: shiftHue(col(c, MR.attr.colorPrimary), hueShiftDeg)
        v.background = softBg(c, tint, strength, variant, radiusDp)
        v.setPadding(dp(c, padH), dp(c, padV), dp(c, padH), dp(c, padV))
    }
}
