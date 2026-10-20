package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.model.MonthlyLimitStatus
import com.dksystems.tracephone.domain.model.WidgetUsage
import com.dksystems.tracephone.domain.preferences.MonthlyLimitPreferences
import com.dksystems.tracephone.domain.repository.UsageRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * 위젯이 그릴 값을 한 번에 모은다.
 *
 * 사용량은 화면과 같은 [UsageRepository.readMobileUsageForPeriod]로 구한다.
 * 위젯이 따로 계산하면 홈 화면과 앱 안의 숫자가 달라지고, 어느 쪽이 맞는지 알 수 없게 된다.
 *
 * 한도를 쓰지 않아도 사용량은 보여준다. 위젯을 올려 둔 사람은 숫자를 보려는 것이지
 * 설정하라는 안내를 보려는 것이 아니다.
 */
class GetWidgetUsageUseCase @Inject constructor(
    private val preferences: MonthlyLimitPreferences,
    private val getMobileUsageForPeriod: GetMobileUsageForPeriodUseCase,
    private val repository: UsageRepository,
    private val clock: Clock
) {

    suspend operator fun invoke(topAppCount: Int): WidgetUsage {
        val settings = preferences.settings.first()
        val today = LocalDate.now(clock)
        val cycle = settings.cycleContaining(today)
        val mobileBytes = getMobileUsageForPeriod(cycle)

        return WidgetUsage(
            cycle = cycle,
            mobileBytes = mobileBytes,
            limitStatus = if (settings.isActive) {
                MonthlyLimitStatus(
                    settings = settings,
                    cycle = cycle,
                    usedBytes = mobileBytes,
                    today = today
                )
            } else {
                null
            },
            topApps = repository.readTopMobileApps(cycle, topAppCount)
        )
    }
}
