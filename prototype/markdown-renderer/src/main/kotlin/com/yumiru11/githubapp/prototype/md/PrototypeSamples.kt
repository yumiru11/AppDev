// ===== PROTOTYPE（可抛弃，验证用）=====
// 问题：mikepenz renderer 0.43.0（+m3 主题 / +code 高亮 / +coil3 图片桥）+ Material Symbols 图标
//       在我们工具链上渲染效果如何？
// 变体：A=Issue 正文 · B=重型 GFM（表格/告警/高亮/任务列表）· C=README 式长文档 · 各 × 亮/暗
// 截图：Roborazzi（Linux 纯 JVM），产物 build/outputs/roborazzi/*.png
// 结论交付后本文件随模块删除。
package com.yumiru11.githubapp.prototype.md

enum class MdVariant(val label: String) {
    A("Issue 正文"),
    B("重型 GFM"),
    C("README 式"),
    D("代码矩阵"),
    E("代码矩阵二"),
}

/** 典型 Issue 正文：标题、引用、有序/无序/任务列表、@提及、#引用、行内代码 */
val SAMPLE_A = """
## 复现步骤：列表滚动时偶发卡顿

在 **快速滚动** 时，`PullToRefreshBox` 偶发回弹，怀疑与 #42 相关。

> 环境：Pixel 8 / Android 15 / 屏幕刷新率 120Hz
> 版本：v0.1.0

1. 打开首页
2. 快速下滑再上滑
3. 观察回弹

- [x] 已复现
- [ ] 已定位根因

@yumir11 有空看一下吗？之前 GitLight 也踩过这个坑 :bug:
""".trimIndent()

/** 重型 GFM 用例：表格、告警、高亮代码块、emoji、嵌套引用 */
val SAMPLE_B = """
## GFM 能力验证

> [!NOTE]
> GitHub Alert 的 `NOTE` 形态。

> [!IMPORTANT]
> **IMPORTANT** 告警块样式。

## 表格

| 特性 | 支持 | 备注 |
|------|:----:|------|
| 表格 | ✅ | 对齐 + 行内格式 |
| 任务列表 | ✅ | `- [x]` / `- [ ]` |
| 删除线 | ✅ | ~~不行~~ 可以 |

## 代码高亮（code 模块）

```kotlin
suspend fun fetchRepo(owner: String, name: String): Repo {
    return withContext(Dispatchers.IO) { api.repo(owner, name) }
}
```

```kotlin
// 泛型 + lambda + 高阶函数
inline fun <T : Any> Result<T>.foldOrNull(
    onSuccess: (T) -> Unit,
    onError: (Throwable) -> Unit,
): T? = fold(
    onSuccess = { onSuccess(it); it },
    onFailure = { onError(it); null },
)
```

```python
# 装饰器
def cached(fn: Callable) -> Callable:
    store = {}
    def wrap(*args):
        if args not in store:
            store[args] = fn(*args)
        return store[args]
    return wrap
```

```go
// Go 并发示例
func fetchAll(urls []string) []string {
	ch := make(chan string)
	for _, u := range urls {
		go func(u string) { ch <- fetch(u) }(u)
	}
	results := make([]string, 0, len(urls))
	for range urls {
		results = append(results, <-ch)
	}
	return results
}
```

```json
{
  "name": "AppDev",
  "version": "0.1.0",
  "tags": ["android", "compose", "material-you"],
  "ci": { "enabled": true, "runs_on": "ubuntu-latest" }
}
```

```yaml
name: Build
on:
  push:
    branches: [main]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./gradlew assembleDebug
```

```bash
# Shell 脚本
for file in src/**/*.kt; do
  if grep -q "TODO" "${'$'}file"; then
    echo "⚠️  ${'$'}file 有 TODO"
  fi
done
```

```java
// Java 8 流式 API
public List<String> filterStarts(List<String> items, String prefix) {
    return items.stream()
        .filter(s -> s.startsWith(prefix))
        .map(String::toUpperCase)
        .collect(Collectors.toList());
}
```

## 其他内联

> 一级引用
>> 二级嵌套引用

**加粗** · *斜体* · ~~删除线~~ · `行内代码` · :rocket: :tada:
""".trimIndent()

/** README 式长文档：多级标题、列表、复杂表格、折叠、告警、图片（coil3 网络加载） */
val SAMPLE_C = """
# AppDev

轻量级 **Android GitHub 客户端** · Material You 设计

## 功能特性

- 仓库浏览 / 文件树 / README
- Issue / PR / Review / 行内评论
- 通知、搜索、代码编辑（Sora Editor）

## 快速上手

1. 克隆仓库
2. 打开 Android Studio
3. 运行 `:app`

```typescript
export const CLIENT_ID = "example";
type Repo = { owner: string; name: string };
```

## 兼容性

| 平台 | 版本 | 状态 |
|:-----|:-----|:----:|
| Android | 8.0+ | ✅ |
| 平板 | 部分 | ⚠️ |

<details><summary>点击展开</summary>折叠区内容 —— GitHub 原生折叠渲染</details>

> [!CAUTION]
> 危险操作区使用 CAUTION 告警。

---

### 图片（coil3 网络加载，测试环境可能不显示）

![GitHub 标志](https://github.githubassets.com/assets/GitHub-Mark-ea2971cee799.png)

**加粗** 结尾 :rocket:
""".trimIndent()

/** 代码矩阵：全部语言高亮在一屏内验证 */
val SAMPLE_D = """
# 代码矩阵

## Kotlin（泛型 + 高阶函数）

```kotlin
inline fun <T : Any> Result<T>.foldOrNull(
    onSuccess: (T) -> Unit,
    onError: (Throwable) -> Unit,
): T? = fold(
    onSuccess = { onSuccess(it); it },
    onFailure = { onError(it); null },
)
```

## Python

```python
def cached(fn: Callable) -> Callable:
    store = {}
    def wrap(*args):
        if args not in store:
            store[args] = fn(*args)
        return store[args]
    return wrap
```

## Go

```go
func fetchAll(urls []string) []string {
	ch := make(chan string)
	for _, u := range urls {
		go func(u string) { ch <- fetch(u) }(u)
	}
	return results
}
```

## JSON

```json
{
  "name": "AppDev",
  "version": "0.1.0",
  "tags": ["android", "compose"],
  "ci": { "enabled": true, "runs_on": "ubuntu-latest" }
}
```

## YAML

```yaml
name: Build
on:
  push:
    branches: [main]
jobs:
  build:
    runs-on: ubuntu-latest
```

## Bash

```bash
for file in src/**/*.kt; do
  if grep -q "TODO" "${'$'}file"; then
    echo "TODO found"
  fi
done
```

## Java

```java
public List<String> filterStarts(List<String> items, String prefix) {
    return items.stream()
        .filter(s -> s.startsWith(prefix))
        .map(String::toUpperCase)
        .collect(Collectors.toList());
}
```
""".trimIndent()

/** 代码矩阵二：YAML/Bash/Java（JSON 已在 D 首屏确认） */
val SAMPLE_E = """
# 代码矩阵（二）


## YAML

```yaml
name: Build
on:
  push:
    branches: [main]
jobs:
  build:
    runs-on: ubuntu-latest
```

## Bash

```bash
for file in src/**/*.kt; do
  if grep -q "TODO" "${'$'}file"; then
    echo "TODO found in ${'$'}file"
  fi
done
```

## Java

```java
public class RepoService {
    private final HttpClient client;

    public RepoService(HttpClient client) {
        this.client = client;
    }

    public List<String> filterStarts(List<String> items, String prefix) {
        return items.stream()
            .filter(s -> s.startsWith(prefix))
            .map(String::toUpperCase)
            .collect(Collectors.toList());
    }
}
```
""".trimIndent()
