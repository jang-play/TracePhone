package com.dksystems.tracephone.domain

import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.DailyUsagePoint
import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.domain.model.NetworkUsageSplit
import com.dksystems.tracephone.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/**
 * 테스트용 저장소.
 *
 * 실제 구현 대신 미리 정해둔 값을 돌려주고, 어떤 인자로 호출되었는지 기록한다.
 */
class FakeUsageRepository(
    private val appUsages: List<AppUsage> = emptyList(),
    private val dailyPoints: List<DailyUsagePoint> = emptyList(),
    var hasStoredUsage: Boolean = true,
    var hasStateRecordedUsage: Boolean = true,
    private val usageStateSplit: NetworkUsageSplit = NetworkUsageSplit.ZERO,
    private val earliestRecordedDate: LocalDate? = null,
    var storedMobileBytes: Long = 0L,
    private val topMobileApps: List<AppUsage> = emptyList(),
    /**
     * 기간마다 다른 값을 돌려줘야 하는 테스트용. 없는 기간은 [storedMobileBytes]로 떨어진다.
     *
     * 주기 이력처럼 **여러 기간을 잇달아 읽는** 경로에서는 값이 섞이지 않는지가 핵심이라,
     * 하나의 고정값만으로는 그것을 확인할 수 없다.
     */
    private val storedMobileBytesByPeriod: Map<UsagePeriod, Long> = emptyMap()
) : UsageRepository {

    override fun observeAppUsage(period: UsagePeriod): Flow<List<AppUsage>> = flowOf(appUsages)

    override fun observeDailyTrend(uid: Int, period: UsagePeriod): Flow<List<DailyUsagePoint>> =
        flowOf(dailyPoints)

    override fun observeUsageStateSplit(uid: Int, period: UsagePeriod): Flow<NetworkUsageSplit> =
        flowOf(usageStateSplit)

    override suspend fun readStoredMobileBytes(period: UsagePeriod): Long =
        storedMobileBytesByPeriod[period] ?: storedMobileBytes

    override suspend fun readTopMobileApps(period: UsagePeriod, limit: Int): List<AppUsage> =
        topMobileApps.take(limit)

    override suspend fun hasAnyStoredUsage(): Boolean = hasStoredUsage

    override suspend fun hasAnyStateRecordedUsage(): Boolean = hasStateRecordedUsage

    override fun observeEarliestRecordedDate(): Flow<LocalDate?> = flowOf(earliestRecordedDate)
}
