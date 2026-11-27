package com.zhou.kebiao.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

    @Test
    fun `课程色不能和非本周的灰撞色`() {
        // 曾经 7 号「暖灰」的 fill 与非本周灰 #E5E1D8 完全相同：
        // 「形势与政策（3）★」正好散列到它，本周明明有课，卡片却和灰卡一样，
        // 看上去像没生效。这个不变量要一直守住。
        CourseTones.forEach { tone ->
            assertTrue("课程 fill 不能等于非本周灰：$tone", tone.fill != InactiveCourseFill)
        }
        assertTrue(
            "形势与政策（3）★ 的卡片必须能和「非本周」区分开",
            courseTone("形势与政策（3）★").fill != InactiveCourseFill,
        )
    }

    @Test
    fun `自定义颜色优先于课名散列`() {
        val custom = 0xFFCFDFEA.toInt()
        val tone = courseTone("任意课程", custom)
        assertEquals(Color(custom), tone.fill)
        assertTrue("自定义色也要有可见的深色条", tone.fill != tone.bar)
    }

    @Test
    fun `推荐色是三个且不与本周灰撞色`() {
        assertEquals(3, RecommendedHighlightColors.size)
        val inactive = InactiveCourseFill.toArgb()
        val argbs = RecommendedHighlightColors.map { it.argb }
        assertEquals("三个推荐色要互不相同", 3, argbs.toSet().size)
        argbs.forEach { argb ->
            assertTrue("推荐色不能等于非本周灰：$argb", argb != inactive)
        }
    }

    @Test
    fun `推荐色用官方品牌主色`() {
        assertEquals(
            0xFF4D6BFE.toInt(),
            RecommendedHighlightColors.first { it.name.startsWith("DeepSeek") }.argb,
        )
        assertEquals(
            0xFFD97757.toInt(),
            RecommendedHighlightColors.first { it.name.startsWith("Claude") }.argb,
        )
    }

    @Test
    fun `品牌深色会自动切白字`() {
        assertEquals(Color.White, courseTone("", 0xFF4D6BFE.toInt()).onFill)
        assertEquals(InkPrimary, courseTone("", 0xFFD97757.toInt()).onFill)
    }
}
