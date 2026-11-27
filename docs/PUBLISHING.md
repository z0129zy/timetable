# GitHub 发布清单（v2.1）

按顺序做完，再点 Publish。仓库：`https://github.com/z0129zy/timetable`，分支 `main`。
当前 Latest 是 v2.0（2026-09-16），本版 v2.1 发布后成为新的 Latest，v2.0 保留历史。
本机没有 `gh` CLI，用 GitHub 网页创建 Release。

## 1. 发布前检查

- [ ] 版本号：`app/build.gradle.kts` 为 `versionCode 4` / `versionName "2.1"`
- [ ] 测试与构建：`./gradlew test assembleRelease`
- [ ] 包已重新生成：`app/build/outputs/apk/release/app-release.apk`
      （上传到 Release 时可改名为 `app-release-2.1.apk`，与 v2.0 的命名保持一致）
- [ ] SHA-256 已记下（填进 Release Notes）：
      `sha256sum app/build/outputs/apk/release/app-release.apk`
- [ ] README、`docs/RELEASE_NOTES_v2.1.md` 已更新
- [ ] `keystore.properties`、`keystore/`、`local.properties` 仍未进版本库：
      `git ls-files | grep -E "keystore|local.properties|\.apk$"` 应该为空

### 姓名/隐私检查

- [x] App 设置里的「关于开发者」入口与个人收款码已删除
- [x] HANDOFF 里的真实姓名/账号名已改成中性描述
- [x] README / Release Notes 不含真实姓名、学号、好友账号
- [ ] 测试样本里的姓名/学号是虚构脱敏值（解析测试需要），不要换成真实信息
- [ ] 如果要加截图：不要出现真实课表、教师名、学号、好友信息
- [ ] 仓库与历史里的设计文档/设计稿如有真实信息，发布前自行确认

### 仍可能带名字的两处（需要你决定）

1. **包名** `com.zhou.kebiao` 含姓氏。改包名等于换一个新 App，旧数据、签名升级都不兼容，
   所以本轮没有改。要改需另做数据迁移，并重新发第一个版本。
2. **Git 提交作者**。如果本机 Git 配置里是真实姓名/邮箱，公开仓库会显示在提交记录里。
   新提交可以改成 GitHub 的 noreply 地址；旧提交需要用 filter-repo / 新建仓库重写历史：
   ```bash
   git config user.name  "z0129zy"
   git config user.email "z0129zy@users.noreply.github.com"
   git log --format='%an <%ae>' | sort -u   # 先确认现状
   ```

## 2. 提交、打 tag、推送

```bash
cd "/c/Users/18711/AndroidStudioProjects/Timetable"
git status
git add README.md docs/ app/ .gitignore
git commit -m "release: v2.1 在线导入 / 自定义高亮色 / 12 分钟上课提醒"
git tag -a v2.1 -m "v2.1"
git push origin main --tags
```

如果只想发已跟踪文件的修改，也可以按 `git status` 逐项 `git add`。

## 3. 创建 GitHub Release

网页方式：

1. 打开 `https://github.com/z0129zy/timetable/releases/new`
2. Tag 选 `v2.1`（或输入后新建）
3. 标题：`v2.1 — 在线导入 · 自定义高亮色 · 上课提醒`
4. 说明：粘贴 `docs/RELEASE_NOTES_v2.1.md` 的内容
5. 上传 `app/build/outputs/apk/release/app-release.apk`（可改名为 `app-release-2.1.apk`）
6. 勾选 `Set as the latest release`，Publish（v2.0 会自动变成 Previous）

## 4. 发布后验证

- [ ] 从 Release 下载 APK，覆盖安装到旧版本设备
- [ ] 确认课表数据保留
- [ ] 确认 Android 13+ 通知权限请求正常
- [ ] 确认在线导入能登录、打开课表并识别导入
- [ ] 计算下载到的 APK 的 SHA-256，与 Release Notes 一致
- [ ] 检查 Release 页面无个人信息（截图/说明/SHA 都对）
