package com.zhou.kebiao.ui.week

import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.WeekType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class SlotsTest {

    private fun course(
        name: String,
        day: Int = 1,
        startPeriod: Int = 5,
        endPeriod: Int = 6,
        startWeek: Int = 1,
        endWeek: Int = 16,
        weekType: WeekType = WeekType.EVERY,
    ) = Course(
        name = name,
        startWeek = startWeek,
        endWeek = endWeek,
        weekType = weekType,
        dayOfWeek = day,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        location = "2-1-313",
        teacher = "王淑芬",
    )

    @Test
    fun `同一格的课合成一块卡片`() {
        // 周一 5-6 节：英语 1-15 周单周，概率论 2-16 周双周（设计文档 §3.3 情形 1）
        val courses = listOf(
            course("大学英语(3)☆", startWeek = 1, endWeek = 15, weekType = WeekType.ODD),
            course("概率论与数理统计B★", startWeek = 2, endWeek = 16, weekType = WeekType.EVEN),
        )
        val slots = buildSlots(courses, week = 3)

        assertEquals(1, slots.size)
        val slot = slots.getValue(1 to 5)
        assertEquals(listOf(0, 1), slot.courseIndexes)
        assertEquals(2, slot.courseIndexes.size)      // 圈码显示「2」
        assertEquals(0, slot.representative)          // 第 3 周是单周，英语要上
    }

    @Test
    fun `本周都不上时卡片显示第一门`() {
        // 周五 1-2 节：国家安全教育 10-11 周、形势与政策 13-14 周（§3.3 情形 2）
        val courses = listOf(
            course("国家安全教育（3）★", day = 5, startPeriod = 1, endPeriod = 2, startWeek = 10, endWeek = 11),
            course("形势与政策(3)★", day = 5, startPeriod = 1, endPeriod = 2, startWeek = 13, endWeek = 14),
        )
        val slots = buildSlots(courses, week = 3)

        val slot = slots.getValue(5 to 1)
        assertEquals(2, slot.courseIndexes.size)
        assertEquals(0, slot.representative)
    }

    @Test
    fun `跨节次的卡片占到最后的一节`() {
        val courses = listOf(
            course("物理实验(2)☆", day = 2, startPeriod = 2, endPeriod = 4),
            course("同格的短课★", day = 2, startPeriod = 2, endPeriod = 2),
        )
        val slots = buildSlots(courses, week = 3)

        assertEquals(4, slots.getValue(2 to 2).endPeriod)
    }

    @Test
    fun `不同起点的课各占一块`() {
        val courses = listOf(
            course("早课★", day = 1, startPeriod = 1, endPeriod = 2),
            course("晚课★", day = 1, startPeriod = 5, endPeriod = 6),
        )
        val slots = buildSlots(courses, week = 3)

        assertEquals(setOf(1 to 1, 1 to 5), slots.keys)
    }

    @Test
    fun `跨节次的卡片只占自己的那一块`() {
        val courses = listOf(course("金工实习☆", day = 5, startPeriod = 5, endPeriod = 8))
        val slots = buildSlots(courses, week = 3)

        assertEquals(setOf(5 to 5), slots.keys)
        assertEquals(8, slots.getValue(5 to 5).endPeriod)
    }

    @Test
    fun `课名末尾的类型符号会被拆出来`() {
        // 原型把符号挪到场地行前（「大学英语(3)」/「★@2-1-313」），
        // 8sp 的窄列里这样能少挤一个字。
        assertEquals("大学英语(3)" to "★", splitTypeSymbol("大学英语(3)★"))
        assertEquals("大学汉语(2)" to "☆", splitTypeSymbol("大学汉语(2)☆"))
        assertEquals("电子产品设计" to "■", splitTypeSymbol("电子产品设计■"))
    }

    @Test
    fun `课名没有类型符号时原样返回`() {
        assertEquals("机械设计基础" to "", splitTypeSymbol("机械设计基础"))
    }

    @Test
    fun `空课名不会炸`() {
        assertEquals("" to "", splitTypeSymbol(""))
    }

    @Test
    fun `时间格式化成分钟精度`() {
        assertEquals("08:05", hhmm(LocalTime.of(8, 5)))
        assertEquals("19:00", hhmm(LocalTime.of(19, 0)))
    }

    @Test
    fun `星期几的中文名`() {
        assertEquals("周一", weekdayFull(1))
        assertEquals("周日", weekdayFull(7))
    }
}
