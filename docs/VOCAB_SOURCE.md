# 内置日语词库来源与许可

本工具内置词库改编自 **egg rolls** 的 [JLPT N1–N5 一万词 v3.5](https://github.com/5mdld/anki-jlpt-decks)。词库许可为 **CC BY-NC 4.0（署名—非商业性使用）**：可在遵守署名等条款的情况下使用、修改和分享，不得直接出售词库或将其内容整合进付费产品、商业服务或商业推广。此限制适用于词库内容，程序代码的许可另行说明。

- 原作者：egg rolls；项目维护账户：5mdld。
- 原始文本： https://raw.githubusercontent.com/5mdld/anki-jlpt-decks/main/deck-source/notes.csv
- 实际下载镜像： https://cdn.jsdelivr.net/gh/5mdld/anki-jlpt-decks@main/deck-source/notes.csv
- 源仓库许可证： https://github.com/5mdld/anki-jlpt-decks/blob/main/LICENSE
- 许可说明： https://creativecommons.org/licenses/by-nc/4.0/deed.zh-hans
- 许可全文随本工具附于 `docs/licenses/eggrolls-CC-BY-NC-4.0.txt`。
- 下载及转换日期：2026-10-29。
- 源文件大小：6,593,640 字节。
- 源文件 Git blob SHA-1：`d27e877700b46559199f49cfbf3fbbec50c54a94`（已与 GitHub contents API 返回值逐字节哈希核验）。

## 收录数量

| 参考等级 | 词条数 |
| --- | ---: |
| N5 | 807 |
| N4 | 757 |
| N3 | 1,817 |
| N2 | 3,215 |
| N1 | 4,045 |
| 合计 | 10,641 |

这些数字是各等级的独立词条数，**不是累计词汇量，也不是 JLPT 官方发布的考试词汇清单**。同一书写形式可能因读音或含义不同出现多次。等级、排序和释义沿用上游民间学习资料，只供学习参考，不保证覆盖考试全部内容或每条均无误。

## 分级与上游说明

上游 README 表明，其范围基于 2010 年改制后至 2025 年 12 月的 JLPT 材料整理；N4、N5 按作者判断由易到难排序，N3 以上参考 JPDB v2.2 词频。上游明确表示排序和分类不保证完全准确。上游将内容概括为“公开互联网资源及个人创作”，并未提供逐条释义和例句的完整权利来源清单。因此本工具保留该来源声明，不声称已独立核验每个第三方条目的权利或语言学准确性。

本包只使用已公开许可的文本导出，**没有打包上游真人发音、VOICEPEAK 音频、字体或卡片模板**。v1.1 默认使用独立打包的 Supertonic3 离线神经语音，也可手动切换到 Windows 日语语音；语音模型和运行库的许可独立列明。

## 本版所做修改

1. 将 39 列 Anki 制表符导出转换为 JSON；保留原词条 UUID 为稳定 ID。
2. 清除 HTML 显示标记和汉字后的 Anki 方括号注音，分别保留书写形式与假名读音。
3. 选用简体中文释义，提取第一条例句及中文译文；原词性、音调、补充说明合并为 Note。
4. 片假名词的原“读音”列实际存放外语词源时，改用片假名书写本身作 Reading，把原字段收入词源备注。
5. 按 N5 → N1 及上游词序排序；未新增、删减或机器翻译释义。

JSON 字段：Id、Term、Reading、Meaning、Level（1–5 整数）、Note、Example、ExampleMeaning。

## 开发备用方案（未内置）

[OpenJLPT](https://github.com/evanclan/OpenJLPT) 提供约 7,811 条英文释义词汇，数据许可为 CC BY-SA 4.0，有 JMdict、Tanos、Tatoeba 的逐项来源说明；如需未来商业发行，可评估改用此类明确支持商业利用的词库，并遵守相同方式共享和上游署名要求。当前发行包使用的是上述中文非商业词库。
