package com.dksystems.tracephone.domain

import com.dksystems.tracephone.domain.model.UsageAlertSettings
import com.dksystems.tracephone.domain.preferences.UsageAlertPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/**
 * 테스트용 경고 설정 저장소.
 *
 * '오늘 앱마다 마지막으로 알린 배수'는 실제 구현과 같은 규칙을 따른다.
 * 저장된 날짜가 물어본 날짜와 다르면 없는 것으로 본다.
 */
class FakeUsageAlertPreferences(
    initialSettings: UsageAlertSettings = UsageAlertSettings.DEFAULT
) : UsageAlertPreferences {

    private val settingsState = MutableStateFlow(initialSettings)

    private var notifiedDate: LocalDate? = null
    private var notifiedMultiples: Map<Int, Int> = emptyMap()

    override val settings: Flow<UsageAlertSettings> = settingsState

    override suspend fun setEnabled(enabled: Boolean) {
        settingsState.value = settingsState.value.copy(isEnabled = enabled)
    }

    override suspend fun setPresetLimitBytes(limitBytes: Long) {
        settingsState.value = settingsState.value.copy(presetLimitBytes = limitBytes)
    }

    override suspend fun setCustomLimitMegabytes(limitMegabytes: Long) {
        settingsState.value = settingsState.value.copy(customLimitMegabytes = limitMegabytes)
    }

    override suspend fun notifiedMultiplesOn(date: LocalDate): Map<Int, Int> =
        if (notifiedDate == date) notifiedMultiples else emptyMap()

    override suspend fun markNotified(date: LocalDate, notifiedMultiples: Map<Int, Int>) {
        val previous = if (notifiedDate == date) this.notifiedMultiples else emptyMap()
        notifiedDate = date
        this.notifiedMultiples = previous + notifiedMultiples
    }
}
