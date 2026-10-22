package com.wooze.rocomerchant

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 彩绘（手绘）主题的绘制原语。
 *
 * 设计稿的手绘感来自 SVG 的 `feTurbulence` + `feDisplacementMap`（把描边整体「揉歪」）。
 * Android 没有这个滤镜，于是这里改成**几何抖动**：沿圆角矩形/直线周边密集采样，
 * 每个采样点叠一个确定性噪声偏移，再连成路径 —— 等价于「手抖着画出来的线」。
 *
 * 噪声用固定 seed + 下标哈希，保证同一控件每次重绘完全一致（不闪烁）。
 */
private fun wob(seed: Int, i: Int): Float {
    var x = seed * 374761393 + i * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return ((x and 0xFFFF) / 65535f) * 2f - 1f   // [-1,1]
}

private fun arcTo(
    pts: MutableList<Float>, cx: Float, cy: Float, r: Float,
    startDeg: Float, sweepDeg: Float, n: Int
) {
    for (k in 0..n) {
        val a = Math.toRadians((startDeg + sweepDeg * k / n).toDouble())
        pts.add((cx + r * cos(a)).toFloat())
        pts.add((cy + r * sin(a)).toFloat())
    }
}

/** 平滑圆角矩形填充 + 抖动墨线描边（彩绘卡片/弹窗/按钮）。 */
class WobbleRectDrawable(
    private var fillColor: Int,
    private var strokeColor: Int,
    private var strokePx: Float,
    private var radiusPx: Float,
    private val seed: Int = 7,
    private val ampPx: Float = -1f,
    private var offX: Float = 0f,
    private var offY: Float = 0f
) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private var path: Path? = null

    fun tintFill(c: Int) { fillColor = c; invalidateSelf() }
    fun tintStroke(c: Int) { strokeColor = c; invalidateSelf() }

    override fun onBoundsChange(bounds: Rect) { path = null }

    private fun build(b: RectF): Path {
        val amp = if (ampPx >= 0f) ampPx else (strokePx * 0.85f).coerceAtLeast(1f)
        val r = radiusPx.coerceIn(0f, min(b.width(), b.height()) / 2f)
        val inset = strokePx / 2f
        val l = b.left + inset + offX
        val t = b.top + inset + offY
        val rt = b.right - inset + offX
        val bo = b.bottom - inset + offY

        val pts = ArrayList<Float>(180)
        val step = 7f
        var x = l + r
        while (x < rt - r) { pts.add(x); pts.add(t); x += step }
        arcTo(pts, rt - r, t + r, r, -90f, 90f, 7)
        var y = t + r
        while (y < bo - r) { pts.add(rt); pts.add(y); y += step }
        arcTo(pts, rt - r, bo - r, r, 0f, 90f, 7)
        x = rt - r
        while (x > l + r) { pts.add(x); pts.add(bo); x -= step }
        arcTo(pts, l + r, bo - r, r, 90f, 90f, 7)
        y = bo - r
        while (y > t + r) { pts.add(l); pts.add(y); y -= step }
        arcTo(pts, l + r, t + r, r, 180f, 90f, 7)

        val p = Path()
        var i = 0
        while (i < pts.size) {
            val nx = pts[i] + wob(seed, i) * amp
            val ny = pts[i + 1] + wob(seed, i + 977) * amp
            if (i == 0) p.moveTo(nx, ny) else p.lineTo(nx, ny)
            i += 2
        }
        p.close()
        return p
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val b = RectF(bounds)
        val r = radiusPx.coerceIn(0f, min(b.width(), b.height()) / 2f)
        fill.color = fillColor
        canvas.drawRoundRect(b, r, r, fill)
        if (strokePx > 0f && strokeColor != 0) {
            stroke.color = strokeColor
            stroke.strokeWidth = strokePx
            canvas.drawPath(path ?: build(b).also { path = it }, stroke)
        }
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha; stroke.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(cf: ColorFilter?) { fill.colorFilter = cf; stroke.colorFilter = cf; invalidateSelf() }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** 手绘抖动横线（分隔线/下划线）。 */
class WobbleLineDrawable(
    private var color: Int,
    private var strokePx: Float,
    private val seed: Int = 3
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var path: Path? = null

    override fun onBoundsChange(bounds: Rect) { path = null }

    private fun build(b: RectF): Path {
        val amp = (strokePx * 0.9f).coerceAtLeast(0.8f)
        val cy = b.centerY()
        val p = Path()
        var x = b.left + 2f
        var i = 0
        p.moveTo(x, cy + wob(seed, 0) * amp)
        while (x <= b.right - 2f) {
            x += 6f; i++
            p.lineTo(min(x, b.right - 2f), cy + wob(seed, i) * amp)
        }
        return p
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        paint.color = color
        paint.strokeWidth = strokePx
        val b = RectF(bounds)
        canvas.drawPath(path ?: build(b).also { path = it }, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf; invalidateSelf() }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * 彩绘顶栏（方案 A）：纸底填充 + 底部一条抖动墨线 + 标题下方一段手绘波浪下划线。
 * [setWaveRange] 由 Activity 在布局完成后按标题实际位置设置。
 */
class WobbleHeaderDrawable(
    private var fillColor: Int,
    private var strokeColor: Int,
    private var strokePx: Float,
    private var waveColor: Int
) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var waveLeft = -1f
    private var waveRight = -1f
    private var linePath: Path? = null
    private var wavePath: Path? = null

    fun setWaveRange(l: Float, r: Float) {
        waveLeft = l; waveRight = r; wavePath = null; invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) { linePath = null; wavePath = null }

    private fun buildLine(b: RectF): Path {
        val amp = (strokePx * 0.9f).coerceAtLeast(0.8f)
        val cy = b.bottom - strokePx
        val p = Path()
        var x = 8f; var i = 0
        p.moveTo(x, cy + wob(21, 0) * amp)
        while (x <= b.right - 8f) {
            x += 8f; i++
            p.lineTo(min(x, b.right - 8f), cy + wob(21, i) * amp)
        }
        return p
    }

    private fun buildWave(b: RectF): Path {
        val l = if (waveRight > waveLeft) waveLeft else b.left + 92f
        val r = if (waveRight > waveLeft) waveRight else (l + 66f)
        val cy = b.bottom - strokePx - 7f
        val p = Path()
        p.moveTo(l, cy)
        var x = l
        var i = 0
        while (x < r) {
            x += 4f; i++
            p.lineTo(min(x, r), cy + sin(i * 0.9f) * 2.4f)
        }
        return p
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val b = RectF(bounds)
        fill.color = fillColor
        canvas.drawRect(b, fill)
        line.color = strokeColor
        line.strokeWidth = strokePx
        canvas.drawPath(linePath ?: buildLine(b).also { linePath = it }, line)
        if (waveColor != 0) {
            line.color = waveColor
            line.strokeWidth = strokePx * 1.35f
            canvas.drawPath(wavePath ?: buildWave(b).also { wavePath = it }, line)
        }
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha; line.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(cf: ColorFilter?) { fill.colorFilter = cf; line.colorFilter = cf; invalidateSelf() }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * 自绘勾选框（给真正的 CheckBox 当 buttonDrawable 用）。
 * 保持 CheckBox 的既有语义（isChecked / 监听 / 联动），只换外观：
 * 未勾 = 圆角方框描边；勾选 = 主色填充 + 白勾。
 */
class CheckDrawable(
    private val sizePx: Int,
    private val onColor: Int,
    private val borderColor: Int,
    private val borderPx: Float,
    private val radiusPx: Float,
    private val checkColor: Int = 0xFFFFFFFF.toInt()
) : Drawable() {

    private var checked = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val check = Path()

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val c = state.contains(android.R.attr.state_checked)
        if (c != checked) {
            checked = c
            invalidateSelf()
            return true
        }
        return false
    }

    override fun getIntrinsicWidth(): Int = sizePx
    override fun getIntrinsicHeight(): Int = sizePx

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        rect.set(
            bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.right.toFloat(), bounds.bottom.toFloat()
        )
        val r = radiusPx.coerceAtMost(min(rect.width(), rect.height()) / 2f)
        if (checked) {
            paint.style = Paint.Style.FILL
            paint.color = onColor
            canvas.drawRoundRect(rect, r, r, paint)

            paint.style = Paint.Style.STROKE
            paint.color = checkColor
            paint.strokeWidth = borderPx * 1.5f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            val w = rect.width()
            val h = rect.height()
            check.reset()
            check.moveTo(rect.left + w * 0.25f, rect.top + h * 0.52f)
            check.lineTo(rect.left + w * 0.43f, rect.top + h * 0.71f)
            check.lineTo(rect.left + w * 0.76f, rect.top + h * 0.31f)
            canvas.drawPath(check, paint)
        } else {
            rect.inset(borderPx / 2f, borderPx / 2f)
            paint.style = Paint.Style.STROKE
            paint.color = borderColor
            paint.strokeWidth = borderPx
            canvas.drawRoundRect(rect, r, r, paint)
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf; invalidateSelf() }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
