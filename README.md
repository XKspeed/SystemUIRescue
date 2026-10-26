<div align="center">

# SystemUI救援

一个 **SystemUI 界面替换与自动救砖** 的原生控制端，UI 基于 [Miuix](https://github.com/compose-miuix-ui/miuix)（小米 HyperOS 风格 Compose 组件库）。

![Android](https://img.shields.io/badge/Android-15%2B%20(API%2035)-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Miuix-4285F4)
![Root](https://img.shields.io/badge/Root-Required-D32F2F)
![License](https://img.shields.io/badge/License-GPL--3.0-blue)

<br>

围绕「**备好救砖界面 → 换入待测界面 → 崩了自动换回 → 继续正常使用**」这条主链路设计：放胆把会崩的界面换进去试，出事自动换回可用的那一版，不再怕 SystemUI 黑屏卡死。

</div>

---

### 📌 基本信息

| 项目 | 说明 |
| :--- | :--- |
| **当前版本** | `1.0.0`（`versionCode 1`） |
| **最低系统** | **Android 15（API 35）** 及以上 |
| **运行环境** | 需 **Root**，与同名的 Magisk / APatch / KernelSU 模块配套使用 |

---

## ✨ 功能

### 救援流程
- **救砖界面**：选一个确认可用的 SystemUI 界面留作后路，出事时换回它
- **待测界面**：选要实测的那一版界面，换进去观察
- **开始 / 停止**：一键接管与收手，随时可以喊停

### 自动恢复
- **崩溃阈值**：累计多少条 fatal 日志后自动换回救砖界面（可调）
- **卡死检测**：日志疯长、堆栈溢出同样判定为卡死 —— 不必等崩溃次数攒够
  - 这类崩溃单次就可能刷出几万行堆栈，但频率低，永远达不到阈值，只能靠它兜住
- **等待超时**：替换界面后等 SystemUI 重启的秒数（可调）
- **音量键救砖**：音量+ / 音量- 直接触发换回，界面已经点不动时也能救
- **屏蔽系统救援**：把 AMS 的崩溃次数限制拉高，避免系统抢在前面杀掉 SystemUI

### 音量键校验
- 按内核能力位识别音量键设备（KEY_VOLUMEUP / KEY_VOLUMEDOWN），逐个实测按键是否真的产生事件
  - 声明了上下键 ≠ 真能按出事件，必须实测
- 识别结果写入配置供脚本优先读取，不再猜 event 编号

### 日志
- **查看日志**：模块运行日志
- **崩溃日志**：独立的崩溃记录页
- **调试日志**：可开关，在 `config/debug.log` 里记录每一步动作

### 界面
- Miuix 界面，支持深色模式，顶栏是可收缩的大标题
- **主题模式**：跟随系统 / 浅色 / 深色，可选莫奈取色（取色种子 · 调色风格 · 色彩规范）
- **模糊强度**：玻璃控件的模糊程度可调，拖动即时生效
- **预测性返回**：返回手势拖动时预览上一页；关闭后返回是瞬时的
- **底栏模糊**：关闭后退化为磨砂胶囊，不做折射

### 关于
- 应用版本（含 `versionCode`）、模块版本、最低系统
- **第三方许可证**：开源库清单与许可证，独立二级页

---

## 🛠️ 技术栈

| 用途 | 技术 |
| :--- | :--- |
| 语言 | Kotlin 2.4.20 |
| 构建 | AGP 9.4.0 |
| UI | Jetpack Compose + Miuix 0.9.4（HyperOS 风格） |
| 导航 | Miuix Nav（NavDisplay + 预测性返回） |
| 玻璃 / 模糊 | Miuix Blur（AGSL 渐进模糊）+ kyant Backdrop 1.0.5 |
| Root 命令 | libsu 6.0.0（常驻 shell，避免每条命令 fork 一次 `su`） |
| 序列化 | KotlinX Serialization 1.7.3 |

---

## 🚀 构建

### Android Studio

1. 用 Android Studio 打开项目根目录
2. 等待 Gradle 同步完成（首次需下载依赖）
3. Run ▶ 安装到设备（Android 15+ 且已 Root）

### 命令行

> 需要 JDK 21 与 Android SDK

```bash
export JAVA_HOME=<jdk21 路径>
export ANDROID_HOME=<sdk 路径>

./gradlew :app:assembleDebug      # 产物 app/build/outputs/apk/debug/
./gradlew :app:assembleRelease    # 需自备签名密钥
```

### 云编译

仓库内置两条 GitHub Actions 工作流：

| 工作流 | 触发 | 产物 |
| :--- | :--- | :--- |
| `build.yml` | 手动触发 | debug APK |
| `release.yml` | 手动一键发布 | Release APK（R8 混淆 + 固定密钥 V2 签名） |

两条工作流**都只在 Actions 页面手动触发**，不挂 push / tag 触发 —— 免得每次提交都产一版包或发一个 Release。

Release 工作流从仓库 Secrets 读取签名密钥，密钥不进入代码库。流程为：

1. 解析版本标签（手动输入 → 读 `versionName`）
2. 从 Secrets 解出密钥
3. R8 构建并签名
4. 打标签并推送
5. 发布 GitHub Release，附带 APK 与 `update.md` 里本次版本的更新说明

---

## 🔐 签名与升级

- 正式版使用固定密钥签名，因此后续版本可以直接覆盖安装升级
- 升级前提：`versionCode` 必须递增、`applicationId` 不可变更
- ⚠️ 更换签名密钥会导致已安装用户无法覆盖升级（需卸载重装），请勿随意更换

---

## 📝 更新日志

更新说明维护在 [`update.md`](update.md)：**我们只管写** `update.md`，把新版本那段内容放在文件顶部，工作流会自动取顶部那一段填进对应的 Release，历史段落自然下移留档。

---

## 📁 项目结构

```text
app/src/main/java/com/xkspeed/systemuimount/
├── ui/                   界面层
│   ├── App.kt            导航与底栏
│   ├── Route.kt          类型安全路由
│   ├── HomePage.kt       救砖 / 待测界面选择、开始停止
│   ├── SettingsPage.kt   运行参数（模式 · 超时 · 阈值）
│   ├── UiSettingsPage.kt 界面与主题
│   ├── LogPage.kt        日志
│   ├── CrashLogPage.kt   崩溃日志
│   ├── VolumeCheckPage.kt  音量键校验
│   ├── PickerPage.kt     文件选择
│   ├── AboutPage.kt      关于
│   ├── ThirdPartyLicensesPage.kt  第三方许可证
│   └── component/        玻璃与背景组件
├── core/                 崩溃日志 · root 命令 · 配置读写
└── ...
```

---

## ⚠️ 免责声明

本工具用于救回自己改崩的 SystemUI，操作涉及替换系统界面，风险自负：**务必先备好可用的救砖界面，再换入待测界面**。

---

## 📄 许可证

GNU General Public License v3.0

玻璃与背景效果参考自 [Sonify](https://github.com/XiaoZhi0729/Sonify)（同为 GPL-3.0）。第三方库的版权声明见应用内「第三方许可证」页，各依赖沿用其自身许可证。
