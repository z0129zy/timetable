package com.zhou.kebiao.parser

import com.zhou.kebiao.data.OtherCourse

/**
 * 解析 PDF 第 3 页底部的「其他课程」区块。
 *
 * 该区块与主表格式完全不同：以「其他课程：」开头，条目用 `;` 分隔，
 * 条目之间有三个空格，整体可能跨两行。单条格式：
 *   {课程名}{类型符号}{教师}(共N周)/{周次}/{场地}
 * 其中周次可能是区间（`2-4周`）也可能是单周（`19周`）。
 *
 * 这个区块整体是一次 writeString 回调（横跨近 800pt），所以它的 x 不落在任何星期列，
 * 主表格解析器不会误收它；反过来这里也只认「其他课程：」这个锚点。
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
