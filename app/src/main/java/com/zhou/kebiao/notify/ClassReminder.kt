package com.zhou.kebiao.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zhou.kebiao.MainActivity
import com.zhou.kebiao.R
import com.zhou.kebiao.data.ClassAlarm
import com.zhou.kebiao.data.Timetable
import com.zhou.kebiao.data.upcomingClassAlarms
import java.time.ZonedDateTime

/**
 * 上课前 12 分钟的本地提醒。纯 AlarmManager，不引进 WorkManager：
 * 提醒要对点，WorkManager 最细只到「差不多」。
 *
 * 整学期的提醒一次性排完（20 门课 × 16 周 ≈ 320 条，低于系统每应用的上限），
 * 这样即使几周不打开 App 也不会漏。课表每次改动都会取消旧排期、按新数据重排。
 */
object ClassReminder {

    const val ACTION_REMIND = "com.zhou.kebiao.action.REMIND_CLASS"
    const val EXTRA_TITLE = "title"
    const val EXTRA_DETAIL = "detail"
    const val EXTRA_ID = "id"

    private const val CHANNEL_ID = "class_reminder"
    private const val SCHEDULER_PREFS = "reminder_scheduler"
    private const val KEY_IDS = "scheduled_ids"

    /** 系统每应用能挂的闹钟有限，留点余量。 */
    private const val MAX_ALARMS = 400

    private const val FIRST_ID = 10_000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "上课前 12 分钟提醒课程、时间与地点"
                }
            )
        }
    }

    /** 按当前课表重排全部提醒。调用前会先取消上一轮排期。 */
    fun reschedule(context: Context, timetable: Timetable) {
        cancelAll(context)
        ensureChannel(context)

        val alarms = upcomingClassAlarms(timetable, ZonedDateTime.now()).take(MAX_ALARMS)
        if (alarms.isEmpty()) return

        val manager = context.getSystemService(AlarmManager::class.java)
        val exact = canScheduleExact(manager)
        val ids = mutableListOf<Int>()
        alarms.forEachIndexed { offset, alarm ->
            val id = FIRST_ID + offset
            val pending = pendingIntent(context, id, alarm)
            val triggerAt = alarm.triggerAt.toInstant().toEpochMilli()
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                // 用户把「闹钟和提醒」权限关了：退化成不精确的闹钟，可能晚几分钟，但不会不响
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            ids += id
        }
        schedulerPrefs(context).edit().putString(KEY_IDS, ids.joinToString(",")).apply()
    }

    /** 取消所有已排的提醒，并清空排期记录。 */
    fun cancelAll(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val prefs = schedulerPrefs(context)
        val ids = prefs.getString(KEY_IDS, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()
        ids.forEach { id -> manager.cancel(cancelPendingIntent(context, id)) }
        prefs.edit().remove(KEY_IDS).apply()
    }

    fun showNotification(context: Context, id: Int, title: String, detail: String) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // 权限在发通知前一刻被撤掉：静默失败，不能让广播崩掉
        }
    }

    private fun pendingIntent(context: Context, id: Int, alarm: ClassAlarm): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMIND
                putExtra(EXTRA_TITLE, alarm.courseName)
                putExtra(EXTRA_DETAIL, alarm.detail)
                putExtra(EXTRA_ID, id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 取消时只需要同 action + 同组件 + 同 requestCode，extras 不参与匹配。 */
    private fun cancelPendingIntent(context: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun schedulerPrefs(context: Context) =
        context.getSharedPreferences(SCHEDULER_PREFS, Context.MODE_PRIVATE)
}
