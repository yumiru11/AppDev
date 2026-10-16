# 第三方组件与许可（关键项）

> 本文件收录**与渲染链路直接相关**的关键第三方组件及其许可声明；完整依赖清单见
> `gradle/libs.versions.toml` 与各模块构建脚本。新增依赖时请在本文件补一行并附许可类型。

## 原生 Mermaid 渲染

- **cmp-mermaid**（`io.github.swithun-liu:mermaid-compose` / `mermaid-core`）
  - 许可：MIT License
  - 版权：Copyright (c) 2026 swithun-liu
  - 上游：https://github.com/swithun-liu/cmp-mermaid
  - 用途：把 Mermaid 12 语义翻译为 Kotlin，并用 Compose Canvas 绘制图表；不内嵌 Mermaid.js、
    不引入网络客户端。

```text
MIT License

Copyright (c) 2026 swithun-liu

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## HTML 解析

- **jsoup**（`org.jsoup:jsoup`）
  - 许可：MIT License
  - 版权：Copyright (c) 2009-2025 Jonathan Hedley
  - 上游：https://jsoup.org/
  - 用途：原生 HTML 子集渲染器的解析层（标签 / 属性 / 协议三级白名单）。

```text
MIT License

Copyright (c) 2009-2025 Jonathan Hedley (https://jsoup.org/)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Markdown 渲染链

- **multiplatform-markdown-renderer**（mikepenz）— Apache-2.0：原生 markdown 渲染骨架。
- **KotlinTextMate** — 语法高亮（许可见其仓库）。
- **Coil 3** — Apache-2.0：图片加载（含 SVG / GIF 解码）。
- **KaTeX** — MIT：离线数学公式渲染（WebView 岛通道）。
- **Mermaid（tiny 构建）** — MIT：WebView 回退通道的图表渲染。
- **DOMPurify** — Apache-2.0 / MPL-2.0 双许可：WebView 侧 HTML 清洗。
