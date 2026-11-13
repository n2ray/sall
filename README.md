# Sall

Sall 是基于 [Sill](https://github.com/Saffsanity/sill) 的非官方 Android 客户端，用于从 Android 手机或平板连接运行原版 Sill 的 Mac。Mac 端不需要安装修改版。

**当前版本：0.1.0-alpha.1，测试版。** 界面和功能仍在完善，不代表与 Sill 的 iOS 客户端完全一致。项目与 Sill 作者没有官方合作或隶属关系。

## 安装与连接

1. 在 Mac 安装并运行 [原版 Sill](https://getsill.app/download)，根据提示授予屏幕录制和辅助功能权限。
2. 从本仓库的 [Releases](https://github.com/n2ray/sall/releases) 下载 Sall 测试版 APK，安装到 Android 10 或更新版本的设备。
3. 打开 Sall，选择“添加 Mac”，扫描 Mac 上 Sill 显示的配对二维码；也可以输入地址和配对码。
4. 跨网络连接时，在 Mac 的 Sill 中启用 Remote Access，并通过你信任的 VPN（例如 Tailscale）访问 Mac。

此版本使用 `io.github.n2ray.sall` 包名。此前开发版 `io.github.n2ray.sill` 的配对记录不能自动迁移，需要在 Sall 中重新配对。卸载旧版会删除旧版在手机上的数据。

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
