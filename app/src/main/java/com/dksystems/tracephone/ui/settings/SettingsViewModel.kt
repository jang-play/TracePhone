package com.dksystems.tracephone.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dksystems.tracephone.core.coroutineFailureLogger
import com.dksystems.tracephone.di.ApplicationScope
import com.dksystems.tracephone.domain.appearance.ThemeModeApplier
import com.dksystems.tracephone.domain.model.AppListLayout
import com.dksystems.tracephone.domain.model.MonthlyLimitSettings
import com.dksystems.tracephone.domain.model.ThemeMode
import com.dksystems.tracephone.domain.model.UsageAlertSettings
import com.dksystems.tracephone.domain.model.WidgetAppearance
import com.dksystems.tracephone.domain.model.WidgetTextColor
import com.dksystems.tracephone.domain.preferences.MonthlyLimitPreferences
import com.dksystems.tracephone.domain.preferences.ThemePreferences
import com.dksystems.tracephone.domain.preferences.UsageAlertPreferences
import com.dksystems.tracephone.domain.preferences.UsageViewPreferences
import com.dksystems.tracephone.domain.preferences.WidgetAppearancePreferences
import com.dksystems.tracephone.domain.usecase.EvaluateMonthlyLimitUseCase
import com.dksystems.tracephone.domain.usecase.EvaluateUsageAlertsUseCase
import com.dksystems.tracephone.ui.SUBSCRIPTION_TIMEOUT_MILLIS
import com.dksystems.tracephone.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 설정 화면의 상태와 저장.
 *
 * ### 설정을 바꾸는 일은 화면 수명과 분리한다
 * 설정 저장 뒤에는 판정과 위젯 갱신이 이어지는데, 이 둘은 시스템 조회를 포함해 시간이 걸린다.
 * `viewModelScope`에서 돌리면 사용자가 적용을 누르고 곧바로 뒤로 나갈 때 화면과 함께 취소되어
 * 화면에서는 바꿨는데 위젯은 옛 값이 남는다. **실기기에서 실제로 겪은 문제다.**
 *
 * 값을 읽는 쪽(StateFlow)은 화면이 살아 있는 동안만 필요하므로 그대로 `viewModelScope`를 쓴다.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val usageAlertPreferences: UsageAlertPreferences,
    private val monthlyLimitPreferences: MonthlyLimitPreferences,
    private val evaluateUsageAlerts: EvaluateUsageAlertsUseCase,
    private val evaluateMonthlyLimit: EvaluateMonthlyLimitUseCase,
    private val widgetAppearancePreferences: WidgetAppearancePreferences,
    private val themePreferences: ThemePreferences,
    private val usageViewPreferences: UsageViewPreferences,
    private val themeModeApplier: ThemeModeApplier,
    private val widgetRefresher: WidgetRefresher,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {

    val alertSettings: StateFlow<UsageAlertSettings> = usageAlertPreferences.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = UsageAlertSettings.DEFAULT
        )

    val monthlyLimitSettings: StateFlow<MonthlyLimitSettings> = monthlyLimitPreferences.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = MonthlyLimitSettings.DEFAULT
        )

    val appListLayout: StateFlow<AppListLayout> = usageViewPreferences.appListLayout
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = AppListLayout.DETAILED
        )

    val widgetAppearance: StateFlow<WidgetAppearance> = widgetAppearancePreferences.appearance
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = WidgetAppearance.DEFAULT
        )

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = ThemeMode.SYSTEM
        )

    /**
     * 화면 모드를 바꾼다.
     *
     * 시스템에 알리는 일이 액티비티를 다시 만들게 하므로 `viewModelScope`에서 돌리면
     * 그 재생성에 휩쓸려 저장이 취소될 수 있다. 다른 설정들과 같이 애플리케이션 범위에서 실행한다.
     */
    fun selectThemeMode(mode: ThemeMode) {
        applicationScope.launch {
            themePreferences.setThemeMode(mode)
            themeModeApplier.apply(mode)
        }
    }

    /**
     * 목록 모양을 바꾼다. 요약 화면이 저장소를 계속 관찰하므로 따로 알릴 필요가 없다.
     *
     * 다른 설정과 달리 뒤따르는 일이 없어(판정도 위젯 갱신도 없다) `viewModelScope`면 충분하다.
     */
    fun selectAppListLayout(layout: AppListLayout) {
        viewModelScope.launch(coroutineFailureLogger) {
            usageViewPreferences.setAppListLayout(layout)
        }
    }

    fun selectWidgetTextColor(textColor: WidgetTextColor) {
        applicationScope.launch {
            widgetAppearancePreferences.setTextColor(textColor)
            widgetRefresher.refresh()
        }
    }

    fun selectWidgetTransparency(transparencyPercent: Int) {
        applicationScope.launch {
            widgetAppearancePreferences.setBackgroundTransparencyPercent(transparencyPercent)
            widgetRefresher.refresh()
        }
    }

    fun setMonthlyLimitEnabled(enabled: Boolean) {
        applicationScope.launch {
            monthlyLimitPreferences.setEnabled(enabled)
            if (enabled) {
                evaluateMonthlyLimit()
            }
            widgetRefresher.refresh()
        }
    }

    fun applyMonthlyLimitMegabytes(limitMegabytes: Long) {
        applicationScope.launch {
            monthlyLimitPreferences.setLimitMegabytes(limitMegabytes)
            evaluateMonthlyLimit()
            widgetRefresher.refresh()
        }
    }

    fun applyCycleStartDay(cycleStartDay: Int) {
        applicationScope.launch {
            monthlyLimitPreferences.setCycleStartDay(cycleStartDay)
            evaluateMonthlyLimit()
            widgetRefresher.refresh()
        }
    }

    fun setAlertEnabled(enabled: Boolean) {
        applicationScope.launch {
            usageAlertPreferences.setEnabled(enabled)
            if (enabled) {
                evaluateNow()
            }
        }
    }

    fun setPresetLimitBytes(limitBytes: Long) {
        applicationScope.launch {
            usageAlertPreferences.setPresetLimitBytes(limitBytes)
            evaluateNow()
        }
    }

    /**
     * 직접 입력한 한도를 적용한다.
     *
     * 입력 도중이 아니라 사용자가 적용을 누른 시점에만 저장한다.
     * 글자를 칠 때마다 저장하면 "1"을 친 순간 1MB 한도로 판정이 돌아 알림이 쏟아진다.
     */
    fun applyCustomLimitMegabytes(limitMegabytes: Long) {
        applicationScope.launch {
            usageAlertPreferences.setCustomLimitMegabytes(limitMegabytes)
            evaluateNow()
        }
    }

    /**
     * 설정을 바꾼 직후 바로 판정한다.
     *
     * 주기 워커(15분)나 앱 재시작을 기다리면, 한도를 낮춰 놓고도 아무 일도 일어나지 않아
     * 설정이 먹히지 않는 것처럼 보인다.
     * 같은 앱은 하루 한 번만 알리므로 여러 번 눌러도 알림이 쏟아지지 않는다.
     */
    private suspend fun evaluateNow() {
        evaluateUsageAlerts()
    }
}
