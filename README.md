# 课表

把学校教务系统导出的课表 PDF，变成手机上随时能查的课表 App。

导入一次，之后完全离线：今天上什么、下一节在哪间教室、这周是单周还是双周。

## 功能

- **导入** — 直接选教务系统导出的课表 PDF，自动解析课程名、周次、节次、教室、教师
- **今天** — 当前课程与下一节课，带起止时间和剩余时长；已经上完的置灰
- **课表** — 整学期网格，逐周切换。单双周正确区分，本周生效的课高亮、其余置灰
- **编辑** — 点任意格子新增、修改、删除课程；每节课的作息时间也能改
- **其他课程** — 教务系统 PDF 底部那类「没有固定上课时间」的课程单独展示

## 隐私

- **零权限**：`AndroidManifest.xml` 里没有声明任何权限 —— 不联网，也不读外部存储
- 选 PDF 走系统自带的文件选择器（SAF），App 只能拿到你主动选中的那一个文件
- 课表存在应用私有目录，不导出、不上传、不统计

## 构建

需要 Android Studio，或 JDK 17+ 与 Android SDK 36。

```bash
./gradlew assembleDebug     # 构建 debug 包
./gradlew installDebug      # 装到已连接的设备 / 模拟器
```

Release 签名读根目录的 `keystore.properties`（该文件与 `/keystore/` 均已 gitignore）。
没有它时 debug 构建照常可用。

## 解析器

这是项目里最需要小心的部分 —— 教务系统生成的 PDF 有不少反直觉之处，解析逻辑集中在
`parser/TimetableParser.kt`，且刻意与 PDFBox 解耦（只有 `PdfTextExtractor` 依赖 Android 运行时），
因此大部分解析逻辑能跑纯 JVM 单元测试。

处理的情形包括：

- **文字方向**：样本 PDF 的文字方向是 90°、页面 `/Rotate` 也是 90°，二者抵消，
  所以 PDFBox 给出的 `xDirAdj` / `yDirAdj` 已经是视觉坐标，不能再转一次
- **折行**：长课程名会被按列宽切成多行，要合并成一门课而不是多门
- **跨页**：单元格内容太长的课程会溢出到下一页顶部同一列，要拼回来
- **误判防护**：PDF 标题与页眉的 x 恰好落在星期列上，不裁剪就会被当成课程名

## 测试

```bash
./gradlew test                      # 解析与数据的 JVM 单元测试
./gradlew connectedAndroidTest      # 端到端：真实 PDF 走一遍 PDFBox
```

仓库里的 `app/src/androidTest/assets/sample-timetable.pdf` 是一份**脱敏后**的样本 ——
姓名、学号、教师名、专业与校区都已替换成虚构值，页面布局、坐标、字体与折行位置与原文件一致，
所以它仍然能覆盖上面那些边界情况。

## 设计文档

`docs/` 下有完整的设计文档与开发记录，包括数据模型为什么这样拆、解析算法怎么定、
配色为什么这么选（色值都逐一算过对比度）。

## 技术栈

Kotlin · Jetpack Compose · Material 3 · PDFBox-Android · kotlinx.serialization

## License

MIT — 见 [LICENSE](LICENSE)。Copyright (c) 2026 [@z0129zy](https://github.com/z0129zy)
