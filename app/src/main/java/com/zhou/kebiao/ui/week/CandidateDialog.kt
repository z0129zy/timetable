package com.zhou.kebiao.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.Timetable
import com.zhou.kebiao.data.endOf
import com.zhou.kebiao.data.isActiveIn
import com.zhou.kebiao.data.label
import com.zhou.kebiao.data.startOf
import com.zhou.kebiao.ui.theme.TextTitle
import com.zhou.kebiao.ui.theme.TextCaption
import com.zhou.kebiao.ui.theme.TextItem
import com.zhou.kebiao.ui.theme.RadiusPanel
import com.zhou.kebiao.ui.theme.SemanticBlue
import com.zhou.kebiao.ui.theme.InkDisabled
import com.zhou.kebiao.ui.theme.InkSecondary
import com.zhou.kebiao.ui.theme.InkTertiary
import com.zhou.kebiao.ui.theme.NextCourseFill

/**
 * 点有课的格子弹出的候选课弹窗（设计文档 §7.3）。
 *
 * 同一格可能压着多门课（同格不同周段），这里全列出来：本周真要上的那几门高亮，
 * 其余置灰。同一周里若有多门同时生效（用户自己填重了），全部高亮、不做取舍 ——
 * 上哪门由用户判断，App 不替他决定。
 *
 * 每门课的「本周上不上」由 `course.isActiveIn(week)` 单独判定，不看
 * `Slot.representative` —— 后者只是给网格卡片挑一个代表，认不出「两门同时生效」。
 */
@Composable
fun CandidateDialog(
    timetable: Timetable,
    slot: Slot,
    week: Int,
    onDismiss: () -> Unit,
    onEdit: (courseIndex: Int) -> Unit,
    onDelete: (courseIndex: Int) -> Unit,
    onAddHere: () -> Unit,
) {
    val candidates: List<Pair<Int, Course>> = slot.courseIndexes
        .mapNotNull { index -> timetable.courses.getOrNull(index)?.let { index to it } }

    val defaultPeriods = timetable.periods
    val from = defaultPeriods.startOf(slot.startPeriod)?.let(::hhmm)
    val to = defaultPeriods.endOf(slot.endPeriod)?.let(::hhmm)
    // 拼起来而不是「前缀 + 固定后缀」：第 11 节没有作息时间时 from/to 都是 null，
    // 用前缀拼会留下一个多余的前导空格（「 共 1 门课」）
    val subtitle = listOfNotNull(
        if (from != null && to != null) "$from – $to" else null,
        "共 ${candidates.size} 门课",
    ).joinToString(" · ")

    AlertDialog(
        shape = RoundedCornerShape(RadiusPanel),
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "${weekdayFull(slot.dayOfWeek)} · 第 ${slot.startPeriod}-${slot.endPeriod} 节",
                    style = TextTitle,
                )
                Text(
                    text = subtitle,
                    style = TextCaption,
                    color = InkTertiary,
                )
            }
        },
        text = {
            Column {
                candidates.forEach { (index, course) ->
                    CandidateRow(
                        course = course,
                        active = course.isActiveIn(week),
                        onEdit = { onEdit(index) },
                        onDelete = { onDelete(index) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "＋ 在这个时段加一门课",
                    style = TextCaption,
                    color = SemanticBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddHere)
                        .padding(vertical = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", style = TextCaption, color = InkTertiary) }
        },
    )
}

@Composable
private fun CandidateRow(
    course: Course,
    active: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) NextCourseFill else Color.Transparent)
            .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        // 本周要上的左边一条蓝杠，其余一条浅灰杠
        Box(
            Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (active) SemanticBlue else InkDisabled)
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = course.name,
                style = TextItem,
                color = if (active) SemanticBlue else InkSecondary,
            )
            Text(
                text = "${course.startWeek}-${course.endWeek}周 ${course.weekType.label()} · " +
                    "${course.startPeriod}-${course.endPeriod}节",
                style = TextCaption,
                color = InkSecondary,
            )
            Text(
                text = "场地 ${course.location.ifBlank { "—" }} · 教师 ${course.teacher.ifBlank { "—" }}",
                style = TextCaption,
                color = InkSecondary,
            )
            Text(
                text = if (active) "● 本周上课" else "○ 本周不上",
                style = TextCaption,
                color = if (active) SemanticBlue else InkTertiary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OutlinedButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Text("编辑", style = TextCaption, color = SemanticBlue)
            }
            OutlinedButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Text("删除", style = TextCaption, color = InkSecondary)
            }
        }
    }
}
