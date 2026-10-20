package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.domain.model.UsageSortOrder
import com.dksystems.tracephone.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 기간별 앱 사용량 목록을 정렬해서 돌려준다.
 *
 * 사용량이 0인 앱은 목록에서 제외한다. 통계에는 트래픽이 전혀 없는 UID도 섞여 들어오는데,
 * 이를 그대로 보여주면 목록이 의미 없는 항목으로 길어진다.
 *
 * **정렬에 따라 더 걸러내는 일은 여기서 하지 않는다.** 합계 카드가 이 목록을 더해 만들므로,
 * 여기서 정렬별로 거르면 **정렬을 바꿀 때마다 그 기간의 Wi-Fi 총량이 달라진다.**
 * 실기기에서 5.49 GB와 6.38 GB를 오갔다. 그 판단은
 * [com.dksystems.tracephone.ui.summary.UsageSummaryUiState.visibleAppUsages]가 맡는다.
 */
class GetAppUsageUseCase @Inject constructor(
    private val repository: UsageRepository
) {

    operator fun invoke(
        period: UsagePeriod,
        sortOrder: UsageSortOrder
    ): Flow<List<AppUsage>> =
        repository.observeAppUsage(period).map { usages ->
            usages
                .filter { it.totalBytes > 0L }
                .sortedByDescending { sortOrder.sortKeyOf(it) }
        }
}
