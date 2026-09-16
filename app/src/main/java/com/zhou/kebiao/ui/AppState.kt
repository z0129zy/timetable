package com.zhou.kebiao.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.Timetable
import com.zhou.kebiao.data.TimetableStore
import com.zhou.kebiao.data.defaultTimetable
import com.zhou.kebiao.data.firstMondayDate
import com.zhou.kebiao.data.normalized
import com.zhou.kebiao.data.weekOf
import com.zhou.kebiao.data.withCourseAdded
import com.zhou.kebiao.data.withCourseRemoved
import com.zhou.kebiao.data.withCourseReplaced
import com.zhou.kebiao.data.withFirstMonday
import com.zhou.kebiao.data.withPeriodTime
import com.zhou.kebiao.data.withSemesterName
import com.zhou.kebiao.data.withTotalWeeks
import com.zhou.kebiao.parser.PdfTextExtractor
import com.zhou.kebiao.parser.TimetableParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 全 App 唯一的状态。持有唯一的 Timetable，所有改动都经由 data 层的纯函数
 * 拿到新副本，再整份落盘 —— 没有增量写，出问题最多丢这一次改动。
 *
 * 课表永远不为 null：没导入过就是一份「课程列表为空」的默认课表。
 * 空课表用 courses.isEmpty() 判断，界面据此区分「还没有课表」与「今天没课」。
 */
class AppState(
    private val context: Context,
    private val store: TimetableStore,
) {
    // 读进来的课表先归一化：文件可能是手改的、也可能是旧版本写坏的
    var timetable by mutableStateOf((store.load() ?: defaultTimetable()).normalized())
        private set

    /** 课表页正在看第几周。 */
    var currentWeek by mutableStateOf(
        weekOf(timetable.firstMondayDate(), LocalDate.now()).coerceIn(1, timetable.totalWeeks)
    )
        private set

    /** 正在解析 PDF。界面据此盖一层进度并挡住重复点击。 */
    var importing by mutableStateOf(false)
        private set

    /** 导入失败要说的一句话；由 AppRoot 弹出来，弹完置回 null。 */
    var importError by mutableStateOf<String?>(null)

    private val extractor: PdfTextExtractor by lazy { PdfTextExtractor(context) }

    /** 今天是第几周。0 = 学期还没开始，大于总周数 = 学期已经结束。 */
    val todayWeek: Int
        get() = weekOf(timetable.firstMondayDate(), LocalDate.now())

    // ---------- 周次 ----------

    fun showWeek(week: Int) {
        currentWeek = week.coerceIn(1, timetable.totalWeeks)
    }

    fun showNextWeek() = showWeek(currentWeek + 1)

    fun showPreviousWeek() = showWeek(currentWeek - 1)

    // ---------- 导入 ----------

    /**
     * 读一份 PDF 并整份导入。失败只写 [importError]，绝不碰现有课表。
     * 解析在 IO 线程，PDFBox 读一个 6KB 三页的文件要百毫秒级，放主线程会卡帧。
     */
    suspend fun importPdf(uri: Uri) {
        importing = true
        importError = null
        try {
            val parsed = withContext(Dispatchers.IO) {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext null
                val fragments = stream.use { extractor.extract(it) }
                TimetableParser().parse(fragments)
            }
            if (parsed == null) {
                importError = "这个文件读不了"
                return
            }
            when (val outcome = timetable.afterImport(parsed)) {
                is ImportOutcome.Rejected -> importError = outcome.reason
                is ImportOutcome.Applied -> save(outcome.timetable)
            }
        } catch (e: CancellationException) {
            // 协程被取消（页面/进程正在销毁）不是「文件读不了」，要继续往外抛，
            // 否则会把取消信号吞掉，还平白闪一个错误提示。
            throw e
        } catch (e: Exception) {
            // 不是 PDF、文件损坏、PDFBox 内部报错都落到这里（设计文档 §8 第一行）。
            // 这里必须吞异常：用户只该看到「读不了」，不该看到堆栈。
            importError = "这个文件读不了"
        } finally {
            importing = false
        }
    }

    fun dismissImportError() {
        importError = null
    }

    // ---------- 编辑 ----------

    fun addCourse(course: Course) = save(timetable.withCourseAdded(course))

    fun replaceCourse(index: Int, course: Course) = save(timetable.withCourseReplaced(index, course))

    fun removeCourse(index: Int) = save(timetable.withCourseRemoved(index))

    fun setPeriodTime(period: Int, start: String, end: String) =
        save(timetable.withPeriodTime(period, start, end))

    fun setSemesterName(name: String) = save(timetable.withSemesterName(name))

    fun setFirstMonday(iso: String) = save(timetable.withFirstMonday(iso))

    fun setTotalWeeks(weeks: Int) {
        save(timetable.withTotalWeeks(weeks))
        // 总周数调小后，正在看的周次可能已经越界
        showWeek(currentWeek)
    }

    /**
     * 先落盘、再改内存。反过来的话，磁盘写失败时界面已经显示新课表、
     * 导入路径还会把这次失败报成「这个文件读不了」—— 内存与磁盘对不上，
     * 提示也误导。几十条数据，主线程写几十 KB，不值得为它引入异步。
     *
     * 进状态前一律 [normalized]：不管是磁盘读来的还是编辑产生的，都给夹一遍，
     * 这样「totalWeeks 为 0 就崩在启动路径上」这件事只需在一处防住。
     */
    private fun save(next: Timetable) {
        val safe = next.normalized()
        store.save(safe)
        timetable = safe
    }
}
