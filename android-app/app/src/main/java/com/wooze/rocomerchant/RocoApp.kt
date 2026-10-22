package com.wooze.rocomerchant

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

/**
 * 应用入口：启动时套用用户保存的深色模式偏好。
 *
 * 不设这段的话，`AppCompatDelegate.setDefaultNightMode` 只在下一次生效，
 * 重装或杀进程重开后会回到「跟随系统」。
 */
class RocoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val saved = getSharedPreferences("merchant_prefs", MODE_PRIVATE)
            .getInt(SettingsActivity.KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(saved)

        // 切主题色（米金/糖果/彩绘/海洋）后，让所有已打开的页面在回到前台时自动换肤。
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is AppCompatActivity && ThemeBridge.shouldRecreate(activity)) {
                    activity.runOnUiThread { activity.recreate() }
                }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })

        // 进程冷启动（被 AlarmManager 唤醒执行 RefreshReceiver/TickReceiver，或用户打开 App）
        // 时都重新排一次后台刷新闹钟——对抗系统/厂商在某些情况下清除闹钟导致「不挂后台就不刷新」。
        try {
            WidgetAlarmScheduler.scheduleAll(this)
            WidgetAlarmScheduler.scheduleTicker(this)
        } catch (_: Exception) {}
    }
}
