package com.zhou.kebiao.ui.week

import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.isActiveIn
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 网格上的一块卡片。同一个「星期 + 起始节」可能压着多门课
 * （教务系统把同格不同周段的课挤在一起，见设计文档 §3.3 情形 2），
 * 故课程是复数。
 */
data class Slot(
    val dayOfWeek: Int,
    val startPeriod: Int,
    /** 这块卡片要占到的最后一节：取同格所有课里最靠下的那一门。 */
    val endPeriod: Int,
    /** 压在这一格的全部课在 Timetable.courses 里的下标，保持原顺序。 */
    val courseIndexes: List<Int>,
    /** 卡片上显示哪一门：本周要上的优先；本周都不上就显示第一门。 */
    val representative: Int,
)

/** 把课程按「星期 + 起始节」归并。键 = (星期, 起始节)。 */
fun buildSlots(courses: List<Course>, week: Int): Map<Pair<Int, Int>, Slot> =
    courses.indices
        .groupBy { courses[it].dayOfWeek to courses[it].startPeriod }
        .mapValues { (key, indexes) ->
            Slot(
                dayOfWeek = key.first,
                startPeriod = key.second,
                endPeriod = indexes.maxOf { courses[it].endPeriod },
                courseIndexes = indexes,
                representative = indexes.firstOrNull { courses[it].isActiveIn(week) }
                    ?: indexes.first(),
            )
        }

/** 课名末尾的类型符号（设计文档 §5.4：符号并进了课名）。 */
private const val TYPE_SYMBOLS = "★☆◆■〇"

/**
 * 把课名末尾的类型符号拆出来，返回 (课名主体, 符号)。
 * 课名没有符号时第二部分是空串。
 */
fun splitTypeSymbol(name: String): Pair<String, String> =
    if (name.isNotEmpty() && name.last() in TYPE_SYMBOLS) {
        name.dropLast(1) to name.last().toString()
    } else {
        name to ""
    }

/** 「一」…「日」，下标 0 = 周一。 */
val WEEKDAY_SHORT = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 1 → 「周一」… 7 → 「周日」。
 * 下标先夹一次：脏数据（dayOfWeek 越界）不该让界面崩在组合期 ——
 * 这个函数会在编辑表单里被直接调用，而那时还没轮到校验。
 */
fun weekdayFull(day: Int): String = "周" + WEEKDAY_SHORT[(day - 1).coerceIn(0, WEEKDAY_SHORT.lastIndex)]

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

/** 只用于展示。解析不到的节次时间上游已经给了 null，不会走到这里。 */
fun hhmm(time: LocalTime): String = time.format(HHMM)
