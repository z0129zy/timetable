# 交接速览（2026-11-28）

新会话接手本项目时先读这一页，细节看 `docs/superpowers/specs/2026-09-15-timetable-app-design.md`。

## 当前状态

- 功能完整的课表 App，约 58 个 Kotlin 文件
- 2026-11-27 新增：课表行高设置（默认 60dp）、上课前 12 分钟系统通知、修复「暖灰课程色与非本周灰同色」的不高亮 bug
- 2026-11-27 新增：联网导入——直接进学校统一身份认证登录页，**登录在网页里完成**；登录后用「课表查询页 / 识别导入」两个按钮，读正方课表 JSON，不下载 PDF。**App 不弹自己的登录框、不保存账号密码**
- 2026-11-27 修正多账号：正方要走统一认证的 **CAS 入口** `http://59.74.174.150/jwglxt/auth/auth_casAuthLogin.html`（门户「教务系统 → 打开」用的就是它）——它用统一认证会话自动登录正方；直接打开课表页不会带登录，会落到正方登录页（之前串到旧账号就是这个原因）。「课表查询页」现在走 CAS，登录后若落在主菜单会自动再打开课表页；顶部「切换账号」清全部 Cookie 并回统一认证登录页；导入页为**全屏 Dialog**
- 2026-11-27 **真实账号实测通过**：模拟器登录的账号走 CAS → 自动打开课表（测试账号）→ 识别导入 **18 门课**；`xskbcx_cxXsgrkb.html` 的 JSON 字段验证通过，无需二次登录
- 2026-11-28 新增：每门课可自定义高亮色——点课程 → 候选弹窗里小字「自定义高亮颜色」→ 推荐三色（DeepSeek 蓝 `#4D6BFE` / Claude 橙 `#D97757` / 雾粉 `#F0D5E0`，深色底自动白字）+ 自由取色（色相/深浅/实时预览）+「自动」；新建/编辑表单也有入口；`Course.customColor` 持久化到 JSON
- 2026-11-28 上课提醒改提前 **12 分钟**（设置页、通知渠道、单测同步）；真实闹钟实测：测试课 16:32 开始，通知 16:20 弹到通知栏（「课表 / 提醒测试课 / 16:32–17:17 · 测试楼101」，HIGH 渠道有 heads-up）。去掉启动页（SplashScreen/TextTagline 删除），冷启动直接进主界面；导入页步骤提示改为按钮上方的浅蓝提示条，并注明「学校服务器较慢，课表页加载可能要几秒，请耐心等待」
- 2026-11-28 导入流程性能：CAS 后不再等正方主菜单整页加载，URL 一到主菜单就 `onPageStarted` 跳课表；加载期间顶部进度条 + 禁用按钮。实测点击到正方页面约 6 秒（学校 CAS/正方服务器与网络慢），测试账号还触发正方 `?kickout=1`（单端登录冲突），正常单设备使用不会
- `./gradlew assembleDebug`、`./gradlew test`、`./gradlew assembleRelease` 均通过
- 版本 versionCode 4 / versionName 2.1，包名 `com.zhou.kebiao`，显示名「课表」
- 待办：用户安装最新包回归测试换账号；接口已用真实账号验证

## 构建与验证（Git Bash）

```bash
cd "/c/Users/18711/AndroidStudioProjects/Timetable"
./gradlew assembleDebug
./gradlew test
"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd Medium_Phone_API_36.1 &
./gradlew installDebug
```

环境要点：JDK 21（`JAVA_HOME=D:\java`）、SDK 仅 platform 36.1、无 cmdline-tools、
`ANDROID_HOME` 未设（本工程 `local.properties` 已写好 sdk.dir）。

## 模块地图

| 路径 | 职责 |
|---|---|
| `data/Models.kt` | Course（5 组字段 + missingFields + customColor）、OtherCourse、PeriodTime、isActiveIn、weekOf、默认作息 |
| `data/TimetableEdits.kt` | 纯函数编辑、validateCourse、normalized()（启动防崩） |
| `data/TimetableStore.kt` | 整份 JSON 读写 `filesDir/timetable.json` |
| `data/ClassAlarms.kt` | 纯函数：按课程×周次算出整学期提醒时刻（12 分钟前） |
| `parser/TimetableParser.kt` | 几何归列 → 跨页拼接 → 字号切课名 → 正则解字段 |
| `parser/PdfTextExtractor.kt` | 唯一依赖 PDFBox 的文件（JVM 单测因此可覆盖解析逻辑） |
| `parser/OtherCourseParser.kt` | 第 3 页底部「其他课程」区块 |
| `notify/ClassReminder.kt` | 通知渠道（HIGH，heads-up）、AlarmManager 精确排期、取消、通知构建 |
| `notify/ReminderReceiver.kt`、`notify/BootReceiver.kt` | 到点弹通知；开机按落盘课表重排 |
| `web/KbJsonParser.kt` | 正方课表 JSON → Course / OtherCourse（纯函数，字段映射都在这里） |
| `ui/web/WebImportScreen.kt` | 全屏 Dialog 内的 WebView 导入：统一认证登录页、CAS 自动登录正方（auth_casAuthLogin）、主菜单 onPageStarted 预跳课表、加载进度条/禁用按钮、门户新窗口拦截、切换账号、步骤提示条、JS 桥、EXTRACT_JS |
| `ui/week/ColorPickerDialog.kt` | 推荐三色 + 自由取色（色相/深浅/实时预览）+「自动」；候选弹窗与课程表单共用 |
| `ui/theme/Colors.kt` | 7 个按课名自动色 + 推荐三色（官方品牌色）+ `onFill` 自动黑/白字；`courseTone(course)` 自定义优先 |
| `ui/theme/ColorMath.kt` | HSL ↔ RGB 纯函数（自由取色） |
| `res/xml/network_security_config.xml` | 只对 *.xust.edu.cn 与 59.74.174.150 放行明文 http |
| `ui/AppState.kt` | 全局唯一 Timetable；先落盘再改内存；导入失败不碰现有数据；行高/提醒开关状态；`importParsed` 供网页导入 |
| `ui/UiPrefs.kt` | 界面偏好（行高、提醒开关），SharedPreferences，与 timetable.json 隔离 |
| `ui/AppRoot.kt` | 三 Tab + 导入启动器 + 通知权限请求 + 提醒重排 + 遮罩（无启动页） |
| `ui/week/` | 网格、候选课/表单/作息/其他课程四种弹窗、Slots 归并 |
| `ui/today/`、`ui/settings/` | 今天时间轴、设置（学期三项 + 课表行高 + 提醒开关） |

## 不能破坏的不变量

1. 课程只存 5 组字段 + missingFields + customColor（自定义高亮色是界面偏好，不是 PDF/教务字段）；
   编辑用 courses 下标定位，无 id。删课会令下标前移（弹窗已处理）
2. 导入：直接覆盖不确认；但解析失败或 0 门课必须拒绝写入；不动开学日期/总周数/作息时间。
   重新导入会重建课程，customColor 回到自动（颜色不随导入保留）
3. 网格按 (星期, 起始节) 归并成 Slot；representative 只决定卡片显示哪门
4. 权限：POST_NOTIFICATIONS / RECEIVE_BOOT_COMPLETED / SCHEDULE_EXACT_ALARM /
   USE_EXACT_ALARM / INTERNET；课表数据仍只存本机 JSON；**App 不收集、不保存账号密码**，
   登录态由学校网站的 Cookie 维持；minSdk 26 / compileSdk 36.1 / AGP 9.1 / Kotlin 2.2.10
5. **课程色板里任何 fill 都不能等于 `InactiveCourseFill`**（ColorsTest 守着）——
   撞色时「本周有课」会和「非本周」灰卡长得一模一样
6. 提醒默认开启、提前 **12 分钟**；首次启动请求通知权限，拒绝则把开关关掉；
   通知走 HIGH 渠道（会 heads-up，显示在通知栏）；整学期闹钟一次性排完，
   课表/作息/开关任一变化都取消旧排期并按新数据重排；重启由 BootReceiver 重排
7. 网页导入与 PDF 导入共用 `Timetable.afterImport`：0 门课拒绝写入、不覆盖学期/作息；
   识别失败绝不写盘。网络只白名单学校域名/IP，WebView 调试只在 debug 构建开启
8. 联网导入的入口是统一认证登录页（`/auth/oauth/authorize?...client_id=...`，state 每次随机）；
   页面默认企业微信扫码，注入脚本自动点「账号密码」，**用户在学校网页上输入**（表单 `#username/#password_`），
   App 不接管密码、不做自动填充。**正方必须走 CAS 入口**
   `http://59.74.174.150/jwglxt/auth/auth_casAuthLogin.html`（门户「教务系统 → 打开」），
   它会用统一认证会话自动登录正方；**不要直接打开课表页**（不带登录，沿用旧正方会话，会串到上一个人）。
   CAS 后若落在主菜单，自动再打开课表页。课表 JSON 接口 `xskbcx_cxXsgrkb.html`，
   字段 `kbList` + `kcmc/xm/cdmc/xqj/jcs/zcd` 已用真实账号验证（18 门课）
9. 正方（59.74.174.150）与统一认证是两套独立会话；CAS 入口负责用统一认证登录正方。
   导入页顶部「切换账号」清全部 Cookie 并回统一认证登录页（换人时用）。
   课表页 URL 对所有人通用（身份在会话里），不存在个人专属网址
10. `WebImportScreen` 必须用全屏 Dialog 承载：普通叠层会让按钮点击穿透到下层课表页；
    底部两个按钮上方是浅蓝步骤提示条（文案固定见 `BottomBar`）
11. 自定义高亮色的文字色由 `CourseTone.onFill` 按对比度自动选黑/白，不能写死 InkPrimary
12. 所有有实质逻辑的改动必须过 `./gradlew test`；UI 改动要装模拟器实测

## 测试基线

- JVM 单测：数据/编辑/校验/导入/存储/解析/提醒排期/课程配色/正方课表 JSON 全有覆盖（解析用夹具文字，不依赖 PDFBox）
- androidTest：真实 PDF 走 PDFBox、表单弹窗交互
- 联网导入链路验证：在 debug 模拟器上用 WebView DevTools 注入 `{"kbList":[...]}` 调 `KebiaoBridge.onKbJson`，可验证桥→解析→写入→关页
- 登录页验证（2026-11-27）：清掉 WebView 的 SSO Cookie 后加载 START_URL，最终落在
  `/auth/oauth/login?...&login_type=normal`，`#username/#password_/#okta-signin-submit` 均存在；
  门户首页的「打开」是 window.open，已注入脚本改成当前页跳转
- 切换账号验证（2026-11-27）：点顶部「切换账号 → 退出并切换」后，WebView 回到统一认证登录页
  （账号密码表单），旧的正方会话被清掉；全屏 Dialog 下按钮点击不再穿透
- 真实导入验证（2026-11-27）：模拟器已登录账号 → 「课表查询页」走 CAS → 自动打开课表页
  （显示当前登录账号，不是之前的旧账号）→ 「识别导入」写入 18 门课；页面自身也调用
  `xskbcx_cxXsgrkb.html`，与 `EXTRACT_JS`/`KbJsonParser` 一致
- 自定义颜色验证（2026-11-28）：推荐页只显示 DeepSeek 蓝 `#4D6BFE` / Claude 橙 `#D97757` / 雾粉；
  选 DeepSeek 蓝后卡片 `#4D6BFE`、文字自动白；自由取色紫色卡片 `#DCCBEB`；
  ColorsTest（官方品牌色/三推荐色互异/不撞灰/onFill）、CourseColorTest（JSON 往返/旧数据默认 null）、
  ColorMathTest（HSL 往返）通过
- 提醒实测（2026-11-28）：插入 16:32 开始的测试课，16:20 收到通知（提前 12 分钟），
  通知栏显示「课表 / 提醒测试课 / 16:32–17:17 · 测试楼101」，HIGH 渠道；单测时间已改 12 分钟
- 启动/提示验证（2026-11-28）：冷启动无 splash（tagline 不再出现）；导入页底部提示条
  在按钮上方，文案「首先输入账号密码登录，然后可以点击课表查询页，等课表显示出来，再点识别导入」，
  下面一行「学校服务器较慢，课表页加载可能要几秒，请耐心等待」
- 导入流程计时（2026-11-28）：点「课表查询页」→ 0.8s 到 CAS 页、约 6s 到正方页面；
  学校服务器/网络是主要耗时，正方网页本身很重。测试账号遇到 `login_slogin.html?kickout=1`
  （正方单端登录冲突），优化后主菜单整页不再白等；正常单设备使用不会 kickout
