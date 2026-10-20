package com.dksystems.tracephone.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.core.SyncConstants
import com.dksystems.tracephone.core.isIgnoringBatteryOptimizations
import com.dksystems.tracephone.domain.model.UsageAlertSettings

/**
 * 사용량 경고 설정 화면.
 *
 * 알림이 실제로 뜨려면 앱 설정 외에 **시스템 알림 권한**과 **제조사 절전 예외**까지
 * 갖춰져야 한다. 셋 중 하나라도 빠지면 사용자는 "왜 알림이 안 오지"만 겪게 되므로
 * 상태를 화면에 드러내고 해결 경로를 함께 둔다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onCycleHistorySelected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.alertSettings.collectAsStateWithLifecycle()
    val monthlyLimitSettings by viewModel.monthlyLimitSettings.collectAsStateWithLifecycle()
    val widgetAppearance by viewModel.widgetAppearance.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val appListLayout by viewModel.appListLayout.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MonthlyLimitCard(
                settings = monthlyLimitSettings,
                actions = MonthlyLimitCardActions(
                    onEnabledChange = viewModel::setMonthlyLimitEnabled,
                    onLimitApplied = viewModel::applyMonthlyLimitMegabytes,
                    onCycleStartDayApplied = viewModel::applyCycleStartDay,
                    onHistorySelected = onCycleHistorySelected
                )
            )

            ThemeModeCard(
                selected = themeMode,
                onSelect = viewModel::selectThemeMode
            )

            AppListLayoutCard(
                selected = appListLayout,
                onSelect = viewModel::selectAppListLayout
            )

            WidgetAppearanceCard(
                appearance = widgetAppearance,
                onTextColorSelected = viewModel::selectWidgetTextColor,
                onTransparencySelected = viewModel::selectWidgetTransparency
            )

            AlertToggleCard(
                isEnabled = settings.isEnabled,
                onEnabledChange = viewModel::setAlertEnabled
            )

            if (settings.isEnabled) {
                DailyLimitCard(
                    settings = settings,
                    onPresetSelected = viewModel::setPresetLimitBytes,
                    onCustomLimitApplied = viewModel::applyCustomLimitMegabytes
                )
            }

            // 알림 권한과 절전 예외는 두 기능 모두에 필요하다.
            // 한쪽만 켠 사용자도 안내를 봐야 "왜 알림이 안 오지"를 겪지 않는다.
            if (settings.isEnabled || monthlyLimitSettings.isEnabled) {
                NotificationPermissionCard()

                BackgroundRestrictionNotice()
            }

            AppUpdateCard()
        }
    }
}

@Composable
private fun AlertToggleCard(isEnabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_alert_switch_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.settings_alert_switch_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isEnabled, onCheckedChange = onEnabledChange)
        }
    }
}

/**
 * 한도 선택 카드.
 *
 * 목록에서 고른 값이 기본이고, 직접 입력한 값이 0보다 크면 그쪽이 우선한다.
 * 지금 무엇이 적용되는지 헷갈리기 쉬우므로 맨 아래에 실제 적용 값을 적어준다.
 */
@Composable
private fun DailyLimitCard(
    settings: UsageAlertSettings,
    onPresetSelected: (Long) -> Unit,
    onCustomLimitApplied: (Long) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.settings_limit_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = stringResource(R.string.settings_limit_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Column(modifier = Modifier.selectableGroup()) {
                UsageAlertSettings.PRESET_CHOICES_BYTES.forEach { limitBytes ->
                    PresetLimitRow(
                        limitBytes = limitBytes,
                        isSelected = limitBytes == settings.presetLimitBytes,
                        isEnabled = !settings.usesCustomLimit,
                        onSelect = { onPresetSelected(limitBytes) }
                    )
                }
            }

            CustomLimitInput(
                storedLimitMegabytes = settings.customLimitMegabytes,
                onApply = onCustomLimitApplied,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Text(
                text = stringResource(
                    R.string.settings_limit_effective,
                    ByteFormatter.formatCompact(settings.effectiveLimitBytes)
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * 미리 정해 둔 한도 한 줄.
 *
 * `selectable`을 줄 전체에 걸어 라디오 버튼뿐 아니라 글자를 눌러도 골라지게 하고,
 * `Role.RadioButton`을 함께 넘겨 스크린 리더가 '선택됨/선택 안 됨'으로 읽게 한다.
 * [ThemeModeCard]의 줄과 같은 모양이다.
 *
 * 직접 입력값을 쓰는 동안에는 **정말로 누를 수 없게** 한다. 예전에는 회색으로 흐려 놓고도
 * 누르면 값이 조용히 바뀌었다. 보이는 것과 실제가 어긋나면 눌러 놓고도 바뀐 줄 모른다.
 * 되돌리는 방법은 바로 아래 입력 칸이 이미 안내하고 있다 — 0을 넣으면 여기서 고른 값을 쓴다.
 */
@Composable
private fun PresetLimitRow(
    limitBytes: Long,
    isSelected: Boolean,
    isEnabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                enabled = isEnabled,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = isSelected, onClick = null, enabled = isEnabled)
        Text(
            text = ByteFormatter.formatCompact(limitBytes),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isEnabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * 직접 입력 칸(MB).
 *
 * 입력 도중이 아니라 적용을 눌렀을 때만 반영한다. 그 이유는 [AppliedTextInput]에 적어 두었다.
 */
@Composable
private fun CustomLimitInput(
    storedLimitMegabytes: Long,
    onApply: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    AppliedTextInput(
        label = stringResource(R.string.settings_custom_limit_label),
        hint = stringResource(R.string.settings_custom_limit_hint),
        storedText = storedLimitMegabytes.toString(),
        isTypingAllowed = { text ->
            // 숫자만 받는다. 부호나 소수점이 섞이면 해석이 갈린다.
            text.length <= MAX_CUSTOM_LIMIT_DIGITS && text.all { it.isDigit() }
        },
        onApply = { text -> text.toLongOrNull()?.let(onApply) },
        modifier = modifier
    )
}

/** 입력 자릿수 상한. 지나치게 큰 값이 들어와 계산이 넘치는 것을 막는다. */
private const val MAX_CUSTOM_LIMIT_DIGITS = 7

/**
 * 시스템 설정에서 바뀌는 값을 화면이 다시 보일 때마다 읽어 온다.
 *
 * 알림 허용도 배터리 최적화 예외도 우리 화면이 아니라 시스템 설정에서 바뀐다.
 * 사용자가 그쪽에서 바꾸고 돌아와도 알려 주는 것이 없으므로 직접 다시 확인하는 수밖에 없다.
 *
 * [MutableState]로 돌려주는 이유는 알림 권한처럼 **요청 결과로 곧바로 갱신해야 하는** 경우가
 * 있기 때문이다. 그때는 화면이 다시 보이기를 기다릴 필요가 없다.
 */
@Composable
private fun rememberSystemState(read: () -> Boolean): MutableState<Boolean> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentRead by rememberUpdatedState(read)
    val state = remember { mutableStateOf(read()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = currentRead()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

/**
 * 알림 권한 상태.
 *
 * Android 13부터는 런타임 권한이라 앱이 요청해야 한다. 그 이전 버전에서도 사용자가
 * 알림을 꺼둘 수 있으므로, 버전과 무관하게 `areNotificationsEnabled()`로 실제 상태를 본다.
 */
@Composable
private fun NotificationPermissionCard() {
    val context = LocalContext.current
    val areNotificationsEnabled = rememberSystemState {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        areNotificationsEnabled.value = isGranted
    }

    if (areNotificationsEnabled.value) {
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_notification_permission_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_notification_permission_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // 그 이전 버전에는 요청할 권한이 없다. 앱 알림 설정으로 보낸다.
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.settings_notification_permission_action))
            }
        }
    }
}

/**
 * 절전 기능 안내. **지금 예외인지 아닌지를 함께 보여준다.**
 *
 * 예전에는 예외 여부와 무관하게 늘 같은 말을 했다. 그래서 사용자는 이미 해 두었는지 알 수 없고,
 * 더 나쁘게는 **앱을 지웠다 깔면 예외가 조용히 풀리는데** 그 사실을 알아챌 방법이 없었다.
 * 실제로 이 앱도 한 번 예외로 등록됐다가 재설치 때 풀린 적이 있다.
 */
@Composable
private fun BackgroundRestrictionNotice() {
    val context = LocalContext.current
    val isUnrestricted by rememberSystemState { context.isIgnoringBatteryOptimizations() }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_battery_title),
                style = MaterialTheme.typography.titleMedium
            )
            BatteryOptimizationStatus(isUnrestricted = isUnrestricted)
            HorizontalDivider()
            Text(
                text = alertIntervalNotice(isUnrestricted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { context.startAppDetailsSettings() }) {
                Text(stringResource(R.string.settings_battery_action))
            }
        }
    }
}

/**
 * 예외 여부 한 줄과, 예외가 아닐 때만 붙는 해결 방법.
 *
 * 예외일 때는 색으로 강조하지 않는다. 이 앱에서 초록은 이미 '직접 사용'이라는 다른 뜻이 있고,
 * 잘 되어 있는 상태까지 색을 쓰면 정작 문제가 있을 때 눈에 띄지 않는다.
 */
@Composable
private fun BatteryOptimizationStatus(isUnrestricted: Boolean) {
    Text(
        text = stringResource(
            if (isUnrestricted) {
                R.string.settings_battery_status_unrestricted
            } else {
                R.string.settings_battery_status_restricted
            }
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isUnrestricted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.error
        }
    )

    if (!isUnrestricted) {
        Text(
            text = stringResource(R.string.settings_battery_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 경고를 얼마나 자주 확인하는지 알린다. **상수에서 직접 끌어온다.**
 *
 * 예전에는 문구에 "15분"을 그대로 적어 두어 주기를 바꿔도 화면이 따라오지 않았다.
 * 이제는 예외 여부에 따라 실제로 쓰이는 값이 그대로 보인다.
 */
@Composable
private fun alertIntervalNotice(isUnrestricted: Boolean): String = if (isUnrestricted) {
    stringResource(
        R.string.settings_alert_interval_fast,
        SyncConstants.FAST_ALERT_CHECK_INTERVAL_MINUTES.toInt()
    )
} else {
    stringResource(
        R.string.settings_alert_interval_throttled,
        SyncConstants.ALERT_CHECK_INTERVAL_MINUTES.toInt()
    )
}

/**
 * 앱 정보 화면을 연다. 배터리 항목이 이 화면 안에 있다.
 *
 * 예외를 곧바로 요청하는 대화상자(`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)를 쓰지 않는다.
 * 그쪽은 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 권한이 필요한데 Play 정책의 제한 목록에 있어
 * 배포 시 별도 심사를 받는다. 한 화면 더 거치는 대신 그 위험을 지지 않는다.
 */
private fun Context.startAppDetailsSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
