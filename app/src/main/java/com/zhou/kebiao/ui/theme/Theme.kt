package com.zhou.kebiao.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 只做浅色：这一版的方向本身就是「暖奶油纸感」，深色不是换套配色能解决的，
// 会变成另一套设计。也不启用 Material You 动态取色 —— 那会把强调色改成
// 系统壁纸色，跟选定的方向不符。
//
// 除了自己画的组件，Material 的对话框、导航栏也会来查这套 scheme，
// 所以 surface / surfaceContainerHigh 必须跟着换成暖色，否则弹窗会是冷的白。

private val WarmLightColors = lightColorScheme(
    primary = SemanticBlue,
    onPrimary = Color.White,
    secondary = Coral,
    onSecondary = Color.White,
    tertiary = CoralDeep,
    onTertiary = OnTodayBadge,
    background = Canvas,
    onBackground = InkPrimary,
    surface = Surface,
    onSurface = InkPrimary,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = InkSecondary,
    // AlertDialog 取的是 surfaceContainerHigh，不设就会是默认的冷白
    surfaceContainerHigh = Surface,
    surfaceContainer = Surface,
    outline = DividerLine,
    outlineVariant = RowLine,
    error = ErrorRed,
    onError = Color.White,
    scrim = Color(0x66241F1A),
)

@Composable
fun 课表Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WarmLightColors,
        typography = Typography,
        content = content,
    )
}
