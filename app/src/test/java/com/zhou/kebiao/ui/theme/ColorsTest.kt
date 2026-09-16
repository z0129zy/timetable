package com.zhou.kebiao.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课程卡片取色。
 * 规则是「同一门课永远同一个色调」—— 按课程名散列取，不按第几门课取，
 * 否则增删一门课会让后面所有课一起换色。
 */
class ColorsTest {

    @Test
    fun `同一课程名取到的色调固定`() {
        assertEquals(courseTone("电路"), courseTone("电路"))
        assertEquals(courseTone("机械设计基础★"), courseTone("机械设计基础★"))
    }

    @Test
    fun `哈希值为负的课程名也要落在色板内`() {
        // "polygenelubricants" 的 String.hashCode() 恰好是 Int.MIN_VALUE，
        // 是 abs() 会失手、Math.floorMod() 才对的经典例子。
        val name = "polygenelubricants"
        assertEquals(Int.MIN_VALUE, name.hashCode())
        assertTrue(courseTone(name) in CourseTones)
    }

    @Test
    fun `批量取色都不越界`() {
        repeat(500) { i ->
            assertTrue(courseTone("课程$i★") in CourseTones)
        }
    }

    @Test
    fun `每个色调的浅底与深条都不是同一个色`() {
        // 卡片结构是「浅底 + 深色条」，两个色配成一样就只剩一块平色，结构就没了
        CourseTones.forEach { tone ->
            assertTrue("fill 与 bar 不应相同：$tone", tone.fill != tone.bar)
        }
    }
}
