package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.material3.MotionScheme

/**
 * M3 Expressive 动效方案的单一入口（ui-design §4.5；决策见
 * `docs/adr/0008-material3-alpha18-pin.md`）。
 *
 * ## 与 [AppMotion] / [LocalMotionScale] 的分工（同一套动效的两种表达）
 * | 层 | 令牌 | 表达 | 管什么 |
 * |---|---|---|---|
 * | 1 转场 | [AppMotion] | **显式时长**（ms）+ `Easing` | 页面进入/退出、fade-through 这类「A→B 有始有终」的编排 |
 * | 2 组件 | 本对象 | **弹簧物理**（dampingRatio / stiffness） | 组件内可中断的位移/尺寸/形状/颜色（按压回弹、形变、展开） |
 *
 * 两者**类型上不可互转**：spring 没有时长，任何「把 spring 折算成 ms」的做法都是把
 * 物理量退化成近似值，等于放弃 Expressive 的核心收益（可中断 + 设备自适应）。因此
 * 官方 [MotionScheme] 与 [AppMotion] **并存不互替**——M3 自己也明确 easing/duration
 * 仍然负责转场，6 个 spec 只表达组件动效。
 *
 * ## ⚠️ spring 不受 [LocalMotionScale] 影响（重要约束）
 * [LocalMotionScale] 只被 [AppMotion.scaledDuration] 消费，作用对象是**时长**。
 * 本对象返回的 spec 是 Compose 弹簧，**没有任何缩放入口**；调用方若直接把它交给
 * `animate*AsState` / `AnimatedContent`，设置页「动画强度」滑杆与系统动画缩放都
 * **管不到它**。
 *
 * 系统侧「移除动画」由 Compose 内建的 `MotionDurationScale` 兜底（整条动画被时间
 * 缩放，缩放为 0 时下一帧结束），所以**系统**的减弱动画是安全的；但**项目自己的
 * 设置页滑杆不在此列**。故约定：
 *
 * > 可感知的弹性动效，调用方必须自行判定缩放为 0 时**跳过动画、直接落终态**
 * > （参见 [CardGroup] 的 `if (motionScale <= NO_MOTION_SCALE) snap() else spring(...)`
 * > 写法），或统一经 [forMotionScale] 取值。
 *
 * [forMotionScale] 把这条约定做进令牌层：缩放 ≤ 0 时返回 [standard]，
 * 把「弹性」降级为「零回弹弹簧」——注意这仍**不是**「不动画」，真正的即时落位
 * 依然要由调用方按上面的约定处理。
 *
 * ## 单例保证（CompositionLocal 语义）
 * [standard] / [expressive] 直接委托官方 `MotionScheme.standard()` /
 * `MotionScheme.expressive()`，二者返回 material3 内部的
 * `StandardMotionSchemeImpl.INSTANCE` / `ExpressiveMotionSchemeImpl.INSTANCE`
 * **单例对象**（1.5.0-alpha18 字节码实测：`getstatic ...INSTANCE` + `areturn`，
 * 无 `new`）。因此可以安全地逐次调用并直接交给
 * `MaterialTheme(motionScheme = …)` —— 引用稳定，不会因为逐帧新建实例而让
 * `LocalMotionScheme` 的值每帧变化、进而整棵子树失效。
 * [AppMotionSchemeTest] 锁死了这条契约。
 *
 * ## 为什么不用自己复刻 spring 常量
 * 本对象直接委托官方实现，而不是在项目内写死 `dampingRatio/stiffness`：
 * 这样 alpha 期的数值修正能自动跟进，也不存在「项目常量与官方漂移」的隐性 bug。
 * （官方 `ExpressiveMotionTokens` 数值表在 1.5.0-alpha18 **仍是 Kotlin-`internal`**
 * ——字节码层 public 但 Kotlin 元数据层 internal，符号引用会编译失败，故测试改为
 * 断言具体数值并在 `AppMotionSchemeTest` 注明来源。）
 */
object AppMotionScheme {
    /**
     * 缩放 ≤ 0（无障碍「移除动画」/ 开发者选项动画关闭 / 设置页滑杆归零）判定为无动效。
     * 与 [CardGroup] 的同名私有常量语义一致。
     */
    private const val NO_MOTION_SCALE: Float = 0f

    /** 标准动效方案：零回弹弹簧（effects dampingRatio = 1），适合克制、工具型场景。 */
    fun standard(): MotionScheme = MotionScheme.standard()

    /** Expressive 动效方案：空间类 spring 带低阻尼回弹（0.6~0.8），M3E 的推荐默认。 */
    fun expressive(): MotionScheme = MotionScheme.expressive()

    /**
     * 按生效动效缩放取方案（纯函数，可单测）。
     *
     * @param motionScale 生效缩放 = `min(设置页滑杆, 系统动画时长缩放)`，见
     *   [resolveEffectiveMotionScale]；本项目经 `AppTheme(motionScale = …)` 下发
     *   到 [LocalMotionScale]，本函数由 `AppTheme` 调用一次。
     * @return 缩放 ≤ 0 时 [standard]（去回弹），否则 [expressive]。
     */
    fun forMotionScale(motionScale: Float): MotionScheme = if (motionScale <= NO_MOTION_SCALE) standard() else expressive()
}
