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
    fun `文件损坏时返回 null 而不是抛异常`() {
        val file = tmp.newFile("t5.json")

        file.writeText("这不是 JSON")
        assertNull(TimetableStore(file).load())

        file.writeText("")
        assertNull(TimetableStore(file).load())

        file.writeText("""{"semesterName": "缺字段"}""")
        assertNull(TimetableStore(file).load())
    }

    @Test
    fun `保存会创建父目录`() {
        // 父目录刻意不预先创建 —— 这样才真正验证到 save() 里的 mkdirs()
        val file = java.io.File(tmp.root, "nested/deeper/timetable.json")
        assertFalse("前置条件：父目录不该存在", file.parentFile!!.exists())

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
