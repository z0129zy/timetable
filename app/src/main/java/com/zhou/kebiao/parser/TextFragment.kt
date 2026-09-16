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
