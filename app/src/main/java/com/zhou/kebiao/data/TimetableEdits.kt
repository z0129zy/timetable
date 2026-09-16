package com.zhou.kebiao.data

import java.time.LocalDate

/**
 * 课表的编辑操作。全是纯函数 —— 返回一份新课表，不改原对象、不碰文件；
 * 落盘由 ui 层的 AppState 负责。
 *
 * 课程用「在 courses 里的下标」定位，不给 Course 加 id：设计文档 §5 限定
 * Course 只有 5 组字段 + missingFields，加 id 会连带改动数据模型与 JSON。
 */
fun Timetable.withCourseAdded(course: Course): Timetable =
    copy(courses = courses + course)

fun Timetable.withCourseReplaced(index: Int, course: Course): Timetable =
    copy(courses = courses.mapIndexed { i, old -> if (i == index) course else old })

fun Timetable.withCourseRemoved(index: Int): Timetable =
    copy(courses = courses.filterIndexed { i, _ -> i != index })

/** 改某一节的起止时间；该节原本没有作息条目（设计文档 §5.3 的情形）就补一条。 */
fun Timetable.withPeriodTime(period: Int, start: String, end: String): Timetable {
    val exists = periods.any { it.period == period }
    val updated = periods.map { if (it.period == period) it.copy(start = start, end = end) else it }
    val merged = if (exists) updated else updated + PeriodTime(period, start, end)
    return copy(periods = merged.sortedBy { it.period })
}

fun Timetable.withSemesterName(name: String): Timetable = copy(semesterName = name)

fun Timetable.withFirstMonday(iso: String): Timetable = copy(firstMonday = iso)

fun Timetable.withTotalWeeks(weeks: Int): Timetable = copy(totalWeeks = weeks)

/**
 * 把课表夹回合法范围。防的是「文件里的值烂了 → App 一打开就崩」——
 * 那种情况下界面里没有任何恢复手段，只能去系统设置里清应用数据。
 * 启动路径上有两处会抛异常，所以夹两项：
 *  - [totalWeeks] 喂给 `coerceIn(1, totalWeeks)`，为 0 时抛 IllegalArgumentException
 *  - [firstMonday] 喂给 `LocalDate.parse`，格式不对时抛 DateTimeParseException
 *
 * 只夹这两项是有意的：PDF 都不提供它们，都是用户自己设的，回退到默认值**不会丢课程数据**。
 * 课程里的脏字段（例如 dayOfWeek 越界）不动 —— 那是另一回事，悄悄把课挪到周一
 * 比让它显眼更糟。
 */
fun Timetable.normalized(): Timetable = copy(
    totalWeeks = totalWeeks.coerceAtLeast(1),
    firstMonday = firstMonday.takeIf(::isIsoDate) ?: DEFAULT_FIRST_MONDAY,
)

private fun isIsoDate(raw: String): Boolean =
    runCatching { LocalDate.parse(raw) }.isSuccess

/**
 * 课程表单校验（设计文档 §7.5）。
 * 返回 null 表示通过，否则是直接显示给用户的中文提示。
 * 教室地点与任课老师允许留空，不校验。
 */
fun validateCourse(course: Course, totalWeeks: Int): String? = when {
    course.name.isBlank() ->
        "课程名称不能为空"

    course.startWeek < 1 || course.startWeek > totalWeeks ||
        course.endWeek < 1 || course.endWeek > totalWeeks ->
        "周次要填第 1 到第 $totalWeeks 周"

    course.startWeek > course.endWeek ->
        "起始周不能晚于结束周"

    course.dayOfWeek < 1 || course.dayOfWeek > 7 ->
        "星期要在周一到周日之间"

    course.startPeriod < 1 || course.startPeriod > 11 ||
        course.endPeriod < 1 || course.endPeriod > 11 ->
        "节次要填第 1 到第 11 节"

    course.startPeriod > course.endPeriod ->
        "起始节不能晚于结束节"

    else -> null
}

/**
 * 新增课程时的预填值（设计文档 §7.5）：周次铺满整学期、每周一次，
 * 星期与节次用所点的那一格，其余留空等用户填。
 * 预填只是省事，5 个字段在表单里都可以改。
 */
fun newCoursePrefill(day: Int, startPeriod: Int, endPeriod: Int, totalWeeks: Int): Course = Course(
    name = "",
    startWeek = 1,
    endWeek = totalWeeks,
    weekType = WeekType.EVERY,
    dayOfWeek = day,
    startPeriod = startPeriod,
    endPeriod = endPeriod,
    location = "",
    teacher = "",
)
