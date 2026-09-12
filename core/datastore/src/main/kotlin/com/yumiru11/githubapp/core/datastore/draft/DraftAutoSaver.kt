package com.yumiru11.githubapp.core.datastore.draft

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * 草稿自动保存调度器：**输入防抖 + 退出即时落盘 + 丢弃取消待写**。
 *
 * 三个分支的时序语义（对应需求审计 §10 P2 的验收面）：
 * 1. **输入防抖** —— [scheduleSave] 取消同键上一次待写任务，`debounceMillis` 后落一次盘。
 *    连续输入只在用户停顿后写一次（Preferences DataStore 每次写入重写整文件，防抖是必需的）。
 * 2. **退出即时落盘** —— [saveNow] 取消待写任务并立刻写；返回键退出编辑页时调用。
 * 3. **丢弃** —— [discard] 先取消待写任务再删草稿：否则"丢弃后 800ms 又写回来"，
 *    用户会看到已丢弃的草稿复活（本票最容易踩的时序坑，单测 `DraftAutoSaverTest.discard_*` 覆盖）。
 *
 * ## 为什么写入跑在**应用级** scope（而不是 `viewModelScope`）
 *
 * 退出编辑页/进程回收时 ViewModel 会被清理，`viewModelScope` 随即取消 —— 挂在上面的
 * "退出前最后写一次"会被直接吞掉。故本类持有独立 scope（默认 `SupervisorJob + Dispatchers.IO`，
 * 与 `SearchCacheModule` 的应用级 scope 同模式），写入不随页面销毁而消失。
 *
 * ## 失败策略（尽力而为）
 *
 * 草稿是**附加能力**：读失败按"没有草稿"处理、写失败静默放弃，绝不让用户因为草稿丢/写不了
 * 而中断编辑（编辑区文本仍在编辑器里）。只捕获 [IOException]（DataStore 文件损坏/磁盘满），
 * 其余异常照常上抛（不掩盖真缺陷）。
 *
 * @param scope 写入作用域（生产 = 应用级 scope；单测 = `TestScope` 以用虚拟时间断言防抖）
 * @param debounceMillis 防抖窗口（默认 [DEFAULT_DEBOUNCE_MILLIS]）
 */
class DraftAutoSaver(
    private val repository: DraftRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    /** 每个草稿键最多一个待写任务（同键新任务顶替旧任务）。 */
    private val pendingWrites = ConcurrentHashMap<DraftKey, Job>()

    /** 读取草稿（无草稿 / 读失败均返回 null，见类 KDoc 失败策略）。 */
    suspend fun load(key: DraftKey): String? =
        try {
            repository.load(key)
        } catch (expected: IOException) {
            // 预期内：DataStore 文件损坏或 IO 失败 → 当作"没有草稿"，不打断用户编辑
            null
        }

    /** 防抖保存（编辑器每次文本变更调用；同键连续调用只落最后一次）。 */
    fun scheduleSave(
        key: DraftKey,
        content: String,
    ) {
        launchWrite(key) {
            delay(debounceMillis)
            repository.save(key, content)
        }
    }

    /** 立即保存（退出编辑页/关闭 Sheet 时调用，不等防抖窗口）。 */
    fun saveNow(
        key: DraftKey,
        content: String,
    ) {
        launchWrite(key) { repository.save(key, content) }
    }

    /** 丢弃草稿（用户主动丢弃 / 提交成功后清除）：取消待写任务 + 删除已存草稿。 */
    fun discard(key: DraftKey) {
        launchWrite(key) { repository.clear(key) }
    }

    private fun launchWrite(
        key: DraftKey,
        block: suspend () -> Unit,
    ) {
        pendingWrites.remove(key)?.cancel()
        val job = scope.launch { writeQuietly(block) }
        pendingWrites[key] = job
        // 完成即摘除映射，避免长会话里按"碰过的键"无限增长；用 (key, job) 二元删除，
        // 防止把后来顶替的新任务误删。
        job.invokeOnCompletion { pendingWrites.remove(key, job) }
    }

    private suspend fun writeQuietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (expected: IOException) {
            // 预期内：写不进去（磁盘满/文件损坏）就放弃这次草稿，不崩 App、不回滚编辑区
        }
    }

    companion object {
        /** 默认防抖窗口（800ms）：用户停顿即落盘，又不至于逐键写盘。 */
        const val DEFAULT_DEBOUNCE_MILLIS = 800L
    }
}
