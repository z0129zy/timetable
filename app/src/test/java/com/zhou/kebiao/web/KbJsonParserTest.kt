package com.zhou.kebiao.web

import com.zhou.kebiao.data.CourseField
import com.zhou.kebiao.data.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KbJsonParserTest {

    @Test
    fun `解析基本课程`() {
        val json = """
            {"kbList":[
              {"kcmc":"形势与政策（3）","xm":"欧阳文博","cdmc":"2-3-217",
               "xqj":"5","jcs":"1-2节","zcd":"13-14周"}
            ]}
        """.trimIndent()

        val r = KbJsonParser.parse(json) as KbParseResult.Success
        assertEquals(1, r.courses.size)
        val c = r.courses[0]
        assertEquals("形势与政策（3）", c.name)
        assertEquals(5, c.dayOfWeek)
        assertEquals(1, c.startPeriod)
        assertEquals(2, c.endPeriod)
        assertEquals(13, c.startWeek)
        assertEquals(14, c.endWeek)
        assertEquals(WeekType.EVERY, c.weekType)
        assertEquals("2-3-217", c.location)
        assertEquals("欧阳文博", c.teacher)
        assertTrue(c.missingFields.isEmpty())
    }

    @Test
    fun `单周双周分别解析`() {
        val json = """
            {"kbList":[
              {"kcmc":"大学英语","xm":"王","cdmc":"1-101","xqj":"1","jcs":"5-6节","zcd":"1-15周(单)"},
              {"kcmc":"概率论","xm":"陈","cdmc":"1-102","xqj":"1","jcs":"5-6节","zcd":"2-16周（双）"}
            ]}
        """.trimIndent()

        val r = KbJsonParser.parse(json) as KbParseResult.Success
        assertEquals(WeekType.ODD, r.courses.first { it.name == "大学英语" }.weekType)
        assertEquals(WeekType.EVEN, r.courses.first { it.name == "概率论" }.weekType)
    }

    @Test
    fun `不连续的周次拆成多条`() {
        val json = """
            {"kbList":[
              {"kcmc":"实验课","xm":"李","cdmc":"实验楼","xqj":"3","jcs":"2-4节","zcd":"1-4,6-8周"}
            ]}
        """.trimIndent()

        val r = KbJsonParser.parse(json) as KbParseResult.Success
        assertEquals(2, r.courses.size)
        assertEquals(setOf(1 to 4, 6 to 8), r.courses.map { it.startWeek to it.endWeek }.toSet())
        assertTrue(r.courses.all { it.startPeriod == 2 && it.endPeriod == 4 })
    }

    @Test
    fun `单节次与单周写法`() {
        val json = """
            {"kbList":[
              {"kcmc":"讲座","xm":"","cdmc":"","xqj":"6","jcs":"7","zcd":"19周"}
            ]}
        """.trimIndent()

        val r = KbJsonParser.parse(json) as KbParseResult.Success
        val c = r.courses[0]
        assertEquals(7, c.startPeriod)
        assertEquals(7, c.endPeriod)
        assertEquals(19, c.startWeek)
        assertEquals(19, c.endWeek)
        assertEquals(setOf(CourseField.LOCATION, CourseField.TEACHER), c.missingFields)
    }

    @Test
    fun `兼容 data 包裹`() {
        val json = """{"data":{"kbList":[{"kcmc":"电路","xm":"孙","cdmc":"2-1","xqj":"2","jcs":"1-2","zcd":"1-16周"}]}}"""
        val r = KbJsonParser.parse(json) as KbParseResult.Success
        assertEquals("电路", r.courses[0].name)
    }

    @Test
    fun `重复行会去重`() {
        val course = """{"kcmc":"电路","xm":"孙","cdmc":"2-1","xqj":"2","jcs":"1-2","zcd":"1-16周"}"""
        val r = KbJsonParser.parse("""{"kbList":[$course,$course]}""") as KbParseResult.Success
        assertEquals(1, r.courses.size)
    }

    @Test
    fun `实践课进其他课程`() {
        val json = """
            {"kbList":[{"kcmc":"电路","xm":"孙","cdmc":"2-1","xqj":"2","jcs":"1-2","zcd":"1-16周"}],
             "sjkList":[{"kcmc":"金工实习","xm":"郑","cdmc":"工程训练中心","zcd":"9-16周"}]}
        """.trimIndent()

        val r = KbJsonParser.parse(json) as KbParseResult.Success
        assertEquals(1, r.otherCourses.size)
        assertEquals("金工实习", r.otherCourses[0].name)
    }

    @Test
    fun `不是课表数据时失败`() {
        assertTrue(KbJsonParser.parse("<html>登录页</html>") is KbParseResult.Failure)
        assertTrue(KbJsonParser.parse("""{"kbList":[]}""") is KbParseResult.Failure)
    }
}
