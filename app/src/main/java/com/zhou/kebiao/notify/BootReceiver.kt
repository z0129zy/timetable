package com.zhou.kebiao.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhou.kebiao.data.TimetableStore
import com.zhou.kebiao.data.normalized
import com.zhou.kebiao.ui.UiPrefs
import java.io.File

/**
 * 重启会把所有闹钟清掉，开机后按落盘的课表重新排一遍。
 * 用户关了提醒就什么都不做。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!UiPrefs(context).classReminderEnabled) return
        val store = TimetableStore(File(context.filesDir, "timetable.json"))
        val timetable = store.load()?.normalized() ?: return
        ClassReminder.reschedule(context, timetable)
    }
}
