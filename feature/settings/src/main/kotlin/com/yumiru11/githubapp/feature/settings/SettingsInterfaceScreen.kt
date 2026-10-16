package com.yumiru11.githubapp.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import com.yumiru11.githubapp.core.designsystem.component.AppSettingsGroup
import com.yumiru11.githubapp.core.designsystem.component.AppSettingsGroupScope
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import kotlin.math.roundToInt

/** 滑杆取值范围/步进（与 DataStore 契约一致：0.5–1.5）。 */
private val SCALE_RANGE = 0.5f..1.5f

private const val SCALE_STEPS = 9

/**
 * 界面与动效（**L3 叶子页**）：形状与动效 / 毛玻璃 / 背景图。
 *
 * 滑杆放**文本列**（`supportingContent`）而不是尾部槽：尾部槽给滑杆会把主标题挤成竖排
 * （出图实测），放文本列才拿得到整行宽度、与右侧数值对齐。
 *
 * 每个分组拆成独立 section 函数（detekt `LongMethod` 80 行阈值；仓库既有做法）。
 */
@Composable
fun SettingsInterfaceScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsPageScaffold(
        title = stringResource(R.string.settings_interface),
        onBackClick = onBackClick,
    ) {
        item { ShapeMotionSection(uiState = uiState, viewModel = viewModel) }
        item { GlassSection(uiState = uiState, viewModel = viewModel) }
        item { BackgroundSection(uiState = uiState, viewModel = viewModel) }
    }
}

/** 形状与动效：圆角强度 / 动画强度（行内滑杆 + 行尾数值）/ 列表入场动画。 */
@Composable
private fun ShapeMotionSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    AppSettingsGroup(title = stringResource(R.string.settings_group_shape)) {
        item(
            headlineContent = { Text(stringResource(R.string.settings_corner_scale)) },
            trailingContent = {
                Text(stringResource(R.string.settings_scale_percent, (uiState.cornerScale * 100).roundToInt()))
            },
            supportingContent = {
                Slider(
                    value = uiState.cornerScale,
                    onValueChange = viewModel::setCornerScale,
                    valueRange = SCALE_RANGE,
                    steps = SCALE_STEPS,
                )
            },
        )
        item(
            headlineContent = { Text(stringResource(R.string.settings_motion_scale)) },
            trailingContent = {
                Text(stringResource(R.string.settings_scale_percent, (uiState.motionScale * 100).roundToInt()))
            },
            supportingContent = {
                Slider(
                    value = uiState.motionScale,
                    onValueChange = viewModel::setMotionScale,
                    valueRange = SCALE_RANGE,
                    steps = SCALE_STEPS,
                )
            },
        )
        switchItem(
            checked = uiState.staggerEnabled,
            onCheckedChange = viewModel::setStaggerEnabled,
            headlineContent = { Text(stringResource(R.string.settings_stagger)) },
            supportingContent = { Text(stringResource(R.string.settings_stagger_desc)) },
        )
    }
}

/** 毛玻璃：总开关 + 四个允许点位；点位开关在总开关关 / OLED / 高对比下整组置灰。 */
@Composable
private fun GlassSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    AppSettingsGroup(title = stringResource(R.string.settings_group_glass)) {
        switchItem(
            checked = uiState.blurEnabled,
            onCheckedChange = viewModel::setBlurEnabled,
            headlineContent = { Text(stringResource(R.string.settings_blur)) },
            supportingContent = { Text(stringResource(R.string.settings_blur_desc)) },
        )
        switchItem(
            checked = uiState.glassTopBar,
            onCheckedChange = viewModel::setGlassTopBar,
            enabled = uiState.glassPerItemEnabled,
            headlineContent = { Text(stringResource(R.string.settings_glass_top_bar)) },
        )
        switchItem(
            checked = uiState.glassBottomBar,
            onCheckedChange = viewModel::setGlassBottomBar,
            enabled = uiState.glassPerItemEnabled,
            headlineContent = { Text(stringResource(R.string.settings_glass_bottom_bar)) },
        )
        switchItem(
            checked = uiState.glassPanel,
            onCheckedChange = viewModel::setGlassPanel,
            enabled = uiState.glassPerItemEnabled,
            headlineContent = { Text(stringResource(R.string.settings_glass_panel)) },
        )
        switchItem(
            checked = uiState.glassBottomSheet,
            onCheckedChange = viewModel::setGlassBottomSheet,
            enabled = uiState.glassPerItemEnabled,
            headlineContent = { Text(stringResource(R.string.settings_glass_bottom_sheet)) },
        )
    }
}

/**
 * 背景图：选图走系统 Photo Picker（Android 13+ 本身即 Material You 风格；低版本由 androidx
 * 回移实现接管，**不申请 READ_MEDIA_IMAGES**——用户选哪张就授哪张）。
 *
 * 选完必须 takePersistableUriPermission，否则进程重启后 URI 失效、背景图变空白。
 */
@Composable
private fun BackgroundSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                viewModel.setBackgroundImageUri(uri.toString())
            }
        }

    AppSettingsGroup(title = stringResource(R.string.settings_group_background)) {
        backgroundImageItem(
            imageUri = uiState.backgroundImageUri,
            opacity = uiState.backgroundOpacity,
            onPickClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onClear = { viewModel.setBackgroundImageUri(null) },
            onOpacityChange = viewModel::setBackgroundOpacity,
        )
    }
}

/** 背景图条目（组作用域函数：复用调用方的卡片，不另起第二张卡）。 */
private fun AppSettingsGroupScope.backgroundImageItem(
    imageUri: String?,
    opacity: Float,
    onPickClick: () -> Unit,
    onClear: () -> Unit,
    onOpacityChange: (Float) -> Unit,
) {
    item(
        headlineContent = { Text(stringResource(R.string.settings_background)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.listSpacing)) {
                Text(
                    text =
                        if (imageUri == null) {
                            stringResource(R.string.settings_background_none)
                        } else {
                            stringResource(R.string.settings_background_selected)
                        },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing.s)) {
                    FilledTonalButton(onClick = onPickClick) {
                        Text(
                            text =
                                stringResource(
                                    if (imageUri == null) {
                                        R.string.settings_background_pick
                                    } else {
                                        R.string.settings_background_change
                                    },
                                ),
                        )
                    }
                    if (imageUri != null) {
                        TextButton(onClick = onClear) {
                            Text(
                                text = stringResource(R.string.settings_background_clear),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                if (imageUri != null) {
                    Text(
                        text = stringResource(R.string.settings_background_opacity, (opacity * 100).roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = opacity,
                        onValueChange = onOpacityChange,
                        valueRange =
                            UserPreferencesRepository.MIN_BACKGROUND_OPACITY..UserPreferencesRepository.MAX_BACKGROUND_OPACITY,
                    )
                }
            }
        },
    )
}
