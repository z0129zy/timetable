package com.zhou.kebiao.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zhou.kebiao.ui.theme.DividerLine
import com.zhou.kebiao.ui.theme.InactiveCourseFill
import com.zhou.kebiao.ui.theme.InkPrimary
import com.zhou.kebiao.ui.theme.InkSecondary
import com.zhou.kebiao.ui.theme.InkTertiary
import com.zhou.kebiao.ui.theme.NextCourseFill
import com.zhou.kebiao.ui.theme.RadiusChip
import com.zhou.kebiao.ui.theme.RecommendedHighlightColors
import com.zhou.kebiao.ui.theme.RadiusPanel
import com.zhou.kebiao.ui.theme.SemanticBlue
import com.zhou.kebiao.ui.theme.TextCaption
import com.zhou.kebiao.ui.theme.TextItem
import com.zhou.kebiao.ui.theme.TextTitle
import com.zhou.kebiao.ui.theme.colorToHsl
import com.zhou.kebiao.ui.theme.courseTone
import com.zhou.kebiao.ui.theme.hslToColor

/** 自由取色的深浅范围：再深文字对比就不稳了。 */
private const val FREE_MIN_LIGHTNESS = 0.74f
private const val FREE_MAX_LIGHTNESS = 0.90f

/**
 * 课程高亮色选择。
 *
 * 两个模式：
 *  - 推荐：DeepSeek 蓝 / Claude 橙 / 雾粉三色，点一下立即生效；
 *  - 自由取色：色相 0–360 + 深浅连续调，饱和度沿用当前色（保证卡片底压得住深色字），
 *    下方实时预览，点「使用这个颜色」才落盘。
 * 底部「自动」清掉自定义色，回到按课名散列。
 */
@Composable
fun ColorPickerDialog(
    current: Int?,
    previewName: String = "",
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentColor = current?.let { Color(it) }
    val initialHsl = remember(current) { currentColor?.let(::colorToHsl) }
    var freeMode by remember(current) {
        mutableStateOf(
            current != null && RecommendedHighlightColors.none { it.argb == current }
        )
    }
    var hue by remember(current) { mutableFloatStateOf(initialHsl?.first ?: 210f) }
    var lightness by remember(current) {
        mutableFloatStateOf(
            (initialHsl?.third ?: 0.86f).coerceIn(FREE_MIN_LIGHTNESS, FREE_MAX_LIGHTNESS)
        )
    }
    val saturation = remember(current) {
        (initialHsl?.second ?: 0.45f).coerceIn(0.30f, 0.55f)
    }
    val freeColor = hslToColor(hue, saturation, lightness)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(RadiusPanel),
        title = { Text("自定义高亮颜色", style = TextTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("推荐", selected = !freeMode) { freeMode = false }
                    ModeChip("自由取色", selected = freeMode) { freeMode = true }
                }
                Spacer(Modifier.height(12.dp))

                if (!freeMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RecommendedHighlightColors.forEach { recommended ->
                            val selected = current == recommended.argb
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(RadiusChip))
                                    .clickable { onPick(recommended.argb) }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color(recommended.argb))
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) {
                                                courseTone("", recommended.argb).onFill
                                            } else {
                                                DividerLine
                                            },
                                            shape = CircleShape,
                                        )
                                        .semantics {
                                            contentDescription = "高亮颜色 ${recommended.name}"
                                        }
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = recommended.name,
                                    style = TextCaption,
                                    color = if (selected) SemanticBlue else InkSecondary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                } else {
                    Text("色相", style = TextCaption, color = InkTertiary)
                    GradientSlider(
                        value = hue,
                        valueRange = 0f..360f,
                        gradient = Brush.horizontalGradient(
                            (0..12).map { i -> hslToColor(i * 30f, saturation, lightness) }
                        ),
                        label = "色相滑块",
                        onValueChange = { hue = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("深浅", style = TextCaption, color = InkTertiary)
                    GradientSlider(
                        value = lightness,
                        valueRange = FREE_MIN_LIGHTNESS..FREE_MAX_LIGHTNESS,
                        gradient = Brush.horizontalGradient(
                            listOf(
                                hslToColor(hue, saturation, FREE_MIN_LIGHTNESS),
                                hslToColor(hue, saturation, FREE_MAX_LIGHTNESS),
                            )
                        ),
                        label = "深浅滑块",
                        onValueChange = { lightness = it },
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(RadiusChip))
                            .background(freeColor)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = previewName.ifBlank { "课程名" },
                            style = TextItem,
                            color = InkPrimary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onPick(freeColor.toArgb()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("使用这个颜色", style = TextItem)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(RadiusChip))
                        .clickable { onPick(null) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(InactiveCourseFill)
                            .border(1.dp, DividerLine, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "自动（按课名）",
                        style = TextItem,
                        color = if (current == null) SemanticBlue else InkSecondary,
                    )
                    if (current == null) {
                        Spacer(Modifier.width(4.dp))
                        Text("✓", style = TextItem, color = SemanticBlue)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = TextItem, color = InkTertiary)
            }
        },
    )
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(RadiusChip)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) NextCourseFill else Color.Transparent)
            .border(1.dp, if (selected) SemanticBlue else DividerLine, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextItem,
            color = if (selected) SemanticBlue else InkSecondary,
        )
    }
}

@Composable
private fun GradientSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    gradient: Brush,
    label: String,
    onValueChange: (Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = InkPrimary,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
        )
    }
}
