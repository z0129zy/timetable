package com.zhou.kebiao.parser

import com.zhou.kebiao.parser.SamplePdfText.frag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableParserEdgeCaseTest {

    private val parser = TimetableParser()

    private fun parse(vararg groups: List<TextFragment>) =
        parser.parse(groups.flatMap { it }) as ParseResult.Success

    @Test
    fun `折成四行的长课程名必须合并成一门课`() {
        val result = parse(SamplePdfText.header, SamplePdfText.wrappedName)

        val mao = result.courses.filter { it.name.contains("毛泽东") }
        assertEquals("毛泽东思想必须合并成 1 门，实际 ${mao.size} 门", 1, mao.size)

        // ★ 是课程名的最后一行，也要并进去，不能被当成独立课程
        assertEquals("毛泽东思想和中国特色社会主义理论体系概论★", mao[0].name)
        assertEquals(5, mao[0].dayOfWeek)
        assertEquals(7, mao[0].startPeriod)
        assertEquals(8, mao[0].endPeriod)
        assertEquals("2-3-313", mao[0].location)
        assertEquals("刘强", mao[0].teacher)
    }

    @Test
    fun `折行课程与相邻课程不会互相污染`() {
        val result = parse(SamplePdfText.header, SamplePdfText.wrappedName)
        // 周四那门独立课程应当完好
        val prob = result.courses.first { it.name == "概率论与数理统计B★" && it.dayOfWeek == 4 }
        assertEquals(1, prob.startWeek)
        assertEquals(16, prob.endWeek)
        assertEquals("2-3-305", prob.location)
    }

    @Test
    fun `跨页截断的课程能拼回完整信息`() {
        // 第 0 页给了课程名和周次，第 1 页同一列同一 x 处续上考核方式等
        val result = parse(SamplePdfText.header, SamplePdfText.crossPage)

        val courses = result.courses.filter {
            it.dayOfWeek == 4 && it.startPeriod == 5
        }
        assertEquals("跨页的两半必须拼成一门课，实际 ${courses.size} 门", 1, courses.size)

        val c = courses[0]
        assertEquals("机械设计基础★", c.name)
        assertEquals(1, c.startWeek)
        assertEquals(7, c.endWeek)
        assertEquals("2-3-305", c.location)
        assertEquals("赵鹏", c.teacher)
    }

    @Test
    fun `PDF 标题与打印时间不会被误判成课程`() {
        // 标题「李明轩课表」(x=361) 落在周三列，且字号 24 明显大于课程名，
        // 不做防护就会被当成周三第一门课的名字；「打印时间:…」(x=751.5) 落在周日列。
        val result = parse(SamplePdfText.header, SamplePdfText.monday, SamplePdfText.noise)

        assertTrue("周三不该凭空多出课程", result.courses.none { it.dayOfWeek == 3 })
        assertTrue("周日不该凭空多出课程", result.courses.none { it.dayOfWeek == 7 })
        assertTrue("课名不该被标题污染", result.courses.none { it.name.contains("李明轩") })
        assertTrue("课名不该被打印时间污染", result.courses.none { it.name.contains("打印时间") })
        assertTrue("学号不该变成课程", result.courses.none { it.name.contains("学号") })

        // 周一那 3 门必须完好无损
        assertEquals(3, result.courses.count { it.dayOfWeek == 1 })
        assertEquals("大学英语(3)★", result.courses.first { it.dayOfWeek == 1 }.name)
    }

    @Test
    fun `正文里没有节次的信息不会被当成课程`() {
        val junk = listOf(
            frag(0, 104.1f, 386.6f, 8f, "某段没有节次标记的文字"),
            frag(0, 104.1f, 399.5f, 8f, "继续一段无关内容"),
        )
        val result = parser.parse(SamplePdfText.header + junk)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `认不出表头时返回失败而不是崩溃`() {
        val notATimetable = listOf(
            TextFragment(0, 10f, 10f, 8f, "这是一份普通的 PDF 文档"),
            TextFragment(0, 10f, 20f, 8f, "和课表没有任何关系"),
        )
        val result = parser.parse(notATimetable)

        assertTrue(result is ParseResult.Failure)
        assertEquals("这份课表认不出来，可能是别的学校的模板",
            (result as ParseResult.Failure).reason)
    }

    @Test
    fun `空输入返回失败`() {
        assertTrue(parser.parse(emptyList()) is ParseResult.Failure)
    }
}
