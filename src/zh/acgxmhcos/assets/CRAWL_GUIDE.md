# ACGXmh Cosplay 搜索索引爬取指南

本文档说明如何在本地运行爬虫脚本来生成搜索索引文件 (`index.json`)，以及如何将其集成到扩展中。

## 前置要求

- Python 3.6+（无需额外安装第三方库，仅使用标准库）

## 使用方法

### 1. 运行爬虫脚本

在终端中，进入 `assets` 目录并运行脚本：

```bash
cd src/zh/acgxmhcos/assets
python crawl_index.py
```

脚本会自动爬取 `www.acgxmh.com/cos/` 的所有列表页面，提取每个图集的 ID、标题和缩略图地址。

### 2. 命令行参数

| 参数            | 默认值       | 说明                                |
| --------------- | ------------ | ----------------------------------- |
| `--output`      | `index.json` | 输出文件路径                        |
| `--max-pages`   | `0`          | 最大爬取页数，`0` 表示爬取所有页面  |
| `--delay`       | `1.0`        | 每次请求之间的延迟时间（秒）        |

**示例**：只爬取前 5 页，每次请求间隔 2 秒：

```bash
python crawl_index.py --max-pages 5 --delay 2.0
```

**示例**：指定输出文件路径：

```bash
python crawl_index.py --output /path/to/index.json
```

### 3. 日志输出

脚本运行时会输出详细的进度信息，包括：

- 每个 HTTP 请求的 URL、状态码、响应大小和耗时
- 每页爬取后的新增条目数和累计总数
- 整体进度百分比、已用时间和预计剩余时间（ETA）
- 最终的汇总统计信息

### 4. 放置 `index.json`

爬取完成后，将生成的 `index.json` 文件放到以下目录：

```
src/zh/acgxmhcos/assets/index.json
```

即与 `crawl_index.py` 同级目录。完整路径结构如下：

```
src/zh/acgxmhcos/
├── assets/
│   ├── crawl_index.py      ← 爬虫脚本
│   ├── index.json          ← 生成的搜索索引（放在这里）
│   └── CRAWL_GUIDE.md      ← 本文档
├── build.gradle
├── res/
│   └── ...
└── src/
    └── eu/kanade/tachiyomi/extension/zh/acgxmhcos/
        └── AcgxmhCos.kt
```

### 5. 构建扩展

放置好 `index.json` 后，重新构建扩展即可：

```bash
# 在项目根目录
./gradlew :src:zh:acgxmhcos:assembleDebug
```

构建后，`index.json` 会被打包到 APK 的 `assets` 目录中。扩展在搜索时会读取该文件，实现离线搜索功能。

### 6. 注意事项

- 爬取所有页面可能需要较长时间，请耐心等待。脚本会显示 ETA（预计剩余时间）。
- 如果爬取过程中出现网络错误，脚本会自动重试最多 3 次。
- 建议定期重新爬取以更新索引，保持搜索结果的最新。
- `index.json` 不应提交到 Git 仓库（文件较大），应在本地构建时生成。
