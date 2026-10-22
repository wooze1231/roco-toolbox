package com.wooze.rocomerchant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 扫描取景框：矩形框内透明、框外压暗，四角 L 形标记，顶部一行小字提示。
 * 也负责把「取景框」换算成相机预览坐标系里的区域（见 [previewRect]）。
 */
class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 取景框占 View 的比例：左、上、右、下 */
    private val holeFrac = RectF(0.06f, 0.26f, 0.94f, 0.56f)

    private val dim = Paint().apply { color = 0xB3000000.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFE4573D.toInt()
        strokeWidth = 2.5f * resources.displayMetrics.density
    }
    private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFE4573D.toInt()
        strokeWidth = 4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    var hint: String = "框住色卡"

    fun holeRect(): RectF = RectF(
        width * holeFrac.left, height * holeFrac.top,
        width * holeFrac.right, height * holeFrac.bottom
    )

    /**
     * 取景框 → 相机预览坐标（预览 90° 旋转后显示）。
     * u（水平）对应预览的 y，(1-u) 从下往上；v（垂直）对应预览的 x。
     */
    fun previewRect(previewW: Int, previewH: Int): RectF {
        if (width == 0 || height == 0) return RectF()
        val u0 = holeFrac.left
        val u1 = holeFrac.right
        val v0 = holeFrac.top
        val v1 = holeFrac.bottom
        return RectF(
            v0 * previewW,
            (1f - u1) * previewH,
            v1 * previewW,
            (1f - u0) * previewH
        )
    }

    override fun onDraw(canvas: Canvas) {
        val h = holeRect()
        // 框外压暗（四块）
        canvas.drawRect(0f, 0f, width.toFloat(), h.top, dim)
        canvas.drawRect(0f, h.bottom, width.toFloat(), height.toFloat(), dim)
        canvas.drawRect(0f, h.top, h.left, h.bottom, dim)
        canvas.drawRect(h.right, h.top, width.toFloat(), h.bottom, dim)

        val r = 16f * resources.displayMetrics.density
        canvas.drawRoundRect(h, r, r, border)

        // 四角
        val len = 18f * resources.displayMetrics.density
        val pts = listOf(
            floatArrayOf(h.left, h.top, h.left + len, h.top),
            floatArrayOf(h.left, h.top, h.left, h.top + len),
            floatArrayOf(h.right, h.top, h.right - len, h.top),
            floatArrayOf(h.right, h.top, h.right, h.top + len),
            floatArrayOf(h.left, h.bottom, h.left + len, h.bottom),
            floatArrayOf(h.left, h.bottom, h.left, h.bottom - len),
            floatArrayOf(h.right, h.bottom, h.right - len, h.bottom),
            floatArrayOf(h.right, h.bottom, h.right, h.bottom - len)
        )
        pts.forEach { canvas.drawLine(it[0], it[1], it[2], it[3], corner) }

        canvas.drawText(hint, width / 2f, h.top - 10f * resources.displayMetrics.density, label)
    }
}
