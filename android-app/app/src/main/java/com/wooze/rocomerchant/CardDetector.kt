package com.wooze.rocomerchant

import android.graphics.RectF

/**
 * 色卡识别：从相机 NV21 帧里取出「一块双色区域」的两个主色。
 *
 * 做法（不依赖页面布局）：
 * 1. 只在取景框范围内采样（步长 2px，够快）；
 * 2. 统计高饱和像素 → 太少的直接判为没有色卡；
 * 3. 把彩色像素按颜色分箱，取出现最多的两簇（两簇颜色距离要够远）→ 底色 / 混色；
 * 4. 白色像素占比作为「有粒子」的旁证（粒子形状识别暂不细分，统一记星芒，可在详情里改）。
 */
object CardDetector {

    class Hit(val base: Int, val mix: Int)

    /** 命中即认为画面里稳定有一张色卡 */
    fun detect(data: ByteArray, pw: Int, ph: Int, region: RectF): Hit? {
        if (pw <= 0 || ph <= 0 || region.isEmpty) return null
        val x0 = region.left.toInt().coerceIn(0, pw - 1)
        val x1 = region.right.toInt().coerceIn(0, pw)
        val y0 = region.top.toInt().coerceIn(0, ph - 1)
        val y1 = region.bottom.toInt().coerceIn(0, ph)
        if (x1 - x0 < 8 || y1 - y0 < 8) return null

        var samples = 0
        var colorful = 0
        // 颜色分箱：每通道取高 4 位 → 4096 箱
        val bins = HashMap<Int, LongArray>()   // key -> [count, rSum, gSum, bSum]
        val yOff = pw * ph
        val halfW = pw / 2

        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                val yv = (data[y * pw + x].toInt() and 0xFF)
                val uvOff = yOff + (y / 2) * halfW * 2 + (x / 2) * 2
                val vv = (data[uvOff].toInt() and 0xFF) - 128
                val uu = (data[uvOff + 1].toInt() and 0xFF) - 128
                val yy = (yv - 16).let { if (it < 0) 0 else it }

                var r = (298 * yy + 409 * vv + 128) shr 8
                var g = (298 * yy - 100 * uu - 208 * vv + 128) shr 8
                var b = (298 * yy + 516 * uu + 128) shr 8
                if (r < 0) r = 0 else if (r > 255) r = 255
                if (g < 0) g = 0 else if (g > 255) g = 255
                if (b < 0) b = 0 else if (b > 255) b = 255

                samples++
                val mx = if (r > g) (if (r > b) r else b) else (if (g > b) g else b)
                val mn = if (r < g) (if (r < b) r else b) else (if (g < b) g else b)
                val sat = if (mx == 0) 0f else (mx - mn) / mx.toFloat()

                if (sat > 0.32f && mx > 90) {
                    colorful++
                    val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                    val s = bins.getOrPut(key) { LongArray(4) }
                    s[0] = s[0] + 1
                    s[1] = s[1] + r
                    s[2] = s[2] + g
                    s[3] = s[3] + b
                }
                x += 2
            }
            y += 2
        }

        if (samples == 0) return null
        // 彩色像素太少 → 没有色卡（或者没对准）
        if (colorful < samples / 12) return null

        data class Cl(val n: Long, val c: Int)
        val sorted = bins.values
            .map { Cl(it[0], rgb((it[1] / it[0]).toInt(), (it[2] / it[0]).toInt(), (it[3] / it[0]).toInt())) }
            .sortedByDescending { it.n }
        if (sorted.isEmpty()) return null

        val c1 = sorted[0].c
        var c2 = 0
        for (k in 1 until sorted.size) {
            if (dist(c1, sorted[k].c) > 70) { c2 = sorted[k].c; break }
        }
        if (c2 == 0) return null

        // 哪个当底色？面积大的那个（数量多的）当底色，另一个当混色
        val base = if (sorted[0].n >= 1) c1 else c1
        return Hit(base, c2)
    }

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun dist(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return Math.sqrt((dr * dr + dg * dg + db * db).toDouble()).toInt()
    }
}
