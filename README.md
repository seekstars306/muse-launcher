# Muse Launcher · Muse 多账号启动器

<p align="center">
  <img src="docs/screenshots/main.png" width="270" alt="Muse Launcher 主界面">
</p>

**把 muse.ai 网页版变成一个真正的 Android 应用**：每个账号一个完全隔离的运行环境、每个账号可配独立代理、支持全屏沉浸浏览。原生壳 + WebView，轻量无广告、无任何数据上报。

[![Release](https://img.shields.io/badge/下载-Releases-blue)](../../releases)
![Platform](https://img.shields.io/badge/Android-10%2B-green)
![WebView](https://img.shields.io/badge/System%20WebView-115%2B-orange)

---

## ✨ 为什么需要它

在桌面放一个 muse.ai 的「网页快捷方式」，点开是浏览器标签页：单账号、无隔离、切换登录要反复登出登入。

Muse Launcher 用 AndroidX WebView 的 **Multi-Profile** 能力，给每个账号一套**完全独立**的 Cookie / localStorage / 存储空间：

- 账号之间互不串数据，像一台手机开了 N 个互不相通的分身；
- 登录状态持久保留，切回来还是登录态；
- 每个账号可以走**不同的代理出口**，适配多账号运营场景。

## 📱 功能一览

| 功能 | 说明 |
|---|---|
| 🔐 多账号完全隔离 | 每账号独立 WebView Profile，Cookie / 存储 / 登录态互不相通 |
| 🌐 每账号独立代理 | 支持 `http` / `https` / `socks5`，支持**带账密**的代理，切换账号即时生效 |
| 📡 代理测活 | 一键测试代理连通性（10 秒超时，显示耗时），**测活通过才允许保存** |
| 🔌 Clash 端口探测 | 自动探测本机 Clash 默认端口（7890 / 7891 / 7892），一键填入 |
| 🟢 登录状态识别 | 基于 muse.ai 登录会话 cookie 判断，匿名访问不会误判为已登录 |
| 🔍 搜索过滤 | 抽屉顶部搜索框，按**名称 + 标签**实时过滤列表和侧栏头像 |
| ⛶ 全屏沉浸 | 隐藏侧栏全屏浏览，左缘右滑或返回键唤回 |
| ⬇️ 系统级下载 / 文件选择 | 网页下载走系统下载管理器，文件上传接系统选择器 |
| 🕵️ UA 伪装 | 去除 WebView 标识，避免 muse.ai 误判为移动端而跳转应用商店 |
| 🧰 adb 自动化接口 | 提供一组 am start 参数，方便脚本批量管理账号 / 配置代理 |

## 🖼️ 界面预览

| 多账号抽屉（搜索过滤中） | 账号配置（头像 / 名称 / 代理 / 标签） | 全屏模式 |
|:---:|:---:|:---:|
| <img src="docs/screenshots/drawer.png" width="240"> | <img src="docs/screenshots/config.png" width="240"> | <img src="docs/screenshots/fullscreen.png" width="240"> |

- **左侧窄条**：账号头像一列，绿点表示该账号已登录，点击即切；
- **抽屉**：展开的账号列表，支持重命名、删除、进入配置；
- **配置页**：头像（1–2 字符或 Emoji，实时预览）、名称、代理、标签。

## 📦 安装

1. 前往 [**Releases**](../../releases) 页面下载最新 `MuseLauncher-vx.x.apk`；
2. 直接安装（需允许「安装未知来源应用」）；
3. 系统要求：
   - Android 10（API 29）及以上；
   - 系统 WebView ≥ 115（多账号隔离依赖；版本过旧时启动会提示先更新 WebView）。

## 🚀 快速上手

1. **添加账号**：点侧栏底部或抽屉里的 ➕，会新建一个独立无痕环境并自动打开 muse.ai；
2. **登录**：在页面里正常登录即可，登录态会持久保留在该账号的隔离环境中；
3. **配置账号**：抽屉里点某账号的 ⚙️ 进入配置页，可设置头像、名称、代理、标签；
4. **切换账号**：点侧栏头像或抽屉行即切，切换时会先应用该账号的代理；
5. **删除账号**：抽屉里点 🗑️ 二次确认后删除，会同时清掉该账号的全部隔离数据。

### 配置代理

在配置页的「代理」卡片中填入代理地址，支持以下格式：

```
127.0.0.1:7890
http://127.0.0.1:7890
http://用户名:密码@1.2.3.4:3129
socks5://127.0.0.1:1080
```

- 点 **探测**：自动探测本机 Clash 默认端口（7890 混合 / 7891 HTTP / 7892 SOCKS）并填入；
- 点 **测活**：临时应用该代理访问探针地址，返回「连接成功（耗时）/ 失败原因 / 超时」三种结果；
- **留空**表示跟随系统网络（系统开了 VPN/TUN 时即走 TUN）；
- 保存前会自动测活，**测活不通过的代理不会被保存**。

> 💡 提示：如果你手机上的 Clash 开着 VPN/TUN 模式，所有流量本来就会经过 Clash，各账号留空即可；独立代理适合「Clash 关闭/仅代理模式」或想让不同账号走不同出口 IP 的场景。

### 登录状态是怎么判断的？

绿点只认 muse.ai 的登录会话 cookie（`hatch_sess`）。匿名访问 muse.ai 也会落下一堆埋点与风控 cookie，但不会出现这个会话 cookie——所以新建的、只打开过页面的账号会正确显示「未登录」。判断在每次页面加载完成 / 回到前台时刷新。

## 🤖 adb 自动化接口

所有能力都可通过 `am start` 参数驱动，方便配合脚本做批量运营（例如批量配置代理、批量建号删号）：

| 参数 | 类型 | 作用 |
|---|---|---|
| `--ez show_drawer true` | bool | 启动后展开账号抽屉 |
| `--el start_account <id>` | long | 启动后切换到指定账号 |
| `--ez fullscreen true/false` | bool | 进入 / 退出全屏模式 |
| `--el open_config <id>` | long | 打开指定账号的配置页 |
| `--es proxy_preset '<url>'` | string | 配置页预填代理地址 |
| `--ez proxy_autotest true` | bool | 预填后自动测活（结果只显示不保存） |
| `--ez proxy_save true` | bool | 测活通过后自动保存入库（失败不保存） |
| `--es search_query '<关键词>'` | string | 预填搜索过滤关键词 |
| `--ez add_account true` | bool | 新建一个账号 |
| `--el del_account <id>` | long | 删除指定账号（含隔离数据） |
| `--ez dump_auth true` | bool | 诊断：logcat 输出各账号 cookie 名与 localStorage 键 |

示例：给账号 3 配置代理，测活通过才保存：

```bash
adb shell am start -n com.seekstars.muselauncher/.MainActivity \
  --el open_config 3 \
  --es proxy_preset 'http://user:pass@1.2.3.4:3129' \
  --ez proxy_save true
```

> ⚠️ 这组接口仅在 **debug 构建**中生效（release 构建出于安全考虑不响应任何 intent 参数），面向**个人设备自动化**设计，未做调用方校验。请勿在不可信的环境中使用。需要自动化能力时请自行用 `./gradlew assembleDebug` 构建安装。

## 🧩 实现原理

- **账号隔离**：AndroidX WebView `ProfileStore`，每个账号一个 `Profile`（`acct_<id>`），绑定到各自的常驻 WebView 实例；删除账号时清空并删除对应 Profile；
- **页面按 LRU 驻留**：最多同时保留 4 个账号的页面会话，超出时最久未用的页面被回收（Profile 数据保留，登录态不丢，切回时重新加载页面）；单个页面渲染进程崩溃时自动重建，不影响其他账号；
- **独立代理**：`WebViewCompat` 的 `ProxyController` 是应用级开关，本应用同一时刻只有一个账号在前台，切换账号时应用该账号的代理（支持代理 URL 内嵌账密，另有 407 挑战应答兜底）；
- **UA 伪装**：移除 UA 中的 `; wv` 与 `Version/x.x` 标识，避免被 muse.ai 判定为 WebView 而重定向商店。

## ❓ 常见问题

**新建环境加载很慢或卡住？**
muse.ai 是 Meta 服务，国内网络直连不通。新建环境会**自动继承当前账号的代理**；如果继承不到（当前账号也没配代理），请先给某个账号配好可用代理再新建，或打开 Clash TUN 模式让全局流量可用。

**页面显示"加载失败"？**
说明该账号的网络路径当前不可用（断网 / 代理失效）。点 **重试** 重新加载；修复网络或代理后再点重试即可，无需删除重建。切换账号时，处于错误状态的页面也会自动重新加载。

**加载速度取决于什么？**
 muse.ai 是重前端单页应用，完整加载需要下载数 MB 资源。速度主要取决于网络路径：经代理（尤其海外代理）会比直连慢，属正常现象；应用侧已按 LRU 保留最近 4 个账号的页面会话，切换回已加载账号是秒开的。

## 🛠️ 本地构建

```bash
# 要求：JDK 17，Android SDK（compileSdk 34）
./gradlew assembleRelease   # 输出 app/build/outputs/apk/release/
./gradlew assembleDebug
```

## 🔒 隐私

- 应用**不包含任何统计、上报或第三方 SDK**（仅 androidx.webkit / appcompat）；
- 网络请求只发生在 muse.ai 本身与你自行配置的代理之间；
- 账号元数据（名称/头像/标签/代理）存于本机 SharedPreferences；登录数据存于各账号的本机 WebView Profile；
- **系统备份已禁用**（`allowBackup=false`）：登录态等敏感数据不会进入云备份或设备迁移，卸载应用即全部清除；
- 账号数据损坏时自动从滚动备份自愈，无法恢复时原始内容会留底在应用私有目录，不会静默丢失。

## ⚠️ 免责声明

本项目是面向 muse.ai 网页版的个人效率工具，与 Meta / muse.ai 官方无关。使用时请遵守目标站点的服务条款与当地法律法规。

## 📄 License

[MIT](LICENSE)
