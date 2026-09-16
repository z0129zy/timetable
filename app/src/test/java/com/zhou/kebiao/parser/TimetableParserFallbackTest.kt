package com.zhou.kebiao.parser

import com.zhou.kebiao.data.CourseField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入时的字段兜底行为。
 *
 * 设计文档 §8 要求「个别字段未识别 → 照常导入，字段留空，编辑弹窗标黄」，
 * 所以解析器必须告诉界面**哪个字段是兜底值**，界面才能标黄。
 * 兜底值与用户手填的值长得一样（都是 "" 或 1），不标记就区分不了。
 */
class TimetableParserFallbackTest {

    private val parser = TimetableParser()

    private fun parse(vararg groups: List<TextFragment>) =
        parser.parse(groups.flatMap { it }) as ParseResult.Success

    private fun frag(y: Float, size: Float, text: String) =
        TextFragment(0, MONDAY_X, y, size, text)

    @Test
    fun `字段齐全的课程不带任何缺失标记`() {
        val result = parse(SamplePdfText.header, SamplePdfText.monday)

        val english = result.courses.first { it.name == "大学英语(3)★" }
        assertEquals(emptySet<CourseField>(), english.missingFields)
        assertTrue(
            "夹具里每门课都字段齐全，不该有标记：${result.courses.map { it.name to it.missingFields }}",
            result.courses.all { it.missingFields.isEmpty() },
        )
    }

    @Test
    fun `课名没解析出来时标记 NAME 并兜底为未知课程`() {
        // 正文完好，只是课程名那一行丢了
        val result = parse(
            SamplePdfText.header,
            listOf(
                frag(386.6f, 8f, "(3-4节)1-16周/场地:2-1-313/教师:王淑芬/教学班:x"),
            ),
        )

        val c = result.courses.single()
        assertEquals("未知课程", c.name)
        assertTrue(CourseField.NAME in c.missingFields)
        assertEquals(setOf(CourseField.NAME), c.missingFields)
    }

    @Test
    fun `地点缺失时只标记 LOCATION`() {
        val result = parse(
            SamplePdfText.header,
            listOf(
                frag(386.6f, 9f, "无场地课★"),
                frag(399.5f, 8f, "(3-4节)1-16周/教师:王淑芬/教学班:x"),
            ),
        )

        val c = result.courses.single()
        assertEquals("", c.location)
        assertEquals("王淑芬", c.teacher)      // 教师有值就不该被标记
        assertEquals(setOf(CourseField.LOCATION), c.missingFields)
    }

    @Test
    fun `周次缺失时标记 WEEKS`() {
        val result = parse(
            SamplePdfText.header,
            listOf(
                frag(386.6f, 9f, "没有周次的课★"),
                frag(399.5f, 8f, "(3-4节)/场地:2-1-313/教师:王淑芬/教学班:x"),
            ),
        )

        val c = result.courses.single()
        assertTrue(CourseField.WEEKS in c.missingFields)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
    }

    @Test
    fun `没有节次标签的课程照常导入并标记 PERIODS`() {
        // 设计文档 §5.3：不允许因缺时间而丢弃课程。
        // 这条正文有场地和教师，是真课程，只是 (N-M节) 那一小段丢了。
        val result = parse(
            SamplePdfText.header,
            listOf(
                frag(386.6f, 9f, "节次丢了的课★"),
                frag(399.5f, 8f, "1-16周/场地:2-3-305/教师:赵鹏/教学班:机械设计基础-0012"),
            ),
        )

        val c = result.courses.single()
        assertEquals("节次丢了的课★", c.name)
        assertEquals(1, c.dayOfWeek)
        assertEquals("2-3-305", c.location)
        assertEquals("赵鹏", c.teacher)
        assertTrue(CourseField.PERIODS in c.missingFields)
        // 节次与周次同源于 `(N-M节)0-0周` 这一段，所以周次也读不出来
        assertTrue(CourseField.WEEKS in c.missingFields)
    }

    @Test
    fun `两门课里只有缺字段的那门被标记`() {
        val result = parse(
            SamplePdfText.header,
            SamplePdfText.monday,
            listOf(
                frag(600f, 9f, "缺老师的课★"),
                // 场地后面必须跟下一个字段标记 —— 真的有 `/教学班:`，正则也靠它收尾
                frag(613f, 8f, "(9-10节)1-16周/场地:2-1-313/教学班:某班"),
            ),
        )

        val mondayOk = result.courses.first { it.name == "大学英语(3)★" }
        val noTeacher = result.courses.first { it.name == "缺老师的课★" }

        assertEquals(emptySet<CourseField>(), mondayOk.missingFields)
        assertEquals(setOf(CourseField.TEACHER), noTeacher.missingFields)
    }

    @Test
    fun `没有课程正文特征的杂项仍然被挡掉`() {
        // 「打印时间:…」落在周日列，既无 (N-M节) 也无场地/教师，不是课程。
        // 放宽闸门后这一条必须仍然被挡住，否则周日会凭空多出一门「未知课程」。
        val junk = listOf(TextFragment(2, 751.5f, 98.5f, 8f, "打印时间:2026-09-03"))
        val result = parser.parse(SamplePdfText.header + junk)

        assertTrue("周日不该凭空多出课程", result is ParseResult.Failure)
    }

    companion object {
        /** 周一列的课程文字 x（实测值，见设计文档 §3.1）。 */
        private const val MONDAY_X = 104.1f
    }
}
