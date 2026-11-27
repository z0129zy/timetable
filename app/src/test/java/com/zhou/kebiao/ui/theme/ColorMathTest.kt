package com.zhou.kebiao.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorMathTest {

    @Test
    fun `HSL 往返大致保持色相`() {
        for (hue in listOf(0f, 60f, 120f, 180f, 240f, 300f, 359f)) {
            val back = colorToHsl(hslToColor(hue, 0.45f, 0.86f))
            assertEquals("色相 $hue", hue, back.first, 1.5f)
            assertEquals(0.45f, back.second, 0.03f)
            assertEquals(0.86f, back.third, 0.01f)
        }
    }

    @Test
    fun `纯色红绿蓝`() {
        assertEquals(Color(1f, 0f, 0f), hslToColor(0f, 1f, 0.5f))
        assertEquals(Color(0f, 1f, 0f), hslToColor(120f, 1f, 0.5f))
        assertEquals(Color(0f, 0f, 1f), hslToColor(240f, 1f, 0.5f))
    }

    @Test
    fun `超出 360 度的色相会绕回来`() {
        assertEquals(Color(1f, 0f, 0f), hslToColor(360f, 1f, 0.5f))
        assertEquals(Color(1f, 0f, 0f), hslToColor(720f, 1f, 0.5f))
    }
}
