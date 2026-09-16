package com.zhou.kebiao.ui.theme

import androidx.compose.ui.graphics.Color

// ── 方向：暖奶油 · 哑光低饱和 ────────────────────────────────────────────
// 色值取自 docs/design/ 里选定的概念图（逐像素实测），不是估出来的。
//
// 下面几条是「先算过对比度才敢定」的，改动前请重算，否则会出现读不清的文字：
//  · 珊瑚 #E8836F 在暖底上只有 2.43 —— 只能做色块/装饰，绝不能当文字色
//  · 旧的强调蓝 #3B6FD4 在暖底上只有 4.35，达不到 AA(4.5)，所以换成 #35618C(5.92)
//  · 旧的第三级灰 #999999 只有 2.60，同样不达标，换成 #8A8075(3.54)
//  · 「非本周」药丸原本是深底白字，任何足够浅的暖灰都到不了 4.5，
//    所以反过来做成「浅底深字」#E5E1D8 + #6B6259 = 4.58

// ── 面 ──

/** 整屏基底。暖奶油。 */
val Canvas = Color(0xFFFBF4E7)

/** 列表类卡片底：比基底再亮一档，靠「更亮」浮起来而不是靠描边。 */
val Surface = Color(0xFFFFFDF4)

/** 次级面：置灰、内嵌区块。 */
val SurfaceMuted = Color(0xFFE5E1D8)

/** 底部导航条底：夹在基底与卡片之间，加一条发丝线就够分开。 */
val NavBarSurface = Color(0xFFF8F1E0)

// ── 墨（暖灰阶，不是中性灰）──

val InkPrimary = Color(0xFF241F1A)
val InkSecondary = Color(0xFF6B6259)

/** 三级：提示、日期这类小字。 */
val InkTertiary = Color(0xFF8A8075)

/** 只给图标与装饰用，不承载文字信息。 */
val InkDisabled = Color(0xFFB3AA9E)

// ── 线 ──

val DividerLine = Color(0xFFE8DFCC)

/** 课表网格里每一节之间的横线，比 DividerLine 更淡。 */
val RowLine = Color(0xFFEFE7D6)

// ── 珊瑚：装饰专用，不用于文字 ──

/** 大色块底（选中态、柔和强调）。 */
val CoralSoft = Color(0xFFF7CFC5)

/** 主装饰：色条、图标、指示条。 */
val Coral = Color(0xFFE8836F)

/** 需要承载白字时用这个（白字在其上 4.58）。 */
val CoralDeep = Color(0xFFB85A45)

// ── 语义蓝：只用于「有含义」的强调 ──

/** 「▸ 下一节」「● 本周上课」、设置页可编辑值。 */
val SemanticBlue = Color(0xFF35618C)

/** 「下一节」卡片的底。 */
val NextCourseFill = Color(0xFFCFDEEA)

/** 置灰态、已经上完的课。 */
val PastCourseFill = Color(0xFFE5E1D8)

// ── 课程色：8 对（浅底 + 深条）──
// 全部实测过：深墨正文压在每个 fill 上都有 11.4–12.5 的对比，远超 4.5；
// 每个 bar 相对基底也都有 2.4–3.4，作为色条看得清。

/** 一门课在界面上的两个色：卡片底 [fill] 与左侧色条 [bar]。 */
data class CourseTone(val fill: Color, val bar: Color)

val CourseTones = listOf(
    CourseTone(Color(0xFFCFDFEA), Color(0xFF6E93AE)),   // 哑光蓝
    CourseTone(Color(0xFFF7CFC5), Color(0xFFE8836F)),   // 珊瑚
    CourseTone(Color(0xFFCFE4E5), Color(0xFF6E9EA2)),   // 青
    CourseTone(Color(0xFFF2D4D8), Color(0xFFC07787)),   // 玫瑰
    CourseTone(Color(0xFFD8E0CF), Color(0xFF82956F)),   // 鼠尾草
    CourseTone(Color(0xFFEAE0C9), Color(0xFFB39A62)),   // 沙
    CourseTone(Color(0xFFDCD6E6), Color(0xFF8B7FA8)),   // 藕荷
    CourseTone(Color(0xFFE5E1D8), Color(0xFF9A9689)),   // 暖灰
)

/**
 * 同一门课在任何位置都是同一个色：按课程名散列取。
 * 用 floorMod 而不是 abs —— abs(Int.MIN_VALUE) 仍是负数，会越界。
 */
fun courseTone(name: String): CourseTone =
    CourseTones[Math.floorMod(name.hashCode(), CourseTones.size)]

// ── 状态 ──

/** 本周不上：整块浅暖灰，配深灰字而不是白字。 */
val InactiveCourseFill = Color(0xFFE5E1D8)
val InactiveCourseText = Color(0xFF6B6259)

/** 日期行里「今天」那一格。 */
val TodayBadge = CoralDeep
val OnTodayBadge = Color(0xFFFFFDF4)

// ── 表单状态（在暖底上重新校准）──

val MissingFieldBackground = Color(0xFFF6E3B8)
val MissingFieldBorder = Color(0xFFB98A22)

/** 校验不通过的红字。暖底上要偏砖红才不刺眼。 */
val ErrorRed = Color(0xFFA8412F)
