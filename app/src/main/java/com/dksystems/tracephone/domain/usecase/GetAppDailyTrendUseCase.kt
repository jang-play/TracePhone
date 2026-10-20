package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.model.DailyUsagePoint
import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 앱 하나의 일자별 사용량 추이를 돌려준다.
 *
 * 기록이 없는 날도 0으로 채워져 오므로 그래프에 빈 구간이 생기지 않는다.
 */
class GetAppDailyTrendUseCase @Inject constructor(
    private val repository: UsageRepository
) {

    operator fun invoke(uid: Int, period: UsagePeriod): Flow<List<DailyUsagePoint>> =
        repository.observeDailyTrend(uid, period)
}
