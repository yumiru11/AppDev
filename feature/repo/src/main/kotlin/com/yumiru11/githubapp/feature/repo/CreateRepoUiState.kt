package com.yumiru11.githubapp.feature.repo

/**
 * 新建仓库页 UI 状态（L04）。
 *
 * 表单字段 + 实时校验结果 + 提交中标志 + 创建成功后的落点（[created] 非空即导航）。
 * 校验是纯函数 [RepoNameValidator]，ViewModel 只缓存结果，UI 只做 stringResource 映射。
 */
data class CreateRepoUiState(
    val name: String = "",
    val description: String = "",
    val isPrivate: Boolean = false,
    val autoInit: Boolean = true,
    val isSubmitting: Boolean = false,
    /** 当前名的校验错误（null = 合法/未触摸） */
    val nameError: RepoNameError? = null,
    /** 创建成功的新仓库（非空 → UI 导航到仓库详情） */
    val created: RepoRef? = null,
) {
    /** 提交按钮可用性：名校验通过（按 trim 后判定，容忍粘贴带来的首尾空白）且无进行中的提交 */
    val canSubmit: Boolean
        get() = !isSubmitting && RepoNameValidator.isValid(name.trim())
}

/** 新仓库定位（owner/name 来自创建响应的 owner.login 与 name）。 */
data class RepoRef(
    val owner: String,
    val name: String,
)

/**
 * 新建仓库失败事件（UI 层 stringResource 映射文案，ViewModel 不产文案）。
 */
sealed interface CreateRepoEvent {
    /** 422：仓库名已存在或非法 */
    data object NameTaken : CreateRepoEvent

    /** 403：无权限（配额/组织策略） */
    data object PermissionDenied : CreateRepoEvent

    /** 网络/未知错误 */
    data object Failed : CreateRepoEvent
}
