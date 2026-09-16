# 课表 App 解析内核 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立安卓工程骨架，并做出一个经过单元测试验证的 PDF 课表解析器——把教务系统导出的课表 PDF 转成结构化课程数据。

**Architecture:** 解析分两层。`PdfTextExtractor` 是薄适配层，用 PDFBox 把 PDF 拆成带坐标和字号的 `TextFragment` 列表；`TimetableParser` 是纯 Kotlin 的纯函数，只吃 `List<TextFragment>`、吐出 `Timetable`，完全不依赖 PDFBox 与 Android。因此最难的逻辑（星期列标定、跨页拼接、课程名折行、字段解析）全部可以用 JVM 单元测试覆盖，不需要模拟器。

**Tech Stack:** Kotlin、Jetpack Compose（本计划只用到模板自带的空 Compose 页面）、PDFBox-Android、kotlinx.serialization、JUnit4

**设计文档：** `docs/superpowers/specs/2026-09-15-timetable-app-design.md`

---

## 文件结构

| 文件 | 职责 |
|---|---|
| `data/Models.kt` | `Course`、`OtherCourse`、`PeriodTime`、`Timetable` 四个数据类，以及周次判断与当前周次计算 |
| `data/TimetableStore.kt` | 单个 JSON 文件的读写（序列化 / 反序列化 / 应用私有目录定位） |
| `parser/TextFragment.kt` | `TextFragment`、`ParseResult`、`PdfHeader` |
| `parser/PdfTextExtractor.kt` | 唯一依赖 PDFBox 的文件。PDF → `List<TextFragment>` |
| `parser/TimetableParser.kt` | 解析内核。`List<TextFragment>` → `ParseResult` |
| `parser/SamplePdfText.kt` | 测试夹具：按真实 PDF 坐标手工构造的 `TextFragment` 列表 |

UI 相关文件在第二份计划（`ui/today`、`ui/week`、`ui/settings`）里创建，本计划不动。

---

## Task 0: 建立 Android 工程骨架

**Files:**
- Create: 整个 Gradle 工程（由 Android Studio 向导生成）
- Modify: `app/build.gradle.kts`（加依赖）

- [ ] **Step 1: 用 Android Studio 向导新建项目**

打开 Android Studio → `New Project` → 选 **Empty Activity**（带 Compose 的那个）→ 按下表填写：

| 字段 | 值 |
|---|---|
| Name | `课表` |
| Package name | `com.zhou.kebiao` |
| Save location | `C:\Users/user\AndroidStudioProjects\Timetable` |
| Language | Kotlin |
| Minimum SDK | API 26 ("Oreo"; Android 8.0) |
| Build configuration language | Kotlin DSL |

**关键**：Save location 必须选已经存在的 `Timetable` 目录（里面已经有 `docs/` 和 `.gitignore`）。向导会提示目录非空 —— 选择继续，它会保留已有文件。

- [ ] **Step 2: 验证向导产出的工程能构建**

Run:
```bash
cd "C:/Users/user/AndroidStudioProjects/Timetable" && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

如果失败，先解决构建问题再继续 —— 后面所有任务都依赖这一步。

- [ ] **Step 3: 把 .gitignore 合并进来**

Android Studio 会生成自己的 `.gitignore`。确认项目根目录的 `.gitignore` 至少包含：

```
*.iml
.gradle/
/local.properties
/.idea/
.DS_Store
build/
/captures
.externalNativeBuild
.cxx
local.properties
```

（向导若已生成 `app/.gitignore`，保留它，两个文件并存没问题。）

- [ ] **Step 4: 加依赖**

打开 `app/build.gradle.kts`，在 `dependencies { }` 块内加入：

```kotlin
    // PDF 解析
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // JSON 序列化
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
```

在文件顶部的 `plugins { }` 块内加入序列化插件：

```kotlin
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
```

> 版本号需与工程里的 Kotlin 版本一致。打开项目根目录的 `build.gradle.kts` 或 `gradle/libs.versions.toml` 看实际 Kotlin 版本，把 `2.0.21` 换成它。若依赖解析失败，用 Android Studio 的红色波浪线提示更新到最新稳定版。

- [ ] **Step 5: 验证依赖可用并提交**

Run:
```bash
cd "C:/Users/user/AndroidStudioProjects/Timetable" && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

```bash
git add -A
git commit -m "chore: 建立 Android 工程骨架（Kotlin + Compose，minSdk 26）"
```

---

## Task 1: 数据模型与周次判断

**Files:**
- Create: `app/src/main/java/com/zhou/kebiao/data/Models.kt`
- Test: `app/src/test/java/com/zhou/kebiao/data/ModelsTest.kt`

- [ ] **Step 1: 写失败的测试**

创建 `app/src/test/java/com/zhou/kebiao/data/ModelsTest.kt`：

```kotlin
package com.zhou.kebiao.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ModelsTest {

    private fun course(
        start: Int = 1,
        end: Int = 16,
        weekType: WeekType = WeekType.EVERY,
    ) = Course(
        name = "测试课",
        startWeek = start,
        endWeek = end,
        weekType = weekType,
        dayOfWeek = 2,
        startPeriod = 1,
        endPeriod = 2,
        location = "2-3-305",
        teacher = "张三",
    )

    @Test
    fun `每周课在范围内任意周都生效`() {
        val c = course(1, 16, WeekType.EVERY)
        assertTrue(c.isActiveIn(1))
        assertTrue(c.isActiveIn(8))
        assertTrue(c.isActiveIn(16))
    }

    @Test
    fun `超出起止周的周次不生效`() {
        val c = course(9, 16, WeekType.EVERY)
        assertFalse(c.isActiveIn(8))
        assertTrue(c.isActiveIn(9))
        assertTrue(c.isActiveIn(16))
        assertFalse(c.isActiveIn(17))
    }

    @Test
    fun `单周只在奇数周生效`() {
        // 样本真实数据：大学英语(3)☆ 1-15周(单)
        val c = course(1, 15, WeekType.ODD)
        assertTrue(c.isActiveIn(1))
        assertFalse(c.isActiveIn(2))
        assertTrue(c.isActiveIn(3))
        assertTrue(c.isActiveIn(15))
    }

    @Test
    fun `双周只在偶数周生效`() {
        // 样本真实数据：概率论与数理统计B 2-16周(双)
        val c = course(2, 16, WeekType.EVEN)
        assertFalse(c.isActiveIn(1))
        assertTrue(c.isActiveIn(2))
        assertFalse(c.isActiveIn(3))
        assertTrue(c.isActiveIn(16))
    }

    @Test
    fun `单双周边界 起止周本身也要满足奇偶`() {
        // 2-16周(双)：第 2 周生效，但 1-15周(单) 的第 16 周不该生效
        assertTrue(course(2, 16, WeekType.EVEN).isActiveIn(2))
        assertFalse(course(1, 15, WeekType.ODD).isActiveIn(16))

        // 起止周落在错误的奇偶上时，该周不生效
        assertFalse(course(2, 16, WeekType.EVEN).isActiveIn(3))
    }

    @Test
    fun `当前周次按开学第一周周一计算`() {
        val start = LocalDate.of(2026, 8, 31)   // 真实开学日期，周一
        assertEquals(1, weekOf(start, start))
        assertEquals(1, weekOf(start, LocalDate.of(2026, 9, 6)))   // 第 1 周周日
        assertEquals(2, weekOf(start, LocalDate.of(2026, 9, 7)))   // 第 2 周周一
        assertEquals(3, weekOf(start, LocalDate.of(2026, 9, 15)))  // 今天
        assertEquals(16, weekOf(start, LocalDate.of(2026, 12, 20)))
    }

    @Test
    fun `开学日之前返回 0 表示学期未开始`() {
        val start = LocalDate.of(2026, 8, 31)
        assertEquals(0, weekOf(start, LocalDate.of(2026, 8, 30)))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd "C:/Users/user/AndroidStudioProjects/Timetable" && ./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.data.ModelsTest"
```
Expected: 编译失败 —— `Unresolved reference: Course`

- [ ] **Step 3: 写最小实现**

创建 `app/src/main/java/com/zhou/kebiao/data/Models.kt`：

```kotlin
package com.zhou.kebiao.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** 周类型。约定：第 1 周为单周，单周 = 奇数周。 */
@Serializable
enum class WeekType { EVERY, ODD, EVEN }

/** 课程。只保留 5 组字段，PDF 中其余信息一律不存。 */
@Serializable
data class Course(
    val name: String,           // 课程名称，含类型符号 ★☆◆■〇
    val startWeek: Int,
    val endWeek: Int,
    val weekType: WeekType,
    val dayOfWeek: Int,         // 1 = 周一 … 7 = 周日
    val startPeriod: Int,       // 1 … 11
    val endPeriod: Int,
    val location: String,       // 教室地点，可为空串
    val teacher: String,        // 任课老师，可为空串
)

/** 其他课程。无星期与节次，故不能并入 Course。 */
@Serializable
data class OtherCourse(
    val name: String,
    val startWeek: Int,
    val endWeek: Int,           // 单周课程时与 startWeek 相等
    val totalWeeks: Int?,       // PDF 里的「共 N 周」
    val location: String,
    val teacher: String,
)

/** 作息时间，每个节次一条。 */
@Serializable
data class PeriodTime(
    val period: Int,            // 1 … 11
    val start: String,          // "08:05"，空串表示未设置
    val end: String,
)

/** 整份课表。全 App 仅一份。 */
@Serializable
data class Timetable(
    val semesterName: String,
    val firstMonday: String,    // ISO-8601，如 "2026-08-31"
    val totalWeeks: Int,
    val periods: List<PeriodTime>,
    val courses: List<Course>,
    val otherCourses: List<OtherCourse>,
)

/** 这门课在第 [week] 周是否要上。 */
fun Course.isActiveIn(week: Int): Boolean {
    if (week < startWeek || week > endWeek) return false
    return when (weekType) {
        WeekType.EVERY -> true
        WeekType.ODD -> week % 2 == 1
        WeekType.EVEN -> week % 2 == 0
    }
}

/**
 * 今天是第几周。返回 0 表示学期尚未开始。
 * 周次 = floor((今天 − 开学第一周周一) / 7) + 1
 */
fun weekOf(firstMonday: LocalDate, today: LocalDate): Int {
    val days = ChronoUnit.DAYS.between(firstMonday, today)
    if (days < 0) return 0
    return (days / 7).toInt() + 1
}

/** 默认作息时间（学校实测值）。 */
fun defaultPeriods(): List<PeriodTime> = listOf(
    PeriodTime(1, "08:05", "08:55"),
    PeriodTime(2, "09:00", "09:50"),
    PeriodTime(3, "10:05", "10:55"),
    PeriodTime(4, "11:00", "11:50"),
    PeriodTime(5, "14:10", "15:00"),
    PeriodTime(6, "15:05", "15:55"),
    PeriodTime(7, "16:05", "16:55"),
    PeriodTime(8, "17:00", "17:50"),
    PeriodTime(9, "19:00", "19:50"),
    PeriodTime(10, "19:55", "20:45"),
)

/** 取某节的开始时间。找不到返回 null，界面据此显示「—」。 */
fun List<PeriodTime>.startOf(period: Int): LocalTime? =
    firstOrNull { it.period == period }?.start?.takeIf { it.isNotBlank() }?.let(LocalTime::parse)

/** 取某节的结束时间。 */
fun List<PeriodTime>.endOf(period: Int): LocalTime? =
    firstOrNull { it.period == period }?.end?.takeIf { it.isNotBlank() }?.let(LocalTime::parse)
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
cd "C:/Users/user/AndroidStudioProjects/Timetable" && ./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.data.ModelsTest"
```
Expected: `BUILD SUCCESSFUL`，7 个测试全部 PASS

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/data/Models.kt app/src/test/java/com/zhou/kebiao/data/ModelsTest.kt
git commit -m "feat(data): 课程数据模型与周次判断逻辑"
```

---

## Task 2: JSON 存储

**Files:**
- Create: `app/src/main/java/com/zhou/kebiao/data/TimetableStore.kt`
- Test: `app/src/test/java/com/zhou/kebiao/data/TimetableStoreTest.kt`

- [ ] **Step 1: 写失败的测试**

创建 `app/src/test/java/com/zhou/kebiao/data/TimetableStoreTest.kt`：

```kotlin
package com.zhou.kebiao.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TimetableStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun sample() = Timetable(
        semesterName = "2026-2027 第1学期",
        firstMonday = "2026-08-31",
        totalWeeks = 16,
        periods = defaultPeriods(),
        courses = listOf(
            Course("大学英语(3)★", 1, 16, WeekType.EVERY, 1, 3, 4, "2-1-313", "王淑芬"),
            Course("概率论与数理统计B★", 2, 16, WeekType.EVEN, 1, 5, 6, "2-3-411", "陈建国"),
        ),
        otherCourses = listOf(
            OtherCourse("电路基础实验☆", 1, 16, 16, "无", "何建军"),
        ),
    )

    @Test
    fun `写入后能原样读回`() {
        val file = tmp.newFile("timetable.json")
        val store = TimetableStore(file)

        store.save(sample())
        val loaded = store.load()

        assertEquals(sample(), loaded)
    }

    @Test
    fun `单周课程的起止周相等也能正确往返`() {
        val file = tmp.newFile("t2.json")
        val store = TimetableStore(file)
        val t = sample().copy(
            otherCourses = listOf(OtherCourse("电子产品设计■", 19, 19, 1, "无", "黄远,徐雯")),
        )

        store.save(t)
        val loaded = store.load()!!

        assertEquals(19, loaded.otherCourses[0].startWeek)
        assertEquals(19, loaded.otherCourses[0].endWeek)
    }

    @Test
    fun `文件不存在时返回 null 而不是抛异常`() {
        val file = tmp.newFile("t3.json")
        file.delete()

        assertNull(TimetableStore(file).load())
    }

    @Test
    fun `保存会创建父目录`() {
        val dir = tmp.newFolder("nested", "deeper")
        val file = java.io.File(dir, "timetable.json")
        val store = TimetableStore(file)

        store.save(sample())

        assertTrue(file.exists())
        assertTrue(file.readText().contains("大学英语(3)★"))
    }

    @Test
    fun `覆盖保存后旧内容不残留`() {
        val file = tmp.newFile("t4.json")
        val store = TimetableStore(file)

        store.save(sample())
        store.save(sample().copy(semesterName = "新名字"))

        val raw = file.readText()
        assertEquals("新名字", store.load()!!.semesterName)
        assertFalse(raw.contains("2026-2027 第1学期"))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd "C:/Users/user/AndroidStudioProjects/Timetable" && ./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.data.TimetableStoreTest"
```
Expected: 编译失败 —— `Unresolved reference: TimetableStore`

- [ ] **Step 3: 写最小实现**

创建 `app/src/main/java/com/zhou/kebiao/data/TimetableStore.kt`：

```kotlin
package com.zhou.kebiao.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 课表持久化：整个课表存成一个 JSON 文件。
 * 不用 Room —— 数据量只有几十条，JSON 更容易排查问题。
 */
class TimetableStore(private val file: File) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun load(): Timetable? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<Timetable>(file.readText()) }.getOrNull()
    }

    fun save(timetable: Timetable) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(timetable))
    }

    fun exists(): Boolean = file.exists()

    fun delete() {
        file.delete()
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
cd "C:/Users/user/AndroidStudioProjects/Timetable" && ./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.data.TimetableStoreTest"
```
Expected: `BUILD SUCCESSFUL`，5 个测试 PASS

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/data/TimetableStore.kt app/src/test/java/com/zhou/kebiao/data/TimetableStoreTest.kt
git commit -m "feat(data): 课表 JSON 持久化"
```

---

## Task 3: TextFragment 与 PDF 文本提取

**Files:**
- Create: `app/src/main/java/com/zhou/kebiao/parser/TextFragment.kt`
- Create: `app/src/main/java/com/zhou/kebiao/parser/PdfTextExtractor.kt`

本任务不写单元测试 —— `PdfTextExtractor` 是唯一碰 PDFBox 的地方，靠 Task 8 的端到端测试验证。中间表示 `TextFragment` 是纯数据，由后续任务的测试间接覆盖。

- [ ] **Step 1: 定义中间表示**

创建 `app/src/main/java/com/zhou/kebiao/parser/TextFragment.kt`：

```kotlin
package com.zhou.kebiao.parser

/** 从 PDF 里抠出来的一段文字，带坐标和字号。坐标已按页面 /Rotate 转正。 */
data class TextFragment(
    val pageIndex: Int,
    val x: Float,          // 文字块左边缘，转正后
    val y: Float,          // 文字块上边缘，转正后，向下增大
    val fontSize: Float,
    val text: String,
)

/** PDF 页眉信息。任一项都可能为空串 —— 认不出来不影响导入。 */
data class PdfHeader(
    val semesterName: String = "",
    val studentName: String = "",
    val studentId: String = "",
)

/** 解析结果。 */
sealed interface ParseResult {
    data class Success(
        val header: PdfHeader,
        val courses: List<com.zhou.kebiao.data.Course>,
        val otherCourses: List<com.zhou.kebiao.data.OtherCourse>,
    ) : ParseResult

    /** [reason] 直接展示给用户，例如「这份课表认不出来，可能是别的学校的模板」。 */
    data class Failure(val reason: String) : ParseResult
}
```

- [ ] **Step 2: 写 PDFBox 提取层**

创建 `app/src/main/java/com/zhou/kebiao/parser/PdfTextExtractor.kt`：

```kotlin
package com.zhou.kebiao.parser

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream

/**
 * PDF → TextFragment 列表。
 *
 * 设计说明：这是全项目唯一依赖 PDFBox 的文件。算法逻辑不写在这里，
 * 而是放到 TimetableParser —— 那样才能用 JVM 单元测试覆盖（PDFBox 需要 Android 运行时）。
 *
 * 坐标处理：样本 PDF 是 A4 横版 + /Rotate 90。PDFBox 的 getYDirAdj() 已经
 * 把 y 翻成「从上往下」，但**不会**应用 /Rotate。所以这里显式读取页面旋转角，
 * 把每个文字块的四角变换到视觉坐标后再取外接矩形。
 */
class PdfTextExtractor(private val context: Context) {

    init {
        // PDFBox-Android 需要在首次使用前初始化资源加载器
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun extract(input: InputStream): List<TextFragment> {
        val fragments = mutableListOf<TextFragment>()
        PDDocument.load(input).use { doc ->
            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String, textPositions: List<TextPosition>) {
                    for (tp in textPositions) {
                        val t = tp.unicode.trim()
                        if (t.isEmpty()) continue
                        fragments += toFragment(pageIndex, tp, t)
                    }
                }
            }
            stripper.sortByPosition = true
            stripper.startPage = 1
            stripper.endPage = doc.numberOfPages
            stripper.getText(doc)
        }
        return fragments
    }

    private fun toFragment(pageIndex: Int, tp: TextPosition, text: String): TextFragment {
        val rotation = (tp.page?.rotation ?: 0) % 360
        val mediaBox = tp.page.mediaBox
        val w = mediaBox.width
        val h = mediaBox.height

        // PDFBox 给的是「已翻转 y」的坐标：x 向右，y 向下，原点在页面左上角（未旋转）
        val x0 = tp.xDirAdj
        val y0 = tp.yDirAdj - tp.heightDir
        val x1 = tp.xDirAdj + tp.widthDir
        val y1 = tp.yDirAdj

        // 把四角按页面旋转角变换到视觉坐标，再取外接矩形
        val corners = listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1).map { (x, y) ->
            when (rotation) {
                90 -> (h - y) to x
                180 -> (w - x) to (h - y)
                270 -> y to (w - x)
                else -> x to y
            }
        }

        return TextFragment(
            pageIndex = currentPageNo - 1,     // PDFBox 的页号是 1 起，这里转成 0 起
            x = corners.minOf { it.first },
            y = corners.minOf { it.second },
            fontSize = tp.fontSizeInPt,
            text = text,
        )
    }
}
```

- [ ] **Step 3: 确认编译通过**

Run:
```bash
cd "C:/Users/user/AndroidStudioProjects/Timetable" && ./gradlew :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/parser/TextFragment.kt app/src/main/java/com/zhou/kebiao/parser/PdfTextExtractor.kt
git commit -m "feat(parser): PDFBox 文本提取层与中间表示"
```

---

## Task 4: 解析器 —— 星期列标定与归位

**Files:**
- Create: `app/src/main/java/com/zhou/kebiao/parser/TimetableParser.kt`
- Create: `app/src/test/java/com/zhou/kebiao/parser/SamplePdfText.kt`
- Test: `app/src/test/java/com/zhou/kebiao/parser/TimetableParserTest.kt`

**算法要点：** 不硬编码列坐标。从表头文字「星期一」…「星期日」的位置**自行标定**列中心，再按最近列中心归类文字块。这样换一个 PDF 库（PyMuPDF → PDFBox）或字距有细微差异都不会崩。

- [ ] **Step 1: 写测试夹具**

创建 `app/src/test/java/com/zhou/kebiao/parser/SamplePdfText.kt`。这是按样本 PDF 的**真实实测坐标**手工构造的，只保留每个测试用例需要的片段：

```kotlin
package com.zhou.kebiao.parser

/**
 * 测试夹具。坐标取自样本 PDF 的真实实测值（见设计文档 §3.1）。
 * 视觉坐标：x 向右，y 向下；列中心 = 表头「星期N」文字的中心。
 */
object SamplePdfText {

    /** 表头。7 个列中心分别是 151.0 / 254.8 / 358.7 / 462.5 / 566.4 / 670.2 / 774.1 */
    val header = listOf(
        frag(0, 21.4f, 63.4f, 12f, "时间段"),
        frag(0, 68.9f, 63.4f, 12f, "节次"),
        frag(0, 133.0f, 63.4f, 12f, "星期一"),
        frag(0, 236.8f, 63.4f, 12f, "星期二"),
        frag(0, 340.7f, 63.4f, 12f, "星期三"),
        frag(0, 444.5f, 63.4f, 12f, "星期四"),
        frag(0, 548.4f, 63.4f, 12f, "星期五"),
        frag(0, 652.2f, 63.4f, 12f, "星期六"),
        frag(0, 756.1f, 63.4f, 12f, "星期日"),
    )

    /**
     * 周一两门课：一门 1-16 周每周，一门 5-6 节单双周交替。
     *
     * 注意「概率论与数理统计B★」落在**第 1 页**、x 与周一其他内容相同（104.1）——
     * 这是真实情况：单元格内容太长，溢出到下一页顶部同一列。夹具如实保留，
     * 顺带把跨页拼接也一起测了。
     */
    val monday = listOf(
        // 第 3-4 节：大学英语(3)★ 1-16周
        frag(0, 104.1f, 386.6f, 9f, "大学英语(3)★"),
        frag(0, 104.1f, 399.5f, 8f, "(3-4节)1-16周/场地:2-1-"),
        frag(0, 104.1f, 411.5f, 8f, "313/教师:王淑芬/教学班:大"),
        frag(0, 104.1f, 423.5f, 8f, "学英语(3)-0073/教学班组成"),
        // 第 5-6 节：大学英语(3)☆ 1-15周(单)
        frag(0, 104.1f, 522.1f, 9f, "大学英语(3)☆"),
        frag(0, 104.1f, 535.0f, 8f, "(5-6节)1-15周(单)/场地:2-2-"),
        frag(0, 104.1f, 547.0f, 8f, "604/教师:王淑芬/教学班:大"),
        // 第 5-6 节：概率论与数理统计B★ 2-16周(双) —— 在下一页
        frag(1, 104.1f, 105.6f, 9f, "概率论与数理统计B★"),
        frag(1, 104.1f, 118.5f, 8f, "(5-6节)2-16周(双)/场地:2-3-"),
        frag(1, 104.1f, 130.5f, 8f, "411/教师:陈建国/教学班:概"),
    )

    /**
     * 干扰项：PDF 顶部的标题与右下的打印时间。
     * 它们的 x 坐标会分别吸附到「周三」和「周日」列，如果不加防护会被误判成课程。
     */
    val noise = listOf(
        frag(0, 361.0f, 28.9f, 24f, "李明轩课表"),        // x 落在周三列，y 在表头之上
        frag(0, 697.9f, 35.0f, 8f, " 学号：25406070399"),  // 同上，在表头之上
        frag(2, 751.5f, 98.5f, 8f, "打印时间:2026-09-03"), // x 落在周日列，在表头之下
    )

    /** 长课程名折行：必须合并成一门课，而不是四门。 */
    val wrappedName = listOf(
        frag(0, 415.6f, 241.1f, 9f, "概率论与数理统计B★"),
        frag(0, 415.6f, 254.0f, 8f, "(7-8节)1-16周/场地:2-3-"),
        frag(0, 415.6f, 266.0f, 8f, "305/教师:陈建国/教学班:概"),
        // 星期五列：课程名折成三行 + 一行正文
        frag(0, 519.5f, 241.1f, 9f, "毛泽东思想和中国特色"),
        frag(0, 519.5f, 254.6f, 9f, "社会主义理论体系概论"),
        frag(0, 519.5f, 268.1f, 9f, "★"),
        frag(0, 519.5f, 281.0f, 8f, "(7-8节)1-16周/场地:2-3-"),
        frag(0, 519.5f, 293.0f, 8f, "313/教师:刘强/教学班:毛泽"),
    )

    /** 跨页截断：第 0 页是该课的开头，第 1 页同一列同一 x 处是续文。 */
    val crossPage = listOf(
        // 第 0 页：周四 第 5-6 节 机械设计基础
        frag(0, 415.6f, 522.1f, 9f, "机械设计基础★"),
        frag(0, 415.6f, 535.0f, 8f, "(5-6节)1-7周/场地:2-3-"),
        frag(0, 415.6f, 547.0f, 8f, "305/教师:赵鹏/教学班:机械"),
        frag(0, 415.6f, 559.0f, 8f, "设计基础-0012/教学班组成"),
        // 第 1 页顶部，同一个 x=415.6，续上
        frag(1, 415.6f, 21.0f, 8f, ":智能科学与技术2501;测控"),
        frag(1, 415.6f, 33.0f, 8f, "技术与仪器2502;矿物加工工"),
        frag(1, 415.6f, 45.0f, 8f, "程2501;金属材料工程"),
        frag(1, 415.6f, 57.0f, 8f, "2502/考核方式:考查/选课备"),
    )

    /** 页面底部的「其他课程」区块，跨两行。 */
    val otherCourseBlock = listOf(
        frag(2, 18.0f, 70.2f, 10f, "其他课程：电路基础实验☆何建军(共16周)/1-16周/无;   电子产品设计■黄远,徐雯(共1周)/19周/无;   机械设计基础☆赵鹏(共3周)/2-4周/无;   机械设计基础☆何军(共3周)/5-7周/无;   专业前"),
        frag(2, 18.0f, 80.2f, 10f, "沿讲座■罗华,谢天成,韩雷,白鸿飞(共1周)/18周/无;   大学劳动教育(2)★沈佳慧(共2周)/12-13周/无;   工程应用软件实践■徐雯,曹远(共1周)/20周/无;"),
        frag(2, 18.0f, 98.5f, 8f, "★: 理论 ☆: 实验 ◆: 上机 ■: 实践 〇: 课外"),
    )

    /** 页眉。 */
    val pdfHeader = listOf(
        frag(0, 361.0f, 28.9f, 24f, "李明轩课表"),
        frag(0, 21.0f, 35.0f, 8f, "2026-2027学年第1学期"),
        frag(0, 697.9f, 35.0f, 8f, " 学号：25406070399"),
    )

    private fun frag(page: Int, x: Float, y: Float, size: Float, text: String) =
        TextFragment(page, x, y, size, text)
}
```

- [ ] **Step 2: 写失败的测试**

创建 `app/src/test/java/com/zhou/kebiao/parser/TimetableParserTest.kt`：

```kotlin
package com.zhou.kebiao.parser

import com.zhou.kebiao.data.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableParserTest {

    private val parser = TimetableParser()

    private fun parse(vararg groups: List<TextFragment>) =
        parser.parse(groups.flatMap { it })

    @Test
    fun `标定出 7 个星期列`() {
        val columns = parser.calibrateColumns(SamplePdfText.header)
        assertEquals(7, columns.size)
        // 列中心应落在表头文字的中心
        assertEquals(151.0f, columns[0], 0.5f)
        assertEquals(254.8f, columns[1], 0.5f)
        assertEquals(774.1f, columns[6], 0.5f)
    }

    @Test
    fun `把文字块归到正确的星期列`() {
        val columns = parser.calibrateColumns(SamplePdfText.header)
        // 周一列（中心 151）里的课程文字 x=104.1，距中心 46.9，应归周一
        assertEquals(1, parser.columnOf(104.1f, columns))
        // 周四列（中心 462.5）里的课程文字 x=415.6
        assertEquals(4, parser.columnOf(415.6f, columns))
        // 周五列（中心 566.4）里的课程文字 x=519.5
        assertEquals(5, parser.columnOf(519.5f, columns))
    }

    @Test
    fun `解析出周一的课程并带上正确的星期`() {
        val result = parse(SamplePdfText.header, SamplePdfText.monday)
        assertTrue(result is ParseResult.Success)
        val courses = (result as ParseResult.Success).courses

        val english = courses.first { it.name == "大学英语(3)★" }
        assertEquals(1, english.dayOfWeek)
        assertEquals(3, english.startPeriod)
        assertEquals(4, english.endPeriod)
        assertEquals(1, english.startWeek)
        assertEquals(16, english.endWeek)
        assertEquals(WeekType.EVERY, english.weekType)
        assertEquals("2-1-313", english.location)
        assertEquals("王淑芬", english.teacher)
    }

    @Test
    fun `同一时段单双周两门课都解析出来且不合并`() {
        val result = parse(SamplePdfText.header, SamplePdfText.monday) as ParseResult.Success
        val slot = result.courses.filter {
            it.dayOfWeek == 1 && it.startPeriod == 5 && it.endPeriod == 6
        }
        assertEquals(2, slot.size)

        val odd = slot.first { it.name == "大学英语(3)☆" }
        assertEquals(WeekType.ODD, odd.weekType)
        assertEquals(1, odd.startWeek)
        assertEquals(15, odd.endWeek)

        val even = slot.first { it.name == "概率论与数理统计B★" }
        assertEquals(WeekType.EVEN, even.weekType)
        assertEquals(2, even.startWeek)
        assertEquals(16, even.endWeek)
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run:
```bash
cd "C:/Users/user/AndroidStudioProjects/Timetable" && ./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.parser.TimetableParserTest"
```
Expected: 编译失败 —— `Unresolved reference: TimetableParser`

- [ ] **Step 4: 写最小实现**

创建 `app/src/main/java/com/zhou/kebiao/parser/TimetableParser.kt`：

```kotlin
package com.zhou.kebiao.parser

import com.zhou.kebiao.data.Course
import com.zhou.kebiao.data.OtherCourse
import com.zhou.kebiao.data.WeekType
import kotlin.math.abs

/**
 * 课表解析内核。纯函数，不碰 PDFBox 也不碰 Android，因此可以完整单元测试。
 *
 * 流水线（见设计文档 §6）：
 *   1. 从表头标定 7 个星期列中心
 *   2. 把每个文字块吸附到最近的列
 *   3. 每列内按 (页序, y) 排序，相邻拼接 —— 解决跨页截断
 *   4. 按字号切分课程名与正文 —— 课程名可能折行
 *   5. 从正文正则解析 5 组字段
 */
class TimetableParser {

    companion object {
        /** 字号差超过这个值就认为不是同一类文字（课程名 vs 正文）。 */
        private const val SIZE_EPSILON = 0.4f

        private val DAY_LABELS = listOf(
            "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日",
        )

        private val PERIOD_RANGE = Regex("""\((\d+)-(\d+)节\)""")
        private val WEEK_RANGE = Regex("""\((\d+)-(\d+)节\)(\d+)-(\d+)周(\((单|双)\))?""")
        private val LOCATION = Regex("""/场地:(.*?)/""")
        private val TEACHER = Regex("""/教师:(.*?)/""")
    }

    // ---------- 第 1 步：标定星期列 ----------

    /** 从表头文字标定 7 个列中心 x。辨认不出 7 个就抛异常。 */
    fun calibrateColumns(fragments: List<TextFragment>): List<Float> {
        val centers = DAY_LABELS.map { label ->
            val f = fragments.firstOrNull { it.text.trim() == label }
                ?: throw IllegalStateException("找不到表头：$label")
            f.x + f.fontSize * label.length / 2f   // 近似文字中心
        }
        return centers.sorted()
    }

    /** 表头所在的 y，用于裁掉它上方的标题与页眉。 */
    private fun headerY(fragments: List<TextFragment>): Float =
        fragments.filter { it.text.trim() == "星期一" }.minOfOrNull { it.y } ?: 0f

    /** 文字块属于第几个星期列（1..7）。超出容差返回 0（不属于任何列）。 */
    fun columnOf(x: Float, columns: List<Float>): Int {
        val half = columnSpacing(columns) / 2f
        var best = 0
        var bestDist = Float.MAX_VALUE
        columns.forEachIndexed { i, c ->
            val d = abs(x - c)
            if (d < bestDist) {
                bestDist = d
                best = i + 1
            }
        }
        return if (bestDist <= half) best else 0
    }

    private fun columnSpacing(columns: List<Float>): Float =
        if (columns.size < 2) Float.MAX_VALUE
        else (columns.last() - columns.first()) / (columns.size - 1)

    // ---------- 入口 ----------

    fun parse(fragments: List<TextFragment>): ParseResult {
        if (fragments.isEmpty()) {
            return ParseResult.Failure("这个文件里没有可读的文字")
        }

        val columns = try {
            calibrateColumns(fragments)
        } catch (e: IllegalStateException) {
            return ParseResult.Failure("这份课表认不出来，可能是别的学校的模板")
        }

        val header = parseHeader(fragments)
        val courses = parseCourses(fragments, columns)
        val others = OtherCourseParser.parse(fragments)

        // 一门网格课程都没有，就不是一份能用的课表 —— 调用方据此拒绝写入，
        // 避免把空数据存进去抹掉用户已有的课表（设计文档 §8.1）
        if (courses.isEmpty()) {
            return ParseResult.Failure("这份课表认不出来，可能是别的学校的模板")
        }

        return ParseResult.Success(header, courses, others)
    }

    private fun parseHeader(fragments: List<TextFragment>): PdfHeader {
        val semester = fragments.firstOrNull {
            Regex("""\d{4}-\d{4}学年第\d学期""").containsMatchIn(it.text)
        }?.text?.trim().orEmpty()

        val name = fragments.firstOrNull {
            it.fontSize > 20f && it.text.endsWith("课表")
        }?.text?.trim()?.removeSuffix("课表").orEmpty()

        val id = fragments.firstOrNull {
            it.text.contains("学号")
        }?.text?.let { Regex("""(\d{6,})""").find(it)?.groupValues?.get(1) }.orEmpty()

        return PdfHeader(semester, name, id)
    }

    // ---------- 第 2~3 步：归列 + 跨页拼接 ----------

    /**
     * 取出属于星期列的文字块，按 (列, 页序, y) 排好序。
     *
     * 两道防护，都是实打实踩出来的：
     *  - **裁掉表头之上的文字**。PDF 标题「李明轩课表」的 x 恰好落在「周三」列，
     *    页眉「学号：…」落在「周日」列；不裁的话会被当成课程名，粘到周三第一门课上。
     *  - **只收吸附得上列的文字**。第 3 页底部的「其他课程」区块 x=18，不属于任何列，自然排除。
     */
    private fun byColumn(fragments: List<TextFragment>, columns: List<Float>): Map<Int, List<TextFragment>> {
        val top = headerY(fragments)
        return fragments
            .filter { it.y >= top - 1f }
            .mapNotNull { f ->
                val col = columnOf(f.x, columns)
                if (col == 0) null else col to f
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> list.sortedWith(compareBy({ it.pageIndex }, { it.y })) }
    }

    // ---------- 第 4~5 步：切课程 + 解字段 ----------

    private fun parseCourses(fragments: List<TextFragment>, columns: List<Float>): List<Course> {
        val out = mutableListOf<Course>()
        for ((dayOfWeek, list) in byColumn(fragments, columns)) {
            // 正文字号 = 这一列里**最小**的字号，因为课程名一定比正文大。
            // 不能用「出现次数最多的字号」—— 遇上课名折成 3 行、正文只有 2 行的
            // 「毛泽东思想和中国特色社会主义理论体系概论★」，众数会落在课程名上，
            // 整门课就会被整段吞掉。
            val bodySize = list.minOf { it.fontSize }
            val nameThreshold = bodySize + SIZE_EPSILON

            var nameParts = mutableListOf<String>()
            var body = StringBuilder()

            fun reset() {
                nameParts = mutableListOf()
                body = StringBuilder()
            }

            fun flush() {
                val full = body.toString()
                // 正文里没有「(N-M节)」就不是课程。挡掉落进列的杂项（如右下的「打印时间:…」）。
                if (!PERIOD_RANGE.containsMatchIn(full)) {
                    reset()
                    return
                }
                out += buildCourse(nameParts.joinToString(""), full, dayOfWeek)
                reset()
            }

            for (f in list) {
                if (f.fontSize > nameThreshold) {
                    // 只有当课程已有正文时，新的名行才代表下一门课；否则是同一课程名的折行
                    if (body.isNotEmpty()) flush()
                    nameParts += f.text
                } else {
                    body.append(f.text)
                }
            }
            flush()
        }
        return out
    }

    private fun buildCourse(name: String, body: String, dayOfWeek: Int): Course {
        val period = PERIOD_RANGE.find(body)
        val week = WEEK_RANGE.find(body)
        val weekType = when (week?.groupValues?.get(6)) {
            "单" -> WeekType.ODD
            "双" -> WeekType.EVEN
            else -> WeekType.EVERY
        }
        return Course(
            name = name.ifBlank { "未知课程" },   // 认不出课名也要保住这门课
            startWeek = week?.groupValues?.get(3)?.toIntOrNull() ?: 1,
            endWeek = week?.groupValues?.get(4)?.toIntOrNull() ?: 16,
            weekType = weekType,
            dayOfWeek = dayOfWeek,
            startPeriod = period?.groupValues?.get(1)?.toIntOrNull() ?: 1,
            endPeriod = period?.groupValues?.get(2)?.toIntOrNull() ?: 1,
            location = LOCATION.find(body)?.groupValues?.get(1).orEmpty(),
            teacher = TEACHER.find(body)?.groupValues?.get(1).orEmpty(),
        )
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run:
```bash
cd "C:/Users/user/AndroidStudioProjects/Timetable" && ./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.parser.TimetableParserTest"
```
Expected: `BUILD SUCCESSFUL`，4 个测试 PASS

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/parser/TimetableParser.kt app/src/test/java/com/zhou/kebiao/parser/
git commit -m "feat(parser): 星期列标定、归列与跨页拼接"
```

---

## Task 5: 解析器 —— 课程名折行与跨页截断的专项测试

**Files:**
- Test: `app/src/test/java/com/zhou/kebiao/parser/TimetableParserEdgeCaseTest.kt`

Task 4 已经实现了逻辑，本任务**只补测试**，用真实边界数据把它钉死。若测试暴露出 bug，就地修 `TimetableParser`。

- [ ] **Step 1: 写测试**

创建 `app/src/test/java/com/zhou/kebiao/parser/TimetableParserEdgeCaseTest.kt`：

```kotlin
package com.zhou.kebiao.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableParserEdgeCaseTest {

    private val parser = TimetableParser()

    private fun parse(vararg groups: List<TextFragment>) =
        parser.parse(groups.flatMap { it }) as ParseResult.Success

    @Test
    fun `折成四行的长课程名必须合并成一门课`() {
        val result = parse(SamplePdfText.header, SamplePdfText.wrappedName)

        val mao = result.courses.filter { it.name.contains("毛泽东") }
        assertEquals("毛泽东思想必须合并成 1 门，实际 ${mao.size} 门", 1, mao.size)

        // ★ 是课程名的最后一行，也要并进去，不能被当成独立课程
        assertEquals("毛泽东思想和中国特色社会主义理论体系概论★", mao[0].name)
        assertEquals(5, mao[0].dayOfWeek)
        assertEquals(7, mao[0].startPeriod)
        assertEquals(8, mao[0].endPeriod)
        assertEquals("2-3-313", mao[0].location)
        assertEquals("刘强", mao[0].teacher)
    }

    @Test
    fun `折行课程与相邻课程不会互相污染`() {
        val result = parse(SamplePdfText.header, SamplePdfText.wrappedName)
        // 周四那门独立课程应当完好
        val prob = result.courses.first { it.name == "概率论与数理统计B★" && it.dayOfWeek == 4 }
        assertEquals(1, prob.startWeek)
        assertEquals(16, prob.endWeek)
        assertEquals("2-3-305", prob.location)
    }

    @Test
    fun `跨页截断的课程能拼回完整信息`() {
        // 第 0 页给了课程名和周次，第 1 页同一列同一 x 处续上考核方式等
        val result = parse(SamplePdfText.header, SamplePdfText.crossPage)

        val courses = result.courses.filter {
            it.dayOfWeek == 4 && it.startPeriod == 5
        }
        assertEquals("跨页的两半必须拼成一门课，实际 ${courses.size} 门", 1, courses.size)

        val c = courses[0]
        assertEquals("机械设计基础★", c.name)
        assertEquals(1, c.startWeek)
        assertEquals(7, c.endWeek)
        assertEquals("2-3-305", c.location)
        assertEquals("赵鹏", c.teacher)
    }

    @Test
    fun `PDF 标题与打印时间不会被误判成课程`() {
        // 标题「李明轩课表」(x=361) 落在周三列，且字号 24 明显大于课程名，
        // 不做防护就会被当成周三第一门课的名字；「打印时间:…」(x=751.5) 落在周日列。
        val result = parse(SamplePdfText.header, SamplePdfText.monday, SamplePdfText.noise)

        assertTrue("周三不该凭空多出课程", result.courses.none { it.dayOfWeek == 3 })
        assertTrue("周日不该凭空多出课程", result.courses.none { it.dayOfWeek == 7 })
        assertTrue("课名不该被标题污染", result.courses.none { it.name.contains("李明轩") })
        assertTrue("课名不该被打印时间污染", result.courses.none { it.name.contains("打印时间") })
        assertTrue("学号不该变成课程", result.courses.none { it.name.contains("学号") })

        // 周一那 3 门必须完好无损
        assertEquals(3, result.courses.count { it.dayOfWeek == 1 })
        assertEquals("大学英语(3)★", result.courses.first { it.dayOfWeek == 1 }.name)
    }

    @Test
    fun `正文里没有节次的信息不会被当成课程`() {
        val junk = listOf(
            frag(0, 104.1f, 386.6f, 8f, "某段没有节次标记的文字"),
            frag(0, 104.1f, 399.5f, 8f, "继续一段无关内容"),
        )
        val result = parser.parse(SamplePdfText.header + junk)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `认不出表头时返回失败而不是崩溃`() {
        val notATimetable = listOf(
            TextFragment(0, 10f, 10f, 8f, "这是一份普通的 PDF 文档"),
            TextFragment(0, 10f, 20f, 8f, "和课表没有任何关系"),
        )
        val result = parser.parse(notATimetable)

        assertTrue(result is ParseResult.Failure)
        assertEquals("这份课表认不出来，可能是别的学校的模板",
            (result as ParseResult.Failure).reason)
    }

    @Test
    fun `空输入返回失败`() {
        assertTrue(parser.parse(emptyList()) is ParseResult.Failure)
    }
}
```

- [ ] **Step 2: 运行测试**

Run:
```bash
cd "C:/Users/user/AndroidStudioProjects/Timetable" && ./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.parser.TimetableParserEdgeCaseTest"
```
Expected: 5 个测试 PASS

若有 FAIL，按提示修 `TimetableParser.kt`（不要改测试数据 —— 夹具是从真实 PDF 实测抄来的），改完重跑直到全绿。

- [ ] **Step 3: 提交**

```bash
git add app/src/test/java/com/zhou/kebiao/parser/TimetableParserEdgeCaseTest.kt app/src/main/java/com/zhou/kebiao/parser/TimetableParser.kt
git commit -m "test(parser): 折行课程名与跨页截断的边界用例"
```

---

## Task 6: 解析器 —— 「其他课程」区块

**Files:**
- Create: `app/src/main/java/com/zhou/kebiao/parser/OtherCourseParser.kt`
- Test: `app/src/test/java/com/zhou/kebiao/parser/OtherCourseParserTest.kt`

- [ ] **Step 1: 写失败的测试**

创建 `app/src/test/java/com/zhou/kebiao/parser/OtherCourseParserTest.kt`：

```kotlin
package com.zhou.kebiao.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class OtherCourseParserTest {

    private val parsed = OtherCourseParser.parse(SamplePdfText.otherCourseBlock)

    @Test
    fun `解析出全部 7 条`() {
        assertEquals(7, parsed.size)
    }

    @Test
    fun `第一条的字段完全正确`() {
        val c = parsed[0]
        assertEquals("电路基础实验☆", c.name)   // 类型符号并入名称
        assertEquals("何建军", c.teacher)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
        assertEquals(16, c.totalWeeks)
        assertEquals("无", c.location)
    }

    @Test
    fun `单周课程的起止周相等`() {
        val design = parsed.first { it.name == "电子产品设计■" }
        assertEquals(19, design.startWeek)
        assertEquals(19, design.endWeek)
        assertEquals(1, design.totalWeeks)
        assertEquals("黄远,徐雯", design.teacher)
    }

    @Test
    fun `多人教师保留逗号分隔`() {
        val lecture = parsed.first { it.name == "专业前沿讲座■" }
        assertEquals("罗华,谢天成,韩雷,白鸿飞", lecture.teacher)
        assertEquals(18, lecture.startWeek)
    }

    @Test
    fun `课程名里带全角数字括号不会被误切`() {
        val labor = parsed.first { it.name.startsWith("大学劳动教育") }
        assertEquals("大学劳动教育(2)★", labor.name)
        assertEquals("沈佳慧", labor.teacher)
        assertEquals(12, labor.startWeek)
        assertEquals(13, labor.endWeek)
    }

    @Test
    fun `跨两行的区块能正确拼接`() {
        // 第 7 条在第 2 行开头，如果拼接失败就会丢
        val last = parsed.first { it.name == "工程应用软件实践■" }
        assertEquals(20, last.startWeek)
        assertEquals("徐雯,曹远", last.teacher)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd "C:/Users/user/AndroidStudioProjects/Timetable" && ./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.parser.OtherCourseParserTest"
```
Expected: 编译失败 —— `Unresolved reference: OtherCourseParser`

- [ ] **Step 3: 写最小实现**

创建 `app/src/main/java/com/zhou/kebiao/parser/OtherCourseParser.kt`：

```kotlin
package com.zhou.kebiao.parser

import com.zhou.kebiao.data.OtherCourse

/**
 * 解析 PDF 第 3 页底部的「其他课程」区块。
 *
 * 该区块与主表格式完全不同：以「其他课程：」开头，条目用 `;` 分隔，
 * 条目之间有三个空格，整体可能跨两行。单条格式：
 *   {课程名}{类型符号}{教师}(共N周)/{周次}/{场地}
 * 其中周次可能是区间（`2-4周`）也可能是单周（`19周`）。
 */
object OtherCourseParser {

    private const val MARKER = "其他课程："
    private const val SEPARATOR = ";"

    /** 单条：课程名 + 符号 + 教师 + (共N周) / 周次 / 场地 */
    private val ITEM = Regex("""^(.+?)([★☆◆■〇])(.+?)\(共(\d+)周\)/([^/]+)/([^;]*)$""")

    /** 区间周次，如 `2-4周`。 */
    private val WEEK_RANGE = Regex("""^(\d+)-(\d+)周$""")

    /** 单周，如 `19周`。 */
    private val WEEK_SINGLE = Regex("""^(\d+)周$""")

    fun parse(fragments: List<TextFragment>): List<OtherCourse> {
        val startIndex = fragments.indexOfFirst { it.text.contains(MARKER) }
        if (startIndex == -1) return emptyList()

        // 从标记行开始，逐行拼接，直到遇到图例行（以「★: 理论」开头）
        val block = StringBuilder()
        for (i in startIndex until fragments.size) {
            val t = fragments[i].text
            if (i > startIndex && (t.trimStart().startsWith("★:") || t.contains("打印时间"))) break
            if (i > startIndex) block.append('\n')
            block.append(t)
        }

        val raw = block.toString()
            .substringAfter(MARKER)
            .replace("\n", "")
            .trim()

        return raw.split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull(::parseItem)
    }

    private fun parseItem(raw: String): OtherCourse? {
        val m = ITEM.find(raw) ?: return null
        val name = m.groupValues[1] + m.groupValues[2]   // 类型符号并入名称
        val teacher = m.groupValues[3].trim()
        val totalWeeks = m.groupValues[4].toIntOrNull()
        val (start, end) = parseWeeks(m.groupValues[5].trim())
        return OtherCourse(
            name = name,
            startWeek = start,
            endWeek = end,
            totalWeeks = totalWeeks,
            location = m.groupValues[6].trim(),
            teacher = teacher,
        )
    }

    private fun parseWeeks(raw: String): Pair<Int, Int> {
        WEEK_RANGE.find(raw)?.let {
            return it.groupValues[1].toInt() to it.groupValues[2].toInt()
        }
        WEEK_SINGLE.find(raw)?.let {
            val w = it.groupValues[1].toInt()
            return w to w       // 单周：起始 = 结束
        }
        return 1 to 16
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
cd "C:/Users/user/AndroidStudioProjects/Timetable" && ./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.parser.OtherCourseParserTest"
```
Expected: `BUILD SUCCESSFUL`，6 个测试 PASS

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/parser/OtherCourseParser.kt app/src/test/java/com/zhou/kebiao/parser/OtherCourseParserTest.kt
git commit -m "feat(parser): 解析「其他课程」区块"
```

---

## Task 7: 解析器 —— 集成到 parse() 并断言组合行为

**Files:**
- Modify: `app/src/main/java/com/zhou/kebiao/parser/TimetableParser.kt`（Task 4 已调用 `OtherCourseParser`，此处若尚未接入则补上）
- Test: `app/src/test/java/com/zhou/kebiao/parser/TimetableParserIntegrationTest.kt`

- [ ] **Step 1: 写测试**

创建 `app/src/test/java/com/zhou/kebiao/parser/TimetableParserIntegrationTest.kt`：

```kotlin
package com.zhou.kebiao.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableParserIntegrationTest {

    private val parser = TimetableParser()

    /** 把夹具的全部片段合起来，模拟真实 PDF 的输出。 */
    private fun fullSample() = listOf(
        SamplePdfText.pdfHeader,
        SamplePdfText.header,
        SamplePdfText.monday,
        SamplePdfText.wrappedName,
        SamplePdfText.crossPage,
        SamplePdfText.otherCourseBlock,
    ).flatten().sortedWith(compareBy({ it.pageIndex }, { it.y }))

    @Test
    fun `整体解析成功`() {
        val result = parser.parse(fullSample())
        assertTrue("解析应成功，实际 $result", result is ParseResult.Success)
    }

    @Test
    fun `页眉信息被读出来`() {
        val h = (parser.parse(fullSample()) as ParseResult.Success).header
        assertEquals("2026-2027学年第1学期", h.semesterName)
        assertEquals("李明轩", h.studentName)
        assertEquals("25406070399", h.studentId)
    }

    @Test
    fun `网格课程与其他课程分别落到两个列表`() {
        val r = parser.parse(fullSample()) as ParseResult.Success
        assertTrue("网格课程不该为空", r.courses.isNotEmpty())
        assertTrue("其他课程不该为空", r.otherCourses.isNotEmpty())
        // 其他课程没有星期与节次，绝不能混进网格课程列表
        assertTrue(r.courses.none { it.name == "电路基础实验☆" })
        assertTrue(r.otherCourses.none { it.name.contains("大学英语") })
    }

    @Test
    fun `其他课程条数正确`() {
        val r = parser.parse(fullSample()) as ParseResult.Success
        assertEquals(7, r.otherCourses.size)
    }
}
```

- [ ] **Step 2: 运行测试**

Run:
```bash
cd "C:/Users/user/AndroidStudioProjects/Timetable" && ./gradlew :app:testDebugUnitTest --tests "com.zhou.kebiao.parser.TimetableParserIntegrationTest"
```
Expected: 4 个测试 PASS。若有 FAIL，按提示修实现。

- [ ] **Step 3: 跑全部单元测试**

Run:
```bash
cd "C:/Users/user/AndroidStudioProjects/Timetable" && ./gradlew :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`，全部测试 PASS（Models 7 + Store 5 + Parser 4 + EdgeCase 7 + OtherCourse 6 + Integration 4 = 33 个）

- [ ] **Step 4: 提交**

```bash
git add app/src/test/java/com/zhou/kebiao/parser/TimetableParserIntegrationTest.kt app/src/main/java/com/zhou/kebiao/parser/TimetableParser.kt
git commit -m "test(parser): 解析器集成测试"
```

---

## Task 8: 端到端 —— 用真实样本 PDF 验证

前面的测试都用**手工构造的坐标夹具**。本任务用**真实的 PDF 文件**跑一遍，确认 PDFBox 提取层给出的坐标与夹具假设一致。这是整套算法的最终验证。

**Files:**
- Create: `app/src/androidTest/java/com/zhou/kebiao/parser/SamplePdfInstrumentedTest.kt`
- Create: `app/src/androidTest/assets/sample-timetable.pdf`（由用户提供的样本 PDF 复制而来）

> 这是 instrumented test，需要模拟器或真机运行。

- [ ] **Step 1: 把样本 PDF 放进工程**

样本 PDF 原始位置（微信接收目录）：

```
D:\WeChatFiles\xwechat_files\wxid_example_user\msg\file\2026-09\李明轩(2026-2027-1)课表 (1).pdf
```

Run:
```bash
mkdir -p "C:/Users/user/AndroidStudioProjects/Timetable/app/src/androidTest/assets"
cp "D:/WeChatFiles/xwechat_files/wxid_example_user/msg/file/2026-09/李明轩(2026-2027-1)课表 (1).pdf" \
   "C:/Users/user/AndroidStudioProjects/Timetable/app/src/androidTest/assets/sample-timetable.pdf"
ls -la "C:/Users/user/AndroidStudioProjects/Timetable/app/src/androidTest/assets/"
```
Expected: 看到 `sample-timetable.pdf`，约 6237 字节

- [ ] **Step 2: 写 instrumented 测试**

创建 `app/src/androidTest/java/com/zhou/kebiao/parser/SamplePdfInstrumentedTest.kt`：

```kotlin
package com.zhou.kebiao.parser

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真实样本 PDF 跑通整条流水线。
 * 夹具测试证明了「给定这些坐标，算法能还原课表」；
 * 这个测试证明「PDFBox 给出的坐标确实长这样」。
 */
class SamplePdfInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun parseSample(): ParseResult {
        val input = context.assets.open("sample-timetable.pdf")
        val fragments = PdfTextExtractor(context).extract(input)
        return TimetableParser().parse(fragments)
    }

    @Test
    fun `样本 PDF 能成功解析`() {
        val result = parseSample()
        assertTrue("应解析成功，实际 $result", result is ParseResult.Success)
    }

    @Test
    fun `解析出 20 门网格课程`() {
        val r = parseSample() as ParseResult.Success
        assertEquals("样本共 20 门网格课程", 20, r.courses.size)
    }

    @Test
    fun `解析出 7 条其他课程`() {
        val r = parseSample() as ParseResult.Success
        assertEquals(7, r.otherCourses.size)
    }

    @Test
    fun `星期分布正确`() {
        val r = parseSample() as ParseResult.Success
        assertEquals(6, r.courses.count { it.dayOfWeek == 1 })  // 周一 6 门
        assertEquals(4, r.courses.count { it.dayOfWeek == 2 })  // 周二 4 门
        assertEquals(3, r.courses.count { it.dayOfWeek == 3 })  // 周三 3 门
        assertEquals(3, r.courses.count { it.dayOfWeek == 4 })  // 周四 3 门
        assertEquals(4, r.courses.count { it.dayOfWeek == 5 })  // 周五 4 门
        assertEquals(0, r.courses.count { it.dayOfWeek == 6 })
        assertEquals(0, r.courses.count { it.dayOfWeek == 7 })
    }

    @Test
    fun `长课程名合并成一门`() {
        val r = parseSample() as ParseResult.Success
        val mao = r.courses.filter { it.name.contains("毛泽东") }
        assertEquals("必须合并成 1 门，实际 ${mao.size} 门", 1, mao.size)
    }

    @Test
    fun `单双周课程都解析出来`() {
        val r = parseSample() as ParseResult.Success
        val single = r.courses.first { it.name.contains("大学英语") && it.name.endsWith("☆") }
        assertEquals(com.zhou.kebiao.data.WeekType.ODD, single.weekType)
        val double = r.courses.first { it.name.contains("概率论") }
        assertTrue(double.weekType == com.zhou.kebiao.data.WeekType.EVEN ||
                   double.weekType == com.zhou.kebiao.data.WeekType.EVERY)
    }

    @Test
    fun `页眉被读出来`() {
        val h = (parseSample() as ParseResult.Success).header
        assertEquals("2026-2027学年第1学期", h.semesterName)
        assertEquals("25406070399", h.studentId)
    }
}
```

在 `app/build.gradle.kts` 的 `defaultConfig { }` 里确认已有（模板默认会加）：

```kotlin
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
```

并在 `dependencies { }` 里确认已有（模板默认会加）：

```kotlin
        androidTestImplementation("androidx.test.ext:junit:1.2.1")
        androidTestImplementation("androidx.test:runner:1.6.2")
```

- [ ] **Step 3: 启动模拟器并运行**

先在 Android Studio 里点 Run 启动 `Medium_Phone_API_36.1` 模拟器，然后：

Run:
```bash
cd "C:/Users/user/AndroidStudioProjects/Timetable" && ./gradlew :app:connectedDebugAndroidTest
```
Expected: `BUILD SUCCESSFUL`，7 个测试 PASS

**这一步很可能暴露真实问题** —— 例如 PDFBox 的坐标与 PyMuPDF 差一个偏移量、字号读数不同。如果失败：

1. 先别改算法逻辑
2. 加一个临时测试把 `fragments` 前 30 条打印出来（`android.util.Log` 或 `println`）
3. 对照设计文档 §3.1 的实测坐标，看差在哪
4. 需要调整的是 `PdfTextExtractor` 的坐标变换或 `TimetableParser` 的容差常数，**不是**测试夹具

- [ ] **Step 4: 提交**

```bash
git add app/src/androidTest/ app/build.gradle.kts
git commit -m "test(parser): 用真实样本 PDF 跑通端到端解析"
```

---

## Task 9: 解析结果接入 App 的调试入口

本计划只做解析内核，但需要一个能在手机上亲眼看到结果的最小入口，否则无法确认「真的能用」。

**Files:**
- Create: `app/src/main/java/com/zhou/kebiao/ui/DebugParseScreen.kt`
- Modify: `app/src/main/java/com/zhou/kebiao/MainActivity.kt`

- [ ] **Step 1: 写调试页**

创建 `app/src/main/java/com/zhou/kebiao/ui/DebugParseScreen.kt`：

```kotlin
package com.zhou.kebiao.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zhou.kebiao.parser.ParseResult
import com.zhou.kebiao.parser.PdfTextExtractor
import com.zhou.kebiao.parser.TimetableParser

/**
 * 临时调试页：选一个 PDF，把解析结果直接印在屏幕上。
 * 第二份计划（界面）完成后整页替换掉。
 */
@Composable
fun DebugParseScreen() {
    val context = LocalContext.current
    var output by remember { mutableStateOf("点上面的按钮选一份课表 PDF") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        output = runCatching {
            val fragments = context.contentResolver.openInputStream(uri)!!.use {
                PdfTextExtractor(context).extract(it)
            }
            when (val r = TimetableParser().parse(fragments)) {
                is ParseResult.Failure -> "解析失败：${r.reason}"
                is ParseResult.Success -> buildString {
                    appendLine("学期：${r.header.semesterName}")
                    appendLine("页眉：${r.header.studentName} / ${r.header.studentId}")
                    appendLine("网格课程 ${r.courses.size} 门，其他课程 ${r.otherCourses.size} 条")
                    appendLine()
                    r.courses.forEach { c ->
                        appendLine(
                            "周${c.dayOfWeek} 第${c.startPeriod}-${c.endPeriod}节 " +
                                "${c.startWeek}-${c.endWeek}周 ${c.weekType} " +
                                "${c.name} @${c.location} ${c.teacher}"
                        )
                    }
                    appendLine()
                    appendLine("—— 其他课程 ——")
                    r.otherCourses.forEach { o ->
                        appendLine("${o.name} ${o.startWeek}-${o.endWeek}周 共${o.totalWeeks}周 ${o.teacher}")
                    }
                }
            }
        }.getOrElse { "出错：${it.message}" }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Button(onClick = { picker.launch(arrayOf("application/pdf")) }) {
            Text("选择课表 PDF")
        }
        Text(
            text = output,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
```

- [ ] **Step 2: 让 MainActivity 显示它**

打开 `app/src/main/java/com/zhou/kebiao/MainActivity.kt`，把 `setContent { }` 里的模板内容整体替换为：

```kotlin
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DebugParseScreen()
                }
            }
        }
```

需要保证 `MainActivity.kt` 顶部有这些 import：

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.zhou.kebiao.ui.DebugParseScreen
```

- [ ] **Step 3: 在模拟器上跑起来，亲手点一遍**

在 Android Studio 里 Run，然后在模拟器上：

1. 先通过模拟器的文件管理器或 `adb push` 把样本 PDF 放进模拟器的下载目录：
   ```bash
   adb push "C:/Users/user/AndroidStudioProjects/Timetable/app/src/androidTest/assets/sample-timetable.pdf" /sdcard/Download/
   ```
2. App 里点「选择课表 PDF」→ 选 `sample-timetable.pdf`
3. 屏幕上应出现「网格课程 20 门，其他课程 7 条」，以及逐条课程列表

Expected: 20 门课程全部列出，毛泽东思想只有一条，单双周标注正确

**这是本计划的验收标准** —— 亲眼看到 20 门课。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/zhou/kebiao/
git commit -m "feat(ui): 解析结果调试页，用于验证导入结果"
```

---

## 完成后的状态

- 工程能构建、能安装到手机
- 选一份课表 PDF，App 能把 20 门课 + 7 条其他课程完整解析出来并显示
- 33 个单元测试 + 7 个 instrumented 测试全绿
- 数据模型与 JSON 存储就绪，等第二份计划接界面

## 第二份计划的内容（不在本计划内）

界面与交互：底部三 Tab 导航、今天页（全天时间轴）、课表页（网格 + 周次滑动 + 三种点击弹窗）、设置页、SAF 导入流程接到顶部按钮、导入后写入 TimetableStore。
