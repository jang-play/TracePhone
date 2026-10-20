package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.model.BillingCycle
import com.dksystems.tracephone.domain.model.BillingCycleHistory
import com.dksystems.tracephone.domain.model.BillingCycleUsage
import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.domain.preferences.MonthlyLimitPreferences
import com.dksystems.tracephone.domain.repository.UsageRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * 거슬러 올라갈 끝난 주기의 최대 개수.
 *
 * 요금제를 다시 보는 간격이 보통 1년이다. 13개가 되면 같은 달 이름이 두 번 나와
 * 어느 쪽이 작년인지 세어야 한다.
 */
private const val MAX_FINISHED_CYCLES = 12

/**
 * 지난 청구 주기들의 모바일 실적. 한도를 얼마로 잡을지 판단할 근거가 된다.
 *
 * ### 기록이 주기 전체를 덮는 주기만 센다
 * 최초 백필은 주기 중간에서 시작하므로 가장 오래된 주기는 거의 항상 반쪽이다.
 * 반쪽을 섞으면 '주기끼리 견준다'는 목적 자체가 깨진다. 뺀 사실은 화면이 한 줄로 밝힌다.
 *
 * ### 이번 주기는 [GetMonthlyLimitStatusUseCase]와 같은 인자로 읽는다
 * 주기 전체를 넘긴다. [GetMobileUsageForPeriodUseCase]가 아직 오지 않은 날은 조회하지 않으므로
 * 값은 '오늘까지'와 같은데, 같은 인자를 쓰면 **요약 화면 한도 카드와 숫자가 어긋날 자리가 없다.**
 */
class GetBillingCycleHistoryUseCase @Inject constructor(
    private val preferences: MonthlyLimitPreferences,
    private val repository: UsageRepository,
    private val getMobileUsageForPeriod: GetMobileUsageForPeriodUseCase,
    private val clock: Clock
) {

    /** @return 한도를 쓰지 않는 상태면 null. [GetMonthlyLimitStatusUseCase]와 같은 약속이다. */
    suspend operator fun invoke(): BillingCycleHistory? {
        val settings = preferences.settings.first()
        if (!settings.isActive) {
            return null
        }

        val today = LocalDate.now(clock)
        val currentCycle = settings.cycleContaining(today)
        val recordsStartDate = repository.observeEarliestRecordedDate().first()

        val finished = fullyRecordedCyclesBefore(
            currentCycle = currentCycle,
            cycleStartDay = settings.cycleStartDay,
            recordsStartDate = recordsStartDate
        ).map { cycle ->
            BillingCycleUsage(
                cycle = cycle,
                countedThrough = cycle.endDateInclusive,
                mobileBytes = getMobileUsageForPeriod(cycle)
            )
        }

        return BillingCycleHistory(
            current = BillingCycleUsage(
                cycle = currentCycle,
                countedThrough = today,
                mobileBytes = getMobileUsageForPeriod(currentCycle)
            ),
            finished = finished,
            currentLimitBytes = settings.limitBytes,
            recordsStartDate = recordsStartDate
        )
    }

    /**
     * [currentCycle] 앞으로 거슬러 올라가며, 기록이 주기 전체를 덮는 주기만 모은다. 최근이 먼저.
     *
     * 주기 시작일이 기록 시작일보다 앞서는 순간 멈춘다. 그 주기부터는 앞쪽이 비어 있고,
     * 더 거슬러 올라가도 전부 마찬가지다.
     */
    private fun fullyRecordedCyclesBefore(
        currentCycle: UsagePeriod,
        cycleStartDay: Int,
        recordsStartDate: LocalDate?
    ): List<UsagePeriod> {
        if (recordsStartDate == null) {
            return emptyList()
        }

        val cycles = mutableListOf<UsagePeriod>()
        var cycle = BillingCycle.previousPeriod(currentCycle, cycleStartDay)

        while (cycles.size < MAX_FINISHED_CYCLES && !cycle.startDate.isBefore(recordsStartDate)) {
            cycles += cycle
            cycle = BillingCycle.previousPeriod(cycle, cycleStartDay)
        }

        return cycles
    }
}
