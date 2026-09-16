package com.zhou.kebiao.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ── 两套容器密度 ────────────────────────────────────────────────────────
// 这是本轮最重要的结构决定。选定的概念图是「3 张大卡的列表」，大圆角加柔和
// 暖投影很有味道；但课表页是 11 行 × 7 列、字号只有 8sp 的密网格 —— 同样的
// 圆角与投影会吃掉本就不够的列宽，还会糊成一团。所以分成两套：
//
//   列表类（今天页、设置页、各弹窗）→ 大圆角 + 暖投影
//   网格（课表页）                  → 小圆角 + 无投影，靠底色区分
//
// 配色、字体、字重仍然全站统一，只是容器的「松紧」不同。

/** 网格内的小容器：课表卡片、输入框、小色块。 */
val RadiusChip = 4.dp

/** 列表类容器：今天页的课程卡、设置页分组。 */
val RadiusCard = 20.dp

/** 弹窗与面板。 */
val RadiusPanel = 24.dp

// 投影用暖褐而不是黑：黑投影压在暖奶油底上会发脏，暖褐才融得进去。
// 实测概念图里卡片正下方的投影色是 #E6E2DC（暖灰褐），不是中性灰。
val ShadowSoft = Color(0x1A5A4A38)
val ShadowStrong = Color(0x2E5A4A38)

/** 列表卡片的投影高度。 */
val ElevationCard = 2.dp

/** 弹窗的投影高度。 */
val ElevationPanel = 8.dp
