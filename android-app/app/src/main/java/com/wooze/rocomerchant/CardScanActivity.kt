package com.wooze.rocomerchant

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.Camera
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.R as MR

/**
 * 相机实时扫描炫彩色卡（仅测试渠道）。
 *
 * 做法：相机预览每 ~400ms 取一帧 → 在取景框内找双色区域 → 连续 3 帧一致才记账 →
 * 累积到待导入列表（可删除）→ 完成导入写入本地。
 *
 * 说明：底色/混色由取色得到；粒子形状暂不细分，默认记为「星芒」，可在展示页详情里改。
 */
class CardScanActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var preview: SurfaceView
    private lateinit var overlay: ScanOverlayView
    private lateinit var listBox: LinearLayout
    private lateinit var countTv: TextView

    private var camera: Camera? = null
    private var previewW = 0
    private var previewH = 0
    private var lastAnalyze = 0L
    private var busy = false
    private var stable = 0
    private var lastBase = 0
    private var lastMix = 0

    private val pending = mutableListOf<Pair<Int, Int>>()   // base to mix

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeBridge.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(buildRoot())
        renderList()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 91)
        }
    }

    private fun buildRoot(): View {
        val root = FrameLayout(this)
        preview = SurfaceView(this)
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        preview.holder.addCallback(this)

        overlay = ScanOverlayView(this).apply { hint = "把色卡放进框里，自动识别" }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(11), dp(13), dp(13))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xF2181715.toInt())
            }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = "对准方框 · 自动识别"
            textSize = 11.5f
            setTextColor(0xFFA89E8C.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        countTv = TextView(this).apply {
            textSize = 11.5f
            setTextColor(0xFFE4573D.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        top.addView(countTv)
        sheet.addView(top)

        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(7), 0, 0)
        }
        val sv = android.widget.ScrollView(this)
        sv.addView(listBox)
        sheet.addView(sv, LinearLayout.LayoutParams(-1, dp(180)))

        val btns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        btns.addView(button("完成并导入", true) { confirmImport() })
        btns.addView(button("取消并放弃", false) { confirmCancel() })
        sheet.addView(btns)

        root.addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        return root
    }

    private fun button(text: String, primary: Boolean, onClick: () -> Unit): View =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            setTextColor(if (primary) 0xFFFFFDF6.toInt() else 0xFFA89E8C.toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                if (primary) setColor(0xFFE4573D.toInt())
                else {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), 0xFF4A443B.toInt())
                }
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
            setOnClickListener { onClick() }
        }

    // ——— 相机 ———

    override fun surfaceCreated(holder: SurfaceHolder) = openCamera(holder)

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releaseCamera()
    }

    private fun openCamera(holder: SurfaceHolder) {
        try {
            val cam = Camera.open()
            camera = cam
            val p = cam.parameters
            val sizes = p.supportedPreviewSizes ?: emptyList()
            val target = sizes
                .filter { it.width * it.height <= 1280 * 720 }
                .maxByOrNull { it.width * it.height }
                ?: sizes.minByOrNull { it.width * it.height }
            if (target != null) {
                previewW = target.width
                previewH = target.height
                p.setPreviewSize(previewW, previewH)
            }
            p.previewFormat = ImageFormat.NV21
            try {
                if (p.supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) == true) {
                    p.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                }
            } catch (_: Throwable) {
            }
            cam.parameters = p
            cam.setDisplayOrientation(90)
            cam.setPreviewDisplay(holder)

            val bufSize = previewW * previewH * 3 / 2
            cam.addCallbackBuffer(ByteArray(bufSize))
            cam.addCallbackBuffer(ByteArray(bufSize))
            cam.setPreviewCallbackWithBuffer { data, c ->
                val now = System.currentTimeMillis()
                if (now - lastAnalyze >= 380 && !busy) {
                    lastAnalyze = now
                    busy = true
                    try {
                        analyze(data)
                    } catch (_: Throwable) {
                    } finally {
                        busy = false
                    }
                }
                try {
                    c.addCallbackBuffer(data)
                } catch (_: Throwable) {
                }
            }
            cam.startPreview()
        } catch (t: Throwable) {
            toast("相机不可用：${t.message ?: "未知错误"}")
            releaseCamera()
        }
    }

    private fun releaseCamera() {
        try {
            camera?.setPreviewCallbackWithBuffer(null)
            camera?.stopPreview()
            camera?.release()
        } catch (_: Throwable) {
        }
        camera = null
    }

    private fun analyze(data: ByteArray) {
        if (previewW <= 0 || previewH <= 0) return
        val region = overlay.previewRect(previewW, previewH)
        val hit = CardDetector.detect(data, previewW, previewH, region)
        if (hit == null) {
            stable = 0
            return
        }
        if (hit.base == lastBase && hit.mix == lastMix) stable++ else {
            lastBase = hit.base
            lastMix = hit.mix
            stable = 1
        }
        if (stable < 3) return
        stable = 0
        if (pending.any { close(it.first, hit.base) && close(it.second, hit.mix) }) return
        pending.add(hit.base to hit.mix)
        runOnUiThread { renderList() }
    }

    private fun close(a: Int, b: Int): Boolean {
        val dr = Color.red(a) - Color.red(b)
        val dg = Color.green(a) - Color.green(b)
        val db = Color.blue(a) - Color.blue(b)
        return dr * dr + dg * dg + db * db < 40 * 40
    }

    // ——— 列表 ———

    private fun renderList() {
        countTv.text = "已记 ${pending.size} 张"
        listBox.removeAllViews()
        if (pending.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "把色卡放进框里，识别到会自动出现"
                textSize = 11.5f
                gravity = Gravity.CENTER
                setTextColor(0xFF7D776B.toInt())
                setPadding(0, dp(24), 0, dp(24))
            })
            return
        }
        pending.forEachIndexed { i, pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(0xFF2B2823.toInt())
                    setStroke(dp(1), 0xFF3A352D.toInt())
                }
            }
            row.addView(CardSwatchView(this).apply {
                bind(pair.first, pair.second, 0)
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(32))
            })
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(9), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            texts.addView(TextView(this).apply {
                text = "未命名"
                textSize = 11.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(0xFFF0EADB.toInt())
            })
            texts.addView(TextView(this).apply {
                text = "#%06X · #%06X".format(pair.first and 0xFFFFFF, pair.second and 0xFFFFFF)
                textSize = 10f
                setTextColor(0xFFA89E8C.toInt())
            })
            row.addView(texts)
            row.addView(TextView(this).apply {
                text = "✕"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(0xFFE08770.toInt())
                setPadding(dp(9), dp(4), dp(9), dp(4))
                background = GradientDrawable().apply {
                    cornerRadius = dp(7).toFloat()
                    setColor(0xFF3A2A22.toInt())
                }
                setOnClickListener { pending.removeAt(i); renderList() }
            })
            listBox.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) })
        }
    }

    private fun confirmImport() {
        if (pending.isEmpty()) {
            toast("还没有识别到色卡")
            return
        }
        UiDialogs.confirm(
            this, "导入这 ${pending.size} 张色卡？",
            "每张独立写入「炫彩色卡」，颜色相同也各占一条。\n写完后可以在展示页改名。",
            confirmText = "确认导入"
        ) {
            val now = System.currentTimeMillis()
            val recs = pending.mapIndexed { i, p ->
                CardRec(now + i, "", p.first, p.second, 0, false, now + i)
            }
            ShinyCardStore.add(this, recs)
            toast("已导入 ${recs.size} 张")
            finish()
        }
    }

    private fun confirmCancel() {
        if (pending.isEmpty()) {
            finish()
            return
        }
        UiDialogs.confirm(
            this, "放弃本次扫描？",
            "已识别的 ${pending.size} 张都会清掉，不会写进列表。",
            confirmText = "放弃",
            danger = true
        ) { finish() }
    }

    // ——— 权限 ———

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 91) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                openCamera(preview.holder)
            } else {
                toast("没有相机权限，无法扫描")
                finish()
            }
        }
    }

    override fun onDestroy() {
        releaseCamera()
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
