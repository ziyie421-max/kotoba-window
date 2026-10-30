# 小词窗 · Kotoba Window

Windows / Android 悬浮日语单词卡片，适合在工作或学习间隙复习。

## 下载与使用

**Android：** [下载 v1.1.2 APK](https://github.com/ziyie421-max/kotoba-window/releases/tag/android-v1.1.2)，Android 8.0 及以上，内置离线语音。支持悬浮窗、多等级词库、随机学习及明暗主题。源码和构建说明见 [android/](android/README.md)。

**Windows：** 前往 [Windows v1.1.1](https://github.com/ziyie421-max/kotoba-window/releases/tag/v1.1.1) 下载 **KotobaWindow-v1.1.1-Windows-x64.zip**，完整解压后运行 `小词窗/KotobaWindow.exe`。请保留同目录的 `voice` 文件夹。Windows 语音模型请从 Windows Release 完整版获取。

运行环境：Windows 10/11 x64、.NET Framework 4.8。离线语音模型已包含在完整版中；无需联网、账号或 API Key。

## 功能

- N5–N1 共 10,641 条日语词汇，提供假名、中文释义和例句。
- N5–N1 多选混学，共享每日新词额度；可选乱序，新词与到期复习分开调度。
- 间隔复习、收藏、撤销上一条评分和本地学习记录。
- Supertonic3 离线神经语音、自动发音与 R 键重播；可手动选择 Windows 日语语音。
- 窗口置顶、紧凑模式、明暗主题、可调背景透明度；文字保持清晰；透明模式下按钮与边框淡显，悬停或键盘聚焦时显现。

| 快捷键 | 操作 |
| --- | --- |
| Space | 翻面 |
| 1–4 | 评分 |
| R | 重播发音 |
| F | 收藏 |
| Alt+E | 撤销上次评分 |
| Alt+R | 显示／隐藏窗口 |

详见 [使用说明](docs/使用说明.md)。

## 数据与语音

学习记录默认保存于程序旁的 `portable-data` 文件夹；目录不可写时使用本地应用数据目录。更新时保留自己的学习记录，勿将其上传到仓库。语音第一次生成可能需要数秒，之后会使用本地缓存。

词库等级来自社区学习资料，不是 JLPT 官方完整词汇表。合成语音并非真人录音，不能替代标准音频或专业音调词典。

## 从源码构建

在 Windows 上运行：

```bat
build.cmd
test.cmd
build-voice.cmd
```

主程序生成在 `build/KotobaWindow.exe`；`test.cmd` 运行 20 项核心测试和 23 项 UI 冒烟测试。VoiceHost 为独立 x64 程序，生成在 `voice-assets/voice/VoiceHost.exe`。

Windows 离线语音资源请从 Release 获取。将 Release 完整版的 `voice` 文件夹复制为 `build/voice`，然后用自己编译的 VoiceHost 替换其中的 `VoiceHost.exe` 即可。官方语音资源来源、组件版本与哈希见 [voice-manifest.json](docs/voice-manifest.json)。语音集成测试源文件为 `tests/VoiceServiceTests.cs`，需额外提供完整语音资源。

## 目录

- `src/`：C# / WPF 主程序与语音适配器
- `data/vocab.json`：词库文本
- `tests/`：核心、UI 与语音测试
- `docs/`：使用说明、调研和来源许可

## 许可

**各部分采用不同许可，请勿将整个发布包视为 MIT 授权。**

- 应用代码： [MIT](LICENSE-CODE.txt)。
- 内置词库：© egg rolls，改编自 [5mdld/anki-jlpt-decks](https://github.com/5mdld/anki-jlpt-decks)，**CC BY-NC 4.0，限非商业使用**。详见 [词库来源](docs/VOCAB_SOURCE.md) 与 [许可全文](docs/licenses/eggrolls-CC-BY-NC-4.0.txt)。
- Supertonic3 模型：OpenRAIL-M，须遵守模型附带许可及使用限制。
- sherpa-onnx：Apache-2.0；ONNX Runtime：MIT，附第三方声明。语音完整许可随 Release 的 `voice` 文件夹提供，仓库亦保留于 `docs/licenses/`。

包含本词库的完整应用仅供非商业学习使用。
