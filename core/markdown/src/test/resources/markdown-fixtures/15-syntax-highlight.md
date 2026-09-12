```kotlin
package demo

import kotlinx.coroutines.flow.Flow

/** 高亮回归：关键字 / 字符串 / 注释 / 数字 / 注解 */
@Deprecated("demo")
suspend fun fetch(id: Int): Flow<String> =
    flow {
        emit("id=$id") // 行尾注释
    }
```
