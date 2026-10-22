package com.wooze.rocomerchant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/** 订阅命中通知（本地生成，无网络）。 */
object NotificationHelper {
    private const val CHANNEL = "merchant_alert"

    fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = c.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(
                    CHANNEL, "洛克助手提醒", NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "订阅商品在售提醒" }
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun notifySubscribed(c: Context, hitCats: Set<String>, items: List<String>) {
        ensureChannel(c)
        val detail = items.take(6).joinToString("、")
        val text = "本轮在售你订阅的【${hitCats.joinToString("、")}】：$detail"
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("洛克助手 · 订阅提醒")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        nm.notify(1001, n)
    }
}
