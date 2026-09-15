# Sipoe

> 一个使用 [Linphone SDK](https://www.linphone.org/) 的极简 Android SIP 软电话,
> 基于 Jetpack Compose Material3 编写,采用 Kotlin 2.x 与 AGP 9.x。

`Sipoe` 旨在以一个清爽的 UI 拨出和接听 SIP 电话,不绑定任何服务商,
完全在本地存储账号配置,适合需要直连自建 SIP 服务的用户或内网 VoIP 场景。

---

## ✨ 主要特性

- **账号管理**:DataStore 持久化,密码以 **AndroidKeyStore (AES-GCM)** 加密保存(失败时降级到沙盒内 Base64)
- **注册保活**:通过特殊类型前台服务 (`specialUse|microphone`) 保持 SIP 内核在线以接收来电
- **来电提醒**:CallStyle 全屏通知(支持接听 / 拒接)+ 通知权限关闭时自动降级为应用内铃声
- **通话中**:挂断、拒接、接听、静音、听筒 / 免提 / 蓝牙 / 耳机路由切换、DTMF 按键音
- **通话记录**:DataStore 存储,上限 50 条,左滑删除可撤销,按日期分组
- **应用内诊断**:登录失败 / 内核状态 / 网络自检(DNS / UDP 绑定 / SIP OPTIONS 探测) / 日志(含原生日志桥接)
- **网络自检 + EPERM 引导**:HyperOS 等系统拦截应用联网时一键提示去设置开启
- **多语言**:简体中文 / English / 跟随系统,通过 `AppLanguageStore` 切换并持久化
- **构建脚本**:`Makefile` 一键完成 debug / release / 签名 / 校验 / 分发

---

## 🏗️ 架构与模块

```
app/src/main/java/com/sipoe/softphone
├── MainActivity.kt          # 入口 Activity,处理 attachBaseContext 语言切换
├── SipoeApp.kt              # Application:初始化内核 / 服务 / 应用语言包装
│
├── data/                    # 持久化层
│   ├── AccountSettings.kt   # 账号配置数据类 + 校验 + 标准化
│   ├── AccountStore.kt      # DataStore 账号读写
│   ├── PasswordCipher.kt    # Keystore AES-GCM 加密 + Base64 降级
│   ├── CallLogEntry.kt / CallLogStore.kt  # 通话记录(上限 50)
│   └── AppLanguage.kt       # AppLanguage 枚举 + AppLanguageStore + LocaleSupport
│
├── sip/                     # SIP 内核封装
│   ├── SipCoreManager.kt    # 单例:Core 创建 / 注册 / 应用账号 / 网络监测 / 调试摘要
│   ├── CallController.kt    # 单例:拨出 / 接听 / 挂断 / 拒接 / 路由 / DTMF / 事件总线
│   ├── RegistrationStatus.kt# 注册状态枚举与 SipRegistrationState
│   ├── ErrorMessages.kt     # SIP 错误码 / 关键字 → 中英文友好提示
│   └── NetworkMonitor.kt    # ConnectivityManager 监听,驱动 Core.setNetworkReachable
│
├── diag/                    # 诊断
│   ├── DiagLog.kt           # 800 行环形缓冲 + StateFlow + liblinphone 日志桥接
│   └── NetworkSelfTest.kt   # DNS / UDP 绑定 / SIP OPTIONS 探测
│
├── service/                 # 系统交互
│   ├── SipForegroundService.kt  # 前台服务 / 状态通知 / 来电通知
│   ├── NotificationHelper.kt    # 通道 / CallStyle / PendingIntent 构造
│   └── InAppRinger.kt           # 通知权限缺失时的应用内铃声 + 振动
│
└── ui/                      # 界面
    ├── theme/               # SipoeColors + SipoeTheme + 品牌 light/dark 调色板
    ├── components/          # Dialpad
    ├── permissions/         # Permissions 权限检测工具 + PermissionWarningCard
    ├── SipoeNavHost.kt      # 路由表 + 状态机(来电自动跳 CALL)
    ├── dialer/  DialerScreen.kt     # 拨号页
    ├── account/ AccountScreen.kt   # 账号配置
    ├── call/    InCallScreen.kt     # 通话中 UI
    ├── history/ HistoryScreen.kt    # 通话记录
    ├── diagnostics/ DiagnosticsScreen.kt  # 诊断页
    └── settings/ SettingsScreen.kt  # 设置(语言)
```

### 数据流

```
键盘/拨号 ─▶ CallController.dial() ─▶ linphone inviteAddress
                                                   │
                              linphone listener (CoreListenerStub)
                                                   │
   CallController._state (MutableStateFlow) ─▶ InCallScreen / DialerScreen

SipCoreManager._registration ─▶ AccountScreen 注册状态卡 / Dialer 顶栏胶囊
```

---

## 🧪 技术栈

| 模块 | 版本 |
| --- | --- |
| Android Gradle Plugin | 9.4.0 |
| Kotlin | 2.4.20 |
| Compose BOM | 2026.09.00(material3 1.4.0、ui 1.12.1) |
| navigation-compose | 2.10.1 |
| lifecycle | 2.11.0 |
| kotlinx-serialization-json | 1.11.0 |
| DataStore Preferences | 1.2.1 |
| core-ktx | 1.19.0 |
| material-icons-core | 1.7.8 |
| **[org.linphone.no-video:linphone-sdk-android](https://www.linphone.org/)** | **5.5.16** |

> **注意**:`linphone-sdk-android` 是 GPLv3 / 商业双许可,本项目同时受 GPLv3 约束。

### SDK 配置

| 字段 | 值 |
| --- | --- |
| `compileSdk` / `targetSdk` | 37 |
| `minSdk` | 26 (Android 8.0) |
| `applicationId` | `com.sipoe.softphone` |
| `versionName` / `versionCode` | `0.1.0` / `1` |
| ABI 支持 | `arm64-v8a` + `armeabi-v7a`(arm-only 以减小 APK) |
| `jniLibs.useLegacyPackaging` | `true`(.so 文件按页对齐存储) |
| R8 / minify | **开启**(`proguard-android-optimize.txt` + 项目规则) |
| 资源压缩(`shrinkResources`) | **开启** |

---

## 🛠️ 环境要求

| 工具 | 版本 | 说明 |
| --- | --- | --- |
| JDK | **17** | 项目固定 `compileOptions = VERSION_17` |
| Android SDK Platform | 37 | 通过 `local.properties` 的 `sdk.dir` 指定 |
| Build Tools | 35+ | Makefile 自动取最新已安装版本 |
| Gradle | wrapper 9.7.1 | 无需手动安装 |
| adb | platform-tools | 真机调试 |
| GNU Make | 3.81+ | 可选,所有命令也可以直接用 `./gradlew` |

> **WSL2 用户**:`adb devices` 在 WSL2 内可能挂起,建议在 Windows 侧执行 `adb install`,
> 或将 `app/build/outputs/.../*.apk` 拷出后用手机互传安装。

---

## 🚀 快速开始

### 1. 克隆与本地配置

```bash
git clone <repo-url> sipoe
cd sipoe
echo "sdk.dir=/path/to/Android/Sdk" > local.properties   # 必需,但不入库
```

### 2. 生成 release 签名密钥

```bash
make keystore       # 自动生成 keystore/sipoe-release.jks 与 keystore.properties(随机密码)
```

或者手工生成:
```bash
keytool -genkeypair -keystore keystore/sipoe-release.jks -alias sipoe \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass <password> -keypass <password> \
    -dname "CN=Sipoe, OU=Mobile, O=Sipoe, C=CN"
```

并手工创建 `keystore.properties`(`keystore.properties` 与 `keystore/` 已加入 `.gitignore`):

```properties
storeFile=keystore/sipoe-release.jks
storePassword=<上面设置的密码>
keyAlias=sipoe
keyPassword=<上面设置的密码>
```

### 3. 构建并校验

```bash
make verify        # 等价于 ./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug
```

输出:
```
BUILD SUCCESSFUL
No issues found.
```

### 4. 安装到手机

```bash
make dist                              # 输出到 dist/sipoe-0.1.0-{debug,release}.apk
make install-release                   # 或 adb install -r dist/sipoe-0.1.0-release.apk
```

> debug 与 release **签名不同**(`debug.keystore` vs `keystore/sipoe-release.jks`),
> 真机上同时安装两个版本会失败,需先卸载其中一个。日常调试建议直接用 `make install`(debug 版)。

---

## 📦 Makefile 用法

`make` / `make help` 查看完整列表(由 `awk` 解析 `## ` 注释生成)。

| 目标 | 作用 |
| --- | --- |
| `make debug` | 构建 debug APK |
| `make release` | 构建 release APK(自动读取 `keystore.properties` 签名) |
| `make apk` | 两个都构建 |
| `make verify` | debug + release + lint(提交前检查) |
| `make lint` | 只跑 lint |
| `make dist` | 构建并复制到 `dist/sipoe-<版本>-{debug,release}.apk` |
| `make sign APK=x.apk OUT=y.apk` | 用 release 密钥签名任意 APK |
| `make verify-sign` | 校验 release APK 签名 |
| `make certs` | 打印 DN + SHA-256 指纹 |
| `make keystore` | 生成签名密钥库(已存在跳过) |
| `make version` | 打印版本号 |
| `make install` / `make install-release` | adb 安装 debug / release |
| `make devices` | 列出 adb 设备 |
| `make clean` | 清理 `build/` |
| `make distclean` | 清理 `build/` + `dist/` |

所有目标均通过 `JAVA_HOME`/`ANDROID_HOME` 自动配置环境,**不需要** `source ~/.bashrc`。

---

## 🔐 权限说明

| 权限 | 用途 |
| --- | --- |
| `INTER |` | SIP 信令 |
| `ACCESS_NETWORK_STATE` | 监听网络变化(NetworkMonitor) |
| `RECORD_AUDIO` | 通话上行 / 听回铃 |
| `MODIFY_AUDIO_SETTINGS` | 切换音频路由 |
| `FOREGROUND_SERVICE` / `SPECIAL_USE` / `MICROPHONE` | 通话保活前台服务 |
| `POST_NOTIFICATIONS` (Android 13+) | 来电通知 |
| `BLUETOOTH_CONNECT` (Android 12+) | 切换蓝牙音频路由 |
| `WAKE_LOCK` | 通话中保持 CPU |
| `USE_FULL_SCREEN_INTENT` | 来电全屏提醒 |
| `VIBRATE` | 应用内铃声时的振动**

应用启动时(`MainActivity.RequestPermissionsOnce`)会一次性请求
`RECORD_AUDIO` + `POST_NOTIFICATIONS` + `BLUETOOTH_CONNECT`,
缺失时拨号页 / 通话页会显示对应的引导卡片(永久拒绝会跳系统设置)。

---

## 📞 SIP 关键细节

1. **端口**:`SipCoreManager` 用 `Random.nextInt(40000, 60000)` 随机一个本机 SIP 端口(UDP/TCP 共用,TLS 用 +1)。
   这样做是因为部分定制 ROM 会拦截 5060 的本地绑定(`EPERM`),随机端口通常可用;
   `NetworkSelfTest` 在诊断页会同时探测 5060 与随机端口的可用性。
2. **IPv4**:强制 `Core.setIpv6Enabled(false)`(参见 `applyDefaultTransports()`),优先 NAT 友好。
3. **传输**:支持 UDP / TCP / TLS,默认 UDP;TLS 时端口 5061,其余 5060(可手动覆盖)。
4. **注册**:默认 expires 300 秒,允许范围 60–3600;服务器地址由 `domain` 或 `proxy` 解析。
5. **STUN**:默认 `stun.l.google.com:19302`,可在高级设置里切换 / 关闭。
6. **网络监听**:`NetworkMonitor` 在网络变更时调用 `Core.setNetworkReachable()`,
   触发 liblinphone 内部重连。
7. **来电优化**:
   - 服务启动后立刻 `ensureStarted()` 再处理通知 action(避免内核未就绪时空调用)
   - 来电时通过 `Core.callsNb` 检查第二路来电,自动 `Reason.Busy` 拒接并 Toast 提示
   - 通话中常驻通知用 `CallStyle.forOngoingCall` 暴露挂断按钮
   - 通知权限关闭时降级为应用内铃声(`InAppRinger`)
8. **挂断有兜底**:点击「挂断」立刻把状态切到 `Ending` + 转圈,
   3 秒内未收到 SDK 回调也会强制置 `Ended` 返回,避免卡住。
9. **密码安全**:`PasswordCipher` 优先使用 `AndroidKeyStore` 的 AES/GCM 256,
   Keystore 不可用时降级为 `plain:<base64>`,数据仍受应用沙盒保护。

---

## 🌍 本地化

- 源语言:**简体中文**(`values/`)
- 已翻译:**English**(`values-en/`)
- 添加新语言:新建 `res/values-xx/strings.xml`,复刻所有 key。
- 切换语言:**拨号页右上角齿轮 → 设置 → 语言**,支持「跟随系统 / 简体中文 / English」,
  选择后立即生效(`recreate()`),并同时更新 `Application` 资源,使通知 / 前台服务文案跟随。

```kotlin
// data/AppLanguage.kt 关键实现
object LocaleSupport {
    fun wrap(base: Context): Context        // Activity attachBaseContext 用
    fun applyToApplication(application: Application)  // 更新 Application Resources
    fun setLanguage(context: Context, language: AppLanguage)  // 写入并应用
}
```

---

## 🧪 已测试 / 验证账号

测试时使用:
```
identity  : sip:REDACTED@example.invalid
server    : sip:example.invalid:5060 (UDP)
expires   : 300
STUN      : stun.l.google.com:19302
```

> 若系统提示 `EPERM` 无法连接,通常为 HyperOS / EMUI 等定制系统的网络管控策略,
> 按诊断页 → 网络自检 → 排查指引卡 操作即可。

---

## 🛠️ 故障排查

| 现象 | 排查 |
| --- | --- |
| 注册一直失败 | 进入「诊断」页 → 网络自检 → 重新自检;若 `EPERM` 提示,按指引开权限 |
| 看不到来电 | 顶部注册胶囊 + 拨号页通知卡片;Android 14+ 检查「来电全屏提醒」开关 |
| 通话听不到对方 | 检查「麦克风」权限卡片;切换音频路由到听筒 / 免提 |
| 切换蓝牙没反应 | 检查「蓝牙权限」卡片(Android 12+ 需要运行时授权) |
| 日志查看 | 诊断页 → 重新注册 / 重新自检 / 刷新 / 清空;「复制」导出报告 |

---

## 🚧 已知限制 / TODO

- **没有开机自启**(`RECEIVE_BOOT_COMPLETED`):重启后首次需手动打开 App,服务才会启动
- **没有通话保持(Hold)** 与「通话中收起」按钮
- **没有未接来电通知 / 角标**
- 通话记录未按号码聚合次数
- ABI 限制在 arm64-v8a + armeabi-v7a:Chromebook / x86 模拟器需要源码另编

---

## 📄 协议 / 致谢

- Linphone SDK (`org.linphone.no-video:linphone-sdk-android`) **GPLv3**
- 本项目在 GPLv3 下发布
- Compose / Material3 / AndroidX: Apache 2.0

---

## 📝 更新日志

- `v0.1.0` — 首版:账号 / 注册 / 拨号 / 来电 / 通话记录 / 诊断,中英双语,Makefile 构建脚本
- R8 + 资源压缩 + ABI 筛选后,release APK **从 56 MB 降至 28 MB**(精简 ~50%),签名保持不变