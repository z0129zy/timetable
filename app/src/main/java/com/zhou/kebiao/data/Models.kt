package com.zhou.kebiao.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** 周类型。约定：第 1 周为单周，单周 = 奇数周。 */
@Serializable
enum class WeekType { EVERY, ODD, EVEN }

/** 课程里可能在导入时认不出来的字段。界面据此在编辑弹窗标黄提示（设计文档 §8）。 */
@Serializable
enum class CourseField { NAME, WEEKS, PERIODS, LOCATION, TEACHER }

/** 课程。只保留 5 组字段，PDF 中其余信息一律不存。 */
@Serializable
data class Course(
    val name: String,           // 课程名称，含类型符号 ★☆◆■〇
    val startWeek: Int,
    val endWeek: Int,
    val weekType: WeekType,
    val dayOfWeek: Int,         // 1 = 周一 … 7 = 周日
    val startPeriod: Int,       // 1 … 11
    val endPeriod: Int,
    val location: String,       // 教室地点，可为空串
    val teacher: String,        // 任课老师，可为空串
    /**
     * 导入时用了兜底值的字段。不是 PDF 里的第 6 组数据，只是「哪些值不可信」的出处记录——
     * 兜底值与用户手填的值长得一样（都是空串或 1），不记下来界面就没法标黄。
     */
    val missingFields: Set<CourseField> = emptySet(),
)

/** 其他课程。无星期与节次，故不能并入 Course。 */
@Serializable
data class OtherCourse(
    val name: String,
    val startWeek: Int,
    val endWeek: Int,           // 单周课程时与 startWeek 相等
    val totalWeeks: Int?,       // PDF 里的「共 N 周」
    val location: String,
    val teacher: String,
)

/** 作息时间，每个节次一条。 */
@Serializable
data class PeriodTime(
    val period: Int,            // 1 … 11
    val start: String,          // "08:05"，空串表示未设置
    val end: String,
)

/** 整份课表。全 App 仅一份。 */
@Serializable
data class Timetable(
    val semesterName: String,
    val firstMonday: String,    // ISO-8601，如 "2026-08-31"
    val totalWeeks: Int,
    val periods: List<PeriodTime>,
    val courses: List<Course>,
    val otherCourses: List<OtherCourse>,
)

/** 这门课在第 [week] 周是否要上。 */
fun Course.isActiveIn(week: Int): Boolean {
    if (week < startWeek || week > endWeek) return false
    return when (weekType) {
        WeekType.EVERY -> true
        WeekType.ODD -> week % 2 == 1
        WeekType.EVEN -> week % 2 == 0
    }
}

/**
 * 今天是第几周。返回 0 表示学期尚未开始。
 * 周次 = floor((今天 − 开学第一周周一) / 7) + 1
 */
fun weekOf(firstMonday: LocalDate, today: LocalDate): Int {
    val days = ChronoUnit.DAYS.between(firstMonday, today)
    if (days < 0) return 0
    return (days / 7).toInt() + 1
}

/** 默认作息时间（学校实测值）。 */
fun defaultPeriods(): List<PeriodTime> = listOf(
    PeriodTime(1, "08:05", "08:55"),
    PeriodTime(2, "09:00", "09:50"),
    PeriodTime(3, "10:05", "10:55"),
    PeriodTime(4, "11:00", "11:50"),
    PeriodTime(5, "14:10", "15:00"),
    PeriodTime(6, "15:05", "15:55"),
    PeriodTime(7, "16:05", "16:55"),
    PeriodTime(8, "17:00", "17:50"),
    PeriodTime(9, "19:00", "19:50"),
    PeriodTime(10, "19:55", "20:45"),
)

/**
 * 取某节的开始时间。
 * 以下三种情况均返回 null，界面据此显示「—」：
 * 找不到该节次、时间为空串、时间格式非法（如用户手输的 "8:05"）。
 */
fun List<PeriodTime>.startOf(period: Int): LocalTime? =
    firstOrNull { it.period == period }?.start
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

/**
 * 取某节的结束时间。
 * 与 [startOf] 一致：找不到该节次、时间为空串、时间格式非法，均返回 null。
 */
fun List<PeriodTime>.endOf(period: Int): LocalTime? =
    firstOrNull { it.period == period }?.end
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

/** 开学第一周的周一。JSON 里存的是 ISO 字符串，用到时才转成日期。 */
fun Timetable.firstMondayDate(): LocalDate = LocalDate.parse(firstMonday)

/** 第 [week] 周周一的日期。日期行与「今天」的周次都靠它算。 */
fun Timetable.weekStartDate(week: Int): LocalDate =
    firstMondayDate().plusDays(((week - 1) * 7).toLong())

/** 周类型的中文名，用于表单单选与候选课列表。 */
fun WeekType.label(): String = when (this) {
    WeekType.EVERY -> "每周"
    WeekType.ODD -> "单周"
    WeekType.EVEN -> "双周"
}

/** 开学第一周周一的默认值。PDF 里没有这一项，是用户设的。 */
const val DEFAULT_FIRST_MONDAY = "2026-08-31"

/**
 * 还没导入过课表时的空课表。学期名、开学日期、总周数、作息时间都取默认值，
 * 课程列表为空 —— 界面据此区分「还没有课表」和「今天没课」。
 */
fun defaultTimetable(): Timetable = Timetable(
    semesterName = "2026-2027 第1学期",
    firstMonday = DEFAULT_FIRST_MONDAY,
    totalWeeks = 16,
    periods = defaultPeriods(),
    courses = emptyList(),
    otherCourses = emptyList(),
)
