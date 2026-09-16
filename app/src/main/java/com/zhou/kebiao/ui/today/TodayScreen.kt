package com.zhou.kebiao.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.PeriodTime
import com.zhou.kebiao.data.Timetable
import com.zhou.kebiao.data.endOf
import com.zhou.kebiao.data.isActiveIn
import com.zhou.kebiao.data.startOf
import com.zhou.kebiao.ui.AppState
import com.zhou.kebiao.ui.theme.TextPageTitle
import com.zhou.kebiao.ui.theme.TextCaption
import com.zhou.kebiao.ui.theme.TextItem
import com.zhou.kebiao.ui.theme.ShadowSoft
import com.zhou.kebiao.ui.theme.ElevationCard
import com.zhou.kebiao.ui.theme.RadiusCard
import com.zhou.kebiao.ui.theme.SemanticBlue
import com.zhou.kebiao.ui.theme.DividerLine
import com.zhou.kebiao.ui.theme.InkPrimary
import com.zhou.kebiao.ui.theme.InkSecondary
import com.zhou.kebiao.ui.theme.InkTertiary
import com.zhou.kebiao.ui.theme.InkDisabled
import com.zhou.kebiao.ui.theme.Canvas
import com.zhou.kebiao.ui.theme.NextCourseFill
import com.zhou.kebiao.ui.theme.PastCourseFill
import com.zhou.kebiao.ui.theme.courseTone
import com.zhou.kebiao.ui.week.CourseEditHost
import com.zhou.kebiao.ui.week.EditRequest
import com.zhou.kebiao.ui.week.TIME_COLUMN_WIDTH
import com.zhou.kebiao.ui.week.buildSlots
import com.zhou.kebiao.ui.week.hhmm
import com.zhou.kebiao.ui.week.weekdayFull
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/**
 * 今天页（设计文档 §7.1）：全天时间轴。
 * 已结束的课整行变淡，下一节高亮，点卡片弹的弹窗与课表页是同一个（§7.3）。
 */
@Composable
fun TodayScreen(state: AppState) {
    val timetable = state.timetable
    val today = LocalDate.now()
    val todayWeek = state.todayWeek
    var edit by remember { mutableStateOf<EditRequest?>(null) }

    // 「下一节」是按当前时刻算的，而 LocalTime.now() 不是状态 —— 不加这个 tick，
    // 页面一直开着跨过下课时间，高亮不会自己跳，要等某次重组才更新。
    // 只在今天页可见时每分钟醒一次，页面离开组合就随 LaunchedEffect 一起取消。
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalTime.now()
        }
    }

    val slots = remember(timetable.courses, todayWeek) {
        buildSlots(timetable.courses, todayWeek)
    }
    val todayCourses = remember(timetable.courses, todayWeek, today) {
        timetable.courses
            .withIndex()
            .filter { (_, course) ->
                course.dayOfWeek == today.dayOfWeek.value && course.isActiveIn(todayWeek)
            }
            .sortedBy { (_, course) -> course.startPeriod }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .verticalScroll(rememberScrollState())
    ) {
        TodayHeader(timetable, todayWeek, today)

        when {
            timetable.courses.isEmpty() ->
                Hint("还没有课表。到「课表」页点右上角的 ＋ 导入一份 PDF。")

            todayWeek == 0 ->
                Hint("这学期还没开始（开学第一周周一是 ${timetable.firstMonday}）。")

            todayWeek > timetable.totalWeeks ->
                Hint("这学期已经结束（共 ${timetable.totalWeeks} 周）。")

            todayCourses.isEmpty() ->
                Hint("今天没课。")

            else -> {
                // 下一节 = 第一门还没结束的课。作息时间缺失（设计文档 §5.3）时
                // 算不出结束时间，就当它还没结束，宁可高亮也不误判成已结束。
                val nextIndex = todayCourses.indexOfFirst { (_, course) ->
                    val end = timetable.periods.endOf(course.endPeriod)
                    end == null || end > now
                }

                todayCourses.forEachIndexed { index, (_, course) ->
                    TodayRow(
                        course = course,
                        periods = timetable.periods,
                        ended = nextIndex == -1 || index < nextIndex,
                        isNext = index == nextIndex,
                        onClick = {
                            slots[course.dayOfWeek to course.startPeriod]?.let {
                                edit = EditRequest.SlotPick(it)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    CourseEditHost(
        state = state,
        request = edit,
        onRequestChange = { edit = it },
        week = todayWeek.coerceIn(1, timetable.totalWeeks),
    )
}

@Composable
private fun TodayHeader(timetable: Timetable, todayWeek: Int, today: LocalDate) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text("今天", style = TextPageTitle, color = InkPrimary)
        Spacer(Modifier.weight(1f))
        Text(
            text = when {
                todayWeek == 0 -> "学期还没开始"
                todayWeek > timetable.totalWeeks -> "学期已结束"
                else -> "第 $todayWeek 周 · ${weekdayFull(today.dayOfWeek.value)}"
            },
            style = TextCaption,
            color = SemanticBlue,
        )
    }
    Text(
        text = "${today.year} 年 ${today.monthValue} 月 ${today.dayOfMonth} 日",
        style = TextCaption,
        color = InkTertiary,
        modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 8.dp),
    )
    HorizontalDivider(color = DividerLine, thickness = 1.dp)
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TextCaption, color = InkTertiary)
    }
}

@Composable
private fun TodayRow(
    course: Course,
    periods: List<PeriodTime>,
    ended: Boolean,
    isNext: Boolean,
    onClick: () -> Unit,
) {
    val start = periods.startOf(course.startPeriod)?.let(::hhmm) ?: "—"
    val end = periods.endOf(course.endPeriod)?.let(::hhmm) ?: "—"
    // 卡片底用课程自己的浅色，左侧色条用同色系的深色 ——
    //「浅底 + 深色条」是这一版卡片的基本结构。
    val tone = courseTone(course.name)
    val bar = when {
        isNext -> SemanticBlue
        // 已结束的不再显示课程色：整块退成中性，视觉上「退到后面去」
        ended -> InkDisabled
        else -> tone.bar
    }
    val background = when {
        isNext -> NextCourseFill
        ended -> PastCourseFill
        else -> tone.fill
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .alpha(if (ended) 0.45f else 1f),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(TIME_COLUMN_WIDTH).padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = start,
                style = TextCaption.copy(
                    fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (isNext) SemanticBlue else InkSecondary,
            )
            Text(
                text = end,
                style = TextCaption.copy(
                    fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (isNext) SemanticBlue else InkSecondary,
            )
        }

        // IntrinsicSize.Min 让左侧色条跟着文字高度走，不用写死卡片高度。
        // 列表类用大圆角 + 暖投影：投影是暖褐不是黑，压在奶油底上才不发脏。
        Row(
            modifier = Modifier
                .weight(1f)
                .height(IntrinsicSize.Min)
                .shadow(ElevationCard, RoundedCornerShape(RadiusCard), ambientColor = ShadowSoft, spotColor = ShadowSoft)
                .clip(RoundedCornerShape(RadiusCard))
                .background(background)
                .clickable(onClick = onClick),
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(bar))
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(course.name, style = TextItem, color = InkPrimary)
                Text(
                    text = buildString {
                        append("@").append(course.location.ifBlank { "未排地点" })
                        if (course.teacher.isNotBlank()) append(" · ").append(course.teacher)
                    },
                    style = TextCaption,
                    color = InkSecondary,
                )
                if (isNext) {
                    Text("▸ 下一节", style = TextCaption, color = SemanticBlue)
                }
            }
        }
    }
}
