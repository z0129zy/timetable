package com.zhou.kebiao.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.PeriodTime
import com.zhou.kebiao.data.Timetable
import com.zhou.kebiao.data.endOf
import com.zhou.kebiao.data.isActiveIn
import com.zhou.kebiao.data.startOf
import com.zhou.kebiao.ui.theme.SemanticBlue
import com.zhou.kebiao.ui.theme.InactiveCourseFill
import com.zhou.kebiao.ui.theme.InkPrimary
import com.zhou.kebiao.ui.theme.InkSecondary
import com.zhou.kebiao.ui.theme.InactiveCourseText
import com.zhou.kebiao.ui.theme.RadiusChip
import com.zhou.kebiao.ui.theme.RowLine
import com.zhou.kebiao.ui.theme.TextMicro
import com.zhou.kebiao.ui.theme.TextMicroStrong
import com.zhou.kebiao.ui.theme.courseTone

/** 网格有 11 节（设计文档 §5.3）。 */
const val PERIOD_COUNT = 11

/** 左侧时间栏宽度。 */
val TIME_COLUMN_WIDTH = 34.dp

/** 每一节的行高 —— 放得下折成 5 行的长课名。 */
val ROW_HEIGHT = 77.dp

/**
 * 课表网格：左侧 11 节的时间栏 + 7 天。
 *
 * 行高统一、整块上下滚动（原型如此）；跨节次的课靠一块更高的卡片盖住多行，
 * 而不是把那几行撑高。每列底层铺 11 个透明格子接「点空白新增」，
 * 卡片画在它们之上并接自己的点击 —— 卡片盖住的那几格自然就点不到了。
 *
 * 星期列用 weight 平分剩余宽度：原型是按 411dp 宽的屏画的固定 53dp，
 * 照搬到 360dp 的机器上会横向溢出。
 *
 * [scrollState] 由调用方传进来（不是内部 remember）：周次转场时新旧两周会同时在组合里，
 * 各自持有一个滚动状态的话，新的一周会从顶部开始画，看着像跳了一下。
 */
@Composable
fun WeekGrid(
    timetable: Timetable,
    week: Int,
    onSlotClick: (Slot) -> Unit,
    onEmptyCellClick: (day: Int, period: Int) -> Unit,
    onPeriodClick: (period: Int) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val slots = remember(timetable.courses, week) { buildSlots(timetable.courses, week) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .drawBehind {
                // 每一节之间的横线画在最底层，不参与布局
                for (i in 1..PERIOD_COUNT) {
                    val y = ROW_HEIGHT.toPx() * i
                    drawLine(RowLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
            }
    ) {
        TimeColumn(timetable.periods, onPeriodClick)
        for (day in 1..7) {
            DayColumn(
                modifier = Modifier.weight(1f),
                day = day,
                week = week,
                timetable = timetable,
                slots = slots,
                onSlotClick = onSlotClick,
                onEmptyCellClick = onEmptyCellClick,
            )
        }
    }
}

@Composable
private fun TimeColumn(periods: List<PeriodTime>, onPeriodClick: (Int) -> Unit) {
    Column(Modifier.width(TIME_COLUMN_WIDTH)) {
        for (period in 1..PERIOD_COUNT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT)
                    .clickable { onPeriodClick(period) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Line("$period", bold = true, color = InkPrimary)
                Line(periods.startOf(period)?.let(::hhmm) ?: "—", color = InkSecondary)
                Line(periods.endOf(period)?.let(::hhmm) ?: "—", color = InkSecondary)
            }
        }
    }
}

@Composable
private fun Line(text: String, bold: Boolean = false, color: Color) {
    Text(
        text = text,
        style = if (bold) TextMicroStrong else TextMicro,
        color = color,
    )
}

@Composable
private fun DayColumn(
    modifier: Modifier,
    day: Int,
    week: Int,
    timetable: Timetable,
    slots: Map<Pair<Int, Int>, Slot>,
    onSlotClick: (Slot) -> Unit,
    onEmptyCellClick: (Int, Int) -> Unit,
) {
    Box(modifier.height(ROW_HEIGHT * PERIOD_COUNT)) {
        Column(Modifier.fillMaxSize()) {
            for (period in 1..PERIOD_COUNT) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .clickable { onEmptyCellClick(day, period) }
                )
            }
        }

        slots.values
            .filter { it.dayOfWeek == day }
            .forEach { slot ->
                SlotCard(
                    course = timetable.courses[slot.representative],
                    slot = slot,
                    week = week,
                    onClick = { onSlotClick(slot) },
                    modifier = Modifier
                        .offset(y = ROW_HEIGHT * (slot.startPeriod - 1))
                        .fillMaxWidth()
                        .height(ROW_HEIGHT * (slot.endPeriod - slot.startPeriod + 1))
                        .padding(1.dp),
                )
            }
    }
}

/** 一张课程卡片。整块淡色平涂，本周不上就整块变灰并挂「非本周」药丸。 */
@Composable
private fun SlotCard(
    course: Course,
    slot: Slot,
    week: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = course.isActiveIn(week)
    val (name, symbol) = splitTypeSymbol(course.name)
    val sub = buildString {
        append(symbol)
        if (course.location.isNotBlank()) append("@").append(course.location)
    }

    Box(
        modifier = modifier
            // 网格用小圆角、无投影：一列只有约 49dp，大圆角会吃掉可写宽度
            .clip(RoundedCornerShape(RadiusChip))
            .background(if (active) courseTone(course.name).fill else InactiveCourseFill)
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        Column {
            if (!active) {
                // 不用药丸底：足够浅的暖灰配白字到不了 4.5 的对比，
                // 反过来做成浅底深字又和卡片底撞色。只留一行小字最干净。
                Text("非本周", style = TextMicro, color = InactiveCourseText)
                Spacer(Modifier.height(2.dp))
            }
            Text(name, style = TextMicroStrong, color = InkPrimary)
            if (sub.isNotEmpty()) {
                Text(sub, style = TextMicro, color = InkPrimary)
            }
        }

        // 同一格压着多门课时，右上角圈出数量（设计文档 §7.2）
        if (slot.courseIndexes.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(SemanticBlue)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${slot.courseIndexes.size}", style = TextMicro, color = Color.White)
            }
        }
    }
}
