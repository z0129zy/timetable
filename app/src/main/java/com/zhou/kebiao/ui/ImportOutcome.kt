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
