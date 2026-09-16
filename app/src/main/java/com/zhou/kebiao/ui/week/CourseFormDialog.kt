package com.zhou.kebiao.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.CourseField
import com.zhou.kebiao.data.Timetable
import com.zhou.kebiao.data.WeekType
import com.zhou.kebiao.data.label
import com.zhou.kebiao.data.startOf
import com.zhou.kebiao.data.validateCourse
import com.zhou.kebiao.ui.theme.TextCaption
import com.zhou.kebiao.ui.theme.TextBodySmall
import com.zhou.kebiao.ui.theme.TextItem
import com.zhou.kebiao.ui.theme.TextTitle
import com.zhou.kebiao.ui.theme.RadiusChip
import com.zhou.kebiao.ui.theme.RadiusPanel
import com.zhou.kebiao.ui.theme.SemanticBlue
import com.zhou.kebiao.ui.theme.NextCourseFill
import com.zhou.kebiao.ui.theme.DividerLine
import com.zhou.kebiao.ui.theme.ErrorRed
import com.zhou.kebiao.ui.theme.InkSecondary
import com.zhou.kebiao.ui.theme.InkTertiary
import com.zhou.kebiao.ui.theme.MissingFieldBackground
import com.zhou.kebiao.ui.theme.MissingFieldBorder

/**
 * 新增与编辑共用的一张 5 字段表单（设计文档 §7.5）。
 * 5 个字段全部可改，没有只读字段 —— 预填只是省事，不是锁定：
 * 在周三第 5-6 节点空白新增之后，照样能把「课程节数」改成周四第 3-4 节。
 */
@Composable
fun CourseFormDialog(
    timetable: Timetable,
    /** null = 新增，非 null = 编辑该下标的课。 */
    editingIndex: Int?,
    /** 进入时的初始值：新增用预填，编辑用原值。 */
    initial: Course,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
) {
    val missing = initial.missingFields

    var name by remember(initial) { mutableStateOf(initial.name) }
    var startWeek by remember(initial) { mutableStateOf(initial.startWeek.toString()) }
    var endWeek by remember(initial) { mutableStateOf(initial.endWeek.toString()) }
    var weekType by remember(initial) { mutableStateOf(initial.weekType) }
    var day by remember(initial) { mutableStateOf(initial.dayOfWeek) }
    var startPeriod by remember(initial) { mutableStateOf(initial.startPeriod.toString()) }
    var endPeriod by remember(initial) { mutableStateOf(initial.endPeriod.toString()) }
    var location by remember(initial) { mutableStateOf(initial.location) }
    var teacher by remember(initial) { mutableStateOf(initial.teacher) }
    var dayMenuOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 设计文档 §5.3：这一节还没有作息时间时照常存课，只在表单里提醒一句
    val periodGapHint = remember(startPeriod, endPeriod, timetable.periods) {
        val a = startPeriod.trim().toIntOrNull()
        val b = endPeriod.trim().toIntOrNull()
        if (a == null || b == null || a < 1 || b > 11 || a > b) {
            null
        } else {
            val gaps = (a..b).filter { timetable.periods.startOf(it) == null }
            if (gaps.isEmpty()) null
            else "第 ${gaps.joinToString("、")} 节还没有作息时间，点课表页左侧时间栏补一下"
        }
    }

    fun save() {
        val course = Course(
            name = name.trim(),
            // 填了非数字就落到 0，交给 validateCourse 统一报错，不在这里各写一套提示
            startWeek = startWeek.trim().toIntOrNull() ?: 0,
            endWeek = endWeek.trim().toIntOrNull() ?: 0,
            weekType = weekType,
            dayOfWeek = day,
            startPeriod = startPeriod.trim().toIntOrNull() ?: 0,
            endPeriod = endPeriod.trim().toIntOrNull() ?: 0,
            location = location.trim(),
            teacher = teacher.trim(),
            // 用户已经在表单里过目并确认过这些值，兜底标记到此清掉（设计文档 §5.5）
            missingFields = emptySet(),
        )
        val problem = validateCourse(course, timetable.totalWeeks)
        if (problem == null) onSave(course) else error = problem
    }

    AlertDialog(
        shape = RoundedCornerShape(RadiusPanel),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingIndex == null) "新增课程" else "编辑课程",
                style = TextTitle,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FieldLabel("课程名称", CourseField.NAME in missing)
                TextFieldBox(
                    value = name,
                    onValueChange = { name = it },
                    missing = CourseField.NAME in missing,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                FieldLabel("课程周期", CourseField.WEEKS in missing)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("第", style = TextCaption, color = InkSecondary)
                    TextFieldBox(startWeek, { startWeek = it }, CourseField.WEEKS in missing, Modifier.width(52.dp))
                    Text("周 到 第", style = TextCaption, color = InkSecondary)
                    TextFieldBox(endWeek, { endWeek = it }, CourseField.WEEKS in missing, Modifier.width(52.dp))
                    Text("周", style = TextCaption, color = InkSecondary)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WeekType.entries.forEach { type ->
                        WeekTypeOption(
                            label = type.label(),
                            selected = weekType == type,
                            onClick = { weekType = type },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                FieldLabel("课程节数", CourseField.PERIODS in missing)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Text(
                            text = weekdayFull(day) + " ▾",
                            style = TextCaption,
                            color = SemanticBlue,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .border(1.dp, DividerLine, RoundedCornerShape(3.dp))
                                .clickable { dayMenuOpen = true }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                        DropdownMenu(expanded = dayMenuOpen, onDismissRequest = { dayMenuOpen = false }) {
                            for (d in 1..7) {
                                DropdownMenuItem(
                                    text = { Text(weekdayFull(d), style = TextCaption) },
                                    onClick = {
                                        day = d
                                        dayMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("第", style = TextCaption, color = InkSecondary)
                    TextFieldBox(startPeriod, { startPeriod = it }, CourseField.PERIODS in missing, Modifier.width(44.dp))
                    Text("节 到 第", style = TextCaption, color = InkSecondary)
                    TextFieldBox(endPeriod, { endPeriod = it }, CourseField.PERIODS in missing, Modifier.width(44.dp))
                    Text("节", style = TextCaption, color = InkSecondary)
                }
                if (periodGapHint != null) {
                    Text(periodGapHint, style = TextCaption, color = MissingFieldBorder)
                }

                Spacer(Modifier.height(8.dp))
                FieldLabel("教室地点", CourseField.LOCATION in missing)
                TextFieldBox(
                    value = location,
                    onValueChange = { location = it },
                    missing = CourseField.LOCATION in missing,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                FieldLabel("任课老师", CourseField.TEACHER in missing)
                TextFieldBox(
                    value = teacher,
                    onValueChange = { teacher = it },
                    missing = CourseField.TEACHER in missing,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error!!, style = TextCaption, color = ErrorRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }) {
                Text("保存", style = TextItem, color = SemanticBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", style = TextBodySmall, color = InkTertiary) }
        },
    )
}

/**
 * 周类型的三选一。
 *
 * 原先是 Row 里一行 10sp 的小字加 clickable：触摸目标约 40×19dp，相邻两项的
 * 中心距只有 50dp —— 比手指触点还小，点「单周」常落到旁边那一项上；外观又和
 * 上面的字段标签一样，用户看不出它可点。改成等宽的带框选项后整块都是触摸
 * 目标（约 86×44dp），选中态由填充色表达，不再只靠一个圆圈。
 */
@Composable
private fun WeekTypeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(RadiusChip)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) NextCourseFill else Color.Transparent)
            .border(1.dp, if (selected) SemanticBlue else DividerLine, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Text(
            text = label,
            style = TextBodySmall,
            color = if (selected) SemanticBlue else InkSecondary,
        )
    }
}

/** 字段名。字段是导入时的兜底值就在旁边说明一句。 */
@Composable
private fun FieldLabel(text: String, missing: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = TextCaption, color = InkTertiary)
        if (missing) {
            Spacer(Modifier.width(4.dp))
            Text("导入时没认出来，请确认", style = TextCaption, color = MissingFieldBorder)
        }
    }
}

/**
 * 输入框。外面再套一层 Box 固定宽度 —— OutlinedTextField 自带一个不小的
 * 最小宽度，直接给窄宽度会被它顶开。
 */
@Composable
private fun TextFieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    missing: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextBodySmall,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (missing) MissingFieldBackground else Color.Transparent,
                unfocusedContainerColor = if (missing) MissingFieldBackground else Color.Transparent,
                focusedBorderColor = if (missing) MissingFieldBorder else DividerLine,
                unfocusedBorderColor = if (missing) MissingFieldBorder else DividerLine,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
