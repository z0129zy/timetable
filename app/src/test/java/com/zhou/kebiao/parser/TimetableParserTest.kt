package com.zhou.kebiao.parser

import com.zhou.kebiao.data.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableParserTest {

    private val parser = TimetableParser()

    private fun parse(vararg groups: List<TextFragment>) =
        parser.parse(groups.flatMap { it })

    @Test
    fun `标定出 7 个星期列`() {
        val columns = parser.calibrateColumns(SamplePdfText.header)
        assertEquals(7, columns.size)
        // 列中心应落在表头文字的中心
        assertEquals(151.0f, columns[0], 0.5f)
        assertEquals(254.9f, columns[1], 0.5f)
        assertEquals(774.1f, columns[6], 0.5f)
    }

    @Test
    fun `把文字块归到正确的星期列`() {
        val columns = parser.calibrateColumns(SamplePdfText.header)
        // 周一列（中心 151）里的课程文字 x=104.1，距中心 46.9，应归周一
        assertEquals(1, parser.columnOf(104.1f, columns))
        // 周四列（中心 462.5）里的课程文字 x=415.6
        assertEquals(4, parser.columnOf(415.6f, columns))
        // 周五列（中心 566.4）里的课程文字 x=519.5
        assertEquals(5, parser.columnOf(519.5f, columns))
    }

    @Test
    fun `解析出周一的课程并带上正确的星期`() {
        val result = parse(SamplePdfText.header, SamplePdfText.monday)
        assertTrue(result is ParseResult.Success)
        val courses = (result as ParseResult.Success).courses

        val english = courses.first { it.name == "大学英语(3)★" }
        assertEquals(1, english.dayOfWeek)
        assertEquals(3, english.startPeriod)
        assertEquals(4, english.endPeriod)
        assertEquals(1, english.startWeek)
        assertEquals(16, english.endWeek)
        assertEquals(WeekType.EVERY, english.weekType)
        assertEquals("2-1-313", english.location)
        assertEquals("王淑芬", english.teacher)
    }

    @Test
    fun `同一时段单双周两门课都解析出来且不合并`() {
        val result = parse(SamplePdfText.header, SamplePdfText.monday) as ParseResult.Success
        val slot = result.courses.filter {
            it.dayOfWeek == 1 && it.startPeriod == 5 && it.endPeriod == 6
        }
        assertEquals(2, slot.size)

        val odd = slot.first { it.name == "大学英语(3)☆" }
        assertEquals(WeekType.ODD, odd.weekType)
        assertEquals(1, odd.startWeek)
        assertEquals(15, odd.endWeek)

        val even = slot.first { it.name == "概率论与数理统计B★" }
        assertEquals(WeekType.EVEN, even.weekType)
        assertEquals(2, even.startWeek)
        assertEquals(16, even.endWeek)
    }
}
