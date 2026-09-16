package com.zhou.kebiao.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableParserIntegrationTest {

    private val parser = TimetableParser()

    /** 把夹具的全部片段合起来，模拟真实 PDF 的输出。 */
    private fun fullSample() = listOf(
        SamplePdfText.pdfHeader,
        SamplePdfText.header,
        SamplePdfText.monday,
        SamplePdfText.wrappedName,
        SamplePdfText.crossPage,
        SamplePdfText.otherCourseBlock,
    ).flatten().sortedWith(compareBy({ it.pageIndex }, { it.y }))

    @Test
    fun `整体解析成功`() {
        val result = parser.parse(fullSample())
        assertTrue("解析应成功，实际 $result", result is ParseResult.Success)
    }

    @Test
    fun `页眉信息被读出来`() {
        val h = (parser.parse(fullSample()) as ParseResult.Success).header
        assertEquals("2026-2027学年第1学期", h.semesterName)
        assertEquals("李明轩", h.studentName)
        assertEquals("25406070399", h.studentId)
    }

    @Test
    fun `网格课程与其他课程分别落到两个列表`() {
        val r = parser.parse(fullSample()) as ParseResult.Success
        assertTrue("网格课程不该为空", r.courses.isNotEmpty())
        assertTrue("其他课程不该为空", r.otherCourses.isNotEmpty())
        // 其他课程没有星期与节次，绝不能混进网格课程列表
        assertTrue(r.courses.none { it.name == "电路基础实验☆" })
        assertTrue(r.otherCourses.none { it.name.contains("大学英语") })
    }

    @Test
    fun `其他课程条数正确`() {
        val r = parser.parse(fullSample()) as ParseResult.Success
        assertEquals(7, r.otherCourses.size)
    }
}
