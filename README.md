# 小星记账无障碍恢复

一个只作用于小星记账（`com.cxincx.xxjz`）的 LSPosed 模块。它使用小星记账在 KernelSU 中获准的 Root 权限，恢复「小星记账-自动记账」的 Android 无障碍开关，并保留其他已启用的服务。本模块无需独立的开机脚本或 Shell 常驻进程。

## 安装与配置

1. 在已安装并启用 KernelSU 和 LSPosed 的手机上，从 [Releases](../../releases) 下载并安装 APK。
2. 在 LSPosed 中启用「小星记账无障碍恢复」，作用域只勾选小星记账。
3. 在 KernelSU 的「超级用户」中为小星记账授予 Root。重新启动小星记账进程或重启手机，以加载模块。
4. 若希望在开机后恢复，保留桌面上的小星记账小组件。重启后首次解锁，让系统启动小组件进程。

## 工作方式与实测范围

小星记账主进程监听无障碍设置变化。小组件进程启动时也检查一次。发现目标服务的开关关闭时，模块调用 Root 读取当前设置并补上该服务，再由 Android 绑定服务。Root 授权针对小星记账的应用身份。

已在 REDMI K80、澎湃 OS 4.0.3.0、LSPosed 2.2.1 和小星记账 4.0.8 上验证。手动关闭开关后约 1 秒恢复。开关关闭状态已写入磁盘后重启，首次解锁时也恢复并成功绑定。重启测试使用了桌面小组件。实际账单识别、其他机型及移除小组件后的开机恢复未验证。若强行停止小星记账，恢复逻辑要等应用进程再次启动才能运行。

如需手动关闭无障碍，请先在 LSPosed 中停用模块并重启手机，再关闭「小星记账-自动记账」。如不再使用模块，也可在 KernelSU 收回小星记账的 Root 授权。

## 构建

需要 JDK 17、Android SDK Platform 35、包含 AAPT2、D8、zipalign 和 apksigner 的 Android SDK Build Tools，以及兼容的 Xposed API JAR。以 Windows PowerShell 为例：

```powershell
./build.ps1 -JavaHome $env:JAVA_HOME -AndroidSdk $env:ANDROID_HOME `
  -BuildToolsVersion 35.0.0 -XposedApiJar 'C:\path\to\xposed-api.jar'
```

本地签名的 APK 生成在 `dist/`。脚本首次运行会在 `build/` 下生成仅供本地构建使用的签名密钥。自行构建的 APK 与 Release APK 的签名可能不同，Android 要求覆盖安装时签名一致。

## 免责声明

本项目是独立开发的第三方模块，与小星记账及其开发者、KernelSU 和 LSPosed 项目无隶属关系，也未获得其背书。项目不分发小星记账安装包、源码或应用资源。

仅在你拥有或获准管理的设备上使用。授予小星记账 Root 会扩大该应用的权限。模块启用时，手动关闭目标无障碍服务也可能被自动恢复。系统、应用或框架更新可能影响功能和兼容性。本项目按现状提供，不作任何明示或默示担保。在适用法律允许的范围内，作者和贡献者不承担因使用本项目产生的索赔、损失或其他责任。

## 许可证

本仓库的模块源码和构建脚本采用 [MIT License](LICENSE)。第三方项目与依赖各自适用其原有许可。
