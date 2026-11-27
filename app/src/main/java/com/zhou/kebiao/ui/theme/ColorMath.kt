package com.zhou.kebiao.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * 彩色取色用的 HSL 转换。放成纯函数，单测可以覆盖；
 * 自由取色只调色相与深浅，饱和度沿用当前色，避免选出来的卡片底压不住深色文字。
 */
internal fun hslToColor(hue: Float, saturation: Float, lightness: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)

    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r1 + m, g1 + m, b1 + m, 1f)
}

internal fun colorToHsl(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, l)

    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f)
        g -> ((b - r) / d + 2f)
        else -> ((r - g) / d + 4f)
    } * 60f
    return Triple(h, s, l)
}
