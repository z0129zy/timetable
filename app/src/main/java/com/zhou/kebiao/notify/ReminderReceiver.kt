package com.zhou.kebiao.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 闹钟到点后弹通知。只认自己排的 action，别的广播一律不理。 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ClassReminder.ACTION_REMIND) return
        val title = intent.getStringExtra(ClassReminder.EXTRA_TITLE) ?: return
        val detail = intent.getStringExtra(ClassReminder.EXTRA_DETAIL).orEmpty()
        val id = intent.getIntExtra(ClassReminder.EXTRA_ID, 0)
        ClassReminder.showNotification(context, id, title, detail)
    }
}
