package com.zhou.kebiao.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class OtherCourseParserTest {

    private val parsed = OtherCourseParser.parse(SamplePdfText.otherCourseBlock)

    @Test
    fun `解析出全部 7 条`() {
        assertEquals(7, parsed.size)
    }

    @Test
    fun `第一条的字段完全正确`() {
        val c = parsed[0]
        assertEquals("电路基础实验☆", c.name)   // 类型符号并入名称
        assertEquals("何建军", c.teacher)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
        assertEquals(16, c.totalWeeks)
        assertEquals("无", c.location)
    }

    @Test
    fun `单周课程的起止周相等`() {
        val design = parsed.first { it.name == "电子产品设计■" }
        assertEquals(19, design.startWeek)
        assertEquals(19, design.endWeek)
        assertEquals(1, design.totalWeeks)
        assertEquals("黄远,徐雯", design.teacher)
    }

    @Test
    fun `多人教师保留逗号分隔`() {
        val lecture = parsed.first { it.name == "专业前沿讲座■" }
        assertEquals("罗华,谢天成,韩雷,白鸿飞", lecture.teacher)
        assertEquals(18, lecture.startWeek)
    }

    @Test
    fun `课程名里的半角数字括号不会被当成共N周误切`() {
        // 「大学劳动教育(2)★」里的 (2) 是课程名的一部分，不是 (共N周)
        val labor = parsed.first { it.name.startsWith("大学劳动教育") }
        assertEquals("大学劳动教育(2)★", labor.name)
        assertEquals("沈佳慧", labor.teacher)
        assertEquals(12, labor.startWeek)
        assertEquals(13, labor.endWeek)
    }

    @Test
    fun `跨两行的区块能正确拼接`() {
        // 第 7 条在第 2 行开头，如果拼接失败就会丢
        val last = parsed.first { it.name == "工程应用软件实践■" }
        assertEquals(20, last.startWeek)
        assertEquals("徐雯,曹远", last.teacher)
    }
}
