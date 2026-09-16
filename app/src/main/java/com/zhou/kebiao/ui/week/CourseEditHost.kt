package com.zhou.kebiao.ui.week

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.newCoursePrefill
import com.zhou.kebiao.ui.AppState

/**
 * 编辑弹窗的当前请求。同一时刻只显示一个：换请求就换弹窗，
 * 前一个关掉 —— 删除确认弹出时候选课弹窗已经不在了，
 * 它里面记的旧下标也不会再被读到。
 */
sealed interface EditRequest {
    /** 点了有课的格子 → 候选课弹窗。 */
    data class SlotPick(val slot: Slot) : EditRequest

    /** 打开 5 字段表单。[index] 为 null 表示新增。 */
    data class Form(val index: Int?, val initial: Course) : EditRequest

    /**
     * 删除前的二次确认。[from] 是被它盖住的那个候选课弹窗。
     *
     * 点「取消」要退回 [from]，不能一路退到网格 —— 用户只是不想删这一门，
     * 并没有想关掉整个时段。反过来，**确认删除后不退回 [from]**：
     * 删掉一门课会让 `courses` 后面的下标整体前移，[from] 里记的
     * `courseIndexes` 会指到别的课上去，所以删除成功就直接关掉。
     */
    data class ConfirmDelete(val index: Int, val from: SlotPick) : EditRequest
}

/**
 * 把编辑弹窗挂到当前请求上。今天页与课表页点的是同一套弹窗，
 * 逻辑集中在这一处，免得两边各写一份、改一边忘一边。
 */
@Composable
fun CourseEditHost(
    state: AppState,
    request: EditRequest?,
    onRequestChange: (EditRequest?) -> Unit,
    week: Int,
) {
    when (request) {
        null -> Unit

        is EditRequest.SlotPick -> CandidateDialog(
            timetable = state.timetable,
            slot = request.slot,
            week = week,
            onDismiss = { onRequestChange(null) },
            onEdit = { index ->
                state.timetable.courses.getOrNull(index)?.let { course ->
                    onRequestChange(EditRequest.Form(index, course))
                }
            },
            onDelete = { index -> onRequestChange(EditRequest.ConfirmDelete(index, request)) },
            onAddHere = {
                onRequestChange(
                    EditRequest.Form(
                        index = null,
                        initial = newCoursePrefill(
                            day = request.slot.dayOfWeek,
                            startPeriod = request.slot.startPeriod,
                            endPeriod = request.slot.endPeriod,
                            totalWeeks = state.timetable.totalWeeks,
                        ),
                    )
                )
            },
        )

        // 下标过期（课已经被删掉）就什么都不画，也不要在组合期里去改状态
        is EditRequest.ConfirmDelete -> state.timetable.courses.getOrNull(request.index)?.let { course ->
            AlertDialog(
                onDismissRequest = { onRequestChange(request.from) },
                title = { Text("删除课程") },
                text = { Text("确定删掉「${course.name}」？删掉就找不回来了。") },
                confirmButton = {
                    TextButton(onClick = {
                        state.removeCourse(request.index)
                        // 删成功就关掉整个弹窗，不退回 request.from：
                        // 下标已经整体前移，那个 SlotPick 里的 courseIndexes 不再可信
                        onRequestChange(null)
                    }) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { onRequestChange(request.from) }) { Text("取消") }
                },
            )
        }

        is EditRequest.Form -> CourseFormDialog(
            timetable = state.timetable,
            editingIndex = request.index,
            initial = request.initial,
            onDismiss = { onRequestChange(null) },
            onSave = { course ->
                if (request.index == null) {
                    state.addCourse(course)
                } else {
                    state.replaceCourse(request.index, course)
                }
                onRequestChange(null)
            },
        )
    }
}
