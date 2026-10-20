package com.dksystems.tracephone.domain

import com.dksystems.tracephone.domain.model.MonthlyLimitSettings
import com.dksystems.tracephone.domain.model.MonthlyLimitThreshold
import com.dksystems.tracephone.domain.preferences.MonthlyLimitPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/**
 * 설정값만 돌려주는 테스트용 한도 저장소.
 *
 * `EvaluateMonthlyLimitUseCaseTest`는 "이미 알린 지점"까지 기억하는 자체 Fake를 품고 있다.
 * 알림 판정이 그 규칙 자체를 검증하기 때문이다. 설정만 필요한 쪽은 여기를 쓴다 —
 * 알림 기록까지 흉내 내면 무엇을 확인하는 테스트인지 흐려진다.
 */
class FakeMonthlyLimitPreferences(
    settings: MonthlyLimitSettings
) : MonthlyLimitPreferences {

    private val settingsFlow = MutableStateFlow(settings)

    override val settings: Flow<MonthlyLimitSettings> = settingsFlow

    override suspend fun setEnabled(enabled: Boolean) {
        settingsFlow.value = settingsFlow.value.copy(isEnabled = enabled)
    }

    override suspend fun setLimitMegabytes(limitMegabytes: Long) {
        settingsFlow.value = settingsFlow.value.copy(limitMegabytes = limitMegabytes)
    }

    override suspend fun setCycleStartDay(cycleStartDay: Int) {
        settingsFlow.value = settingsFlow.value.copy(cycleStartDay = cycleStartDay)
    }

    override suspend fun notifiedThresholdsIn(cycleStart: LocalDate): Set<MonthlyLimitThreshold> =
        emptySet()

    override suspend fun markNotified(cycleStart: LocalDate, threshold: MonthlyLimitThreshold) = Unit
}
