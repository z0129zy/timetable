package com.zhou.kebiao.web

import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.CourseField
import com.zhou.kebiao.data.OtherCourse
import com.zhou.kebiao.data.WeekType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 正方教务学生课表接口（xskbcx_cxXsgrkb.html）返回的 JSON 解析。
 *
 * 不直接解析网页 DOM：正方把课表放在一个结构化的 JSON 里，字段名是拼音缩写
 * （kcmc/xm/cdmc/xqj/jcs/zcd）。解析它是纯函数，能用 JVM 单测覆盖；
 * 网页一改版也只需要动这里的字段映射。
 */
sealed interface KbParseResult {
    data class Success(
        val courses: List<Course>,
        val otherCourses: List<OtherCourse>,
    ) : KbParseResult

    data class Failure(val reason: String) : KbParseResult
}

object KbJsonParser {

    private data class WeekSpan(val start: Int, val end: Int, val type: WeekType)

    fun parse(raw: String): KbParseResult {
        val root = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
            ?: return KbParseResult.Failure("教务系统返回的不是课表数据")

        val lists = findCourseLists(root)
            ?: return KbParseResult.Failure("没找到课表数据，可能登录已过期")

        val courses = mutableListOf<Course>()
        for (item in lists.kbList) {
            val obj = item as? JsonObject ?: continue
            val name = obj.str("kcmc", "kcname", "courseName")
            if (name.isBlank()) continue
            val day = parseDay(obj.str("xqj", "xq", "weekday", "xqjmc")) ?: continue
            val periods = parsePeriods(obj.str("jcs", "jc", "periods", "jcor")) ?: continue
            val weeks = parseWeeks(obj.str("zcd", "zc", "weeks", "zcmc"))
            if (weeks.isEmpty()) continue

            val location = obj.str("cdmc", "dd", "location")
            val teacher = obj.str("xm", "jsxm", "teacher")
            val missing = buildSet {
                if (location.isEmpty()) add(CourseField.LOCATION)
                if (teacher.isEmpty()) add(CourseField.TEACHER)
            }
            for (w in weeks) {
                courses += Course(
                    name = name,
                    startWeek = w.start,
                    endWeek = w.end,
                    weekType = w.type,
                    dayOfWeek = day,
                    startPeriod = periods.first,
                    endPeriod = periods.second,
                    location = location,
                    teacher = teacher,
                    missingFields = missing,
                )
            }
        }

        val others = lists.sjkList.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj.str("kcmc", "kcname", "courseName")
            val weeks = parseWeeks(obj.str("zcd", "zc", "weeks", "zcmc"))
            if (name.isBlank() || weeks.isEmpty()) return@mapNotNull null
            OtherCourse(
                name = name,
                startWeek = weeks.first().start,
                endWeek = weeks.first().end,
                totalWeeks = null,
                location = obj.str("cdmc", "dd", "location"),
                teacher = obj.str("xm", "jsxm", "teacher"),
            )
        }.distinct()

        val distinct = courses.distinct()
        if (distinct.isEmpty()) {
            return KbParseResult.Failure("没有从教务系统识别出课程")
        }
        return KbParseResult.Success(distinct, others)
    }

    // ---------- 字段提取 ----------

    private class CourseLists(val kbList: List<JsonElement>, val sjkList: List<JsonElement>)

    private fun findCourseLists(root: JsonElement): CourseLists? {
        val containers = listOfNotNull(
            root as? JsonObject,
            (root as? JsonObject)?.get("data") as? JsonObject,
        )
        for (obj in containers) {
            val kb = asArray(obj["kbList"]) ?: continue
            val sjk = asArray(obj["sjkList"]).orEmpty()
            return CourseLists(kb, sjk)
        }
        return null
    }

    /** 接口偶尔会把数组再包成字符串，这里两种都吃。 */
    private fun asArray(element: JsonElement?): List<JsonElement>? = when (element) {
        is JsonArray -> element
        is JsonPrimitive -> runCatching {
            Json.parseToJsonElement(element.content) as? JsonArray
        }.getOrNull()
        else -> null
    }

    private fun JsonObject.str(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
        }.orEmpty()

    // ---------- 节次 / 星期 ----------

    private fun parsePeriods(raw: String): Pair<Int, Int>? {
        val numbers = REGEX_NUMBER.findAll(raw).map { it.value.toInt() }.toList()
        if (numbers.isEmpty()) return null
        if (numbers.size == 1) return numbers[0] to numbers[0]
        val a = numbers[0]
        val b = numbers[1]
        return minOf(a, b) to maxOf(a, b)
    }

    private fun parseDay(raw: String): Int? {
        val number = REGEX_NUMBER.find(raw)?.value?.toIntOrNull()
        if (number != null) return number.takeIf { it in 1..7 }
        val index = WEEKDAY_CHARS.indexOfFirst { raw.contains(it) }
        return if (index >= 0) index + 1 else null
    }

    // ---------- 周次 ----------

    private fun parseWeeks(raw: String): List<WeekSpan> {
        if (raw.isBlank()) return emptyList()
        val text = raw.replace('（', '(').replace('）', ')')
        val type = when {
            REGEX_ODD.containsMatchIn(text) || text.contains("单周") -> WeekType.ODD
            REGEX_EVEN.containsMatchIn(text) || text.contains("双周") -> WeekType.EVEN
            else -> WeekType.EVERY
        }
        val ranges = REGEX_RANGE.findAll(text).map { match ->
            val a = match.groupValues[1].toInt()
            val b = match.groupValues[2].toInt()
            WeekSpan(minOf(a, b), maxOf(a, b), type)
        }.toList()
        if (ranges.isNotEmpty()) return ranges
        // 只有「19周」这种单周写法
        return REGEX_NUMBER.findAll(text).map { WeekSpan(it.value.toInt(), it.value.toInt(), type) }.toList()
    }

    private val REGEX_NUMBER = Regex("""\d+""")
    private val REGEX_RANGE = Regex("""(\d+)\s*[-—－~至]\s*(\d+)""")
    private val REGEX_ODD = Regex("""\(\s*单\s*\)""")
    private val REGEX_EVEN = Regex("""\(\s*双\s*\)""")
    private val WEEKDAY_CHARS = listOf("一", "二", "三", "四", "五", "六", "日")
}
