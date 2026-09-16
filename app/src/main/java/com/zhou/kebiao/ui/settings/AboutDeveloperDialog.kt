package com.zhou.kebiao.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zhou.kebiao.R
import com.zhou.kebiao.ui.theme.TextTitle
import com.zhou.kebiao.ui.theme.TextCaption
import com.zhou.kebiao.ui.theme.TextBodySmall
import com.zhou.kebiao.ui.theme.RadiusPanel
import com.zhou.kebiao.ui.theme.InkPrimary
import com.zhou.kebiao.ui.theme.InkTertiary

/** 收款码原图的宽高比，用来给图片留出正确的占位高度。 */
private const val QR_ASPECT = 1259f / 1714f

/**
 * 「关于开发者」弹窗：一句致谢 + 收款码。
 * 和这个 App 里其他弹窗一样是居中卡片，不跳页面。
 */
@Composable
fun AboutDeveloperDialog(onDismiss: () -> Unit) {
    AlertDialog(
        shape = RoundedCornerShape(RadiusPanel),
        onDismissRequest = onDismiss,
        title = { Text("关于开发者", style = TextTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "如果您觉得使用舒服，也可以赏作者一小杯咖啡！",
                    style = TextBodySmall,
                    color = InkPrimary,
                )
                Spacer(Modifier.height(12.dp))
                Image(
                    painter = painterResource(R.drawable.donate_qr),
                    contentDescription = "微信收款码",
                    contentScale = ContentScale.FillWidth,
                    // 显式按原图比例占位：只给宽度的话，Image 未必能自己推出高度
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(QR_ASPECT)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", style = TextCaption, color = InkTertiary) }
        },
    )
}
