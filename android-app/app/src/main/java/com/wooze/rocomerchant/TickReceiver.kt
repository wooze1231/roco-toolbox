package com.wooze.rocomerchant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 后台保底心跳：每 15 分钟唤醒一次，联网拉取最新商品并刷新组件。
 *
 * 这是「App 不挂在后台」时组件仍能自动刷新的关键 —— App 进程存活时靠 MainActivity
 * 的每秒轮询跨点刷新；进程被杀后，只有刷新点（8/12/16/20+3 分）和本心跳能唤醒。
 * 原来这个心跳只重绘、不联网，所以进程死后中间时段永远拉不到新数据。
 *
 * 用 RTC_WAKEUP + allowWhileIdle 确保即使设备/模拟器休眠也能唤醒进程执行拉取。
 */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val i = Intent(context, RefreshReceiver::class.java)
            .putExtra(RefreshReceiver.EXTRA_TICK, true)
        context.sendBroadcast(i)
        // 排下一次心跳（RefreshReceiver 内部也会排，这里双保险）
        WidgetAlarmScheduler.scheduleTicker(context)
    }
}
