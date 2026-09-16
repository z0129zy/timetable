# 界面（三 Tab + 网格 + 弹窗 + 导入）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把已跑通的解析器接上真正的界面：今天 / 课表 / 设置三个 Tab，课表页是参考风格网格并承担全部编辑动作，顶部栏按钮走 SAF 导入 PDF 并写入本地 JSON。

**Architecture:** 解析层（已完工）与界面层之间只隔一个状态容器 `AppState`：它持有唯一的 `Timetable`，所有改动都走 `data/TimetableEdits.kt` 里的纯函数拿到新副本，再交给 `TimetableStore` 落盘。可测的逻辑（编辑函数、校验、网格归并、取色）一律做成不依赖 Android 的纯函数并写单元测试；Compose 界面本身不写自动化测试，按设计文档 §9.1 手动验证。

**Tech Stack:** Kotlin 2.2.10、Jetpack Compose（BOM 2024.09.00，Material 3）、kotlinx.serialization、PDFBox-Android、JUnit 4。无导航库 —— 三个 Tab 用一个 `selectedTab` 整数切换即可。无 ViewModel 依赖 —— 状态持有者用 `remember` 建。

**规格来源：** `docs/superpowers/specs/2026-09-15-timetable-app-design.md`（下称「设计文档」）；视觉原型 `docs/design/week-v2-reference-style.html`、`today-page.html`、`inline-edit-flow.html`。

**已确认的两处视觉决策：**
1. 课程卡片按原型 week-v2 —— **整块淡色平涂**，圆角 4dp，内边距 3dp，课名加粗、场地行常规字重，同为 8sp。参考截图里「顶条 + 淡底」的画法不采用（用户已选）。
2. 界面只做浅色，不跟随系统深色，也不启用 Material You 动态取色 —— 原型全是浅色稿，且卡片本身就是浅色块，换配色解决不了；动态取色会把强调色改成系统壁纸色，与原型不符。

---

## 文件结构

**新增**

| 文件 | 职责 |
|---|---|
| `app/src/main/java/com/zhou/kebiao/data/TimetableEdits.kt` | 课表的纯编辑函数 + 表单校验。不改原对象、不碰文件 |
| `app/src/main/java/com/zhou/kebiao/ui/AppState.kt` | 状态容器：唯一的 Timetable、当前周次、导入、落盘 |
| `app/src/main/java/com/zhou/kebiao/ui/AppRoot.kt` | Scaffold + 底部三 Tab + 页面接线 + 导入结果提示 |
| `app/src/main/java/com/zhou/kebiao/ui/today/TodayScreen.kt` | 今天页 |
| `app/src/main/java/com/zhou/kebiao/ui/week/Slots.kt` | 纯逻辑：把课程归并成网格上的卡片块 |
| `app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt` | 课表页：顶栏 + 日期行 + 周次滑动 |
| `app/src/main/java/com/zhou/kebiao/ui/week/WeekGrid.kt` | 时间栏 + 7 天列 + 卡片 |
| `app/src/main/java/com/zhou/kebiao/ui/week/CandidateDialog.kt` | 候选课弹窗 + 删除二次确认 |
| `app/src/main/java/com/zhou/kebiao/ui/week/CourseFormDialog.kt` | 新增 / 编辑课程表单（含标黄） |
| `app/src/main/java/com/zhou/kebiao/ui/week/PeriodTimeDialog.kt` | 修改某一节作息时间 |
| `app/src/main/java/com/zhou/kebiao/ui/week/OtherCoursesDialog.kt` | 其他课程（纯查看） |
| `app/src/main/java/com/zhou/kebiao/ui/settings/SettingsScreen.kt` | 设置页 |
| `app/src/main/java/com/zhou/kebiao/ui/theme/Colors.kt` | 原型实测色板（注意是复数 `Colors`） |

**修改**

| 文件 | 改动 |
|---|---|
| `app/src/main/java/com/zhou/kebiao/ui/theme/Theme.kt` | 整份替换：固定浅色、关掉动态取色 |
| `app/src/main/java/com/zhou/kebiao/data/Models.kt` | 加 `firstMondayDate()`、`weekStartDate()`、`WeekType.label()`、`defaultTimetable()` |
| `app/src/main/java/com/zhou/kebiao/MainActivity.kt` | 改为挂 `AppRoot()` |

**删除**

| 文件 | 原因 |
|---|---|
| `app/src/main/java/com/zhou/kebiao/ui/DebugParseScreen.kt` | 解析器调试页，界面接上后整页替换（Task 6） |
| `app/src/main/java/com/zhou/kebiao/ui/theme/Color.kt` | 模板自带的紫色常量文件（单数），被 `Colors.kt` 取代后成了死代码（Task 1） |

**新增测试**

| 文件 | 覆盖 |
|---|---|
| `app/src/test/java/com/zhou/kebiao/ui/theme/ColorsTest.kt` | 取色稳定、负哈希不越界 |
| `app/src/test/java/com/zhou/kebiao/data/TimetableEditsTest.kt` | 增删改、改作息、表单校验 |
| `app/src/test/java/com/zhou/kebiao/ui/week/SlotsTest.kt` | 同格多课归并、本周生效优先、跨节次命中 |

---

### Task 1: 设计色板与浅色主题

**Files:**
- Modify: `app/src/main/java/com/zhou/kebiao/ui/theme/Colors.kt`（整份替换）
- Modify: `app/src/main/java/com/zhou/kebiao/ui/theme/Theme.kt`（整份替换）
- Test: `app/src/test/java/com/zhou/kebiao/ui/theme/ColorsTest.kt`

- [ ] **Step 1: 先写取色的失败测试**

新建 `app/src/test/java/com/zhou/kebiao/ui/theme/ColorsTest.kt`：

```kotlin
package com.zhou.kebiao.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课程卡片取色。
 * 规则是「同一门课永远同一个颜色」—— 按课程名散列取色，不按第几门课取色，
 * 否则增删一门课会让后面所有课一起换色。
 */
class ColorsTest {

    @Test
    fun `同一课程名取到的颜色固定`() {
        assertEquals(courseColor("电路"), courseColor("电路"))
        assertEquals(courseColor("机械设计基础★"), courseColor("机械设计基础★"))
    }

    @Test
    fun `哈希值为负的课程名也要落在色板内`() {
        // "polygenelubricants" 的 String.hashCode() 恰好是 Int.MIN_VALUE，
        // 是 abs() 会失手、Math.floorMod() 才对的经典例子。
        val name = "polygenelubricants"
        assertEquals(Int.MIN_VALUE, name.hashCode())
        assertTrue(courseColor(name) in CoursePalette)
    }

    @Test
    fun `批量取色都不越界`() {
        repeat(500) { i ->
            assertTrue(courseColor("课程$i★") in CoursePalette)
        }
    }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.ui.theme.ColorsTest" --console=plain`

Expected: 编译失败，`Unresolved reference: courseColor` / `CoursePalette`。

- [ ] **Step 3: 写色板**

整份替换 `app/src/main/java/com/zhou/kebiao/ui/theme/Colors.kt`：

```kotlin
package com.zhou.kebiao.ui.theme

import androidx.compose.ui.graphics.Color

// 全站配色，数值取自视觉原型 docs/design/week-v2-reference-style.html
// 与 today-page.html。参考截图里课程卡片是「顶条 + 淡底」，
// 原型是整块平涂 —— 已确认以原型为准。

/** 强调色：今天页「第 N 周」、候选课选中项、设置页可编辑的值。 */
val AccentBlue = Color(0xFF3B6FD4)

val InkPrimary = Color(0xFF222222)
val InkSecondary = Color(0xFF666666)
val InkTertiary = Color(0xFF999999)
val InkDisabled = Color(0xFFBBBBBB)

/** 块与块之间的分隔线。 */
val DividerLine = Color(0xFFEEEEEE)

/** 网格里每一节之间的细线。 */
val RowLine = Color(0xFFF0F0F0)

val NavBarBackground = Color(0xFFFAFAFA)
/** 日期行里「今天」那一格：黑底白字。 */
val TodayBadgeBackground = Color(0xFF111111)

/** 今天页：下一节的卡片底色。 */
val NextCourseBackground = Color(0xFFEAF1FD)

/** 今天页：已结束课程的左侧色条与底色。 */
val PastCourseBar = Color(0xFFBBBBBB)
val PastCourseBackground = Color(0xFFF4F4F4)

/** 课表页：本周不上的课，整块灰底 + 左上角「非本周」药丸。 */
val InactiveCourseBackground = Color(0xFFE2E2E2)
val InactiveCoursePill = Color(0xFF9A9A9A)

/** 课表页：格子右上角「该时段有 N 门课」的圈码。 */
val CandidateBadge = Color(0xFF4A7FE0)

/** 表单里「这个值是导入时的兜底值」的标黄（设计文档 §8）。 */
val MissingFieldBackground = Color(0xFFFFF3C4)
val MissingFieldBorder = Color(0xFFE6B800)

/** 表单校验不通过时的红字。三张表单（课程、作息、设置）共用同一个。 */
val ErrorRed = Color(0xFFB00020)

/** 课程卡片的 8 个底色，取自原型 week-v2。 */
val CoursePalette = listOf(
    Color(0xFFDDD6F3),   // 紫
    Color(0xFFFAD2D2),   // 粉红
    Color(0xFFF3D6E8),   // 粉
    Color(0xFFD3E3FA),   // 蓝
    Color(0xFFCFE9E5),   // 青
    Color(0xFFCFE4D6),   // 灰绿
    Color(0xFFFAE8BF),   // 鹅黄
    Color(0xFFFAD9C0),   // 橙
)

/**
 * 同一门课在任何位置都是同一个颜色：按课程名散列取色。
 * 用 floorMod 而不是 abs —— abs(Int.MIN_VALUE) 仍是负数，会越界。
 */
fun courseColor(name: String): Color =
    CoursePalette[Math.floorMod(name.hashCode(), CoursePalette.size)]
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.ui.theme.ColorsTest" --console=plain`

Expected: `BUILD SUCCESSFUL`，3 个用例通过。

- [ ] **Step 5: 换成固定浅色主题**

整份替换 `app/src/main/java/com/zhou/kebiao/ui/theme/Theme.kt`：

```kotlin
package com.zhou.kebiao.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 只做浅色：原型全是浅色稿，且课程卡片本身就是浅色块，深色模式不是换个配色
// 就能解决的。也不启用 Material You 动态取色 —— 那会把强调色改成系统壁纸色，
// 与原型不符。

private val LightColors = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = InkPrimary,
    surface = Color.White,
    onSurface = InkPrimary,
    surfaceVariant = PastCourseBackground,
    onSurfaceVariant = InkSecondary,
    outline = DividerLine,
)

@Composable
fun 课表Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content,
    )
}
```

- [ ] **Step 6: 编译，确认 Theme 换签名后没有残留调用方报错**

Run: `./gradlew :app:compileDebugKotlin --console=plain`

Expected: `BUILD SUCCESSFUL`。`MainActivity` 里是 `课表Theme { ... }`，新签名只收 `content`，不用改。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/ui/theme/Colors.kt \
        app/src/main/java/com/zhou/kebiao/ui/theme/Theme.kt \
        app/src/test/java/com/zhou/kebiao/ui/theme/ColorsTest.kt
git -c user.name=18711 -c user.email=18711@local commit -m "feat(ui): 原型实测色板与固定浅色主题"
```

- [x] **Step 8: 删掉模板遗留的 Color.kt（已完成，commit 4630d26）**

仓库里原本有一个 Android Studio 模板生成的 `ui/theme/Color.kt`（单数），里面是 `Purple80` 等 6 个紫色常量，只被旧版 `Theme.kt` 引用。Step 5 整份替换 `Theme.kt` 之后它就是死代码了。

先确认除了它自己没有任何引用：

Run: `grep -rn "Purple80\|Purple40\|PurpleGrey\|Pink80\|Pink40" app/src --include=*.kt`

Expected: 只有 `theme/Color.kt` 自身命中。删掉：

```bash
git rm app/src/main/java/com/zhou/kebiao/ui/theme/Color.kt
git -c user.name=18711 -c user.email=18711@local commit -m "chore(ui): 删掉模板遗留的 Color.kt，色板已迁到 Colors.kt"
```

留着它的坏处是 `Color.kt` 与 `Colors.kt` 只差一个字母，以后看包目录容易看错。

---

### Task 2: 课表的纯编辑函数与表单校验

**Files:**
- Modify: `app/src/main/java/com/zhou/kebiao/data/Models.kt`（追加，不删原有内容）
- Create: `app/src/main/java/com/zhou/kebiao/data/TimetableEdits.kt`
- Test: `app/src/test/java/com/zhou/kebiao/data/TimetableEditsTest.kt`

- [ ] **Step 1: 在 Models.kt 末尾追加模型辅助函数**

追加到 `app/src/main/java/com/zhou/kebiao/data/Models.kt` 的最后（`endOf` 之后）：

```kotlin
/** 开学第一周的周一。JSON 里存的是 ISO 字符串，用到时才转成日期。 */
fun Timetable.firstMondayDate(): LocalDate = LocalDate.parse(firstMonday)

/** 第 [week] 周周一的日期。日期行与「今天」的周次都靠它算。 */
fun Timetable.weekStartDate(week: Int): LocalDate =
    firstMondayDate().plusDays(((week - 1) * 7).toLong())

/** 周类型的中文名，用于表单单选与候选课列表。 */
fun WeekType.label(): String = when (this) {
    WeekType.EVERY -> "每周"
    WeekType.ODD -> "单周"
    WeekType.EVEN -> "双周"
}

/** 开学第一周周一的默认值。PDF 里没有这一项，是用户设的。 */
const val DEFAULT_FIRST_MONDAY = "2026-08-31"

/**
 * 还没导入过课表时的空课表。学期名、开学日期、总周数、作息时间都取默认值，
 * 课程列表为空 —— 界面据此区分「还没有课表」和「今天没课」。
 */
fun defaultTimetable(): Timetable = Timetable(
    semesterName = "2026-2027 第1学期",
    firstMonday = DEFAULT_FIRST_MONDAY,
    totalWeeks = 16,
    periods = defaultPeriods(),
    courses = emptyList(),
    otherCourses = emptyList(),
)
```

- [ ] **Step 2: 先写编辑函数的失败测试**

新建 `app/src/test/java/com/zhou/kebiao/data/TimetableEditsTest.kt`：

```kotlin
package com.zhou.kebiao.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TimetableEditsTest {

    private fun course(
        name: String = "电路★",
        day: Int = 3,
        startPeriod: Int = 1,
        endPeriod: Int = 2,
        startWeek: Int = 1,
        endWeek: Int = 16,
    ) = Course(
        name = name,
        startWeek = startWeek,
        endWeek = endWeek,
        weekType = WeekType.EVERY,
        dayOfWeek = day,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        location = "2-3-301",
        teacher = "何建军",
    )

    private val base = defaultTimetable().withCourseAdded(course()).withCourseAdded(course(name = "大学英语(3)★"))

    @Test
    fun `新增课程追加到末尾`() {
        val added = base.withCourseAdded(course(name = "物理实验(2)☆"))
        assertEquals(listOf("电路★", "大学英语(3)★", "物理实验(2)☆"), added.courses.map { it.name })
    }

    @Test
    fun `替换只影响指定下标`() {
        val replaced = base.withCourseReplaced(0, course(name = "换过的课★"))
        assertEquals(listOf("换过的课★", "大学英语(3)★"), replaced.courses.map { it.name })
    }

    @Test
    fun `删除只删指定下标`() {
        val removed = base.withCourseRemoved(0)
        assertEquals(listOf("大学英语(3)★"), removed.courses.map { it.name })
    }

    @Test
    fun `改已有节次的作息时间`() {
        val next = base.withPeriodTime(1, "08:10", "09:00")
        val first = next.periods.first { it.period == 1 }
        assertEquals("08:10", first.start)
        assertEquals("09:00", first.end)
        assertEquals(base.periods.size, next.periods.size)
    }

    @Test
    fun `原本没有的节次会补一条并且作息仍按节次有序`() {
        val withoutEleven = base.copy(periods = base.periods.filter { it.period != 11 })
        val next = withoutEleven.withPeriodTime(11, "21:00", "21:50")
        assertEquals(11, next.periods.last().period)
        assertEquals("21:00", next.periods.last().start)
        assertEquals(next.periods.sortedBy { it.period }, next.periods)
    }

    @Test
    fun `学期级设置各自独立更新`() {
        val next = base.withSemesterName("2027-2028 第1学期").withTotalWeeks(18).withFirstMonday("2027-08-30")
        assertEquals("2027-2028 第1学期", next.semesterName)
        assertEquals(18, next.totalWeeks)
        assertEquals("2027-08-30", next.firstMonday)
        assertEquals(base.courses, next.courses)   // 不动课程
    }

    @Test
    fun `开学日期能算成日期与每周周一`() {
        assertEquals(LocalDate.of(2026, 8, 31), base.firstMondayDate())
        assertEquals(LocalDate.of(2026, 8, 31), base.weekStartDate(1))
        assertEquals(LocalDate.of(2026, 9, 14), base.weekStartDate(3))
    }

    @Test
    fun `校验拦下课程名为空`() {
        assertEquals("课程名称不能为空", validateCourse(course(name = "  "), 16))
    }

    @Test
    fun `校验拦下周次越界`() {
        assertEquals("周次要填第 1 到第 16 周", validateCourse(course(endWeek = 17), 16))
        assertEquals("周次要填第 1 到第 16 周", validateCourse(course(startWeek = 0), 16))
    }

    @Test
    fun `校验拦下起始周晚于结束周`() {
        assertEquals("起始周不能晚于结束周", validateCourse(course(startWeek = 5, endWeek = 3), 16))
    }

    @Test
    fun `校验拦下节次越界与起始节晚于结束节`() {
        assertEquals("节次要填第 1 到第 11 节", validateCourse(course(startPeriod = 1, endPeriod = 12), 16))
        assertEquals("起始节不能晚于结束节", validateCourse(course(startPeriod = 6, endPeriod = 4), 16))
    }

    @Test
    fun `教室与老师留空不算错`() {
        val c = course().copy(location = "", teacher = "")
        assertNull(validateCourse(c, 16))
    }

    @Test
    fun `合法课程通过校验`() {
        assertNull(validateCourse(course(), 16))
        // 边界值本身是合法的
        assertNull(validateCourse(course(startWeek = 16, endWeek = 16), 16))
        assertNull(validateCourse(course(startPeriod = 1, endPeriod = 11), 16))
    }

    @Test
    fun `总周数为 0 的课表会被夹回 1 周`() {
        // 手改文件、或被旧版本/异常写入，都可能让磁盘上的 totalWeeks 变成 0。
        // 0 会让 AppState 启动路径上的 coerceIn(1, totalWeeks) 抛异常 ——
        // App 一打开就崩，界面里还救不回来。所以读进来必须先夹一次。
        assertEquals(1, base.copy(totalWeeks = 0).normalized().totalWeeks)
        assertEquals(1, base.copy(totalWeeks = -5).normalized().totalWeeks)
    }

    @Test
    fun `合法课表归一化后一个字节都不变`() {
        assertEquals(base, base.normalized())
        assertEquals(16, base.normalized().totalWeeks)
        assertEquals("2026-08-31", base.normalized().firstMonday)
    }

    @Test
    fun `开学日期烂掉时回退到默认值`() {
        // 启动路径上会 LocalDate.parse(firstMonday)，格式不对就抛异常 ——
        // App 一打开就崩，界面里救不回来
        assertEquals("2026-08-31", base.copy(firstMonday = "2026/08/31").normalized().firstMonday)
        assertEquals("2026-08-31", base.copy(firstMonday = "").normalized().firstMonday)
        assertEquals("2026-08-31", base.copy(firstMonday = "八月三十一日").normalized().firstMonday)
        // 换一个合法的开学日期不该被改
        assertEquals("2027-03-01", base.copy(firstMonday = "2027-03-01").normalized().firstMonday)
    }

    @Test
    fun `校验拦下星期越界`() {
        assertEquals("星期要在周一到周日之间", validateCourse(course(day = 0), 16))
        assertEquals("星期要在周一到周日之间", validateCourse(course(day = 8), 16))
        assertNull(validateCourse(course(day = 7), 16))
    }
}
```

- [ ] **Step 3: 跑测试，确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.data.TimetableEditsTest" --console=plain`

Expected: 编译失败，`Unresolved reference: withCourseAdded` 等。

- [ ] **Step 4: 写编辑函数**

新建 `app/src/main/java/com/zhou/kebiao/data/TimetableEdits.kt`：

```kotlin
package com.zhou.kebiao.data

import java.time.LocalDate

/**
 * 课表的编辑操作。全是纯函数 —— 返回一份新课表，不改原对象、不碰文件；
 * 落盘由 ui 层的 AppState 负责。
 *
 * 课程用「在 courses 里的下标」定位，不给 Course 加 id：设计文档 §5 限定
 * Course 只有 5 组字段 + missingFields，加 id 会连带改动数据模型与 JSON。
 */
fun Timetable.withCourseAdded(course: Course): Timetable =
    copy(courses = courses + course)

fun Timetable.withCourseReplaced(index: Int, course: Course): Timetable =
    copy(courses = courses.mapIndexed { i, old -> if (i == index) course else old })

fun Timetable.withCourseRemoved(index: Int): Timetable =
    copy(courses = courses.filterIndexed { i, _ -> i != index })

/** 改某一节的起止时间；该节原本没有作息条目（设计文档 §5.3 的情形）就补一条。 */
fun Timetable.withPeriodTime(period: Int, start: String, end: String): Timetable {
    val exists = periods.any { it.period == period }
    val updated = periods.map { if (it.period == period) it.copy(start = start, end = end) else it }
    val merged = if (exists) updated else updated + PeriodTime(period, start, end)
    return copy(periods = merged.sortedBy { it.period })
}

fun Timetable.withSemesterName(name: String): Timetable = copy(semesterName = name)
fun Timetable.withFirstMonday(iso: String): Timetable = copy(firstMonday = iso)

fun Timetable.withTotalWeeks(weeks: Int): Timetable = copy(totalWeeks = weeks)

/**
 * 把课表夹回合法范围。防的是「文件里的值烂了 → App 一打开就崩」——
 * 那种情况下界面里没有任何恢复手段，只能去系统设置里清应用数据。
 * 启动路径上有两处会抛异常，所以夹两项：
 *  - [totalWeeks] 喂给 `coerceIn(1, totalWeeks)`，为 0 时抛 IllegalArgumentException
 *  - [firstMonday] 喂给 `LocalDate.parse`，格式不对时抛 DateTimeParseException
 *
 * 只夹这两项是有意的：PDF 都不提供它们，都是用户自己设的，回退到默认值**不会丢课程数据**。
 * 课程里的脏字段（例如 dayOfWeek 越界）不动 —— 那是另一回事，悄悄把课挪到周一
 * 比让它显眼更糟。
 */
fun Timetable.normalized(): Timetable = copy(
    totalWeeks = totalWeeks.coerceAtLeast(1),
    firstMonday = firstMonday.takeIf(::isIsoDate) ?: DEFAULT_FIRST_MONDAY,
)

private fun isIsoDate(raw: String): Boolean =
    runCatching { LocalDate.parse(raw) }.isSuccess

/**
 * 课程表单校验（设计文档 §7.5）。
 * 返回 null 表示通过，否则是直接显示给用户的中文提示。
 * 教室地点与任课老师允许留空，不校验。
 */
fun validateCourse(course: Course, totalWeeks: Int): String? = when {
    course.name.isBlank() ->
        "课程名称不能为空"

    course.startWeek < 1 || course.startWeek > totalWeeks ||
        course.endWeek < 1 || course.endWeek > totalWeeks ->
        "周次要填第 1 到第 $totalWeeks 周"

    course.startWeek > course.endWeek ->
        "起始周不能晚于结束周"

    course.dayOfWeek < 1 || course.dayOfWeek > 7 ->
        "星期要在周一到周日之间"

    course.startPeriod < 1 || course.startPeriod > 11 ||
        course.endPeriod < 1 || course.endPeriod > 11 ->
        "节次要填第 1 到第 11 节"

    course.startPeriod > course.endPeriod ->
        "起始节不能晚于结束节"

    else -> null
}
```

- [ ] **Step 5: 跑测试，确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.data.TimetableEditsTest" --console=plain`

Expected: `BUILD SUCCESSFUL`，13 个用例通过。

- [ ] **Step 6: 跑全量单测，确认 Models.kt 的追加没碰坏既有用例**

Run: `./gradlew :app:testDebugUnitTest --console=plain`

Expected: `BUILD SUCCESSFUL`，解析器与存储的既有用例全绿。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/data/Models.kt \
        app/src/main/java/com/zhou/kebiao/data/TimetableEdits.kt \
        app/src/test/java/com/zhou/kebiao/data/TimetableEditsTest.kt
git -c user.name=18711 -c user.email=18711@local commit -m "feat(data): 课表编辑纯函数与表单校验"
```

> 注意单测在 `app/src/test/` 下，不是 `app/src/main/`。
```

---

### Task 3: 网格归并（同格多课 → 一块卡片）

**Files:**
- Create: `app/src/main/java/com/zhou/kebiao/ui/week/Slots.kt`
- Test: `app/src/test/java/com/zhou/kebiao/ui/week/SlotsTest.kt`

**为什么需要它：** 教务系统会把「同一天、同一节、不同周段」的课挤在同一格（设计文档 §3.3 情形 2：周五 1-2 节既是国家安全教育 10-11 周、又是形势与政策 13-14 周）。一个格子放不下两门课，所以一格只画一块卡片：本周要上的那门优先显示，其余用右上角圈码标出数量，点开才是候选课列表（§7.3）。

- [ ] **Step 1: 先写归并的失败测试**

新建 `app/src/test/java/com/zhou/kebiao/ui/week/SlotsTest.kt`：

```kotlin
package com.zhou.kebiao.ui.week

import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.WeekType
import org.junit.Assert.assertEquals
import org.junit.Test

class SlotsTest {

    private fun course(
        name: String,
        day: Int = 1,
        startPeriod: Int = 5,
        endPeriod: Int = 6,
        startWeek: Int = 1,
        endWeek: Int = 16,
        weekType: WeekType = WeekType.EVERY,
    ) = Course(
        name = name,
        startWeek = startWeek,
        endWeek = endWeek,
        weekType = weekType,
        dayOfWeek = day,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        location = "2-1-313",
        teacher = "王淑芬",
    )

    @Test
    fun `同一格的课合成一块卡片`() {
        // 周一 5-6 节：英语 1-15 周单周，概率论 2-16 周双周（设计文档 §3.3 情形 1）
        val courses = listOf(
            course("大学英语(3)☆", startWeek = 1, endWeek = 15, weekType = WeekType.ODD),
            course("概率论与数理统计B★", startWeek = 2, endWeek = 16, weekType = WeekType.EVEN),
        )
        val slots = buildSlots(courses, week = 3)

        assertEquals(1, slots.size)
        val slot = slots.getValue(1 to 5)
        assertEquals(listOf(0, 1), slot.courseIndexes)
        assertEquals(2, slot.courseIndexes.size)      // 圈码显示「2」
        assertEquals(0, slot.representative)          // 第 3 周是单周，英语要上
    }

    @Test
    fun `本周都不上时卡片显示第一门`() {
        // 周五 1-2 节：国家安全教育 10-11 周、形势与政策 13-14 周（§3.3 情形 2）
        // 节次必须显式传 —— course() 的默认值是 5-6 节，不传就归并到 5 to 5，取不到 5 to 1
        val courses = listOf(
            course("国家安全教育（3）★", day = 5, startPeriod = 1, endPeriod = 2, startWeek = 10, endWeek = 11),
            course("形势与政策(3)★", day = 5, startPeriod = 1, endPeriod = 2, startWeek = 13, endWeek = 14),
        )
        val slots = buildSlots(courses, week = 3)

        val slot = slots.getValue(5 to 1)
        assertEquals(2, slot.courseIndexes.size)
        assertEquals(0, slot.representative)
    }

    @Test
    fun `跨节次的卡片占到最后的一节`() {
        val courses = listOf(
            course("物理实验(2)☆", day = 2, startPeriod = 2, endPeriod = 4),
            course("同格的短课★", day = 2, startPeriod = 2, endPeriod = 2),
        )
        val slots = buildSlots(courses, week = 3)

        assertEquals(4, slots.getValue(2 to 2).endPeriod)
    }

    @Test
    fun `不同起点的课各占一块`() {
        val courses = listOf(
            course("早课★", day = 1, startPeriod = 1, endPeriod = 2),
            course("晚课★", day = 1, startPeriod = 5, endPeriod = 6),
        )
        val slots = buildSlots(courses, week = 3)

        assertEquals(setOf(1 to 1, 1 to 5), slots.keys)
    }

    @Test
    fun `跨节次的卡片只占自己的那一块`() {
        val courses = listOf(course("金工实习☆", day = 5, startPeriod = 5, endPeriod = 8))
        val slots = buildSlots(courses, week = 3)

        assertEquals(setOf(5 to 5), slots.keys)
        assertEquals(8, slots.getValue(5 to 5).endPeriod)
    }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.ui.week.SlotsTest" --console=plain`

Expected: 编译失败，`Unresolved reference: buildSlots` / `Slot`。

- [ ] **Step 3: 写归并逻辑**

新建 `app/src/main/java/com/zhou/kebiao/ui/week/Slots.kt`（**不要引入任何 Compose 依赖** —— 这个文件要能在普通 JVM 单测里跑）：

```kotlin
package com.zhou.kebiao.ui.week

import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.isActiveIn

/**
 * 网格上的一块卡片。同一个「星期 + 起始节」可能压着多门课
 * （教务系统把同格不同周段的课挤在一起，见设计文档 §3.3 情形 2），
 * 故课程是复数。
 */
data class Slot(
    val dayOfWeek: Int,
    val startPeriod: Int,
    /** 这块卡片要占到的最后一节：取同格所有课里最靠下的那一门。 */
    val endPeriod: Int,
    /** 压在这一格的全部课在 Timetable.courses 里的下标，保持原顺序。 */
    val courseIndexes: List<Int>,
    /** 卡片上显示哪一门：本周要上的优先；本周都不上就显示第一门。 */
    val representative: Int,
)

/** 把课程按「星期 + 起始节」归并。键 = (星期, 起始节)。 */
fun buildSlots(courses: List<Course>, week: Int): Map<Pair<Int, Int>, Slot> =
    courses.indices
        .groupBy { courses[it].dayOfWeek to courses[it].startPeriod }
        .mapValues { (key, indexes) ->
            Slot(
                dayOfWeek = key.first,
                startPeriod = key.second,
                endPeriod = indexes.maxOf { courses[it].endPeriod },
                courseIndexes = indexes,
                representative = indexes.firstOrNull { courses[it].isActiveIn(week) }
                    ?: indexes.first(),
            )
        }
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.ui.week.SlotsTest" --console=plain`

Expected: `BUILD SUCCESSFUL`，5 个用例通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/ui/week/Slots.kt \
        app/src/test/java/com/zhou/kebiao/ui/week/SlotsTest.kt
git -c user.name=18711 -c user.email=18711@local commit -m "feat(ui): 网格卡片归并，同格多课与跨节次"
```

---

### Task 4: 导入结果的落点（要不要写入）

**Files:**
- Create: `app/src/main/java/com/zhou/kebiao/ui/ImportOutcome.kt`
- Test: `app/src/test/java/com/zhou/kebiao/ui/ImportOutcomeTest.kt`

**为什么单独一个纯函数：** 「导入要不要写入」是设计文档里唯一一处会**毁掉用户数据**的判断（§8.1：0 门课拒绝写入；§8：模板不匹配不写入）。把它从 Android 的 IO 里剥出来，才能真正被单测盯住 —— 留在 `AppState` 里就只能靠手点。

- [ ] **Step 1: 先写失败测试**

新建 `app/src/test/java/com/zhou/kebiao/ui/ImportOutcomeTest.kt`：

```kotlin
package com.zhou.kebiao.ui

import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.OtherCourse
import com.zhou.kebiao.data.WeekType
import com.zhou.kebiao.data.defaultPeriods
import com.zhou.kebiao.data.defaultTimetable
import com.zhou.kebiao.data.withCourseAdded
import com.zhou.kebiao.data.withPeriodTime
import com.zhou.kebiao.parser.ParseResult
import com.zhou.kebiao.parser.PdfHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportOutcomeTest {

    private fun course(name: String) = Course(
        name = name,
        startWeek = 1,
        endWeek = 16,
        weekType = WeekType.EVERY,
        dayOfWeek = 1,
        startPeriod = 5,
        endPeriod = 6,
        location = "2-1-313",
        teacher = "王淑芬",
    )

    private val other = OtherCourse(
        name = "电路基础实验☆",
        startWeek = 1,
        endWeek = 16,
        totalWeeks = 16,
        location = "无",
        teacher = "何建军",
    )

    /**
     * 用户已经改过设置、也已经有课的现有课表。
     * 三样设置**都要真的与默认值不同**，否则「导入不动设置」的断言是空的
     * —— 把 periods 留在默认值上，「next.periods == existing.periods」就只等价于
     * 「next.periods 还是默认值」，实现里手滑写成 periods = defaultPeriods() 也照样绿。
     */
    private val existing = defaultTimetable()
        .withCourseAdded(course("旧课★"))
        .withPeriodTime(1, "08:20", "09:10")
        .copy(
            semesterName = "用户自己改的学期名",
            firstMonday = "2026-08-24",
            totalWeeks = 18,
        )

    private fun success(
        semesterName: String = "2026-2027 第1学期",
        courses: List<Course> = listOf(course("新课★")),
        others: List<OtherCourse> = listOf(other),
    ) = ParseResult.Success(PdfHeader(semesterName = semesterName), courses, others)

    @Test
    fun `解析失败时拒绝写入并原样带上原因`() {
        val outcome = existing.afterImport(ParseResult.Failure("这份课表认不出来，可能是别的学校的模板"))
        assertEquals(
            ImportOutcome.Rejected("这份课表认不出来，可能是别的学校的模板"),
            outcome,
        )
    }

    @Test
    fun `解析出 0 门课时拒绝写入`() {
        // 设计文档 §8.1 的底线：宁可什么都不做，也不能把空数据写进去抹掉用户的课表。
        val outcome = existing.afterImport(success(courses = emptyList()))
        assertEquals(
            ImportOutcome.Rejected("这份课表认不出来，可能是别的学校的模板"),
            outcome,
        )
    }

    @Test
    fun `导入成功时替换课程与其他课程`() {
        val outcome = existing.afterImport(success())
        assertTrue(outcome is ImportOutcome.Applied)
        val next = (outcome as ImportOutcome.Applied).timetable
        assertEquals(listOf("新课★"), next.courses.map { it.name })
        assertEquals(listOf(other), next.otherCourses)
    }

    @Test
    fun `导入不动用户改过的开学日期总周数与作息时间`() {
        // PDF 里没有这三样，导入不该把它们冲回默认值。
        // 先证明夹具里的值确实偏离默认 —— 否则下面的断言测不出任何东西。
        assertNotEquals(defaultTimetable().firstMonday, existing.firstMonday)
        assertNotEquals(defaultTimetable().totalWeeks, existing.totalWeeks)
        assertNotEquals(
            defaultPeriods().first { it.period == 1 },
            existing.periods.first { it.period == 1 },
        )

        val next = (existing.afterImport(success()) as ImportOutcome.Applied).timetable
        assertEquals("2026-08-24", next.firstMonday)
        assertEquals(18, next.totalWeeks)
        assertEquals(existing.periods, next.periods)
        assertEquals("08:20", next.periods.first { it.period == 1 }.start)
    }

    @Test
    fun `页眉有学期名时采用页眉的`() {
        val next = (existing.afterImport(success(semesterName = "2026-2027 第1学期")) as ImportOutcome.Applied).timetable
        assertEquals("2026-2027 第1学期", next.semesterName)
    }

    @Test
    fun `页眉读不出学期名时保留用户原来的`() {
        val next = (existing.afterImport(success(semesterName = "")) as ImportOutcome.Applied).timetable
        assertEquals("用户自己改的学期名", next.semesterName)
    }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.ui.ImportOutcomeTest" --console=plain`

Expected: 编译失败，`Unresolved reference: afterImport` / `ImportOutcome`。

- [ ] **Step 3: 写落点函数**

新建 `app/src/main/java/com/zhou/kebiao/ui/ImportOutcome.kt`：

```kotlin
package com.zhou.kebiao.ui

import com.zhou.kebiao.data.Timetable
import com.zhou.kebiao.parser.ParseResult

/** 一次导入的去向。 */
sealed interface ImportOutcome {
    /** 拒绝写入，[reason] 直接显示给用户，现有课表原封不动。 */
    data class Rejected(val reason: String) : ImportOutcome

    /** 写入这份新课表。 */
    data class Applied(val timetable: Timetable) : ImportOutcome
}

/**
 * 把解析结果落成一份新课表（设计文档 §8、§8.1）。
 *
 * 三条规矩：
 *  - 解析失败 → 拒绝，原样把解析器给的话术带给用户
 *  - 0 门课 → 拒绝。§8.1 的底线，也是「选错文件」与「模板不匹配」的最后一道闸
 *  - 成功 → 替换课程与其他课程，但**不动**开学日期、总周数、作息时间：
 *    这三样 PDF 里没有，是用户在设置页调过的，冲掉就是白调
 *
 * 学期名只在页眉读得出时才覆盖，读不出就留着用户原来的。
 */
fun Timetable.afterImport(parsed: ParseResult): ImportOutcome = when (parsed) {
    is ParseResult.Failure -> ImportOutcome.Rejected(parsed.reason)

    is ParseResult.Success -> if (parsed.courses.isEmpty()) {
        ImportOutcome.Rejected("这份课表认不出来，可能是别的学校的模板")
    } else {
        ImportOutcome.Applied(
            copy(
                semesterName = parsed.header.semesterName.ifBlank { semesterName },
                courses = parsed.courses,
                otherCourses = parsed.otherCourses,
            )
        )
    }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.ui.ImportOutcomeTest" --console=plain`

Expected: `BUILD SUCCESSFUL`，6 个用例通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/ui/ImportOutcome.kt \
        app/src/test/java/com/zhou/kebiao/ui/ImportOutcomeTest.kt
git -c user.name=18711 -c user.email=18711@local commit -m "feat(ui): 导入落点纯函数，0 门课拒绝写入"
```

---

### Task 5: 状态容器 AppState

**Files:**
- Create: `app/src/main/java/com/zhou/kebiao/ui/AppState.kt`

**说明：** 这一层是 Android 边界（要 `Context` 读 `contentResolver`、要写文件），按设计文档 §9.1 不写自动化测试；它能被验证的部分（落点判断）已经在 Task 4 剥出去测掉了，这里只剩 IO 接线。

- [ ] **Step 1: 写状态容器**

新建 `app/src/main/java/com/zhou/kebiao/ui/AppState.kt`：

```kotlin
package com.zhou.kebiao.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
```

- [ ] **Step 2: 编译**

Run: `./gradlew :app:compileDebugKotlin --console=plain`

Expected: `BUILD SUCCESSFUL`。此时还没有任何界面用它，编译通过即可。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/ui/AppState.kt
git -c user.name=18711 -c user.email=18711@local commit -m "feat(ui): 状态容器与导入流程"
```

---

### Task 6: 三 Tab 骨架，删掉调试页

**Files:**
- Create: `app/src/main/java/com/zhou/kebiao/ui/AppRoot.kt`
- Create: `app/src/main/java/com/zhou/kebiao/ui/today/TodayScreen.kt`（**占位版**，Task 13 整份替换）
- Create: `app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt`（**占位版**，Task 7 整份替换）
- Create: `app/src/main/java/com/zhou/kebiao/ui/settings/SettingsScreen.kt`（**占位版**，Task 14 整份替换）
- Modify: `app/src/main/java/com/zhou/kebiao/MainActivity.kt`
- Delete: `app/src/main/java/com/zhou/kebiao/ui/DebugParseScreen.kt`

**说明：** 三个页面先放占位实现，只为让骨架当次就能编译、能装到机器上看到三个 Tab。占位版随后由各自的 Task 整份替换，不会留到最终版本里。

- [ ] **Step 1: 写三个占位页面**

新建 `app/src/main/java/com/zhou/kebiao/ui/today/TodayScreen.kt`：

```kotlin
package com.zhou.kebiao.ui.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zhou.kebiao.ui.AppState

/** 占位：Task 13 整份替换。 */
@Composable
fun TodayScreen(state: AppState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("今天")
    }
}
```

新建 `app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt`：

```kotlin
package com.zhou.kebiao.ui.week

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zhou.kebiao.ui.AppState

/** 占位：Task 7 整份替换。 */
@Composable
fun WeekScreen(state: AppState, onRequestImport: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("课表")
    }
}
```

新建 `app/src/main/java/com/zhou/kebiao/ui/settings/SettingsScreen.kt`：

```kotlin
package com.zhou.kebiao.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zhou.kebiao.ui.AppState

/** 占位：Task 14 整份替换。 */
@Composable
fun SettingsScreen(state: AppState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("设置")
    }
}
```

- [ ] **Step 2: 写根组件**

新建 `app/src/main/java/com/zhou/kebiao/ui/AppRoot.kt`：

```kotlin
package com.zhou.kebiao.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.zhou.kebiao.data.TimetableStore
import com.zhou.kebiao.ui.settings.SettingsScreen
import com.zhou.kebiao.ui.theme.AccentBlue
import com.zhou.kebiao.ui.theme.InkPrimary
import com.zhou.kebiao.ui.theme.InkTertiary
import com.zhou.kebiao.ui.theme.NavBarBackground
import com.zhou.kebiao.ui.today.TodayScreen
import com.zhou.kebiao.ui.week.WeekScreen
import kotlinx.coroutines.launch
import java.io.File

private const val TAB_TODAY = 0
private const val TAB_WEEK = 1
private const val TAB_SETTINGS = 2

/**
 * 应用根：底部三 Tab + 导入的启动器。
 *
 * 选文件的启动器与协程作用域放在这一层而不是课表页，是因为导入要跑几秒，
 * 而 rememberCoroutineScope() 会跟着页面离开组合而被取消 —— 用户点了导入
 * 再切到「今天」页，导入就会被从中掐断。
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val state = remember {
        val app = context.applicationContext
        AppState(app, TimetableStore(File(app.filesDir, "timetable.json")))
    }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(TAB_TODAY) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch { state.importPdf(uri) }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = NavBarBackground) {
                NavigationBarItem(
                    selected = tab == TAB_TODAY,
                    onClick = { tab = TAB_TODAY },
                    icon = { Text("🏠", fontSize = 16.sp) },
                    label = { Text("今天") },
                    colors = navItemColors(),
                )
                NavigationBarItem(
                    selected = tab == TAB_WEEK,
                    onClick = { tab = TAB_WEEK },
                    icon = { Text("📅", fontSize = 16.sp) },
                    label = { Text("课表") },
                    colors = navItemColors(),
                )
                NavigationBarItem(
                    selected = tab == TAB_SETTINGS,
                    onClick = { tab = TAB_SETTINGS },
                    icon = { Text("⚙", fontSize = 16.sp) },
                    label = { Text("设置") },
                    colors = navItemColors(),
                )
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (tab) {
                TAB_TODAY -> TodayScreen(state)
                TAB_WEEK -> WeekScreen(
                    state = state,
                    onRequestImport = { picker.launch(arrayOf("application/pdf")) },
                )
                else -> SettingsScreen(state)
            }

            // 转圈期间必须真把点击吃掉。只铺一层半透明背景是不够的 ——
            // 没有 clickable 的 Box 不消费指针事件，下面的「＋」照样能点到，
            // 会重复触发导入。
            if (state.importing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99FFFFFF))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            }
        }
    }

    state.importError?.let { reason ->
        AlertDialog(
            onDismissRequest = { state.dismissImportError() },
            title = { Text("没能导入") },
            text = { Text(reason) },
            confirmButton = {
                TextButton(onClick = { state.dismissImportError() }) { Text("知道了") }
            },
        )
    }
}

/** 原型里底部导航没有选中胶囊，选中项只是把字色从灰变黑。 */
@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = InkPrimary,
    selectedTextColor = InkPrimary,
    indicatorColor = Color.Transparent,
    unselectedIconColor = InkTertiary,
    unselectedTextColor = InkTertiary,
)
```

- [ ] **Step 3: MainActivity 改挂根组件**

整份替换 `app/src/main/java/com/zhou/kebiao/MainActivity.kt`：

```kotlin
package com.zhou.kebiao

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zhou.kebiao.ui.AppRoot
import com.zhou.kebiao.ui.theme.课表Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            课表Theme {
                AppRoot()
            }
        }
    }
}
```

- [ ] **Step 4: 删掉调试页**

```bash
git rm app/src/main/java/com/zhou/kebiao/ui/DebugParseScreen.kt
```

- [ ] **Step 5: 编译并构建 APK，确认删了调试页没有悬挂引用**

Run: `./gradlew :app:assembleDebug --console=plain`

Expected: `BUILD SUCCESSFUL`。若有 `Unresolved reference: DebugParseScreen`，说明还有地方引用它 —— 全局搜一遍清掉。

- [ ] **Step 6: 装机看三个 Tab 能切换**

Run: `./gradlew :app:installDebug --console=plain`，然后在设备/模拟器上打开 App。

Expected: 底部三个 Tab「今天 / 课表 / 设置」都在，点击能切；选中项是黑字，未选中是灰字。这一步只验骨架，三个页面此刻都还是占位文字。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/ui/AppRoot.kt \
        app/src/main/java/com/zhou/kebiao/ui/today/TodayScreen.kt \
        app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt \
        app/src/main/java/com/zhou/kebiao/ui/settings/SettingsScreen.kt \
        app/src/main/java/com/zhou/kebiao/MainActivity.kt
git -c user.name=18711 -c user.email=18711@local commit -m "feat(ui): 三 Tab 骨架，移除解析调试页"
```

---

### Task 7: 课表页——顶栏、日期行、网格、周次滑动

**Files:**
- Modify: `app/src/main/java/com/zhou/kebiao/ui/week/Slots.kt`（追加纯展示辅助）
- Modify: `app/src/test/java/com/zhou/kebiao/ui/week/SlotsTest.kt`（追加这些辅助的用例）
- Create: `app/src/main/java/com/zhou/kebiao/ui/week/WeekGrid.kt`
- Replace: `app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt`

**这一版网格是只读的：** 点格子、点时间栏的点击回调先留空，Task 8 / 9 / 10 各自接上自己的弹窗。这样每个 Task 结束时都是能编译、能装机的完整状态，不留半成品。

**两处与原型不同的取舍（已确认）：**
- 7 天的列宽用 `weight(1f)` 平分剩余宽度，而不是原型的固定 53dp —— 原型是按 411dp 宽的屏画的，固定值在 360dp 的屏上会横向溢出。
- 周次切换用 `◀ 第3周 ▶`，不是原型的 `第3周 ▾`。设计文档 §7.2 明确要求「与顶部 ◀ ▶ 箭头一致」，行为以设计文档为准。

- [ ] **Step 1: 先写纯展示辅助的失败测试**

把下面这些用例追加到 `app/src/test/java/com/zhou/kebiao/ui/week/SlotsTest.kt` 的最后一个 `}` **之前**：

```kotlin
    @Test
    fun `课名末尾的类型符号会被拆出来`() {
        // 原型把符号挪到场地行前（「大学英语(3)」/「★@2-1-313」），
        // 8sp 的窄列里这样能少挤一个字。
        assertEquals("大学英语(3)" to "★", splitTypeSymbol("大学英语(3)★"))
        assertEquals("大学汉语(2)" to "☆", splitTypeSymbol("大学汉语(2)☆"))
        assertEquals("电子产品设计" to "■", splitTypeSymbol("电子产品设计■"))
    }

    @Test
    fun `课名没有类型符号时原样返回`() {
        assertEquals("机械设计基础" to "", splitTypeSymbol("机械设计基础"))
    }

    @Test
    fun `空课名不会炸`() {
        assertEquals("" to "", splitTypeSymbol(""))
    }

    @Test
    fun `时间格式化成分钟精度`() {
        assertEquals("08:05", hhmm(LocalTime.of(8, 5)))
        assertEquals("19:00", hhmm(LocalTime.of(19, 0)))
    }

    @Test
    fun `星期几的中文名`() {
        assertEquals("周一", weekdayFull(1))
        assertEquals("周日", weekdayFull(7))
    }
```

并在该文件的 import 区补上：

```kotlin
import org.junit.Assert.assertEquals
import java.time.LocalTime
```

（`org.junit.Assert.assertEquals` 原本就有，重复加会报错 —— 只补 `java.time.LocalTime` 即可。）

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.ui.week.SlotsTest" --console=plain`

Expected: 编译失败，`Unresolved reference: splitTypeSymbol` / `hhmm` / `weekdayFull`。

- [ ] **Step 3: 往 Slots.kt 追加这些辅助**

追加到 `app/src/main/java/com/zhou/kebiao/ui/week/Slots.kt` 末尾（并把文件顶部的 import 区补成下面这样）：

```kotlin
import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.isActiveIn
import java.time.LocalTime
import java.time.format.DateTimeFormatter
```

```kotlin
/** 课名末尾的类型符号（设计文档 §5.4：符号并进了课名）。 */
private const val TYPE_SYMBOLS = "★☆◆■〇"

/**
 * 把课名末尾的类型符号拆出来，返回 (课名主体, 符号)。
 * 课名没有符号时第二部分是空串。
 */
fun splitTypeSymbol(name: String): Pair<String, String> =
    if (name.isNotEmpty() && name.last() in TYPE_SYMBOLS) {
        name.dropLast(1) to name.last().toString()
    } else {
        name to ""
    }

/** 「一」…「日」，下标 0 = 周一。 */
val WEEKDAY_SHORT = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 1 → 「周一」… 7 → 「周日」。
 * 下标先夹一次：脏数据（dayOfWeek 越界）不该让界面崩在组合期 ——
 * 这个函数会在编辑表单里被直接调用，而那时还没轮到校验。
 */
fun weekdayFull(day: Int): String = "周" + WEEKDAY_SHORT[(day - 1).coerceIn(0, WEEKDAY_SHORT.lastIndex)]

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

/** 只用于展示。解析不到的节次时间上游已经给了 null，不会走到这里。 */
fun hhmm(time: LocalTime): String = time.format(HHMM)
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.ui.week.SlotsTest" --console=plain`

Expected: `BUILD SUCCESSFUL`，10 个用例通过。

- [ ] **Step 5: 写网格**

新建 `app/src/main/java/com/zhou/kebiao/ui/week/WeekGrid.kt`：

```kotlin
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.PeriodTime
import com.zhou.kebiao.data.Timetable
import com.zhou.kebiao.data.endOf
import com.zhou.kebiao.data.isActiveIn
import com.zhou.kebiao.data.startOf
import com.zhou.kebiao.ui.theme.CandidateBadge
import com.zhou.kebiao.ui.theme.InactiveCourseBackground
import com.zhou.kebiao.ui.theme.InactiveCoursePill
import com.zhou.kebiao.ui.theme.InkPrimary
import com.zhou.kebiao.ui.theme.InkSecondary
import com.zhou.kebiao.ui.theme.RowLine
import com.zhou.kebiao.ui.theme.courseColor

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
 */
@Composable
fun WeekGrid(
    timetable: Timetable,
    week: Int,
    onSlotClick: (Slot) -> Unit,
    onEmptyCellClick: (day: Int, period: Int) -> Unit,
    onPeriodClick: (period: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = remember(timetable.courses, week) { buildSlots(timetable.courses, week) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
        fontSize = 8.sp,
        lineHeight = 10.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
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
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) courseColor(course.name) else InactiveCourseBackground)
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        Column {
            if (!active) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(InactiveCoursePill)
                        .padding(horizontal = 3.dp),
                ) {
                    Text("非本周", fontSize = 7.sp, lineHeight = 9.sp, color = Color.White)
                }
                Spacer(Modifier.height(2.dp))
            }
            Text(name, fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, color = InkPrimary)
            if (sub.isNotEmpty()) {
                Text(sub, fontSize = 8.sp, lineHeight = 10.sp, color = InkPrimary)
            }
        }

        // 同一格压着多门课时，右上角圈出数量（设计文档 §7.2）
        if (slot.courseIndexes.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(CandidateBadge)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${slot.courseIndexes.size}", fontSize = 7.sp, lineHeight = 9.sp, color = Color.White)
            }
        }
    }
}
```

- [ ] **Step 6: 写课表页**

整份替换 `app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt`：

```kotlin
package com.zhou.kebiao.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhou.kebiao.data.Timetable
import com.zhou.kebiao.data.weekStartDate
import com.zhou.kebiao.ui.AppState
import com.zhou.kebiao.ui.theme.DividerLine
import com.zhou.kebiao.ui.theme.InkPrimary
import com.zhou.kebiao.ui.theme.InkSecondary
import com.zhou.kebiao.ui.theme.InkTertiary
import com.zhou.kebiao.ui.theme.TodayBadgeBackground
import java.time.LocalDate

/** 左右滑动多少像素算翻一周。 */
private const val SWIPE_THRESHOLD = 80f

@Composable
fun WeekScreen(state: AppState, onRequestImport: () -> Unit) {
    val timetable = state.timetable
    val week = state.currentWeek

    Column(Modifier.fillMaxSize().background(Color.White)) {
        WeekTopBar(
            week = week,
            semesterName = timetable.semesterName,
            onPrevious = state::showPreviousWeek,
            onNext = state::showNextWeek,
            onOtherCourses = { /* Task 12 接上 */ },
            onRequestImport = onRequestImport,
        )
        WeekDateHeader(timetable, week)

        WeekGrid(
            timetable = timetable,
            week = week,
            onSlotClick = { /* Task 8 接上 */ },
            onEmptyCellClick = { _, _ -> /* Task 9 接上 */ },
            onPeriodClick = { /* Task 11 接上 */ },
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
        GlyphButton("◀", onPrevious)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                text = "第$week 周",
                fontSize = 15.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                color = InkPrimary,
            )
            Text(
                text = semesterName,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                color = InkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GlyphButton("▶", onNext)
        Text(
            text = "其他课程",
            fontSize = 9.sp,
            color = InkSecondary,
            modifier = Modifier.clickable(onClick = onOtherCourses).padding(4.dp),
        )
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(TodayBadgeBackground)
                .clickable(onClick = onRequestImport),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", fontSize = 14.sp, lineHeight = 16.sp, color = Color.White)
        }
    }
    HorizontalDivider(color = DividerLine, thickness = 1.dp)
}

@Composable
private fun GlyphButton(glyph: String, onClick: () -> Unit) {
    Text(
        text = glyph,
        fontSize = 13.sp,
        color = InkSecondary,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp),
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
            Text("${monday.monthValue}月", fontSize = 9.sp, lineHeight = 11.sp, color = InkTertiary)
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
                            .background(TodayBadgeBackground)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    } else {
                        Modifier
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = WEEKDAY_SHORT[day - 1],
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = if (isToday) Color.White else InkSecondary,
                    )
                    Text(
                        text = "${date.dayOfMonth}",
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) Color.White else InkPrimary,
                    )
                }
            }
        }
    }
    HorizontalDivider(color = DividerLine, thickness = 1.dp)
}
```

- [ ] **Step 7: 编译**

Run: `./gradlew :app:compileDebugKotlin --console=plain`

Expected: `BUILD SUCCESSFUL`。若报 `Unresolved reference: HorizontalDivider`，说明 material3 版本偏低 —— 换成 `Divider(color = DividerLine, thickness = 1.dp)`。

- [ ] **Step 8: 装机核对网格**

Run: `./gradlew :app:installDebug --console=plain`，打开 App → 课表 Tab。

Expected（此刻还没导入过课表，网格是空的，但日期与周次已经有值）：
- 顶栏左边 `◀`、中间 `第N 周` 与学期名、右边 `▶ 其他课程 ＋`
  **`N` 是当前自然周，不是 1** —— 周次由 `defaultTimetable()` 的开学第一周周一（2026-08-31）与今天算出（设计文档 §5.2），跟有没有导入课表无关。今天若是 2026-09-15，N 就是 3
- 日期行左边 `9月`，7 列显示 `一 14`…`日 20` 这样的日期（第 3 周是 9/14–9/20）
- 时间栏 11 行，每行 `1 / 08:05 / 08:55` 三行小字
- 上下能滚动，每节之间有细横线
- 左右滑动能翻周次，标题的周数跟着变；在第 1 周右滑、在第 16 周左滑都没有反应
- 点 ＋ 会弹出系统选文件界面（但导入的结果此刻还没接到课表上 —— Task 8 起才逐步接）

- [ ] **Step 9: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/ui/week/Slots.kt \
        app/src/test/java/com/zhou/kebiao/ui/week/SlotsTest.kt \
        app/src/main/java/com/zhou/kebiao/ui/week/WeekGrid.kt \
        app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt
git -c user.name=18711 -c user.email=18711@local commit -m "feat(ui): 课表页网格、日期行与周次滑动"
```

---

### Task 8: 候选课弹窗与删除二次确认

**Files:**
- Create: `app/src/main/java/com/zhou/kebiao/ui/week/CandidateDialog.kt`
- Modify: `app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt`

- [ ] **Step 1: 写候选课弹窗**

新建 `app/src/main/java/com/zhou/kebiao/ui/week/CandidateDialog.kt`：

```kotlin
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.Timetable
import com.zhou.kebiao.data.endOf
import com.zhou.kebiao.data.isActiveIn
import com.zhou.kebiao.data.label
import com.zhou.kebiao.data.startOf
import com.zhou.kebiao.ui.theme.AccentBlue
import com.zhou.kebiao.ui.theme.InkDisabled
import com.zhou.kebiao.ui.theme.InkSecondary
import com.zhou.kebiao.ui.theme.InkTertiary
import com.zhou.kebiao.ui.theme.NextCourseBackground

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
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "${weekdayFull(slot.dayOfWeek)} · 第 ${slot.startPeriod}-${slot.endPeriod} 节",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    fontSize = 9.sp,
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
                    fontSize = 9.sp,
                    color = AccentBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddHere)
                        .padding(vertical = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", fontSize = 9.sp, color = InkTertiary) }
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
            .background(if (active) NextCourseBackground else Color.Transparent)
            .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        // 本周要上的左边一条蓝杠，其余一条浅灰杠
        Box(
            Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (active) AccentBlue else InkDisabled)
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = course.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) AccentBlue else InkSecondary,
            )
            Text(
                text = "${course.startWeek}-${course.endWeek}周 ${course.weekType.label()} · " +
                    "${course.startPeriod}-${course.endPeriod}节",
                fontSize = 9.sp,
                color = InkSecondary,
            )
            Text(
                text = "场地 ${course.location.ifBlank { "—" }} · 教师 ${course.teacher.ifBlank { "—" }}",
                fontSize = 9.sp,
                color = InkSecondary,
            )
            Text(
                text = if (active) "● 本周上课" else "○ 本周不上",
                fontSize = 9.sp,
                color = if (active) AccentBlue else InkTertiary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OutlinedButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Text("编辑", fontSize = 9.sp, color = AccentBlue)
            }
            OutlinedButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Text("删除", fontSize = 9.sp, color = InkSecondary)
            }
        }
    }
}
```

- [ ] **Step 2: 把弹窗接进课表页**

在 `app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt` 里改三处。

其一，`WeekScreen` 函数体最前面加上弹窗状态：

```kotlin
    var candidateSlot by remember { mutableStateOf<Slot?>(null) }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
```

其二，把 `onSlotClick` 的空回调换成：

```kotlin
            onSlotClick = { candidateSlot = it },
```

其三，在 `WeekScreen` 最外层 `Column { ... }` **之后**追加两个弹窗：

```kotlin
    candidateSlot?.let { slot ->
        CandidateDialog(
            timetable = timetable,
            slot = slot,
            week = week,
            onDismiss = { candidateSlot = null },
            onEdit = { /* Task 9 接上编辑表单 */ },
            onDelete = { index -> pendingDelete = index },
            onAddHere = { /* Task 9 接上新增表单 */ },
        )
    }

    pendingDelete?.let { index ->
        val name = state.timetable.courses.getOrNull(index)?.name ?: return@let
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除课程") },
            text = { Text("确定删掉「$name」？删掉就找不回来了。") },
            confirmButton = {
                TextButton(onClick = {
                    state.removeCourse(index)
                    candidateSlot = null   // 弹窗里存的是删除前的下标，再留着就会指错课
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
```

需要在该文件补 import：

```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
```

- [ ] **Step 3: 先导入一份课表，才能看到弹窗**

此时导入已经能写进课表了（`AppState.importPdf` 在 Task 5 就绪）。装机后点 ＋，选 `C:\Temp\coordcheck\` 下的样本 PDF —— 若该目录已不在，用仓库里解析器测试用的那份样本。

Run: `./gradlew :app:installDebug --console=plain`

- [ ] **Step 4: 核对点格子的行为**

在课表页上：

Expected:
- 点任意一张课程卡片 → 弹出候选课弹窗，标题是「周X · 第 N-M 节」，副标题带时间范围与门数
- 同一格有多门课的（如周一 5-6 节）→ 卡片右上角有蓝色圈码「2」；点开弹窗里两门都在，**本周真要上的那门**名字是蓝的、左边蓝杠、底下写「● 本周上课」；另一门灰的、写「○ 本周不上」
- 点某门课的「删除」→ 弹出二次确认，**候选课弹窗此时应该已经不在屏幕上**（同一时刻只显示一个弹窗）
- 在二次确认里点「取消」→ 退回**候选课弹窗**（两门课都还在），而不是一路退到网格
- 在二次确认里点「删除」→ 这门课从网格上消失，弹窗整个关掉；再点开该格，弹窗里少了一门
- 点「关闭」→ 弹窗关掉
- 点空白格子 → 此刻什么都不该发生（Task 9 才接）
- 删到最后一门后，再点开该格 → 走的是「空白格子」路径，不会再弹候选课弹窗

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/ui/week/CandidateDialog.kt \
        app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt
git -c user.name=18711 -c user.email=18711@local commit -m "feat(ui): 候选课弹窗与删除二次确认"
```

---

### Task 9: 把编辑弹窗抽成一处（今天页也要用）

**Files:**
- Modify: `app/src/main/java/com/zhou/kebiao/data/TimetableEdits.kt`（加新增课程的预填工厂）
- Modify: `app/src/test/java/com/zhou/kebiao/data/TimetableEditsTest.kt`
- Create: `app/src/main/java/com/zhou/kebiao/ui/week/CourseEditHost.kt`
- Modify: `app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt`

**为什么现在就抽：** 设计文档 §7.1 要求今天页点课程卡片也弹同一个候选课弹窗。如果先把接线写死在课表页、等写今天页时再复制一遍，两份差不多的状态机迟早改岔。趁现在只有一个调用方，先把它抽成一个组件。

- [ ] **Step 1: 先写预填工厂的失败测试**

把下面这条追加到 `app/src/test/java/com/zhou/kebiao/data/TimetableEditsTest.kt` 最后一个 `}` 之前：

```kotlin
    @Test
    fun `新增课程的预填值`() {
        // 设计文档 §7.5：周次铺满整学期、每周；星期与节次用所点的格子；其余留空。
        val prefill = newCoursePrefill(day = 3, startPeriod = 5, endPeriod = 6, totalWeeks = 16)
        assertEquals("", prefill.name)
        assertEquals(1, prefill.startWeek)
        assertEquals(16, prefill.endWeek)
        assertEquals(WeekType.EVERY, prefill.weekType)
        assertEquals(3, prefill.dayOfWeek)
        assertEquals(5, prefill.startPeriod)
        assertEquals(6, prefill.endPeriod)
        assertEquals("", prefill.location)
        assertEquals("", prefill.teacher)
        assertEquals(emptySet<CourseField>(), prefill.missingFields)
    }
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.data.TimetableEditsTest" --console=plain`

Expected: 编译失败，`Unresolved reference: newCoursePrefill`。

- [ ] **Step 3: 加上工厂**

追加到 `app/src/main/java/com/zhou/kebiao/data/TimetableEdits.kt` 末尾：

```kotlin
/**
 * 新增课程时的预填值（设计文档 §7.5）：周次铺满整学期、每周一次，
 * 星期与节次用所点的那一格，其余留空等用户填。
 * 预填只是省事，5 个字段在表单里都可以改。
 */
fun newCoursePrefill(day: Int, startPeriod: Int, endPeriod: Int, totalWeeks: Int): Course = Course(
    name = "",
    startWeek = 1,
    endWeek = totalWeeks,
    weekType = WeekType.EVERY,
    dayOfWeek = day,
    startPeriod = startPeriod,
    endPeriod = endPeriod,
    location = "",
    teacher = "",
)
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.data.TimetableEditsTest" --console=plain`

Expected: `BUILD SUCCESSFUL`，14 个用例通过。

- [ ] **Step 5: 写编辑弹窗宿主**

新建 `app/src/main/java/com/zhou/kebiao/ui/week/CourseEditHost.kt`：

```kotlin
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

        is EditRequest.Form -> { /* Task 10 接上 CourseFormDialog */ }
    }
}
```

- [ ] **Step 6: 课表页改用宿主，删掉自己那套接线**

在 `app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt` 里做三件事。

其一，把两个状态变量换成一个：

```kotlin
    var edit by remember { mutableStateOf<EditRequest?>(null) }
```

（原来 Task 8 加的 `candidateSlot` 与 `pendingDelete` 都删掉。）

其二，把 `onSlotClick` 改成：

```kotlin
            onSlotClick = { edit = EditRequest.SlotPick(it) },
```

其三，把 Task 8 追加在 `Column { ... }` 之后的那两段弹窗（`candidateSlot?.let { ... }` 与 `pendingDelete?.let { ... }`）整段删掉，换成：

```kotlin
    CourseEditHost(
        state = state,
        request = edit,
        onRequestChange = { edit = it },
        week = week,
    )
```

并把已经用不到的 import 删掉：

```kotlin
import androidx.compose.material3.AlertDialog   // 删
import androidx.compose.material3.TextButton    // 删
```

- [ ] **Step 7: 编译并装机，确认行为没变**

Run: `./gradlew :app:installDebug --console=plain`

Expected: 与 Task 8 Step 4 的表现完全一致（点卡片弹候选课、点删除弹二次确认、**取消退回候选课弹窗**、确认删除则整个关掉）。这一步是重构，可观察行为不该有任何变化。

**这里有个坑**：把 Task 8 的两个独立状态（`candidateSlot` / `pendingDelete`）合并成一个 `EditRequest?` 时，如果 `ConfirmDelete` 只存下标、不存来路，取消就会退到 `null` —— 也就是直接回网格，候选课弹窗不回来了。所以 `ConfirmDelete` 必须带上 `from: SlotPick`，取消时 `onRequestChange(request.from)`。

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/data/TimetableEdits.kt \
        app/src/test/java/com/zhou/kebiao/data/TimetableEditsTest.kt \
        app/src/main/java/com/zhou/kebiao/ui/week/CourseEditHost.kt \
        app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt
git -c user.name=18711 -c user.email=18711@local commit -m "refactor(ui): 编辑弹窗接线抽成 CourseEditHost"
```

---

### Task 10: 新增 / 编辑课程表单（含标黄）

**Files:**
- Create: `app/src/main/java/com/zhou/kebiao/ui/week/CourseFormDialog.kt`
- Modify: `app/src/main/java/com/zhou/kebiao/ui/week/CourseEditHost.kt`
- Modify: `app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt`

**标黄的依据**是 `Course.missingFields`（设计文档 §5.5）：导入时用了兜底值的字段，在表单里黄底黄框标出来，并写一句「导入时没认出来，请确认」。用户保存后标记整体清空 —— 表单里已经过目过了。

- [ ] **Step 1: 写表单**

新建 `app/src/main/java/com/zhou/kebiao/ui/week/CourseFormDialog.kt`：

```kotlin
package com.zhou.kebiao.ui.week

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.CourseField
import com.zhou.kebiao.data.Timetable
import com.zhou.kebiao.data.WeekType
import com.zhou.kebiao.data.label
import com.zhou.kebiao.data.startOf
import com.zhou.kebiao.data.validateCourse
import com.zhou.kebiao.ui.theme.AccentBlue
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
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingIndex == null) "新增课程" else "编辑课程",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
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
                    Text("第", fontSize = 9.sp, color = InkSecondary)
                    TextFieldBox(startWeek, { startWeek = it }, CourseField.WEEKS in missing, Modifier.width(52.dp))
                    Text("周 到 第", fontSize = 9.sp, color = InkSecondary)
                    TextFieldBox(endWeek, { endWeek = it }, CourseField.WEEKS in missing, Modifier.width(52.dp))
                    Text("周", fontSize = 9.sp, color = InkSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WeekType.entries.forEach { type ->
                        Text(
                            text = (if (weekType == type) "● " else "○ ") + type.label(),
                            fontSize = 9.sp,
                            color = if (weekType == type) AccentBlue else InkSecondary,
                            modifier = Modifier.clickable { weekType = type }.padding(vertical = 2.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                FieldLabel("课程节数", CourseField.PERIODS in missing)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Text(
                            text = weekdayFull(day) + " ▾",
                            fontSize = 9.sp,
                            color = AccentBlue,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .border(1.dp, DividerLine, RoundedCornerShape(3.dp))
                                .clickable { dayMenuOpen = true }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                        DropdownMenu(expanded = dayMenuOpen, onDismissRequest = { dayMenuOpen = false }) {
                            for (d in 1..7) {
                                DropdownMenuItem(
                                    text = { Text(weekdayFull(d), fontSize = 10.sp) },
                                    onClick = {
                                        day = d
                                        dayMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("第", fontSize = 9.sp, color = InkSecondary)
                    TextFieldBox(startPeriod, { startPeriod = it }, CourseField.PERIODS in missing, Modifier.width(44.dp))
                    Text("节 到 第", fontSize = 9.sp, color = InkSecondary)
                    TextFieldBox(endPeriod, { endPeriod = it }, CourseField.PERIODS in missing, Modifier.width(44.dp))
                    Text("节", fontSize = 9.sp, color = InkSecondary)
                }
                if (periodGapHint != null) {
                    Text(periodGapHint, fontSize = 8.sp, color = MissingFieldBorder)
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
                    Text(error!!, fontSize = 9.sp, color = ErrorRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }) {
                Text("保存", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontSize = 11.sp, color = InkTertiary) }
        },
    )
}

/** 字段名。字段是导入时的兜底值就在旁边说明一句。 */
@Composable
private fun FieldLabel(text: String, missing: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 9.sp, color = InkTertiary)
        if (missing) {
            Spacer(Modifier.width(4.dp))
            Text("导入时没认出来，请确认", fontSize = 8.sp, color = MissingFieldBorder)
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
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
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
```

- [ ] **Step 2: 宿主的 Form 分支接上表单**

把 `app/src/main/java/com/zhou/kebiao/ui/week/CourseEditHost.kt` 里这一行：

```kotlin
        is EditRequest.Form -> { /* Task 10 接上 CourseFormDialog */ }
```

换成：

```kotlin
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
```

- [ ] **Step 3: 课表页接上「点空白格子新增」**

在 `app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt` 里，把 `onEmptyCellClick` 的空回调换成：

```kotlin
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
```

并补 import：

```kotlin
import com.zhou.kebiao.data.newCoursePrefill
```

- [ ] **Step 4: 编译**

Run: `./gradlew :app:compileDebugKotlin --console=plain`

Expected: `BUILD SUCCESSFUL`。若报 `WeekType.entries` 不可用，改成 `WeekType.values()`。

- [ ] **Step 5: 装机核对新增、编辑与标黄**

Run: `./gradlew :app:installDebug --console=plain`

Expected:
- 点**空白**格子 → 表单弹出，标题「新增课程」，星期与节次已经预填成所点的那一格，周次是 `第 1 周 到 第 16 周`、`● 每周`
- 名称留空直接点保存 → 表单里出现红字「课程名称不能为空」，弹窗不关
- 名称填上、保存 → 网格上出现新卡片，颜色按课名散列取色
- 点**有课**格子 → 候选课弹窗 → 点某门课的「编辑」→ 表单标题是「编辑课程」，5 个字段都是原值
- 把「课程节数」从 `周三 5-6 节` 改成 `周四 3-4 节` 保存 → 卡片挪到周四第 3-4 节（设计文档 §7.5：预填不锁定）
- 点有课格子 → 底部「＋ 在这个时段加一门课」→ 表单的星期与节次预填为该时段
- **标黄**：导入来的课若某个字段没认出来，打开编辑表单时那个字段应是黄底黄框，旁边写「导入时没认出来，请确认」；保存之后**再打开**该课，黄底应当消失（设计文档 §5.5：编辑保存后标记整体清空）
- 把某门课的节次改到第 11 节（默认作息没有第 11 节）→ 表单里出现黄字「第 11 节还没有作息时间…」，保存后网格上仍在第 11 节行，时间栏显示「—」（设计文档 §5.3）

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/ui/week/CourseFormDialog.kt \
        app/src/main/java/com/zhou/kebiao/ui/week/CourseEditHost.kt \
        app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt
git -c user.name=18711 -c user.email=18711@local commit -m "feat(ui): 新增/编辑课程表单，兜底字段标黄"
```

---

### Task 11: 修改某一节的作息时间

**Files:**
- Create: `app/src/main/java/com/zhou/kebiao/ui/week/PeriodTimeDialog.kt`
- Modify: `app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt`

- [ ] **Step 1: 写作息弹窗**

新建 `app/src/main/java/com/zhou/kebiao/ui/week/PeriodTimeDialog.kt`：

```kotlin
package com.zhou.kebiao.ui.week

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhou.kebiao.data.PeriodTime
import com.zhou.kebiao.ui.theme.AccentBlue
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
        onDismissRequest = onDismiss,
        title = {
            Text("第 $period 节的作息时间", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeBox(start, { start = it })
                    Text("  –  ", fontSize = 11.sp, color = InkSecondary)
                    TimeBox(end, { end = it })
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "改这里影响所有用到第 $period 节的课，不会改动课程本身。",
                    fontSize = 8.sp,
                    color = InkTertiary,
                )
                Text("时间写成 HH:mm，例如 08:05", fontSize = 8.sp, color = InkTertiary)
                if (error != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(error!!, fontSize = 9.sp, color = ErrorRed)
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
                Text("保存", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontSize = 11.sp, color = InkTertiary) }
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
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DividerLine,
                unfocusedBorderColor = DividerLine,
            ),
            modifier = Modifier.width(84.dp),
        )
    }
}
```

- [ ] **Step 2: 课表页接上**

在 `app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt` 里加一个状态：

```kotlin
    var periodToEdit by remember { mutableStateOf<Int?>(null) }
```

把 `onPeriodClick` 的空回调换成：

```kotlin
            onPeriodClick = { periodToEdit = it },
```

并在 `CourseEditHost(...)` 那段旁边追加：

```kotlin
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
```

- [ ] **Step 3: 装机核对**

Run: `./gradlew :app:installDebug --console=plain`

Expected:
- 点网格左侧第 5 节的时间（`14:10 / 15:00`）→ 弹窗标题「第 5 节的作息时间」，两个框里是 `14:10` 与 `15:00`
- **先把框里的内容清空**，再输入 `8:05` 保存 → 红字「时间要写成 HH:mm，例如 08:05」，弹窗不关。
  （不清空直接输入会拼成 `14:108:05`，虽然也会被拒，但验的就不是 `8:05` 这条了）
- 把开始时间改成晚于结束时间 → 红字「开始时间要早于结束时间」
- 改成合法时间保存 → 时间栏该节的两行字跟着变
- 点第 11 节（默认没有作息）→ 两个框是空的，填上 `21:00` / `21:50` 保存 → 第 11 节出现了时间。
  **第 11 节在网格最下方，要先往上滚**；11 节 × 77dp 在 1080×2400 上一次 `swipe 540 1600 540 400` 刚好能露出来，若没露再滑一次
- 今天页暂不校验（Task 13 才做），此处只看课表页
- **改完记得把被改动的节次恢复原值**，或在报告里写清楚最终留下了什么值 —— 设备上那份课表是后续 Task 的验证数据

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/ui/week/PeriodTimeDialog.kt \
        app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt
git -c user.name=18711 -c user.email=18711@local commit -m "feat(ui): 点时间栏修改某一节的作息时间"
```

---

### Task 12: 其他课程（纯查看）

**Files:**
- Create: `app/src/main/java/com/zhou/kebiao/ui/week/OtherCoursesDialog.kt`
- Modify: `app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt`

- [ ] **Step 1: 写其他课程弹窗**

新建 `app/src/main/java/com/zhou/kebiao/ui/week/OtherCoursesDialog.kt`：

```kotlin
package com.zhou.kebiao.ui.week

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhou.kebiao.data.OtherCourse
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
        onDismissRequest = onDismiss,
        title = { Text("其他课程", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
        text = {
            if (sorted.isEmpty()) {
                Text("这份课表里没有其他课程。", fontSize = 10.sp, color = InkTertiary)
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
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
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
                                fontSize = 9.sp,
                                color = InkSecondary,
                            )
                        }
                        HorizontalDivider(color = RowLine, thickness = 1.dp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "这些课没有固定上课时间，不参与课表网格与「今天」，也不能编辑。",
                        fontSize = 8.sp,
                        color = InkTertiary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", fontSize = 9.sp, color = InkTertiary) }
        },
    )
}
```

- [ ] **Step 2: 课表页接上**

在 `app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt` 里加一个状态：

```kotlin
    var showOtherCourses by remember { mutableStateOf(false) }
```

把顶栏的 `onOtherCourses` 空回调换成：

```kotlin
            onOtherCourses = { showOtherCourses = true },
```

并在 `periodToEdit?.let { ... }` 那段旁边追加：

```kotlin
    if (showOtherCourses) {
        OtherCoursesDialog(
            otherCourses = timetable.otherCourses,
            onDismiss = { showOtherCourses = false },
        )
    }
```

- [ ] **Step 3: 装机核对**

Run: `./gradlew :app:installDebug --console=plain`

Expected:
- 课表页顶栏点「其他课程」→ 弹窗列出条目，**按起始周从小到大排**
- 样本应能看到「电子产品设计■ 19 周 · 共 1 周 · 教师 黄远,徐雯」这样超出 16 周的条目，它们照常显示、不影响网格
- 每条显示课名（含 ★☆◆■〇 符号）、周次、共几周、教师
- 弹窗里没有任何编辑或删除按钮

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/ui/week/OtherCoursesDialog.kt \
        app/src/main/java/com/zhou/kebiao/ui/week/WeekScreen.kt
git -c user.name=18711 -c user.email=18711@local commit -m "feat(ui): 其他课程查看"
```

---

### Task 13: 今天页

**Files:**
- Replace: `app/src/main/java/com/zhou/kebiao/ui/today/TodayScreen.kt`

- [ ] **Step 1: 写今天页**

整份替换 `app/src/main/java/com/zhou/kebiao/ui/today/TodayScreen.kt`：

```kotlin
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.PeriodTime
import com.zhou.kebiao.data.Timetable
import com.zhou.kebiao.data.endOf
import com.zhou.kebiao.data.isActiveIn
import com.zhou.kebiao.data.startOf
import com.zhou.kebiao.ui.AppState
import com.zhou.kebiao.ui.theme.AccentBlue
import com.zhou.kebiao.ui.theme.DividerLine
import com.zhou.kebiao.ui.theme.InkPrimary
import com.zhou.kebiao.ui.theme.InkSecondary
import com.zhou.kebiao.ui.theme.InkTertiary
import com.zhou.kebiao.ui.theme.NavBarBackground
import com.zhou.kebiao.ui.theme.NextCourseBackground
import com.zhou.kebiao.ui.theme.PastCourseBackground
import com.zhou.kebiao.ui.theme.PastCourseBar
import com.zhou.kebiao.ui.theme.courseColor
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
            .background(Color.White)
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
        Text("今天", fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = InkPrimary)
        Spacer(Modifier.weight(1f))
        Text(
            text = when {
                todayWeek == 0 -> "学期还没开始"
                todayWeek > timetable.totalWeeks -> "学期已结束"
                else -> "第 $todayWeek 周 · ${weekdayFull(today.dayOfWeek.value)}"
            },
            fontSize = 9.sp,
            color = AccentBlue,
        )
    }
    Text(
        text = "${today.year} 年 ${today.monthValue} 月 ${today.dayOfMonth} 日",
        fontSize = 9.sp,
        color = InkTertiary,
        modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 8.dp),
    )
    HorizontalDivider(color = DividerLine, thickness = 1.dp)
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 10.sp, color = InkTertiary)
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
    val bar = when {
        isNext -> AccentBlue
        ended -> PastCourseBar
        else -> courseColor(course.name)
    }
    val background = when {
        isNext -> NextCourseBackground
        ended -> PastCourseBackground
        else -> NavBarBackground
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
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                color = if (isNext) AccentBlue else InkSecondary,
            )
            Text(
                text = end,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                color = if (isNext) AccentBlue else InkSecondary,
            )
        }

        // IntrinsicSize.Min 让左侧 3dp 色条跟着文字高度走，不用写死卡片高度
        Row(
            modifier = Modifier
                .weight(1f)
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(4.dp))
                .background(background)
                .clickable(onClick = onClick),
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(bar))
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = course.name,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                )
                Text(
                    text = buildString {
                        append("@").append(course.location.ifBlank { "未排地点" })
                        if (course.teacher.isNotBlank()) append(" · ").append(course.teacher)
                    },
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    color = InkSecondary,
                )
                if (isNext) {
                    Text("▸ 下一节", fontSize = 9.sp, lineHeight = 11.sp, color = AccentBlue)
                }
            }
        }
    }
}
```

- [ ] **Step 2: 编译**

Run: `./gradlew :app:compileDebugKotlin --console=plain`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 装机核对**

Run: `./gradlew :app:installDebug --console=plain`

Expected（样本 PDF 的今天是 2026-09-15 周二，第 3 周）：
- 顶部「今天」加粗，右边蓝字「第 3 周 · 周二」，下面一行日期
- 当天的课按节次从上到下排列，每张卡片左侧一条 3dp 色条
- 已经上完的课整行变淡（半透明），色条是灰的
- 第一门还没结束的课：色条与时间都是蓝的、底色淡蓝、卡里多一行「▸ 下一节」
- 点卡片 → 弹出与课表页一样的候选课弹窗
- 「这学期还没开始…」「这学期已经结束…」「还没有课表…」「今天没课。」四种态。
  **不要去改系统时间** —— 手边的模拟器多半是 production 镜像，`adb root` 会报
  `adbd cannot run as root in production builds`，`date` 改不了。
  改用数据等价触发，四条都能造出来：

  | 想看到 | 怎么造 |
  |---|---|
  | 这学期还没开始 | `firstMonday` 改成一个未来的日期（如 `2026-12-01`） |
  | 这学期已经结束 | `totalWeeks` 改成 1 |
  | 还没有课表 | `courses` 改成 `[]` |
  | 今天没课 | 删掉 `dayOfWeek` 等于今天的那些课 |

  **动数据前先 `run-as cat files/timetable.json` 备份到主机，测完还原并比对 sha256** ——
  设备上那份课表是后面几个 Task 的验证数据，弄坏了要重跑种子流程

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/ui/today/TodayScreen.kt
git -c user.name=18711 -c user.email=18711@local commit -m "feat(ui): 今天页时间轴"
```

---

### Task 14: 设置页

**Files:**
- Replace: `app/src/main/java/com/zhou/kebiao/ui/settings/SettingsScreen.kt`

**一处与原型不同的取舍：** 原型把「学期名称」画成灰色只读，但设计文档 §7.7 明确写了三个字段「可直接点击修改」，行为以设计文档为准 —— 三个都能点开改。

- [ ] **Step 1: 写设置页**

整份替换 `app/src/main/java/com/zhou/kebiao/ui/settings/SettingsScreen.kt`：

```kotlin
package com.zhou.kebiao.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhou.kebiao.ui.AppState
import com.zhou.kebiao.ui.theme.AccentBlue
import com.zhou.kebiao.ui.theme.DividerLine
import com.zhou.kebiao.ui.theme.ErrorRed
import com.zhou.kebiao.ui.theme.InkPrimary
import com.zhou.kebiao.ui.theme.InkSecondary
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

    Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState())) {
        GroupLabel("学期")
        SettingRow("学期名称", timetable.semesterName, editable = true) {
            editing = SettingField.SEMESTER_NAME
        }
        SettingRow("开学第一周周一", timetable.firstMonday, editable = true) {
            editing = SettingField.FIRST_MONDAY
        }
        SettingRow("学期总周数", "${timetable.totalWeeks} 周", editable = true) {
            editing = SettingField.TOTAL_WEEKS
        }

        GroupLabel("关于")
        SettingRow("课程数据", "只存在本机，不上传", editable = false) {}
        // 这两行的右侧写成「去…」而不是「在…」，读起来才是指路而不是动作 ——
        // 否则用户会去点一行点了没反应的「导入课表」
        SettingRow("导入课表", "去「课表」页点右上角的 ＋", editable = false) {}
        SettingRow("编辑作息时间", "去「课表」页点左侧时间栏", editable = false) {}
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

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        fontSize = 8.sp,
        color = InkTertiary,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(label: String, value: String, editable: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = editable, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 11.sp, color = InkPrimary)
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 11.sp,
            color = if (editable) AccentBlue else InkTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (editable) {
            Text("  ›", fontSize = 11.sp, color = InkTertiary)
        }
    }
    HorizontalDivider(color = RowLine, thickness = 1.dp)
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
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DividerLine,
                        unfocusedBorderColor = DividerLine,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(hint, fontSize = 8.sp, color = InkTertiary, modifier = Modifier.padding(top = 4.dp))
                if (error != null) {
                    Text(error!!, fontSize = 9.sp, color = ErrorRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = validate(value)
                if (problem == null) onSave(value) else error = problem
            }) {
                Text("保存", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontSize = 11.sp, color = InkTertiary) }
        },
    )
}
```

- [ ] **Step 2: 装机核对**

Run: `./gradlew :app:installDebug --console=plain`

Expected:
- 设置页三行：`学期名称`、`开学第一周周一`、`学期总周数`，值都是蓝字并带 `›`
- 关于那三行是灰字，点上去没反应
- 点「学期总周数」→ 填 `0` 保存 → **红字**「总周数要在 1 到 30 之间」，弹窗不关；填 `18` 保存 → 行上变成 `18 周`，课表页最多能翻到第 18 周
- 点「开学第一周周一」→ 填 `2026-8-31` 保存 → 提示「要写成 yyyy-MM-dd，例如 2026-08-31」；填 `2026-08-24` 保存 → 今天页与日期行都对得上新日期
- 点「学期名称」→ 清空保存 → 提示「学期名称不能为空」
- **设置页没有导入入口，也没有作息时间入口**

- [ ] **Step 3: 提交**

顺带把 `ErrorRed` 提到色板：`ui/theme/Colors.kt` 加常量，`ui/week/CourseFormDialog.kt`
与 `ui/week/PeriodTimeDialog.kt` 删掉各自的私有定义、改用主题里的那个（后者的
`androidx.compose.ui.graphics.Color` import 也随之不再需要）。

```bash
git add app/src/main/java/com/zhou/kebiao/ui/settings/SettingsScreen.kt \
        app/src/main/java/com/zhou/kebiao/ui/theme/Colors.kt \
        app/src/main/java/com/zhou/kebiao/ui/week/CourseFormDialog.kt \
        app/src/main/java/com/zhou/kebiao/ui/week/PeriodTimeDialog.kt
git -c user.name=18711 -c user.email=18711@local commit -m "feat(ui): 设置页，错误色提到色板"
```

---

### Task 15: 端到端验证

**Files:** 无改动，只做验证。

- [ ] **Step 1: 跑全量单元测试**

Run: `./gradlew :app:testDebugUnitTest --rerun --console=plain`

Expected: `BUILD SUCCESSFUL`。用下面的命令确认没有失败或跳过的用例：

```bash
cd app/build/test-results/testDebugUnitTest && for f in *.xml; do head -3 "$f" | tr '\n' ' ' | sed 's/.*name="\([^"]*\)".*tests="\([0-9]*\)".*failures="\([0-9]*\)".*errors="\([0-9]*\)".*/\1: tests=\2 fail=\3 err=\4/'; echo; done
```

Expected: 每个类 `fail=0 err=0`；其中 `TimetableParserFallbackTest`、`TimetableEditsTest`、`SlotsTest`、`ImportOutcomeTest`、`ColorsTest` 都在列。

- [ ] **Step 2: 构建 release 之外的两个产物**

Run: `./gradlew :app:assembleDebug :app:lintDebug --console=plain`

Expected: `BUILD SUCCESSFUL`。lint 若报错，逐条看清楚是真问题还是噪声再决定 —— 不要直接关掉规则。

- [ ] **Step 3: 确认调试页已经彻底删掉**

Run: `git ls-files | grep -i debugparse` 以及 `grep -rn "DebugParseScreen" app/src docs/superpowers/plans/2026-09-15-ui-screens.md`

Expected: 两条都没有输出（计划文档里提到它是为了说明「删掉」，若 grep 命中的是计划文档里的说明文字，忽略即可；`app/src` 下必须没有任何命中）。

- [ ] **Step 4: 走一遍完整的用户路径**

装到真机或模拟器上，从头走一遍：

1. 清掉数据（卸载重装，或删掉 App 私有目录下的 `files/timetable.json`）
2. 打开 → 落在「今天」页 → 显示「还没有课表…」
3. 切到「课表」→ 顶栏点 ＋ → 选样本 PDF → 出现转圈遮罩，转完后网格上出现课程
4. 「今天」页此时应显示当天的课（样本对应 2026-09-15 第 3 周周二）
5. 顶栏点「其他课程」→ 7 条按周次列出
6. 点周一第 5-6 节那张带圈码「2」的卡片 → 候选课弹窗里两门都在，单周那门是本周上的
7. 删掉其中一门 → 二次确认 → 确认 → 该格只剩一门，圈码消失
8. 点一个空白格子 → 新增表单 → 填名字保存 → 卡片出现
9. 点该卡片 → 编辑 → 改节次 → 保存 → 卡片换位置
10. 点左侧时间栏某一节 → 改时间 → 保存 → 时间栏更新
11. 「设置」页把总周数改成 8 → 回课表页，最多只能翻到第 8 周
12. **选一个不是课表的 PDF**（比如随便一份别的 PDF）→ 应弹出「这份课表认不出来，可能是别的学校的模板」，**网格上的课一门不少**
13. 选文件时按返回、不选任何文件 → 什么也不发生，不报错

- [ ] **Step 5: 逐条对照设计文档 §8 的错误处理**

| 设计文档要求 | 怎么验 |
|---|---|
| 选的文件不是 PDF → 「这个文件读不了」，**不写入** | 找一个扩展名不是 .pdf 的文件，用「所有文件」的方式选它（SAF 的 mime 过滤挡不住的情形）；或者临时把 `AppState.importPdf` 里的 `openInputStream` 传一个不存在的 uri。核心是确认弹的是「这个文件读不了」而不是堆栈，且课表没变 |
| PDF 模板不匹配 → 「这份课表认不出来…」，**不写入** | 见 Step 4 第 12 条 |
| 解析出 0 门课 → 同上，**不写入** | 同上（解析器把 0 门课直接归成 Failure，`ImportOutcome` 再兜一次，`ImportOutcomeTest` 有用例） |
| 个别字段未识别 → 照常导入，字段留空，编辑弹窗标黄 | 见 Task 10 Step 5 |
| 课程名未识别 → 兜底「未知课程」，其余字段保留 | `TimetableParserFallbackTest` 有用例；手动核对：若样本里出现「未知课程」，编辑它看其余字段是否还在 |
| 用户删除课程 → 二次确认；删除后不可撤销 | 见 Step 4 第 7 条 |
| **导入不弹确认框，直接覆盖**；但 0 门课拒绝写入 | 见 Step 4 第 3 条：选完文件直接覆盖，中途没有任何确认框 |

- [ ] **Step 6: 收尾**

```bash
git log --oneline -20
git status --short
```

Expected: 工作区干净（`git status` 无输出）；`git log` 里能看到本计划各个 Task 的提交。

若 Step 1–5 有任何一条不过，**不要**标记本任务完成 —— 把它当成新缺陷回到 systematic-debugging 走一遍。

---

## 自检记录

写完计划后对着设计文档 §7、§8、§9 逐节核对的结果：

**§7.1 今天页** — Task 13。全天时间轴、已结束变灰、下一节高亮、「今天没课」、学期未开始/已结束提示、点卡片开 §7.3 弹窗，都有落点。

**§7.2 课表页** — Task 7（网格、日期行、周次滑动与两端不循环）、Task 8（圈码、灰卡「非本周」）、Task 6（顶栏两个按钮：导入 ＋ 与其他课程）。左右滑动方向与 ◀▶ 一致。

**§7.3 候选课弹窗** — Task 8。标题带星期与节次范围、候选课全列、本周生效高亮、编辑/删除、底部 ＋、删除二次确认、多门同时生效时全高亮不取舍。

**§7.4 修改作息时间** — Task 11。点左侧时间栏、改该节起止、弹窗里注明影响范围。

**§7.5 新增/编辑表单** — Task 10。5 个字段共用一张表单、全部可改、新增预填、三条校验、地点与老师可空。

**§7.6 其他课程** — Task 12。顶栏小按钮进入、按周次排序、纯查看不可编辑。

**§7.7 设置** — Task 14。三个可改字段 + 关于；导入与作息都不在这里。

**§8 错误处理** — Task 4（落点纯函数 + 6 条用例）、Task 5（IO 异常兜底）、Task 10（标黄）、Task 15 Step 5 逐条核对。

**§9 项目结构** — 目录与 `ui/today/`、`ui/week/`、`ui/settings/` 一致。

**§9.1 测试策略** — 解析器既有测试不动；界面不写自动化测试，改用手动清单（各 Task 的装机核对步骤 + Task 15）。新增的单测只覆盖剥出来纯逻辑（取色、编辑函数、归并、导入落点）。

**留待实现的已知取舍（执行时不要"顺手补上"）：**
- 底部导航与顶栏的图标用文字字形（🏠📅⚙、◀▶＋），不引入图标库 —— 与原型一致。
- 不跟随系统深色、不用动态取色（Task 1 已注明理由）。
- 星期选择用下拉、单双周用圆点单选，与原型的「下拉 + 数字框」一致；日期行、7 列宽度、行高与卡片样式按原型 week-v2。
- 没有给 `Course` 加 id，编辑与删除按 `Timetable.courses` 的下标定位（Task 2 已注明理由：§5 限定字段数）。
