# AppDev README Prototype

This is the **shared render fixture** for the AppDev README prototype. It covers *emphasis*, **strong text**, ~~strikethrough~~, and `inline code` in one paragraph, plus a [relative link](./docs/architecture.md) and an [external link](https://developer.android.com/jetpack/compose).

## Rendering strategy

The project uses a layered markdown strategy: native Compose for short content and a WebView fallback for complex GFM documents. See the `core:markdown` module.

### Supported features

- Unordered list with **bold** and `code`
- Ordered list with *italic*
  1. First renderer candidate
  2. Second renderer candidate
  3. Screenshot comparison
- Task list:
  - [x] Compose native renderer
  - [ ] WebView fallback polish
  - [ ] User reviews the prototype

#### Typography targets

Body text uses a GitHub-like 16sp / 1.6 baseline. Headings are deliberately tighter than the upstream renderer defaults.

---

> Ordinary blockquotes get a 3dp primary left bar and a soft surface background.

> [!NOTE]
> Notes carry an octicon icon, a bold title, and a tinted card with a left color bar.

> [!WARNING]
> Warnings must stay visible in both light and dark themes without emoji icons.

> [!TIP]
> Tips use the same layout with a green accent and a light-bulb icon.

## Code blocks

```kotlin
fun main() {
    val answer = 42
    println("answer=$answer")
}
```

```json
{
  "renderer": "native",
  "fallback": "webview",
  "themes": ["light", "dark"]
}
```

```markdown
> [!NOTE]
> Useful information that users should know.
```

## Table sample

| Feature | Version A | Version B | Status |
| ------- | --------- | --------- | ------ |
| Theme fusion | CSS variables | MaterialTheme | Compare |
| Code highlight | GitHub token colors | GitHub token colors | Compare |
| Table scrolling | Native browser overflow | HorizontalScroll | Compare |

## Image sample

![AppDev prototype image](assets/readme-sample.png)

<details>
<summary>Native details fallback</summary>

If the native renderer cannot intercept the `HTML_BLOCK` node, this section is recorded as a native gap. If it can, the arrow rotates and content animates open.

</details>

## Links

- [Relative repository link](./docs/architecture.md)
- [External documentation](https://developer.android.com/jetpack/compose)
