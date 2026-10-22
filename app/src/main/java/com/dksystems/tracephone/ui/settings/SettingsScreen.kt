package com.dksystems.tracephone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dksystems.tracephone.R
import com.dksystems.tracephone.ui.backup.UsageBackupCard

/**
 * 설정 화면.
 *
 * 카드를 **쌓는 일만** 한다. 각 카드의 내용은 저마다 파일을 따로 두는데, 고치는 이유가
 * 서로 다르기 때문이다. 알림 문구를 손보려고 이 파일을 열 일이 없어야 한다.
 *
 * 알림이 실제로 뜨려면 앱 설정 외에 **시스템 알림 권한**과 **제조사 절전 예외**까지
 * 갖춰져야 한다. 셋 중 하나라도 빠지면 사용자는 "왜 알림이 안 오지"만 겪게 되므로
 * [NotificationPermissionCard]와 [BackgroundRestrictionNotice]가 그 상태를 드러낸다.
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

            UsageBackupCard()

            AppUpdateCard()
        }
    }
}
