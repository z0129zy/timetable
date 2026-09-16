package com.zhou.kebiao.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ModelsTest {

    private fun course(
        start: Int = 1,
        end: Int = 16,
        weekType: WeekType = WeekType.EVERY,
    ) = Course(
        name = "测试课",
        startWeek = start,
        endWeek = end,
        weekType = weekType,
        dayOfWeek = 2,
        startPeriod = 1,
        endPeriod = 2,
        location = "2-3-305",
        teacher = "张三",
    )

    @Test
    fun `每周课在范围内任意周都生效`() {
        val c = course(1, 16, WeekType.EVERY)
        assertTrue(c.isActiveIn(1))
        assertTrue(c.isActiveIn(8))
        assertTrue(c.isActiveIn(16))
    }

    @Test
    fun `超出起止周的周次不生效`() {
        val c = course(9, 16, WeekType.EVERY)
        assertFalse(c.isActiveIn(8))
        assertTrue(c.isActiveIn(9))
        assertTrue(c.isActiveIn(16))
        assertFalse(c.isActiveIn(17))
    }

    @Test
    fun `单周只在奇数周生效`() {
        // 样本真实数据：大学英语(3)☆ 1-15周(单)
        val c = course(1, 15, WeekType.ODD)
        assertTrue(c.isActiveIn(1))
        assertFalse(c.isActiveIn(2))
        assertTrue(c.isActiveIn(3))
        assertTrue(c.isActiveIn(15))
    }

    @Test
    fun `双周只在偶数周生效`() {
        // 样本真实数据：概率论与数理统计B 2-16周(双)
        val c = course(2, 16, WeekType.EVEN)
        assertFalse(c.isActiveIn(1))
        assertTrue(c.isActiveIn(2))
        assertFalse(c.isActiveIn(3))
        assertTrue(c.isActiveIn(16))
    }

    @Test
    fun `单双周边界 起止周本身也要满足奇偶`() {
        // 2-16周(双)：第 2 周生效，但 1-15周(单) 的第 16 周不该生效
        assertTrue(course(2, 16, WeekType.EVEN).isActiveIn(2))
        assertFalse(course(1, 15, WeekType.ODD).isActiveIn(16))

        // 起止周落在错误的奇偶上时，该周不生效
        assertFalse(course(2, 16, WeekType.EVEN).isActiveIn(3))
    }

    @Test
    fun `当前周次按开学第一周周一计算`() {
        val start = LocalDate.of(2026, 8, 31)   // 真实开学日期，周一
        assertEquals(1, weekOf(start, start))
        assertEquals(1, weekOf(start, LocalDate.of(2026, 9, 6)))   // 第 1 周周日
        assertEquals(2, weekOf(start, LocalDate.of(2026, 9, 7)))   // 第 2 周周一
        assertEquals(3, weekOf(start, LocalDate.of(2026, 9, 15)))  // 今天
        assertEquals(16, weekOf(start, LocalDate.of(2026, 12, 20)))
    }

    @Test
    fun `开学日之前返回 0 表示学期未开始`() {
        val start = LocalDate.of(2026, 8, 31)
        assertEquals(0, weekOf(start, LocalDate.of(2026, 8, 30)))
    }

    @Test
    fun `默认作息时间是 10 条且时间值正确`() {
        val periods = defaultPeriods()
        assertEquals(10, periods.size)
        assertEquals((1..10).toList(), periods.map { it.period })
        assertEquals(
            listOf(
                "08:05" to "08:55", "09:00" to "09:50",
                "10:05" to "10:55", "11:00" to "11:50",
                "14:10" to "15:00", "15:05" to "15:55",
                "16:05" to "16:55", "17:00" to "17:50",
                "19:00" to "19:50", "19:55" to "20:45",
            ),
            periods.map { it.start to it.end },
        )
    }

    @Test
    fun `作息时间缺失时返回 null`() {
        assertNull(emptyList<PeriodTime>().startOf(1))
        assertNull(defaultPeriods().startOf(11))              // 第 11 节本来就没有作息时间
        assertNull(listOf(PeriodTime(1, "", "")).startOf(1))  // 空串
        assertNull(listOf(PeriodTime(1, "", "")).endOf(1))
    }

    @Test
    fun `作息时间格式非法时返回 null 而不是抛异常`() {
        val bad = listOf(PeriodTime(1, "8:05", "8:55"))
        assertNull(bad.startOf(1))
        assertNull(bad.endOf(1))
    }
}
