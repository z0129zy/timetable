package com.zhou.kebiao.parser

import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.CourseField
import com.zhou.kebiao.data.WeekType
import kotlin.math.abs

/**
 * 课表解析内核。纯函数，不碰 PDFBox 也不碰 Android，因此可以完整单元测试。
 *
 * 流水线（见设计文档 §6）：
 *   1. 从表头标定 7 个星期列中心
 *   2. 把每个文字块吸附到最近的列
 *   3. 每列内按 (页序, y) 排序，相邻拼接 —— 解决跨页截断
 *   4. 按字号切分课程名与正文 —— 课程名可能折行
 *   5. 从正文正则解析 5 组字段
 */
class TimetableParser {

    companion object {
        /** 字号差超过这个值就认为不是同一类文字（课程名 vs 正文）。 */
        private const val SIZE_EPSILON = 0.4f

        private val DAY_LABELS = listOf(
            "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日",
        )

        private val PERIOD_RANGE = Regex("""\((\d+)-(\d+)节\)""")
        private val WEEK_RANGE = Regex("""\((\d+)-(\d+)节\)(\d+)-(\d+)周(\((单|双)\))?""")
        private val LOCATION = Regex("""/场地:(.*?)/""")
        private val TEACHER = Regex("""/教师:(.*?)/""")
    }

    // ---------- 第 1 步：标定星期列 ----------

    /** 从表头文字标定 7 个列中心 x。辨认不出 7 个就抛异常。 */
    fun calibrateColumns(fragments: List<TextFragment>): List<Float> {
        val centers = DAY_LABELS.map { label ->
            val f = fragments.firstOrNull { it.text.trim() == label }
                ?: throw IllegalStateException("找不到表头：$label")
            f.x + f.fontSize * label.length / 2f   // 近似文字中心
        }
        return centers.sorted()
    }

    /**
     * 每一页的表头行 y：该页所有「星期N」文字里最靠上的那个。
     * 表头行本身以及它上方的标题、页眉都要裁掉。
     */
    private fun headerRows(fragments: List<TextFragment>): Map<Int, Float> =
        fragments
            .filter { it.text.trim() in DAY_LABELS }
            .groupBy { it.pageIndex }
            .mapValues { (_, list) -> list.minOf { it.y } }

    /** 文字块属于第几个星期列（1..7）。超出容差返回 0（不属于任何列）。 */
    fun columnOf(x: Float, columns: List<Float>): Int {
        val half = columnSpacing(columns) / 2f
        var best = 0
        var bestDist = Float.MAX_VALUE
        columns.forEachIndexed { i, c ->
            val d = abs(x - c)
            if (d < bestDist) {
                bestDist = d
                best = i + 1
            }
        }
        return if (bestDist <= half) best else 0
    }

    private fun columnSpacing(columns: List<Float>): Float =
        if (columns.size < 2) Float.MAX_VALUE
        else (columns.last() - columns.first()) / (columns.size - 1)

    // ---------- 入口 ----------

    fun parse(fragments: List<TextFragment>): ParseResult {
        if (fragments.isEmpty()) {
            return ParseResult.Failure("这个文件里没有可读的文字")
        }

        val columns = try {
            calibrateColumns(fragments)
        } catch (e: IllegalStateException) {
            return ParseResult.Failure("这份课表认不出来，可能是别的学校的模板")
        }

        val header = parseHeader(fragments)
        val courses = parseCourses(fragments, columns)
        val others = OtherCourseParser.parse(fragments)

        // 一门网格课程都没有，就不是一份能用的课表 —— 调用方据此拒绝写入，
        // 避免把空数据存进去抹掉用户已有的课表（设计文档 §8.1）
        if (courses.isEmpty()) {
            return ParseResult.Failure("这份课表认不出来，可能是别的学校的模板")
        }

        return ParseResult.Success(header, courses, others)
    }

    private fun parseHeader(fragments: List<TextFragment>): PdfHeader {
        val semester = fragments.firstOrNull {
            Regex("""\d{4}-\d{4}学年第\d学期""").containsMatchIn(it.text)
        }?.text?.trim().orEmpty()

        val name = fragments.firstOrNull {
            it.fontSize > 20f && it.text.endsWith("课表")
        }?.text?.trim()?.removeSuffix("课表").orEmpty()

        val id = fragments.firstOrNull {
            it.text.contains("学号")
        }?.text?.let { Regex("""(\d{6,})""").find(it)?.groupValues?.get(1) }.orEmpty()

        return PdfHeader(semester, name, id)
    }

    // ---------- 第 2~3 步：归列 + 跨页拼接 ----------

    /**
     * 取出属于星期列的文字块，按 (列, 页序, y) 排好序。
     *
     * 两道防护，都是实打实踩出来的：
     *  - **裁掉每一页表头行及其上方的文字**。PDF 标题「李明轩课表」的 x 恰好落在「周三」列，
     *    页眉「学号：…」落在「周六」列，表头「星期一」自己就落在「周一」列；不裁的话
     *    它们会被当成课程名，粘到对应列的第一门课上（实测「星期一」就是这么混进来的）。
     *    按页裁而不是全局裁：跨页溢出的续文可能出现在下一页表头**之上**（见 crossPage 夹具）。
     *  - **只收吸附得上列的文字**。第 3 页底部的「其他课程」区块 x=18，不属于任何列，自然排除。
     */
    private fun byColumn(fragments: List<TextFragment>, columns: List<Float>): Map<Int, List<TextFragment>> {
        val headerRows = headerRows(fragments)
        return fragments
            .filter { f -> headerRows[f.pageIndex]?.let { f.y > it } ?: true }
            .mapNotNull { f ->
                val col = columnOf(f.x, columns)
                if (col == 0) null else col to f
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> list.sortedWith(compareBy({ it.pageIndex }, { it.y })) }
    }

    // ---------- 第 4~5 步：切课程 + 解字段 ----------

    private fun parseCourses(fragments: List<TextFragment>, columns: List<Float>): List<Course> {
        val out = mutableListOf<Course>()
        for ((dayOfWeek, list) in byColumn(fragments, columns)) {
            // 正文字号 = 这一列里**最小**的字号，因为课程名一定比正文大。
            // 不能用「出现次数最多的字号」—— 遇上课名折成 3 行、正文只有 2 行的
            // 「毛泽东思想和中国特色社会主义理论体系概论★」，众数会落在课程名上，
            // 整门课就会被整段吞掉。
            val bodySize = list.minOf { it.fontSize }
            val nameThreshold = bodySize + SIZE_EPSILON

            var nameParts = mutableListOf<String>()
            var body = StringBuilder()

            fun reset() {
                nameParts = mutableListOf()
                body = StringBuilder()
            }

            fun flush() {
                val full = body.toString()
                // 正文不像课程就丢掉，挡掉落进列的杂项（如右下的「打印时间:…」）。
                if (!looksLikeCourse(full)) {
                    reset()
                    return
                }
                out += buildCourse(nameParts.joinToString(""), full, dayOfWeek)
                reset()
            }

            for (f in list) {
                if (f.fontSize > nameThreshold) {
                    // 只有当课程已有正文时，新的名行才代表下一门课；否则是同一课程名的折行
                    if (body.isNotEmpty()) flush()
                    nameParts += f.text
                } else {
                    body.append(f.text)
                }
            }
            flush()
        }
        return out
    }

    /**
     * 正文是否像一门课的正文：有节次、场地、教师三者之一。
     *
     * 不能只看 `(N-M节)`：那一小段缺失时整门课会被无声丢掉，违反设计文档 §5.3
     * 「不允许因缺时间而丢弃课程」。场地与教师标记是「这是真课程」更可靠的证据，
     * 而「打印时间:2026-09-03」这类杂项三者皆无，照样被挡住。
     */
    private fun looksLikeCourse(body: String): Boolean =
        PERIOD_RANGE.containsMatchIn(body) ||
            LOCATION.containsMatchIn(body) ||
            TEACHER.containsMatchIn(body)

    private fun buildCourse(name: String, body: String, dayOfWeek: Int): Course {
        val period = PERIOD_RANGE.find(body)
        val week = WEEK_RANGE.find(body)
        val location = LOCATION.find(body)?.groupValues?.get(1).orEmpty()
        val teacher = TEACHER.find(body)?.groupValues?.get(1).orEmpty()
        val weekType = when (week?.groupValues?.get(6)) {
            "单" -> WeekType.ODD
            "双" -> WeekType.EVEN
            else -> WeekType.EVERY
        }
        return Course(
            name = name.ifBlank { "未知课程" },   // 认不出课名也要保住这门课
            startWeek = week?.groupValues?.get(3)?.toIntOrNull() ?: 1,
            endWeek = week?.groupValues?.get(4)?.toIntOrNull() ?: 16,
            weekType = weekType,
            dayOfWeek = dayOfWeek,
            startPeriod = period?.groupValues?.get(1)?.toIntOrNull() ?: 1,
            endPeriod = period?.groupValues?.get(2)?.toIntOrNull() ?: 1,
            location = location,
            teacher = teacher,
            missingFields = buildSet {
                if (name.isBlank()) add(CourseField.NAME)
                if (week == null) add(CourseField.WEEKS)
                if (period == null) add(CourseField.PERIODS)
                if (location.isEmpty()) add(CourseField.LOCATION)
                if (teacher.isEmpty()) add(CourseField.TEACHER)
            },
        )
    }
}
