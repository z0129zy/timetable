package com.zhou.kebiao.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 字号原来散落成 10 个值（7–16sp），收敛成 6 档。
//
// 数字一律开 tnum（等宽字形）：时间列、节次、日期在等宽下才对得齐。
// 比例数字宽度不一，同一列里「08:05」和「19:55」会差出半格，看着毛糙。
private const val TABULAR = "tnum"

/** 页面主标题：今天、第 N 周。 */
val TextPageTitle = TextStyle(
    fontSize = 20.sp,
    lineHeight = 26.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = TABULAR,
)

/** 分区标题、弹窗标题。 */
val TextTitle = TextStyle(
    fontSize = 15.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = TABULAR,
)

/** 弹窗正文。 */
val TextBody = TextStyle(
    fontSize = 13.sp,
    lineHeight = 19.sp,
    fontWeight = FontWeight.Normal,
)

/** 列表里的主行：课名、设置项名。 */
val TextItem = TextStyle(
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.SemiBold,
)

/** 弹窗里的正文、设置项的值：比 [TextItem] 小一号且不加粗。 */
val TextBodySmall = TextStyle(
    fontSize = 11.sp,
    lineHeight = 15.sp,
    fontWeight = FontWeight.Normal,
    fontFeatureSettings = TABULAR,
)

/** 说明、次要行、日期。 */
val TextCaption = TextStyle(
    fontSize = 10.sp,
    lineHeight = 15.sp,
    fontWeight = FontWeight.Normal,
    fontFeatureSettings = TABULAR,
)

/**
 * 网格卡片文字。8sp 是列宽逼出来的下限：一列只有约 49dp，
 * 中文一行也就放下 6 个字。再小读不清，再大长课名折行会失控。
 */
val TextMicro = TextStyle(
    fontSize = 8.sp,
    lineHeight = 11.sp,
    fontWeight = FontWeight.Normal,
    fontFeatureSettings = TABULAR,
)

/** 网格卡片里的课名，比场地行重一档。 */
val TextMicroStrong = TextStyle(
    fontSize = 8.sp,
    lineHeight = 11.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = TABULAR,
)

/** 启动页那句小字：衬线 + 拉开字距。 */
val TextTagline = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = 14.sp,
    lineHeight = 22.sp,
    letterSpacing = 2.sp,
    fontWeight = FontWeight.Normal,
)

/**
 * Material 组件（对话框按钮、导航栏标签等）会去查这套 Typography，
 * 把上面几档映射进去，它们才跟着走同一套字号。
 */
val Typography = Typography(
    titleLarge = TextPageTitle,
    titleMedium = TextTitle,
    bodyLarge = TextBody,
    bodyMedium = TextItem,
    bodySmall = TextCaption,
    labelLarge = TextItem,
    labelMedium = TextCaption,
    labelSmall = TextMicro,
)
