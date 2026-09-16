package com.zhou.kebiao.ui.week

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.WeekType
import com.zhou.kebiao.data.defaultTimetable
import com.zhou.kebiao.data.newCoursePrefill
import com.zhou.kebiao.ui.theme.课表Theme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 周类型三选一的交互回归。
 *
 * 锁的是「点不中、点完像没反应」这个 bug：原先它是 Row 里一行 10sp 的小字，
 * 触摸目标只有约 40×19dp，还和相邻项挤在 50dp 的中心距上 —— 点「单周」经常
 * 落到「每周」或「双周」上。所以这里断言的是节点尺寸，不是文字内容。
 */
@RunWith(AndroidJUnit4::class)
class CourseFormDialogTest {

    @get:Rule
    val rule = createComposeRule()

    /** 表单默认铺满整学期、每周一次，星期取周一。 */
    private fun showForm(initial: Course) {
        rule.setContent {
            课表Theme {
                CourseFormDialog(
                    timetable = defaultTimetable(),
                    editingIndex = null,
                    initial = initial,
                    onDismiss = {},
                    onSave = {},
                )
            }
        }
    }

    private fun prefill() = newCoursePrefill(day = 1, startPeriod = 1, endPeriod = 2, totalWeeks = 16)

    @Test
    fun `周类型的触摸目标不比手指小`() {
        showForm(prefill())

        listOf("每周", "单周", "双周").forEach { label ->
            val bounds = rule.onNodeWithText(label).getUnclippedBoundsInRoot()
            assertTrue("「$label」触摸宽度 ${bounds.width} 太窄", bounds.width >= 56.dp)
            assertTrue("「$label」触摸高度 ${bounds.height} 太矮", bounds.height >= 44.dp)
        }
    }

    @Test
    fun `点单周后只有它被选中`() {
        showForm(prefill())

        rule.onNodeWithText("单周").performClick()

        rule.onNodeWithText("单周").assertIsSelected()
        rule.onNodeWithText("每周").assertIsNotSelected()
        rule.onNodeWithText("双周").assertIsNotSelected()
    }

    @Test
    fun `点双周后再点每周能切回来`() {
        showForm(prefill())

        rule.onNodeWithText("双周").performClick()
        rule.onNodeWithText("双周").assertIsSelected()

        rule.onNodeWithText("每周").performClick()
        rule.onNodeWithText("每周").assertIsSelected()
        rule.onNodeWithText("双周").assertIsNotSelected()
    }

    @Test
    fun `编辑双周课时按原值预选`() {
        showForm(
            prefill().copy(startWeek = 2, endWeek = 16, weekType = WeekType.EVEN)
        )

        rule.onNodeWithText("双周").assertIsSelected()
        rule.onNodeWithText("每周").assertIsNotSelected()
    }
}
