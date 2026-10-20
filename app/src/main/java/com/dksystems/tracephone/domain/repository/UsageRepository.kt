package com.dksystems.tracephone.domain.repository

import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.DailyUsagePoint
import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.domain.model.NetworkUsageSplit
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 저장소(Room)에 쌓아 둔 사용량을 읽는 창구.
 *
 * 화면은 오직 여기만 관찰한다. 시스템 통계를 직접 읽지 않는다.
 * 최신 값은 [UsageSynchronizer]가 저장소를 갱신하면 [observeAppUsage]의 Flow를 타고 자동으로 흘러온다.
 * 이렇게 하면 "DB와 시스템 중 어느 쪽이 최신인가"를 화면이 판단할 필요가 없다.
 *
 * **지금 값이 필요한 곳은 [SystemUsageReader]를 쓴다.** 여기는 마지막 동기화 시점까지만 안다.
 */
interface UsageRepository {

    /**
     * 기간 내 앱별 사용량을 관찰한다. 정렬은 호출부(유스케이스)가 담당한다.
     *
     * @param period 조회할 날짜 범위 (양 끝 포함)
     */
    fun observeAppUsage(period: UsagePeriod): Flow<List<AppUsage>>

    /**
     * 앱 하나의 일자별 추이를 관찰한다.
     *
     * 기록이 없는 날짜는 0으로 채워져 빠짐없이 반환된다. 그래프에 구멍이 생기지 않게 하기 위함이다.
     *
     * @param uid 대상 앱의 커널 UID
     * @param period 조회할 날짜 범위
     */
    fun observeDailyTrend(uid: Int, period: UsagePeriod): Flow<List<DailyUsagePoint>>

    /**
     * 앱 하나를 기간 전체에 걸쳐 회선별·상태별로 나눈 합계를 관찰한다.
     */
    fun observeUsageStateSplit(uid: Int, period: UsagePeriod): Flow<NetworkUsageSplit>

    /**
     * 기간 내 모바일 총 사용량. **저장된 것만 센다.**
     *
     * 오늘치를 섞는 일은 여기서 하지 않는다. 그건 저장 방식이 아니라 업무 규칙이라
     * [com.dksystems.tracephone.domain.usecase.GetMobileUsageForPeriodUseCase]가 맡는다.
     */
    suspend fun readStoredMobileBytes(period: UsagePeriod): Long

    /**
     * 기간 내 모바일 사용량이 많은 앱을 위에서부터 [limit]개.
     *
     * 저장된 값만 본다. 위젯의 보조 정보라 오늘치까지 정확할 필요가 없고,
     * 앱마다 시스템을 다시 읽으면 위젯 갱신이 느려진다.
     */
    suspend fun readTopMobileApps(period: UsagePeriod, limit: Int): List<AppUsage>

    /**
     * 저장소에 기록이 하나라도 있는지 확인한다.
     *
     * 최초 실행을 판별해 긴 백필을 한 번만 수행하기 위해 사용한다.
     */
    suspend fun hasAnyStoredUsage(): Boolean

    /**
     * 포그라운드 구분이 기록된 자료가 하나라도 있는지.
     *
     * 이 구분을 도입하기 전부터 쓰던 기기를 알아내, 시스템이 아직 보관 중인 기간을
     * 한 번 다시 읽어 채울지 판단하는 데 쓴다.
     */
    suspend fun hasAnyStateRecordedUsage(): Boolean

    /**
     * 저장된 가장 오래된 기록 날짜를 관찰한다. 기록이 없으면 null.
     *
     * 기간을 과거로 이동할 때 더 갈 곳이 있는지 판단하는 데 쓴다.
     */
    fun observeEarliestRecordedDate(): Flow<LocalDate?>
}
