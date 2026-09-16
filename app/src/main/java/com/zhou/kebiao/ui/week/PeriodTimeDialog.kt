package com.zhou.kebiao.ui.week

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.zhou.kebiao.data.PeriodTime
import com.zhou.kebiao.ui.theme.TextTitle
import com.zhou.kebiao.ui.theme.TextCaption
import com.zhou.kebiao.ui.theme.TextBodySmall
import com.zhou.kebiao.ui.theme.TextItem
import com.zhou.kebiao.ui.theme.RadiusPanel
import com.zhou.kebiao.ui.theme.SemanticBlue
import com.zhou.kebiao.ui.theme.DividerLine
import com.zhou.kebiao.ui.theme.ErrorRed
import com.zhou.kebiao.ui.theme.InkSecondary
import com.zhou.kebiao.ui.theme.InkTertiary
import java.time.LocalTime

/**
 * 点网格左侧时间栏弹出的弹窗（设计文档 §7.4）。
 * 改的是作息时间表本身，所有用到这一节的课都会跟着变，但课程数据不动 ——
 * 所以弹窗里要写清楚这一点。
 */
@Composable
fun PeriodTimeDialog(
    period: Int,
    current: PeriodTime?,
    onDismiss: () -> Unit,
    onSave: (start: String, end: String) -> Unit,
) {
    var start by remember(period) { mutableStateOf(current?.start.orEmpty()) }
    var end by remember(period) { mutableStateOf(current?.end.orEmpty()) }
    // 三个都用 period 作 key，保持一致。今天靠「periodToEdit 置空后整棵子树被销毁」
    // 恰好也能清掉 error，但那是巧合；将来若改成常驻弹窗就会残留上一次的红字。
    var error by remember(period) { mutableStateOf<String?>(null) }

    AlertDialog(
        shape = RoundedCornerShape(RadiusPanel),
        onDismissRequest = onDismiss,
        title = {
            Text("第 $period 节的作息时间", style = TextTitle)
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeBox(start, { start = it })
                    Text("  –  ", style = TextBodySmall, color = InkSecondary)
                    TimeBox(end, { end = it })
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "改这里影响所有用到第 $period 节的课，不会改动课程本身。",
                    style = TextCaption,
                    color = InkTertiary,
                )
                Text("时间写成 HH:mm，例如 08:05", style = TextCaption, color = InkTertiary)
                if (error != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(error!!, style = TextCaption, color = ErrorRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = start.trim()
                val e = end.trim()
                // 只认 HH:mm。用户手输的 "8:05" 会被 LocalTime.parse 顶掉，
                // 与其存进去让列表页每处都要容错，不如这里就挡住。
                val parsedStart = runCatching { LocalTime.parse(s) }.getOrNull()
                val parsedEnd = runCatching { LocalTime.parse(e) }.getOrNull()
                when {
                    parsedStart == null || parsedEnd == null -> error = "时间要写成 HH:mm，例如 08:05"
                    !parsedStart.isBefore(parsedEnd) -> error = "开始时间要早于结束时间"
                    // 用 hhmm() 归一化再存：LocalTime.parse 会静默接受 "08:05:00"
                    // 这种带秒的写法，原样落盘的话存进去的就不是 HH:mm 了
                    else -> onSave(hhmm(parsedStart), hhmm(parsedEnd))
                }
            }) {
                Text("保存", style = TextItem, color = SemanticBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", style = TextBodySmall, color = InkTertiary) }
        },
    )
}

@Composable
private fun TimeBox(value: String, onValueChange: (String) -> Unit) {
    Box(Modifier.width(84.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextBodySmall,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DividerLine,
                unfocusedBorderColor = DividerLine,
            ),
            modifier = Modifier.width(84.dp),
        )
    }
}
