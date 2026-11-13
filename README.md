# Sall

Sall 是基于 [Sill](https://github.com/Saffsanity/sill) 的非官方 Android 客户端，用于从 Android 手机或平板连接运行原版 Sill 的 Mac。Mac 端不需要安装修改版。

**当前版本：0.1.2-alpha.1，测试版。** 界面和功能仍在完善，不代表与 Sill 的 iOS 客户端完全一致。项目与 Sill 作者没有官方合作或隶属关系。

## 安装与连接

1. 在 Mac 安装并运行 [原版 Sill](https://getsill.app/download) 0.6.1 或更新版本，根据提示授予屏幕录制和辅助功能权限。
2. 从本仓库的 [Releases](https://github.com/n2ray/sall/releases) 下载 Sall 测试版 APK，安装到 Android 10 或更新版本的设备。
3. 打开 Sall，选择“添加 Mac”，扫描 Mac 上 Sill 显示的配对二维码；也可以输入地址和配对码。
4. 跨网络连接时，在 Mac 的 Sill 中启用 Remote Access，并通过你信任的 VPN（例如 Tailscale）访问 Mac。

此版本使用 `io.github.n2ray.sall` 包名。此前开发版 `io.github.n2ray.sill` 的配对记录不能自动迁移，需要在 Sall 中重新配对。卸载旧版会删除旧版在手机上的数据。

## 设备要求

- Android 10（API 29）或更新版本的手机或平板。Android SDK 35 是构建与目标版本，并非手机必须运行 Android 15。
- 设备需提供可用的 **HEVC / H.265 视频解码器**。扫描二维码需要相机；也可以使用手动配对。
- Mac 运行原版 Sill 0.6.1 或更新版本，并能通过局域网或可信 VPN 与 Android 设备互通。
- 软件解码的流畅度取决于设备性能、画面分辨率和帧率。满足系统版本要求并不保证所有设备都能流畅解码；没有可用 HEVC 解码器时无法显示画面。

从 `0.1.1-alpha.1` 开始，Sall 优先使用系统默认 HEVC 解码器；硬解启动或运行失败时，会尝试设备提供的 HEVC 软解，并请求新的关键帧。展锐 T310 平板仍需实机验证。旧版 `0.1.0-alpha.1` 没有自动切换功能。

从 `0.1.2-alpha.1` 开始，Sall 会检查视频尺寸和设备的解码、图形能力；画面超限时会请求 Mac 使用 Standard 分辨率。此设置可能影响其他连接设备；若 Standard 仍超限，请降低 Mac 显示器分辨率或选择较小窗口。展锐 T310 的修复仍需实机验证。

## 已有功能与限制

- 配对和固定 Mac 身份、局域网发现、VPN 连接、断线重连。
- Mac 窗口与桌面画面、窗口切换、触控板、键盘、缩放和画中画。
- 可选剪贴板共享；Mac 声音仍是实验功能，需要在两端启用。部分设备上声音可能短暂中断或自动恢复。
- 窗口的最小尺寸、虚拟显示器状态和不同版本的 Mac 端能力可能影响画面适配与多显示器操作。

功能状态、已验证场景和待验证项目见 [iOS 功能对照](AndroidClient/IOS_PARITY.md)。这不是全面验证过的正式版本；不要将其作为唯一的远程访问途径。

## 构建与测试

Android 项目位于 [`AndroidClient`](AndroidClient/)。需要 JDK 17 或 21、Android SDK 35、Gradle 8.10.2。可使用 Android SDK 命令行工具，无需安装 Android Studio。

```sh
cd AndroidClient
ANDROID_HOME=/path/to/android-sdk gradle assembleDebug
sh tests/run.sh
```

测试版发布 APK 使用独立的本地签名密钥；公开仓库不包含密钥。有关签名环境变量和设备测试见 [Android 构建说明](AndroidClient/README.md)。桌面图标的矢量设计源文件为 [`sall-icon.svg`](AndroidClient/design/sall-icon.svg)。

## 许可与来源

Sall 派生自 [Sill](https://github.com/Saffsanity/sill)，感谢 Noah Saffer 和上游贡献者。项目以 [Apache License 2.0](LICENSE) 发布；上游原始说明保存在 [UPSTREAM_README.md](UPSTREAM_README.md)。Sill 是上游项目名称，Sall 是本 fork 的非官方 Android 客户端名称。

---

Sall is an unofficial Android client for the original Sill Mac host. This alpha release requires a new pairing if you used an older development build. Audio is experimental; see [AndroidClient/README.md](AndroidClient/README.md) and [the parity tracker](AndroidClient/IOS_PARITY.md) for build instructions and limitations.
