package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.model.MonthlyLimitStatus
import com.dksystems.tracephone.domain.preferences.MonthlyLimitPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 요금제 한도 현황을 화면에서 관찰한다.
 *
 * 설정이 바뀌면 다시 계산한다. 사용량 자체는 [GetMonthlyLimitStatusUseCase]가
 * 오늘치를 시스템에서 직접 읽으므로, 화면에 다시 들어오면 그 시점의 값이 나온다.
 *
 * 설정 관찰과 현황 계산을 한곳에 묶어 화면이 두 가지를 각각 들고 있지 않게 한다.
 */
class ObserveMonthlyLimitStatusUseCase @Inject constructor(
    private val preferences: MonthlyLimitPreferences,
    private val getMonthlyLimitStatus: GetMonthlyLimitStatusUseCase
) {

    /** @return 한도를 쓰지 않는 상태면 null을 내보낸다. */
    operator fun invoke(): Flow<MonthlyLimitStatus?> =
        preferences.settings.map { getMonthlyLimitStatus() }
}
