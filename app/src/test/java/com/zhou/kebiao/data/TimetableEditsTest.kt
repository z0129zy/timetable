package com.zhou.kebiao.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TimetableEditsTest {

    private fun course(
        name: String = "电路★",
        day: Int = 3,
        startPeriod: Int = 1,
        endPeriod: Int = 2,
        startWeek: Int = 1,
        endWeek: Int = 16,
    ) = Course(
        name = name,
        startWeek = startWeek,
        endWeek = endWeek,
        weekType = WeekType.EVERY,
        dayOfWeek = day,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        location = "2-3-301",
        teacher = "何建军",
    )

    private val base = defaultTimetable().withCourseAdded(course()).withCourseAdded(course(name = "大学英语(3)★"))

    @Test
    fun `新增课程追加到末尾`() {
        val added = base.withCourseAdded(course(name = "物理实验(2)☆"))
        assertEquals(listOf("电路★", "大学英语(3)★", "物理实验(2)☆"), added.courses.map { it.name })
    }

    @Test
    fun `替换只影响指定下标`() {
        val replaced = base.withCourseReplaced(0, course(name = "换过的课★"))
        assertEquals(listOf("换过的课★", "大学英语(3)★"), replaced.courses.map { it.name })
    }

    @Test
    fun `删除只删指定下标`() {
        val removed = base.withCourseRemoved(0)
        assertEquals(listOf("大学英语(3)★"), removed.courses.map { it.name })
    }

    @Test
    fun `改已有节次的作息时间`() {
        val next = base.withPeriodTime(1, "08:10", "09:00")
        val first = next.periods.first { it.period == 1 }
        assertEquals("08:10", first.start)
        assertEquals("09:00", first.end)
        assertEquals(base.periods.size, next.periods.size)
    }

    @Test
    fun `原本没有的节次会补一条并且作息仍按节次有序`() {
        val withoutEleven = base.copy(periods = base.periods.filter { it.period != 11 })
        val next = withoutEleven.withPeriodTime(11, "21:00", "21:50")
        assertEquals(11, next.periods.last().period)
        assertEquals("21:00", next.periods.last().start)
        assertEquals(next.periods.sortedBy { it.period }, next.periods)
    }

    @Test
    fun `学期级设置各自独立更新`() {
        val next = base.withSemesterName("2027-2028 第1学期").withTotalWeeks(18).withFirstMonday("2027-08-30")
        assertEquals("2027-2028 第1学期", next.semesterName)
        assertEquals(18, next.totalWeeks)
        assertEquals("2027-08-30", next.firstMonday)
        assertEquals(base.courses, next.courses)   // 不动课程
    }

    @Test
    fun `开学日期能算成日期与每周周一`() {
        assertEquals(LocalDate.of(2026, 8, 31), base.firstMondayDate())
        assertEquals(LocalDate.of(2026, 8, 31), base.weekStartDate(1))
        assertEquals(LocalDate.of(2026, 9, 14), base.weekStartDate(3))
    }

    @Test
    fun `校验拦下课程名为空`() {
        assertEquals("课程名称不能为空", validateCourse(course(name = "  "), 16))
    }

    @Test
    fun `校验拦下周次越界`() {
        assertEquals("周次要填第 1 到第 16 周", validateCourse(course(endWeek = 17), 16))
        assertEquals("周次要填第 1 到第 16 周", validateCourse(course(startWeek = 0), 16))
    }

    @Test
    fun `校验拦下起始周晚于结束周`() {
        assertEquals("起始周不能晚于结束周", validateCourse(course(startWeek = 5, endWeek = 3), 16))
    }

    @Test
    fun `校验拦下节次越界与起始节晚于结束节`() {
        assertEquals("节次要填第 1 到第 11 节", validateCourse(course(startPeriod = 1, endPeriod = 12), 16))
        assertEquals("起始节不能晚于结束节", validateCourse(course(startPeriod = 6, endPeriod = 4), 16))
    }

    @Test
    fun `教室与老师留空不算错`() {
        val c = course().copy(location = "", teacher = "")
        assertNull(validateCourse(c, 16))
    }

    @Test
    fun `合法课程通过校验`() {
        assertNull(validateCourse(course(), 16))
        // 边界值本身是合法的
        assertNull(validateCourse(course(startWeek = 16, endWeek = 16), 16))
        assertNull(validateCourse(course(startPeriod = 1, endPeriod = 11), 16))
    }

    @Test
    fun `总周数为 0 的课表会被夹回 1 周`() {
        // 手改文件、或被旧版本/异常写入，都可能让磁盘上的 totalWeeks 变成 0。
        // 0 会让 AppState 启动路径上的 coerceIn(1, totalWeeks) 抛异常 ——
        // App 一打开就崩，界面里还救不回来。所以读进来必须先夹一次。
        assertEquals(1, base.copy(totalWeeks = 0).normalized().totalWeeks)
        assertEquals(1, base.copy(totalWeeks = -5).normalized().totalWeeks)
    }

    @Test
    fun `合法课表归一化后一个字节都不变`() {
        assertEquals(base, base.normalized())
        assertEquals(16, base.normalized().totalWeeks)
        assertEquals("2026-08-31", base.normalized().firstMonday)
    }

    @Test
    fun `开学日期烂掉时回退到默认值`() {
        // 启动路径上会 LocalDate.parse(firstMonday)，格式不对就抛异常 ——
        // App 一打开就崩，界面里救不回来
        assertEquals("2026-08-31", base.copy(firstMonday = "2026/08/31").normalized().firstMonday)
        assertEquals("2026-08-31", base.copy(firstMonday = "").normalized().firstMonday)
        assertEquals("2026-08-31", base.copy(firstMonday = "八月三十一日").normalized().firstMonday)
        // 换一个合法的开学日期不该被改
        assertEquals("2027-03-01", base.copy(firstMonday = "2027-03-01").normalized().firstMonday)
    }

    @Test
    fun `校验拦下星期越界`() {
        assertEquals("星期要在周一到周日之间", validateCourse(course(day = 0), 16))
        assertEquals("星期要在周一到周日之间", validateCourse(course(day = 8), 16))
        assertNull(validateCourse(course(day = 7), 16))
    }

    @Test
    fun `新增课程的预填值`() {
        // 设计文档 §7.5：周次铺满整学期、每周；星期与节次用所点的格子；其余留空。
        val prefill = newCoursePrefill(day = 3, startPeriod = 5, endPeriod = 6, totalWeeks = 16)
        assertEquals("", prefill.name)
        assertEquals(1, prefill.startWeek)
        assertEquals(16, prefill.endWeek)
        assertEquals(WeekType.EVERY, prefill.weekType)
        assertEquals(3, prefill.dayOfWeek)
        assertEquals(5, prefill.startPeriod)
        assertEquals(6, prefill.endPeriod)
        assertEquals("", prefill.location)
        assertEquals("", prefill.teacher)
        assertEquals(emptySet<CourseField>(), prefill.missingFields)
    }
}
