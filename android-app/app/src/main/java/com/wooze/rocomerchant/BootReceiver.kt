package com.wooze.rocomerchant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机后重新排程每日刷新闹钟（AlarmManager 在重启后会丢失）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            DailyLog.prune(context)   // 开机顺手清一次过期记录
            WidgetAlarmScheduler.scheduleAll(context)
            WidgetAlarmScheduler.scheduleTicker(context)
        }
    }
}
