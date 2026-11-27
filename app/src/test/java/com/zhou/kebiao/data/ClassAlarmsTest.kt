package com.zhou.kebiao.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ClassAlarmsTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun course(
        name: String = "形势与政策（3）★",
        day: Int = 5,
        startPeriod: Int = 1,
        endPeriod: Int = 2,
        startWeek: Int = 13,
        endWeek: Int = 14,
        weekType: WeekType = WeekType.EVERY,
        location: String = "2-3-217",
    ) = Course(name, startWeek, endWeek, weekType, day, startPeriod, endPeriod, location, "欧阳文博")

    private fun timetable(vararg courses: Course, periods: List<PeriodTime> = defaultPeriods()) =
        Timetable(
            semesterName = "2026-2027 第1学期",
            firstMonday = "2026-08-31",
            totalWeeks = 16,
            periods = periods,
            courses = courses.toList(),
            otherCourses = emptyList(),
        )

    private fun now(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    @Test
    fun `提前十二分钟并带上时间段与地点`() {
        val alarms = upcomingClassAlarms(timetable(course()), now(2026, 11, 27, 7, 0))

        assertEquals(2, alarms.size) // 13、14 周各一次
        val first = alarms[0]
        assertEquals(now(2026, 11, 27, 7, 53), first.triggerAt)
        assertEquals("形势与政策（3）★", first.courseName)
        assertEquals("08:05–09:50 · 2-3-217", first.detail)
    }

    @Test
    fun `不生效的周次不排`() {
        val earlier = course(name = "国家安全教育（3）★", startWeek = 10, endWeek = 11)
        val alarms = upcomingClassAlarms(timetable(earlier, course()), now(2026, 11, 27, 7, 0))

        assertTrue(alarms.all { it.courseName.startsWith("形势与政策") })
        assertEquals(2, alarms.size)
    }

    @Test
    fun `已经过去的时刻不排`() {
        // 第 13 周周五 08:05 的课已经过去，只剩第 14 周
        val alarms = upcomingClassAlarms(timetable(course()), now(2026, 11, 27, 8, 30))
        assertEquals(1, alarms.size)
        assertEquals(now(2026, 12, 4, 7, 53), alarms[0].triggerAt)
    }

    @Test
    fun `单周只排奇数周`() {
        val odd = course(startWeek = 1, endWeek = 5, weekType = WeekType.ODD, day = 1)
        val alarms = upcomingClassAlarms(timetable(odd), now(2026, 8, 31, 0, 0))

        assertEquals(3, alarms.size) // 1、3、5 周
        assertEquals(
            listOf(
                now(2026, 8, 31, 7, 53),
                now(2026, 9, 14, 7, 53),
                now(2026, 9, 28, 7, 53),
            ),
            alarms.map { it.triggerAt },
        )
    }

    @Test
    fun `没有作息时间的节次跳过`() {
        val noTime = course(startPeriod = 11, endPeriod = 11)
        assertTrue(upcomingClassAlarms(timetable(noTime), now(2026, 11, 27, 7, 0)).isEmpty())
    }

    @Test
    fun `地点没填时写上未填`() {
        val alarms = upcomingClassAlarms(timetable(course(location = "")), now(2026, 11, 27, 7, 0))
        assertEquals("08:05–09:50 · 地点未填", alarms[0].detail)
    }
}
