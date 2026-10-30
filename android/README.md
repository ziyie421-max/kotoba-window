# 小词窗 Android

原生 Java Android 悬浮日语词卡，内置 10,641 词与 Supertonic 3 离线神经语音。使用方法见 release/使用说明.txt。

## 构建

使用 Windows PowerShell 5.1 执行 build.ps1。参数 ToolRoot、JavaBin、SigningDir 可指定工具和私有签名目录。无 Gradle 依赖；需要 JDK 17、Android platform 35、build-tools 35、R8 8.13.25、Kotlin stdlib 2.2.0、sherpa-onnx Android AAR 2.0.1。

工具目录结构以 build.ps1 为准。签名目录包含 release.jks 和 password.txt，应单独安全备份，不能提交到仓库。更新安装必须沿用原签名。输出 release/KotobaWindow-Android-v1.0.0.apk。

在 Windows 上通过 jar 打包 assets 以确保 ZIP 使用正斜杠路径，避免 aapt2 35 嵌套资源路径问题。

## 数据与权限

学习数据保存在应用私有目录，可通过系统文件选择器导入导出 JSON；语音缓存自动控制容量。不申请 INTERNET 权限。悬浮模式使用显示在其他应用上层及前台服务权限。

## 许可

代码 MIT，见 assets/licenses；词库 CC BY-NC 4.0，详见 assets/VOCAB_SOURCE.md 和 assets/VOCAB_LICENSE.txt；Supertonic 模型 OpenRAIL-M，见 assets/model。第三方运行库许可保留在 assets/licenses。不得将整包误标为纯 MIT 或无条件商业使用。

## 验证

Android 15 x86_64 模拟器通过安装、启动、自动合成和播放流程、翻面评分、撤销、重启保存、多等级与乱序、悬浮收起关闭、透明及明暗切换验证。未在 OPPO 真机运行；未对所有词条逐一听音核验。跨设备进度导入尚未做完整兼容测试。
