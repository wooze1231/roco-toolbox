package com.wooze.rocomerchant

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import java.io.File

/** 商品名的图标解析结果：内置资源 / 本地缓存文件 / 无（占位图）。 */
sealed class IconResult {
    data class ResId(val id: Int) : IconResult()
    data class LocalFile(val file: File) : IconResult()
    object None : IconResult()
}

/**
 * 统一的图标解析（App 与桌面组件共用）。
 * 不触发任何网络请求——联网抓取由 IconCache 在后台刷新线程里预先完成。
 */
object IconResolver {

    fun resolve(c: Context, name: String): IconResult {
        ItemIcons.MAP[name]?.let { return IconResult.ResId(it) }
        for ((k, v) in ItemIcons.MAP) {           // 模糊兜底（带数量后缀等）
            if (name.contains(k) || k.contains(name)) return IconResult.ResId(v)
        }
        val f = IconCache.cacheFile(c, name)
        if (f.exists()) return IconResult.LocalFile(f)
        return IconResult.None
    }

    /** 应用到 RemoteViews 的图标 ImageView 上。 */
    fun applyToRemote(c: Context, views: RemoteViews, viewId: Int, name: String) {
        when (val r = resolve(c, name)) {
            is IconResult.ResId -> views.setImageViewResource(viewId, r.id)
            is IconResult.LocalFile -> {
                val bmp = BitmapFactory.decodeFile(r.file.absolutePath)
                if (bmp != null) views.setImageViewBitmap(viewId, bmp)
                else views.setImageViewResource(viewId, R.drawable.widget_icon_placeholder)
            }
            IconResult.None -> views.setImageViewResource(viewId, R.drawable.widget_icon_placeholder)
        }
    }
}
