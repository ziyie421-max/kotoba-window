# 小词窗 Android

原生 Java Android 悬浮日语词卡，内置 10,641 词与 Supertonic 3 离线神经语音。[下载 Android v1.1.2 APK](https://github.com/ziyie421-max/kotoba-window/releases/tag/android-v1.1.2)。安装后点击「悬浮」并按提示授权，即可覆盖在其他应用上学习。

## 界面

纸白与墨黑两套配色，朱色只用来标注"新词"、等级与收藏。主界面就是卡面本身，不放卡片套卡片：一张词、一行假名、一条短的朱色分隔线、释义，下面是听音与收藏、主按钮、四段评分；评分格同时给出下次出现的时间。设置与词库是整屏页面，底部三个入口切换，设置项改动即生效，没有"保存"按钮。悬浮窗是同一个小尺寸版本，顶部一条可拖动的细把手，收起后只剩把手。

字体只用系统内建：词条用 serif（明朝/宋体），其余 sans。图标是代码里画的 24×24 线性图标，不随系统图标包变形。

## 构建

使用 Windows PowerShell 5.1 执行 build.ps1。参数 ToolRoot、JavaBin、SigningDir、Version 可指定工具、私有签名目录与版本号。无 Gradle 依赖；需要 JDK 17、Android platform 35、build-tools 35、R8 8.13.25、Kotlin stdlib 2.2.0、sherpa-onnx Android AAR 2.0.1。

工具目录结构以 build.ps1 为准。签名目录包含 release.jks 和 password.txt，应单独安全备份，不能提交到仓库。更新安装必须沿用原签名。输出 release/KotobaWindow-Android-v1.1.2.apk。

在 Windows 上通过 jar 打包 assets 以确保 ZIP 使用正斜杠路径，避免 aapt2 35 嵌套资源路径问题。

## 数据与权限

学习数据保存在应用私有目录，可通过系统文件选择器导入导出 JSON；语音缓存自动控制容量。不申请 INTERNET 权限。悬浮模式使用显示在其他应用上层及前台服务权限。

## 许可

代码 MIT，见 assets/licenses；词库 CC BY-NC 4.0，详见 assets/VOCAB_SOURCE.md 和 assets/VOCAB_LICENSE.txt；Supertonic 模型 OpenRAIL-M，见 assets/model。第三方运行库许可保留在 assets/licenses。不得将整包误标为纯 MIT 或无条件商业使用。

## 验证

1.0.0 曾在 Android 15 x86_64 模拟器验证安装、启动、自动合成与播放、翻面评分、撤销、重启保存、多等级与乱序、悬浮收起关闭、透明及明暗切换。

1.1.1 诊断版已经由使用者在 OPPO Find X8s 上确认能够打开使用。1.1.2 修复桌面图标生成时的日文编码，以及设置开关圆点越界；已检查实际图标渲染、不同密度与动画位置下的圆点边界，并通过构建与 APK 签名校验。1.1.2 尚未在真机重新验证。

覆盖安装使用相同签名并保留学习进度，无需先卸载。桌面图标为浅色底、红色手写「あ」，已转换为矢量轮廓，不需要手机安装对应字体。
