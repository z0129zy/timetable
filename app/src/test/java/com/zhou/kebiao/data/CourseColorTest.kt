package com.zhou.kebiao.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseColorTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `自定义颜色能写入并读回`() {
        val course = Course(
            name = "测试课",
            startWeek = 1,
            endWeek = 16,
            weekType = WeekType.EVERY,
            dayOfWeek = 1,
            startPeriod = 1,
            endPeriod = 2,
            location = "2-3-305",
            teacher = "老师",
            customColor = 0xFFCFDFEA.toInt(),
        )
        val raw = json.encodeToString(defaultTimetable().copy(courses = listOf(course)))
        val back = json.decodeFromString<Timetable>(raw)
        assertEquals(course, back.courses.single())
    }

    @Test
    fun `旧课表没有颜色字段时默认自动`() {
        val raw = """
            {"semesterName":"s","firstMonday":"2026-08-31","totalWeeks":16,
             "periods":[],"otherCourses":[],
             "courses":[{"name":"课","startWeek":1,"endWeek":16,"weekType":"EVERY",
             "dayOfWeek":1,"startPeriod":1,"endPeriod":2,"location":"","teacher":""}]}
        """.trimIndent()
        val timetable = json.decodeFromString<Timetable>(raw)
        assertNull(timetable.courses.single().customColor)
    }
}
