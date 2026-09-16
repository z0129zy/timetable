package com.zhou.kebiao.parser

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真实样本 PDF 跑通整条流水线。
 * 夹具测试证明了「给定这些坐标，算法能还原课表」；
 * 这个测试证明「PDFBox 给出的坐标确实长这样」。
 *
 * 两个 context 各有用途，不能混：
 *  - [appContext]（targetContext，被测 App）给 `PDFBoxResourceLoader.init` 用。
 *    测试 APK 的 context 其 `getApplicationContext()` 是 null，传进去会 NPE。
 *  - [testAssets]（instrumentation 自己的 context）读样本 PDF。
 *    样本放在 `src/androidTest/assets`，只进测试 APK，不进正式包（含姓名学号）。
 */
class SamplePdfInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val appContext = instrumentation.targetContext
    private val testAssets = instrumentation.context.assets

    private fun parseSample(): ParseResult {
        val input = testAssets.open("sample-timetable.pdf")
        val fragments = PdfTextExtractor(appContext).extract(input)
        return TimetableParser().parse(fragments)
    }

    @Test
    fun `样本PDF能成功解析`() {
        val result = parseSample()
        assertTrue("应解析成功，实际 $result", result is ParseResult.Success)
    }

    @Test
    fun `解析出20门网格课程`() {
        val r = parseSample() as ParseResult.Success
        assertEquals("样本共 20 门网格课程", 20, r.courses.size)
    }

    @Test
    fun `解析出7条其他课程`() {
        val r = parseSample() as ParseResult.Success
        assertEquals(7, r.otherCourses.size)
    }

    @Test
    fun `星期分布正确`() {
        val r = parseSample() as ParseResult.Success
        assertEquals(6, r.courses.count { it.dayOfWeek == 1 })  // 周一 6 门
        assertEquals(4, r.courses.count { it.dayOfWeek == 2 })  // 周二 4 门
        assertEquals(3, r.courses.count { it.dayOfWeek == 3 })  // 周三 3 门
        assertEquals(3, r.courses.count { it.dayOfWeek == 4 })  // 周四 3 门
        assertEquals(4, r.courses.count { it.dayOfWeek == 5 })  // 周五 4 门
        assertEquals(0, r.courses.count { it.dayOfWeek == 6 })
        assertEquals(0, r.courses.count { it.dayOfWeek == 7 })
    }

    @Test
    fun `折行的长课程名被合并成一门`() {
        val r = parseSample() as ParseResult.Success
        // 真实 PDF 里这门课在周二、周五各出现一次，每次的课名都被拆成 3 行
        // （「毛泽东思想和中国特色」/「社会主义理论体系概论」/「★」）。
        // 断言两处都合并成完整课名，而不是拆成多门。
        val mao = r.courses.filter { it.name.contains("毛泽东") }
        assertEquals("周二、周五各一门，实际 ${mao.size} 门", 2, mao.size)
        assertTrue(
            "课名没合并完整：${mao.map { it.name }}",
            mao.all { it.name == "毛泽东思想和中国特色社会主义理论体系概论★" },
        )
        // 合并失败的残留会长成独立课程
        assertTrue(
            "折行残留被当成了独立课程",
            r.courses.none { it.name == "★" || it.name == "社会主义理论体系概论" },
        )
    }

    @Test
    fun `单双周课程都解析出来`() {
        val r = parseSample() as ParseResult.Success
        val single = r.courses.first { it.name.contains("大学英语") && it.name.endsWith("☆") }
        assertEquals(com.zhou.kebiao.data.WeekType.ODD, single.weekType)
        val double = r.courses.first { it.name.contains("概率论") }
        assertTrue(double.weekType == com.zhou.kebiao.data.WeekType.EVEN ||
                   double.weekType == com.zhou.kebiao.data.WeekType.EVERY)
    }

    @Test
    fun `页眉被读出来`() {
        val h = (parseSample() as ParseResult.Success).header
        assertEquals("2026-2027学年第1学期", h.semesterName)
        assertEquals("25406070399", h.studentId)
    }
}
