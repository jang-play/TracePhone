package com.dksystems.tracephone.domain.preferences

import com.dksystems.tracephone.domain.model.AppListLayout
import com.dksystems.tracephone.domain.model.PeriodUnit
import com.dksystems.tracephone.domain.model.UsageSortOrder
import kotlinx.coroutines.flow.Flow

/**
 * 사용자가 마지막으로 고른 조회 조건. 앱을 다시 열어도 그대로 이어지게 한다.
 *
 * **기간 단위와 정렬만 저장하고 '몇 칸 전인지'는 저장하지 않는다.**
 * 3주 전을 보다가 앱을 껐다고 해서 다음에 열었을 때도 3주 전이 나오면
 * 사용자는 지금이 언제인지부터 다시 파악해야 한다. 시작은 항상 현재 칸이다.
 */
interface UsageViewPreferences {

    val periodUnit: Flow<PeriodUnit>

    val sortOrder: Flow<UsageSortOrder>

    /**
     * 목록을 그리는 모양. 조회 조건은 아니지만 같은 목록을 두고 고르는 값이라 여기 둔다.
     *
     * 설정 화면에서 바꾸고 요약 화면이 읽는다. 저장소를 따로 만들면 같은 목록에 관한 설정이
     * 두 곳으로 갈린다.
     */
    val appListLayout: Flow<AppListLayout>

    suspend fun setPeriodUnit(unit: PeriodUnit)

    suspend fun setSortOrder(order: UsageSortOrder)

    suspend fun setAppListLayout(layout: AppListLayout)
}
