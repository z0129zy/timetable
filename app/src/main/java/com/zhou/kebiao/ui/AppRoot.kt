package com.zhou.kebiao.ui
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.DateRange

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.zhou.kebiao.data.TimetableStore
import com.zhou.kebiao.notify.ClassReminder
import com.zhou.kebiao.ui.settings.SettingsScreen
import com.zhou.kebiao.ui.theme.TextCaption
import com.zhou.kebiao.ui.theme.CoralDeep
import com.zhou.kebiao.ui.theme.RadiusPanel
import com.zhou.kebiao.ui.theme.Canvas
import com.zhou.kebiao.ui.theme.SemanticBlue
import com.zhou.kebiao.ui.theme.InkPrimary
import com.zhou.kebiao.ui.theme.InkTertiary
import com.zhou.kebiao.ui.theme.NavBarSurface
import com.zhou.kebiao.ui.today.TodayScreen
import com.zhou.kebiao.ui.web.WebImportScreen
import com.zhou.kebiao.ui.week.WeekScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAB_TODAY = 0
private const val TAB_WEEK = 1
private const val TAB_SETTINGS = 2

/**
 * 应用根：底部三 Tab + 导入的启动器。
 *
 * 选文件的启动器与协程作用域放在这一层而不是课表页，是因为导入要跑几秒，
 * 而 rememberCoroutineScope() 会跟着页面离开组合而被取消 —— 用户点了导入
 * 再切到「今天」页，导入就会被从中掐断。
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val state = remember {
        val app = context.applicationContext
        AppState(app, TimetableStore(File(app.filesDir, "timetable.json")))
    }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(TAB_TODAY) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch { state.importPdf(uri) }
    }

    var showImportChooser by rememberSaveable { mutableStateOf(false) }
    var showWebImport by rememberSaveable { mutableStateOf(false) }

    // 提醒默认是开的：首次启动请求一次通知权限；拒绝就把开关关掉，
    // 别让「设置里显示开着、实际收不到」这种事发生。
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) state.setClassReminder(false)
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            state.classReminderEnabled &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 课表、开关任一变化就重排整学期的提醒；关掉则全部取消。
    // 排几百个闹钟是 IPC 操作，放 IO 线程，别卡首帧。
    LaunchedEffect(state.timetable, state.classReminderEnabled) {
        withContext(Dispatchers.IO) {
            if (state.classReminderEnabled) {
                ClassReminder.reschedule(context.applicationContext, state.timetable)
            } else {
                ClassReminder.cancelAll(context.applicationContext)
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = NavBarSurface) {
                // 用矢量图标而不是 emoji：emoji 无法着色，也就用不上珊瑚选中态，
                // 而且读屏只能念出「🏠」这种字符。图标带 contentDescription 才可读。
                NavigationBarItem(
                    selected = tab == TAB_TODAY,
                    onClick = { tab = TAB_TODAY },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "今天") },
                    label = { Text("今天", style = TextCaption) },
                    colors = navItemColors(),
                )
                NavigationBarItem(
                    selected = tab == TAB_WEEK,
                    onClick = { tab = TAB_WEEK },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = "课表") },
                    label = { Text("课表", style = TextCaption) },
                    colors = navItemColors(),
                )
                NavigationBarItem(
                    selected = tab == TAB_SETTINGS,
                    onClick = { tab = TAB_SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "设置") },
                    label = { Text("设置", style = TextCaption) },
                    colors = navItemColors(),
                )
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (tab) {
                TAB_TODAY -> TodayScreen(state)
                TAB_WEEK -> WeekScreen(
                    state = state,
                    onRequestImport = { showImportChooser = true },
                )
                else -> SettingsScreen(state)
            }

            // 转圈期间必须真把点击吃掉。只铺一层半透明背景是不够的 ——
            // 没有 clickable 的 Box 不消费指针事件，下面的「＋」
            // 照样能点到，会重复触发导入。
            if (state.importing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Canvas.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = SemanticBlue)
                }
            }
        }
    }

    if (showImportChooser) {
        AlertDialog(
            shape = RoundedCornerShape(RadiusPanel),
            onDismissRequest = { showImportChooser = false },
            title = { Text("导入课表") },
            text = {
                Column {
                    Text("从教务系统导入：登录后直接读取课表，不用下载 PDF。")
                    Spacer(Modifier.height(6.dp))
                    Text("选择本地 PDF：导入已经下载好的课表文件。")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportChooser = false
                    showWebImport = true
                }) {
                    Text("从教务系统导入")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportChooser = false
                    picker.launch(arrayOf("application/pdf"))
                }) {
                    Text("选择本地 PDF")
                }
            },
        )
    }

    if (showWebImport) {
        // 用全屏 Dialog 而不是普通叠层：独立窗口能保证 WebView 上的按钮不会被下层页面抢走触摸
        Dialog(
            onDismissRequest = { showWebImport = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            WebImportScreen(state = state, onClose = { showWebImport = false })
        }
    }

    state.importError?.let { reason ->
        AlertDialog(
            shape = RoundedCornerShape(RadiusPanel),
            onDismissRequest = { state.dismissImportError() },
            title = { Text("没能导入") },
            text = { Text(reason) },
            confirmButton = {
                TextButton(onClick = { state.dismissImportError() }) { Text("知道了") }
            },
        )
    }

}

/**
 * 选中态：图标用珊瑚深色（珊瑚本体在暖底上只有 2.4，做图标偏弱），
 * 文字用主墨色（珊瑚做文字到不了 AA，选中的字必须读得清）。
 * 不要选中胶囊——这个方向的选中靠色与重，不靠一块底。
 */
@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = CoralDeep,
    selectedTextColor = InkPrimary,
    indicatorColor = Color.Transparent,
    unselectedIconColor = InkTertiary,
    unselectedTextColor = InkTertiary,
)
