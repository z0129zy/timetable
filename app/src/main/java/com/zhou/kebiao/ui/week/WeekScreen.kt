package com.zhou.kebiao.ui.week

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.zhou.kebiao.data.Timetable
import com.zhou.kebiao.data.newCoursePrefill
import com.zhou.kebiao.data.weekStartDate
import com.zhou.kebiao.ui.AppState
import com.zhou.kebiao.ui.theme.TextCaption
import com.zhou.kebiao.ui.theme.TextBodySmall
import com.zhou.kebiao.ui.theme.TextTitle
import com.zhou.kebiao.ui.theme.OnTodayBadge
import com.zhou.kebiao.ui.theme.Canvas
import com.zhou.kebiao.ui.theme.DividerLine
import com.zhou.kebiao.ui.theme.InkPrimary
import com.zhou.kebiao.ui.theme.InkSecondary
import com.zhou.kebiao.ui.theme.InkTertiary
import com.zhou.kebiao.ui.theme.TodayBadge
import java.time.LocalDate

/** 左右滑动多少像素算翻一周。 */
private const val SWIPE_THRESHOLD = 80f

/** 周次转场的时长。太快看不出淡，太慢会觉得卡；220ms 与系统里翻页的手感接近。 */
private const val WEEK_FADE_MS = 220

@Composable
fun WeekScreen(state: AppState, onRequestImport: () -> Unit) {
    var edit by remember { mutableStateOf<EditRequest?>(null) }
    var periodToEdit by remember { mutableStateOf<Int?>(null) }
    var showOtherCourses by remember { mutableStateOf(false) }

    val timetable = state.timetable
    val week = state.currentWeek
    // 滚动位置提到这一层：转场时新旧两周同时在组合里，各自的 ScrollState 会让新的一周
    // 从顶部开始画，看起来像跳了一下。共用一个就不会。
    val gridScroll = rememberScrollState()

    Column(Modifier.fillMaxSize().background(Canvas)) {
        WeekTopBar(
            week = week,
            semesterName = timetable.semesterName,
            onPrevious = state::showPreviousWeek,
            onNext = state::showNextWeek,
            onOtherCourses = { showOtherCourses = true },
            onRequestImport = onRequestImport,
        )
        WeekDateHeader(timetable, week)

        AnimatedContent(
            targetState = week,
            // 往哪边滑，新的一周就从哪边轻轻推入（设计文档 §7.2：左滑 = 下一周）
            transitionSpec = {
                val forward = targetState > initialState
                val enter = slideInHorizontally { width -> if (forward) width / 8 else -width / 8 } +
                    fadeIn(tween(WEEK_FADE_MS))
                val leave = slideOutHorizontally { width -> if (forward) -width / 8 else width / 8 } +
                    fadeOut(tween(WEEK_FADE_MS))
                enter togetherWith leave
            },
            label = "week",
            modifier = Modifier
                .weight(1f)
                .pointerInput(week) {
                    var dragged = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = 0f },
                        onDragEnd = {
                            // 左滑 = 下一周，右滑 = 上一周（设计文档 §7.2）。
                            // 两端由 showWeek 夹住，滑到头继续滑没有效果，也不循环。
                            when {
                                dragged <= -SWIPE_THRESHOLD -> state.showNextWeek()
                                dragged >= SWIPE_THRESHOLD -> state.showPreviousWeek()
                            }
                        },
                    ) { _, dragAmount -> dragged += dragAmount }
                },
        ) { animatedWeek ->
            WeekGrid(
                timetable = timetable,
                week = animatedWeek,
                onSlotClick = { edit = EditRequest.SlotPick(it) },
                onEmptyCellClick = { day, period ->
                    edit = EditRequest.Form(
                        index = null,
                        initial = newCoursePrefill(
                            day = day,
                            startPeriod = period,
                            endPeriod = period,
                            totalWeeks = timetable.totalWeeks,
                        ),
                    )
                },
                onPeriodClick = { periodToEdit = it },
                scrollState = gridScroll,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    CourseEditHost(
        state = state,
        request = edit,
        onRequestChange = { edit = it },
        week = week,
    )

    periodToEdit?.let { period ->
        PeriodTimeDialog(
            period = period,
            current = state.timetable.periods.firstOrNull { it.period == period },
            onDismiss = { periodToEdit = null },
            onSave = { start, end ->
                state.setPeriodTime(period, start, end)
                periodToEdit = null
            },
        )
    }

    if (showOtherCourses) {
        OtherCoursesDialog(
            otherCourses = timetable.otherCourses,
            onDismiss = { showOtherCourses = false },
        )
    }
}

@Composable
private fun WeekTopBar(
    week: Int,
    semesterName: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOtherCourses: () -> Unit,
    onRequestImport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton("◀", "上一周", onPrevious)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                text = "第$week 周",
                style = TextTitle,
                color = InkPrimary,
            )
            Text(
                text = semesterName,
                style = TextCaption,
                color = InkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GlyphButton("▶", "下一周", onNext)
        Text(
            text = "其他课程",
            style = TextCaption,
            color = InkSecondary,
            modifier = Modifier.clickable(onClick = onOtherCourses).padding(4.dp),
        )
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(TodayBadge)
                .clickable(onClick = onRequestImport)
                .semantics { contentDescription = "导入课表" },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = TextTitle, color = OnTodayBadge)
        }
    }
    HorizontalDivider(color = DividerLine, thickness = 1.dp)
}

/** 字形按钮。字形本身读屏念不出来，靠 [label] 给它一个说法。 */
@Composable
private fun GlyphButton(glyph: String, label: String, onClick: () -> Unit) {
    Text(
        text = glyph,
        style = TextTitle,
        color = InkSecondary,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun WeekDateHeader(timetable: Timetable, week: Int) {
    val monday = timetable.weekStartDate(week)
    val today = LocalDate.now()

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(TIME_COLUMN_WIDTH), contentAlignment = Alignment.Center) {
            Text("${monday.monthValue}月", style = TextCaption, lineHeight = 11.sp, color = InkTertiary)
        }
        for (day in 1..7) {
            val date = monday.plusDays((day - 1).toLong())
            val isToday = date == today
            Box(
                modifier = Modifier.weight(1f).padding(vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = if (isToday) {
                        Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(TodayBadge)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    } else {
                        Modifier
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = WEEKDAY_SHORT[day - 1],
                        style = TextCaption,
                        color = if (isToday) OnTodayBadge else InkSecondary,
                    )
                    Text(
                        text = "${date.dayOfMonth}",
                        style = TextBodySmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) OnTodayBadge else InkPrimary,
                    )
                }
            }
        }
    }
    HorizontalDivider(color = DividerLine, thickness = 1.dp)
}
