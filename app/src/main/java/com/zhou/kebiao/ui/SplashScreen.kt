package com.zhou.kebiao.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zhou.kebiao.R
import com.zhou.kebiao.ui.theme.TextTagline
import com.zhou.kebiao.ui.theme.Canvas
import com.zhou.kebiao.ui.theme.InkSecondary
import kotlinx.coroutines.delay

/** 启动页停留多久再开始淡出。 */
private const val HOLD_MS = 1_400L

/**
 * 启动页：白底、居中的圆角图标，底下一行小字。
 *
 * 它由 [AppRoot] 叠在正常界面之上，到点自己淡出 —— 底下那层一开始就已经组合好了，
 * 所以淡出之后不需要再等一次首帧，看不到跳变。
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(HOLD_MS)
        onFinished()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Canvas),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 上下权重不同，让图标略高于正中 —— 正中间会显得发闷
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier.size(104.dp).clip(RoundedCornerShape(24.dp)),
        )
        Spacer(Modifier.height(30.dp))
        Text(
            text = "愿你拥有美好而充实的一天～",
            style = TextTagline,
            color = InkSecondary,
        )
        Spacer(Modifier.weight(1.2f))
    }
}
