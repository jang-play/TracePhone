package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.alert.MonthlyLimitNotifier
import com.dksystems.tracephone.domain.model.MonthlyLimitThreshold
import com.dksystems.tracephone.domain.preferences.MonthlyLimitPreferences
import javax.inject.Inject

/**
 * 요금제 한도를 넘겼는지 판정하고, 아직 알리지 않은 지점이면 알린다.
 *
 * 지점마다 주기당 한 번만 알린다. 80%를 지나 100%에 닿으면 두 번 알리지만,
 * 같은 지점으로 다시 알리지는 않는다.
 *
 * 로그는 두지 않는다. domain은 안드로이드에 기대지 않아야 순수 단위 테스트가 가능하다.
 * 실행 결과 기록은 이 use case를 부르는 쪽이 담당한다.
 */
class EvaluateMonthlyLimitUseCase @Inject constructor(
    private val getMonthlyLimitStatus: GetMonthlyLimitStatusUseCase,
    private val preferences: MonthlyLimitPreferences,
    private val notifier: MonthlyLimitNotifier
) {

    /** @return 이번에 알린 지점. 알릴 것이 없으면 null. */
    suspend operator fun invoke(): MonthlyLimitThreshold? {
        val status = getMonthlyLimitStatus() ?: return null
        val threshold = status.reachedThreshold() ?: return null

        val alreadyNotified = preferences.notifiedThresholdsIn(status.cycle.startDate)
        if (threshold in alreadyNotified) {
            return null
        }

        notifier.notifyThresholdReached(status, threshold)
        preferences.markNotified(status.cycle.startDate, threshold)
        return threshold
    }
}
