package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.model.MonthlyLimitStatus
import com.dksystems.tracephone.domain.preferences.MonthlyLimitPreferences
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * 이번 청구 주기의 한도 사용 현황을 구한다.
 *
 * 화면과 위젯이 같은 경로를 쓰게 해 두 곳의 숫자가 어긋나지 않게 한다.
 */
class GetMonthlyLimitStatusUseCase @Inject constructor(
    private val preferences: MonthlyLimitPreferences,
    private val getMobileUsageForPeriod: GetMobileUsageForPeriodUseCase,
    private val clock: Clock
) {

    /** @return 한도를 쓰지 않는 상태면 null */
    suspend operator fun invoke(): MonthlyLimitStatus? {
        val settings = preferences.settings.first()
        if (!settings.isActive) {
            return null
        }

        val today = LocalDate.now(clock)
        val cycle = settings.cycleContaining(today)

        return MonthlyLimitStatus(
            settings = settings,
            cycle = cycle,
            usedBytes = getMobileUsageForPeriod(cycle),
            today = today
        )
    }
}
