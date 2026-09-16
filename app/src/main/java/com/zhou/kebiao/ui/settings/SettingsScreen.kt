package com.zhou.kebiao.ui.settings
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.zhou.kebiao.ui.AppState
import com.zhou.kebiao.ui.theme.TextTitle
import com.zhou.kebiao.ui.theme.TextBodySmall
import com.zhou.kebiao.ui.theme.TextCaption
import com.zhou.kebiao.ui.theme.TextItem
import com.zhou.kebiao.ui.theme.ShadowSoft
import com.zhou.kebiao.ui.theme.ElevationCard
import com.zhou.kebiao.ui.theme.RadiusCard
import com.zhou.kebiao.ui.theme.Surface
import com.zhou.kebiao.ui.theme.Coral
import com.zhou.kebiao.ui.theme.RadiusPanel
import com.zhou.kebiao.ui.theme.Canvas
import com.zhou.kebiao.ui.theme.SemanticBlue
import com.zhou.kebiao.ui.theme.DividerLine
import com.zhou.kebiao.ui.theme.ErrorRed
import com.zhou.kebiao.ui.theme.InkPrimary
import com.zhou.kebiao.ui.theme.InkTertiary
import com.zhou.kebiao.ui.theme.RowLine
import java.time.LocalDate

/**
 * 设置页（设计文档 §7.7）：只有学期级的三样东西能改。
 * 导入入口不在这里（在课表页顶栏），作息时间也不在这里（课表页点左侧时间栏）。
 */
@Composable
fun SettingsScreen(state: AppState) {
    val timetable = state.timetable
    var editing by remember { mutableStateOf<SettingField?>(null) }
    var showAboutDeveloper by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Canvas).verticalScroll(rememberScrollState())) {
        SettingGroup("学期") {
            SettingRow("学期名称", timetable.semesterName, editable = true) {
                editing = SettingField.SEMESTER_NAME
            }
            SettingRow("开学第一周周一", timetable.firstMonday, editable = true) {
                editing = SettingField.FIRST_MONDAY
            }
            SettingRow("学期总周数", "${timetable.totalWeeks} 周", editable = true, last = true) {
                editing = SettingField.TOTAL_WEEKS
            }
        }

        SettingGroup("关于") {
            SettingRow("课程数据", "只存在本机，不上传", editable = false) {}
            // 这两行的右侧写成「去…」而不是「在…」，读起来才是指路而不是动作 ——
            // 否则用户会去点一行点了没反应的「导入课表」
            SettingRow("导入课表", "去「课表」页点右上角的 ＋", editable = false) {}
            SettingRow("编辑作息时间", "去「课表」页点左侧时间栏", editable = false) {}
            SettingRow("关于开发者", "感谢你的支持", editable = true, last = true) {
                showAboutDeveloper = true
            }
        }
    }

    if (showAboutDeveloper) {
        AboutDeveloperDialog(onDismiss = { showAboutDeveloper = false })
    }

    when (editing) {
        null -> Unit

        SettingField.SEMESTER_NAME -> InputDialog(
            title = "学期名称",
            initial = timetable.semesterName,
            hint = "例如 2026-2027 第1学期",
            validate = { if (it.isBlank()) "学期名称不能为空" else null },
            onDismiss = { editing = null },
            onSave = { state.setSemesterName(it.trim()); editing = null },
        )

        SettingField.FIRST_MONDAY -> InputDialog(
            title = "开学第一周周一",
            initial = timetable.firstMonday,
            hint = "写成 yyyy-MM-dd，例如 2026-08-31",
            validate = { raw ->
                if (runCatching { LocalDate.parse(raw.trim()) }.isSuccess) null
                else "要写成 yyyy-MM-dd，例如 2026-08-31"
            },
            onDismiss = { editing = null },
            onSave = { state.setFirstMonday(it.trim()); editing = null },
        )

        SettingField.TOTAL_WEEKS -> InputDialog(
            title = "学期总周数",
            initial = timetable.totalWeeks.toString(),
            hint = "课表页只能翻到第 1 到第 N 周",
            validate = { raw ->
                val weeks = raw.trim().toIntOrNull()
                when {
                    weeks == null -> "总周数要填数字"
                    weeks < 1 || weeks > 30 -> "总周数要在 1 到 30 之间"
                    else -> null
                }
            },
            onDismiss = { editing = null },
            onSave = { raw ->
                raw.trim().toIntOrNull()?.let(state::setTotalWeeks)
                editing = null
            },
        )
    }
}

private enum class SettingField { SEMESTER_NAME, FIRST_MONDAY, TOTAL_WEEKS }

/**
 * 一组设置项，装在一张圆角卡里。
 *
 * 卡片是「列表类容器」：大圆角 + 暖投影（课表网格那套是小圆角、无投影，
 * 两套密度的分工见 Dimens.kt）。分组标题前面那截珊瑚短条是装饰，不承载信息。
 */
@Composable
private fun SettingGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Coral)
            )
            Spacer(Modifier.width(6.dp))
            Text(title, style = TextCaption, color = InkTertiary)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    ElevationCard,
                    RoundedCornerShape(RadiusCard),
                    ambientColor = ShadowSoft,
                    spotColor = ShadowSoft,
                )
                .clip(RoundedCornerShape(RadiusCard))
                .background(Surface),
            content = content,
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    editable: Boolean,
    last: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = editable, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = TextItem, color = InkPrimary)
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = TextItem.copy(fontWeight = FontWeight.Normal),
            color = if (editable) SemanticBlue else InkTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (editable) {
            Text("  ›", style = TextItem, color = InkTertiary)
        }
    }
    // 分隔线只在卡片内部，最后一行不画 —— 画了会贴着圆角，看着像没对齐
    if (!last) {
        HorizontalDivider(
            color = RowLine,
            thickness = 1.dp,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

/**
 * 改一个值的小弹窗。[validate] 返回 null 表示通过，否则是给用户看的提示。
 * 三个设置项共用一张，差别只在提示文案与校验。
 */
@Composable
private fun InputDialog(
    title: String,
    initial: String,
    hint: String,
    validate: (String) -> String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        shape = RoundedCornerShape(RadiusPanel),
        onDismissRequest = onDismiss,
        title = { Text(title, style = TextTitle) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = TextBodySmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DividerLine,
                        unfocusedBorderColor = DividerLine,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(hint, style = TextCaption, color = InkTertiary, modifier = Modifier.padding(top = 4.dp))
                if (error != null) {
                    Text(error!!, style = TextCaption, color = ErrorRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = validate(value)
                if (problem == null) onSave(value) else error = problem
            }) {
                Text("保存", style = TextItem, color = SemanticBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", style = TextBodySmall, color = InkTertiary) }
        },
    )
}
