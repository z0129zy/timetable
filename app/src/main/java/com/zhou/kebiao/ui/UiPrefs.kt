package com.zhou.kebiao.ui

import android.content.Context

/**
 * 只影响界面的本地偏好。与 timetable.json 分开存：
 * 这些值不属于课表数据，也不该被「导入覆盖」碰到。
 */
class UiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    var gridRowHeightDp: Int
        get() = prefs.getInt(KEY_GRID_ROW_HEIGHT, DEFAULT_GRID_ROW_HEIGHT_DP)
        set(value) {
            prefs.edit().putInt(KEY_GRID_ROW_HEIGHT, value).apply()
        }

    var classReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLASS_REMINDER, DEFAULT_CLASS_REMINDER_ENABLED)
        set(value) {
            prefs.edit().putBoolean(KEY_CLASS_REMINDER, value).apply()
        }

    companion object {
        const val MIN_GRID_ROW_HEIGHT_DP = 52
        const val MAX_GRID_ROW_HEIGHT_DP = 88
        /** 比原来的 77dp 收紧一档：一屏能多看到两节。 */
        const val DEFAULT_GRID_ROW_HEIGHT_DP = 60
        const val GRID_ROW_HEIGHT_STEP_DP = 4

        /** 提醒默认开着：装好后第一次启动会请求通知权限。 */
        const val DEFAULT_CLASS_REMINDER_ENABLED = true

        private const val KEY_GRID_ROW_HEIGHT = "grid_row_height_dp"
        private const val KEY_CLASS_REMINDER = "class_reminder_enabled"
    }
}
