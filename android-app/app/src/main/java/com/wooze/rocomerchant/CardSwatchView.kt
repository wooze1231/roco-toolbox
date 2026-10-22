package com.wooze.rocomerchant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 炫彩色卡缩略图：底色大块 + 顶部混色波浪 + 三个粒子（点位与形状全站统一）。
 *
 * 粒子点位固定为 (0.28, 0.27) 小 / (0.58, 0.53) 大 / (0.84, 0.23) 小 ——
 * 与设计稿一致：**四种粒子共用同一套位置，只换形状**。
 */
class CardSwatchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var baseColor: Int = 0xFFE5576F.toInt()
        set(v) { field = v; invalidate() }

    var mixColor: Int = 0xFF3DD6D6.toInt()
        set(v) { field = v; invalidate() }

    /** 0 星芒 / 1 爱心 / 2 菱形 / 3 五角星 / 4 特殊 */
    var particle: Int = 0
        set(v) { field = v; invalidate() }

    /** 特殊外观（黑白/赛季）：整张成品图，assets/cards/<art> */
    var art: String = ""
        set(v) {
            field = v
            artBmp = null
            invalidate()
        }
    private var artBmp: Bitmap? = null

    private fun artBitmap(): Bitmap? {
        if (art.isEmpty()) return null
        if (artBmp == null) {
            artBmp = try {
                context.assets.open("cards/$art").use { BitmapFactory.decodeStream(it) }
            } catch (_: Throwable) {
                null
            }
        }
        return artBmp
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bmpCache = HashMap<Int, Bitmap?>()
    private val maskCache = HashMap<String, Bitmap?>()

    /** 形状遮罩（白色图，染色用）。整套只有 base / wave / 4 种粒子，共约 20KB。 */
    private fun mask(name: String): Bitmap? {
        if (maskCache.containsKey(name)) return maskCache[name]
        val b = try {
            context.assets.open("particles/$name").use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        }
        maskCache[name] = b
        return b
    }

    /** 粒子原图（assets/particles）。星芒没有原图 → 返回 null，走手绘。 */
    private fun particleBitmap(): Bitmap? {
        bmpCache[particle]?.let { return it }
        if (bmpCache.containsKey(particle) && bmpCache[particle] == null) return null
        val name = when (particle) {
            1 -> "heart.png"
            2 -> "diamond.png"
            3 -> "star5.png"
            else -> null
        }
        val bmp = if (name == null) null else try {
            context.assets.open("particles/$name").use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        }
        bmpCache[particle] = bmp
        return bmp
    }

    fun bind(base: Int, mix: Int, particle: Int) {
        this.baseColor = base
        this.mixColor = mix
        this.particle = particle
    }

    /**
     * 强制 16:9（宽 : 高），宽度随父容器自适应 —— 所以卡片不管多宽，比例都一致。
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        if (w <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val h = (w * 9f / 16f + 0.5f).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val r = 10f * resources.displayMetrics.density

        // 特殊外观：直接画成品图（自带形状/颜色）
        val specialArt = artBitmap()
        if (specialArt != null) {
            canvas.drawBitmap(specialArt, null, RectF(0f, 0f, w, h), bmpPaint)
            return
        }

        rect.set(0f, 0f, w, h)

        canvas.save()
        path.reset()
        path.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.clipPath(path)

        // 底色：整块铺满（有原图遮罩就用遮罩染色，圆角/边缘与游戏一致）
        paint.style = Paint.Style.FILL
        paint.color = baseColor
        val baseMask = mask("base.png")
        if (baseMask != null) {
            paint.colorFilter = PorterDuffColorFilter(baseColor, PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(baseMask, null, rect, paint)
            paint.colorFilter = null
        } else {
            canvas.drawRect(rect, paint)
        }

        // 混色：上方波浪（wave.png 是 280×108 的遮罩，按宽度等比缩放到卡片高度的 108/154）
        paint.color = mixColor
        val waveMask = mask("wave.png")
        if (waveMask != null) {
            paint.colorFilter = PorterDuffColorFilter(mixColor, PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(
                waveMask, null,
                RectF(0f, 0f, w, h * (108f / 154f)),
                paint
            )
            paint.colorFilter = null
        } else {
            path.reset()
            path.moveTo(0f, h * 0.47f)
            path.cubicTo(w * 0.14f, h * 0.20f, w * 0.26f, h * 0.67f, w * 0.42f, h * 0.57f)
            path.cubicTo(w * 0.60f, h * 0.45f, w * 0.74f, h * 0.17f, w, h * 0.33f)
            path.lineTo(w, 0f)
            path.lineTo(0f, 0f)
            path.close()
            canvas.drawPath(path, paint)
        }

        // 粒子：优先用游戏原图（点位/形状 100% 一致）；星芒无原图则按量出的 6 个位置手绘。
        val bmp = particleBitmap()
        if (bmp != null) {
            // 同一套逻辑：遮罩 + 染色
            bmpPaint.colorFilter =
                PorterDuffColorFilter(0xFFF5EFE1.toInt(), PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bmp, null, RectF(0f, 0f, w, h), bmpPaint)
            bmpPaint.colorFilter = null
        } else {
            val ink = 0xFFF5EFE1.toInt()
            BLOBS.forEach { b ->
                drawParticle(canvas, w * b[0], h * b[1], minOf(w * b[2], h * b[3]), ink)
            }
        }

        canvas.restore()
    }

    private fun drawParticle(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val s = size / 2f
        if (s <= 0f) return
        val hollow = particle == 3
        paint.color = color
        paint.style = if (hollow) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = if (hollow) s * 0.42f else 0f
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND

        path.reset()
        when (particle) {
            1 -> { // 爱心
                path.moveTo(0f, 0.67f)
                path.cubicTo(-0.70f, 0.15f, -0.89f, -0.19f, -0.53f, -0.52f)
                path.cubicTo(-0.29f, -0.74f, 0f, -0.49f, 0f, -0.27f)
                path.cubicTo(0f, -0.49f, 0.29f, -0.74f, 0.53f, -0.52f)
                path.cubicTo(0.89f, -0.19f, 0.70f, 0.15f, 0f, 0.67f)
                path.close()
            }
            2 -> { // 菱形
                path.moveTo(0f, -1f); path.lineTo(1f, 0f)
                path.lineTo(0f, 1f); path.lineTo(-1f, 0f); path.close()
            }
            3 -> { // 五角星（空心）
                path.moveTo(0f, -1f)
                path.lineTo(0.247f, -0.340f); path.lineTo(0.951f, -0.309f)
                path.lineTo(0.399f, 0.130f); path.lineTo(0.588f, 0.809f)
                path.lineTo(0f, 0.42f); path.lineTo(-0.588f, 0.809f)
                path.lineTo(-0.399f, 0.130f); path.lineTo(-0.951f, -0.309f)
                path.lineTo(-0.247f, -0.340f); path.close()
            }
            else -> { // 四角星芒（特殊卡也用它）
                path.moveTo(0f, -1f)
                path.cubicTo(0.16f, -0.29f, 0.29f, -0.16f, 1f, 0f)
                path.cubicTo(0.29f, 0.16f, 0.16f, 0.29f, 0f, 1f)
                path.cubicTo(-0.16f, 0.29f, -0.29f, 0.16f, -1f, 0f)
                path.cubicTo(-0.29f, -0.16f, -0.16f, -0.29f, 0f, -1f)
                path.close()
            }
        }

        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(s, s)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    companion object {
        /**
         * 从游戏粒子图（assets/particles/diamond.png，280×154）量出来的 6 个点位：
         * x / y / 宽 / 高，均为相对卡片宽高的比例。星芒没有原图时按这套位置手绘。
         */
        val BLOBS = arrayOf(
            floatArrayOf(0.198f, 0.212f, 0.100f, 0.208f),
            floatArrayOf(0.795f, 0.273f, 0.121f, 0.247f),
            floatArrayOf(0.441f, 0.425f, 0.207f, 0.429f),
            floatArrayOf(0.682f, 0.588f, 0.129f, 0.273f),
            floatArrayOf(0.220f, 0.685f, 0.150f, 0.312f),
            floatArrayOf(0.818f, 0.791f, 0.071f, 0.143f)
        )
    }
}
