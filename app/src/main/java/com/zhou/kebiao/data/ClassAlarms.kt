package com.zhou.kebiao.data

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** 一次「上课前提醒」的排期。[triggerAt] 是应该弹通知的时刻。 */
data class ClassAlarm(
    val triggerAt: ZonedDateTime,
    val courseName: String,
    /** 通知正文：上课时间段 + 地点。 */
    val detail: String,
)

/** 提前多少分钟提醒。 */
const val CLASS_REMINDER_LEAD_MINUTES = 12

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 算出从现在起、整学期所有还没到点的提醒（按时间排序）。
 * 纯函数，不碰 Android，便于单元测试。
 *
 * 每条课程 × 它生效的每一周 = 一次提醒。单双周、只上半学期的课都自然覆盖。
 * 没有作息时间的节次算不出上课时刻，跳过 —— 这类课本来也不该有提醒。
 */
fun upcomingClassAlarms(
    timetable: Timetable,
    now: ZonedDateTime,
    leadMinutes: Int = CLASS_REMINDER_LEAD_MINUTES,
): List<ClassAlarm> {
    val out = mutableListOf<ClassAlarm>()
    val firstMonday = timetable.firstMondayDate()
    for (course in timetable.courses) {
        if (course.dayOfWeek !in 1..7) continue
        val start = timetable.periods.startOf(course.startPeriod) ?: continue
        val end = timetable.periods.endOf(course.endPeriod)
        val timeText = buildString {
            append(HHMM.format(start))
            if (end != null) append("–").append(HHMM.format(end))
        }
        val detail = listOf(timeText, course.location.ifBlank { "地点未填" }).joinToString(" · ")
        val lastWeek = minOf(course.endWeek, timetable.totalWeeks)
        for (week in maxOf(course.startWeek, 1)..lastWeek) {
            if (!course.isActiveIn(week)) continue
            val date = firstMonday.plusDays(((week - 1) * 7 + (course.dayOfWeek - 1)).toLong())
            val trigger = date.atTime(start).atZone(now.zone).minusMinutes(leadMinutes.toLong())
            if (trigger.isAfter(now)) {
                out += ClassAlarm(trigger, course.name.ifBlank { "未知课程" }, detail)
            }
        }
    }
    return out.sortedBy { it.triggerAt }
}
