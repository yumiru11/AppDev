package com.yumiru11.githubapp.core.datastore.draft

/**
 * 草稿仓库（plan.md §4.6「草稿 | DataStore/Room」；本仓落地为 **Preferences DataStore**）。
 *
 * ## 为什么是 DataStore 而不是 Room（选型理由，勿凭直觉改）
 *
 * plan.md 对草稿的介质是**两可**的：§3.1 的表把「本地缓存（含草稿/历史）」列在 Room，
 * §4.6 则写「草稿 | DataStore/Room」。落到本仓实际约束后取 DataStore：
 * 1. **数据形状**：草稿是「少量条目 + 短文本 + 按固定键点查」，没有列表 UI、没有范围/全文查询、
 *    没有多表关联 —— Room 的关系能力用不上（对比：搜索历史/Issue 分页缓存需要排序与游标查询，故在 Room）。
 * 2. **量级有硬上限**：[DefaultDraftRepository.MAX_DRAFT_COUNT] × [DefaultDraftRepository.MAX_DRAFT_CHARS]
 *    即最坏磁盘占用（≈ 2.4 MB），且每条草稿是整存整取，天然契合 Preferences 的"整文件重写"语义。
 *    反过来说：**超长文本（如 > MAX_DRAFT_CHARS 的大文件）本就不该进草稿**，见 [save] 契约。
 * 3. **模块门禁**：`core:database` 不在 `coverageThresholds` 里、且其 Robolectric 测试不产 JaCoCo 数据
 *    （#181 沙箱类不计覆盖）——把新逻辑放那里会让 diff 覆盖率门禁把每一行都判成未覆盖
 *    （口径见 build.gradle.kts 的 diffCoverageCheck 注释：文件不在报告里 = 全部新增行未覆盖）。
 *    `core:datastore` 有实测覆盖率基线与纯 JVM 的 DataStore 测试夹具（`UserPreferencesRepositoryTest` 先例）。
 * 4. **零迁移风险**：DataStore 无 schema 版本，Room 需 +1 版本 + migration + schema JSON 重导。
 *
 * 代价（已知并接受）：Preferences 每次写入重写整个文件；[DraftAutoSaver] 的防抖把写频压到
 * 「用户停顿后一次」，且单条/总条数都有上限，故此代价可忽略。
 *
 * ## 契约（三个关键分支的存储侧语义）
 *
 * - [save] **空内容 = 无草稿**：`content.isBlank()` 不落盘，并清除同键旧草稿
 *   （用户清空编辑区后不该再看到"已恢复草稿"）。调用方无需自己判空。
 * - [save] **超长内容不落盘**：超过 [DefaultDraftRepository.MAX_DRAFT_CHARS] 时清除同键旧草稿。
 *   故意**不截断保存**：截断会让用户恢复出"看着像自己的、其实被砍过"的内容，比没有草稿更危险。
 * - [clear] **幂等**：无草稿时调用是 no-op。
 *
 * 读写都只在 `Dispatchers.IO` 上经 DataStore 串行执行（Preferences DataStore 自带单写者保证）。
 */
interface DraftRepository {
    /** 读取草稿（无草稿返回 null）。 */
    suspend fun load(key: DraftKey): String?

    /** 写入草稿（空内容/超长内容 → 清除，见类 KDoc 契约）。 */
    suspend fun save(
        key: DraftKey,
        content: String,
    )

    /** 清除草稿（提交成功 / 用户主动丢弃 / 回到基线文本）。 */
    suspend fun clear(key: DraftKey)
}
