package com.zhou.kebiao.ui

import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.OtherCourse
import com.zhou.kebiao.data.WeekType
import com.zhou.kebiao.data.defaultPeriods
import com.zhou.kebiao.data.defaultTimetable
import com.zhou.kebiao.data.withCourseAdded
import com.zhou.kebiao.data.withPeriodTime
import com.zhou.kebiao.parser.ParseResult
import com.zhou.kebiao.parser.PdfHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportOutcomeTest {

    private fun course(name: String) = Course(
        name = name,
        startWeek = 1,
        endWeek = 16,
        weekType = WeekType.EVERY,
        dayOfWeek = 1,
        startPeriod = 5,
        endPeriod = 6,
        location = "2-1-313",
        teacher = "王淑芬",
    )

    private val other = OtherCourse(
        name = "电路基础实验☆",
        startWeek = 1,
        endWeek = 16,
        totalWeeks = 16,
        location = "无",
        teacher = "何建军",
    )

    /**
     * 用户已经改过设置、也已经有课的现有课表。
     * 三样设置**都要真的与默认值不同**，否则「导入不动设置」的断言是空的
     * —— 把 periods 留在默认值上，「next.periods == existing.periods」就只等价于
     * 「next.periods 还是默认值」，实现里手滑写成 periods = defaultPeriods() 也照样绿。
     */
    private val existing = defaultTimetable()
        .withCourseAdded(course("旧课★"))
        .withPeriodTime(1, "08:20", "09:10")
        .copy(
            semesterName = "用户自己改的学期名",
            firstMonday = "2026-08-24",
            totalWeeks = 18,
        )

    private fun success(
        semesterName: String = "2026-2027 第1学期",
        courses: List<Course> = listOf(course("新课★")),
        others: List<OtherCourse> = listOf(other),
    ) = ParseResult.Success(PdfHeader(semesterName = semesterName), courses, others)

    @Test
    fun `解析失败时拒绝写入并原样带上原因`() {
        val outcome = existing.afterImport(ParseResult.Failure("这份课表认不出来，可能是别的学校的模板"))
        assertEquals(
            ImportOutcome.Rejected("这份课表认不出来，可能是别的学校的模板"),
            outcome,
        )
    }

    @Test
    fun `解析出 0 门课时拒绝写入`() {
        // 设计文档 §8.1 的底线：宁可什么都不做，也不能把空数据写进去抹掉用户的课表。
        val outcome = existing.afterImport(success(courses = emptyList()))
        assertEquals(
            ImportOutcome.Rejected("这份课表认不出来，可能是别的学校的模板"),
            outcome,
        )
    }

    @Test
    fun `导入成功时替换课程与其他课程`() {
        val outcome = existing.afterImport(success())
        assertTrue(outcome is ImportOutcome.Applied)
        val next = (outcome as ImportOutcome.Applied).timetable
        assertEquals(listOf("新课★"), next.courses.map { it.name })
        assertEquals(listOf(other), next.otherCourses)
    }

    @Test
    fun `导入不动用户改过的开学日期总周数与作息时间`() {
        // PDF 里没有这三样，导入不该把它们冲回默认值。
        // 先证明夹具里的值确实偏离默认 —— 否则下面的断言测不出任何东西。
        assertNotEquals(defaultTimetable().firstMonday, existing.firstMonday)
        assertNotEquals(defaultTimetable().totalWeeks, existing.totalWeeks)
        assertNotEquals(
            defaultPeriods().first { it.period == 1 },
            existing.periods.first { it.period == 1 },
        )

        val next = (existing.afterImport(success()) as ImportOutcome.Applied).timetable
        assertEquals("2026-08-24", next.firstMonday)
        assertEquals(18, next.totalWeeks)
        assertEquals(existing.periods, next.periods)
        assertEquals("08:20", next.periods.first { it.period == 1 }.start)
    }

    @Test
    fun `页眉有学期名时采用页眉的`() {
        val next = (existing.afterImport(success(semesterName = "2026-2027 第1学期")) as ImportOutcome.Applied).timetable
        assertEquals("2026-2027 第1学期", next.semesterName)
    }

    @Test
    fun `页眉读不出学期名时保留用户原来的`() {
        val next = (existing.afterImport(success(semesterName = "")) as ImportOutcome.Applied).timetable
        assertEquals("用户自己改的学期名", next.semesterName)
    }
}
