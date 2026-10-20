package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.domain.model.NetworkUsageSplit
import com.dksystems.tracephone.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 앱 하나의 사용량을 회선별로, 다시 직접 사용 / 백그라운드 / 구분 없음으로 나눠 관찰한다.
 */
class GetUsageStateSplitUseCase @Inject constructor(
    private val repository: UsageRepository
) {

    operator fun invoke(uid: Int, period: UsagePeriod): Flow<NetworkUsageSplit> =
        repository.observeUsageStateSplit(uid, period)
}
