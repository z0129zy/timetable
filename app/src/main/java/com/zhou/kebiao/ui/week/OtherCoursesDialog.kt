package com.zhou.kebiao.ui.week

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhou.kebiao.data.OtherCourse
import com.zhou.kebiao.ui.theme.TextTitle
import com.zhou.kebiao.ui.theme.TextCaption
import com.zhou.kebiao.ui.theme.TextItem
import com.zhou.kebiao.ui.theme.RadiusPanel
import com.zhou.kebiao.ui.theme.InkPrimary
import com.zhou.kebiao.ui.theme.InkSecondary
import com.zhou.kebiao.ui.theme.InkTertiary
import com.zhou.kebiao.ui.theme.RowLine

/**
 * 其他课程：PDF 第 3 页底部那个区块（设计文档 §7.6）。
 *
 * 这些课没有固定的星期与节次，所以不参与课表网格、不参与今天页、
 * 也不参与周次推算 —— 只在这里按周次排序列出来，不可编辑、不可删除，
 * 保持与 PDF 一致。
 *
 * 注意其中会有超出学期总周数的条目（如「19周」），这是样本里真实存在的，
 * 它们不在网格里，不受总周数限制。
 */
@Composable
fun OtherCoursesDialog(otherCourses: List<OtherCourse>, onDismiss: () -> Unit) {
    val sorted = remember(otherCourses) { otherCourses.sortedBy { it.startWeek } }

    AlertDialog(
        shape = RoundedCornerShape(RadiusPanel),
        onDismissRequest = onDismiss,
        title = { Text("其他课程", style = TextTitle) },
        text = {
            if (sorted.isEmpty()) {
                Text("这份课表里没有其他课程。", style = TextCaption, color = InkTertiary)
            } else {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 420.dp)
                ) {
                    sorted.forEach { course ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(
                                text = course.name,
                                style = TextItem,
                                color = InkPrimary,
                            )
                            Text(
                                text = buildString {
                                    append(course.startWeek)
                                    if (course.endWeek != course.startWeek) append("-").append(course.endWeek)
                                    append(" 周")
                                    course.totalWeeks?.let { append(" · 共 ").append(it).append(" 周") }
                                    append(" · 教师 ").append(course.teacher.ifBlank { "—" })
                                },
                                style = TextCaption,
                                color = InkSecondary,
                            )
                        }
                        HorizontalDivider(color = RowLine, thickness = 1.dp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "这些课没有固定上课时间，不参与课表网格与「今天」，也不能编辑。",
                        style = TextCaption,
                        color = InkTertiary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", style = TextCaption, color = InkTertiary) }
        },
    )
}
