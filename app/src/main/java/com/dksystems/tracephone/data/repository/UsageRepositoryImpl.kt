package com.dksystems.tracephone.data.repository

import com.dksystems.tracephone.data.local.AppUsageTotalsRow
import com.dksystems.tracephone.data.local.DailyAppUsageDao
import com.dksystems.tracephone.data.local.DailyUsageTotalsRow
import com.dksystems.tracephone.data.local.TopMobileAppRow
import com.dksystems.tracephone.data.local.UsageStateSplitRow
import com.dksystems.tracephone.data.packages.AppIdentityResolver
import com.dksystems.tracephone.data.system.AGGREGATE_UID
import com.dksystems.tracephone.di.IoDispatcher
import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.DailyUsagePoint
import com.dksystems.tracephone.domain.model.UsageBytes
import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.domain.model.NetworkUsageSplit
import com.dksystems.tracephone.domain.model.UsageStateSplit
import com.dksystems.tracephone.domain.repository.UsageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 저장소(Room)에 쌓인 사용량만 읽는다. **시스템도, 쓰기도 건드리지 않는다.**
 *
 * 지금 값이 필요하면 [com.dksystems.tracephone.domain.repository.SystemUsageReader],
 * 시스템에서 읽어 옮기는 일은 [com.dksystems.tracephone.domain.repository.UsageSynchronizer]가 맡는다.
 * 셋을 갈라 둔 덕분에 **타입만 보고 어디서 온 값인지 알 수 있다.**
 */
@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val appIdentityResolver: AppIdentityResolver,
    private val dailyAppUsageDao: DailyAppUsageDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : UsageRepository {

    override fun observeAppUsage(period: UsagePeriod): Flow<List<AppUsage>> =
        dailyAppUsageDao
            .observeAppTotals(period.startDate, period.endDateInclusive)
            .map { rows -> rows.filter { it.uid != AGGREGATE_UID }.map(::toAppUsage) }
            // 앱 이름 조회는 PackageManager를 왕복하므로 메인 스레드에서 실행하지 않는다.
            .flowOn(ioDispatcher)

    override fun observeDailyTrend(uid: Int, period: UsagePeriod): Flow<List<DailyUsagePoint>> =
        combine(
            dailyAppUsageDao.observeDailyTotals(uid, period.startDate, period.endDateInclusive),
            dailyAppUsageDao.observeEarliestDate()
        ) { rows, earliestRecordedDate ->
            fillMissingDates(rows, period, earliestRecordedDate)
        }.flowOn(ioDispatcher)

    override fun observeUsageStateSplit(uid: Int, period: UsagePeriod): Flow<NetworkUsageSplit> =
        dailyAppUsageDao
            .observeStateSplit(uid, period.startDate, period.endDateInclusive)
            .map(::toUsageStateSplit)
            .flowOn(ioDispatcher)

    override suspend fun readTopMobileApps(period: UsagePeriod, limit: Int): List<AppUsage> =
        withContext(ioDispatcher) {
            dailyAppUsageDao
                .topMobileApps(period.startDate, period.endDateInclusive, limit)
                .filter { it.uid != AGGREGATE_UID }
                .map(::toTopAppUsage)
        }

    private fun toTopAppUsage(row: TopMobileAppRow): AppUsage = AppUsage(
        app = appIdentityResolver.resolve(uid = row.uid, storedPackageName = row.packageName),
        usage = NetworkUsageSplit(
            // 위젯 목록은 모바일 총량만 쓴다. 나머지 항목까지 조회하면 갱신만 느려진다.
            mobile = UsageStateSplit.allUnknown(totalBytes = row.mobileBytes),
            wifi = UsageStateSplit.ZERO
        )
    )

    override suspend fun hasAnyStoredUsage(): Boolean = withContext(ioDispatcher) {
        dailyAppUsageDao.hasAnyRecord()
    }

    override suspend fun hasAnyStateRecordedUsage(): Boolean = withContext(ioDispatcher) {
        dailyAppUsageDao.hasAnyStateRecord()
    }

    override fun observeEarliestRecordedDate(): Flow<LocalDate?> =
        dailyAppUsageDao.observeEarliestDate().flowOn(ioDispatcher)

    override suspend fun readStoredMobileBytes(period: UsagePeriod): Long =
        withContext(ioDispatcher) {
            dailyAppUsageDao.mobileTotalBetween(period.startDate, period.endDateInclusive)
        }

    private fun toAppUsage(row: AppUsageTotalsRow): AppUsage = AppUsage(
        app = appIdentityResolver.resolve(uid = row.uid, storedPackageName = row.packageName),
        usage = NetworkUsageSplit(
            mobile = UsageStateSplit(
                totalBytes = row.mobileReceivedBytes + row.mobileTransmittedBytes,
                foregroundBytes = row.mobileForegroundBytes,
                unknownBytes = row.mobileUnknownStateBytes
            ),
            wifi = UsageStateSplit(
                totalBytes = row.wifiReceivedBytes + row.wifiTransmittedBytes,
                foregroundBytes = row.wifiForegroundBytes,
                unknownBytes = row.wifiUnknownStateBytes
            )
        )
    )

    private fun toUsageStateSplit(row: UsageStateSplitRow): NetworkUsageSplit = NetworkUsageSplit(
        mobile = UsageStateSplit(
            totalBytes = row.mobileTotalBytes,
            foregroundBytes = row.mobileForegroundBytes,
            unknownBytes = row.mobileUnknownStateBytes
        ),
        wifi = UsageStateSplit(
            totalBytes = row.wifiTotalBytes,
            foregroundBytes = row.wifiForegroundBytes,
            unknownBytes = row.wifiUnknownStateBytes
        )
    )

    /**
     * 기록이 없는 날짜를 0으로 채워 기간을 빠짐없이 돌려준다.
     *
     * 그래프에서 데이터가 없는 날이 통째로 사라지면 날짜 축이 왜곡되어 추이를 잘못 읽게 된다.
     *
     * @param earliestRecordedDate 저장소 전체에서 가장 오래된 기록 날짜.
     *   '전체 누적'의 시작일은 1970년이라 그대로 채우면 빈 점이 수만 개 생기므로 여기서 자른다.
     *
     *   **이 앱의 첫 기록일이 아니라 저장소 전체의 첫 기록일을 쓴다.** 앱별로 자르면
     *   그 앱이 늦게 쓰이기 시작한 경우 그래프가 선택한 기간보다 늦게 시작해,
     *   화면에 적힌 기간과 그래프 범위가 어긋난다.
     */
    private fun fillMissingDates(
        rows: List<DailyUsageTotalsRow>,
        period: UsagePeriod,
        earliestRecordedDate: LocalDate?
    ): List<DailyUsagePoint> {
        val rowsByDate = rows.associateBy { it.usageDate }

        val fillStartDate = maxOf(period.startDate, earliestRecordedDate ?: period.startDate)
            .coerceAtMost(period.endDateInclusive)
        val visiblePeriod = UsagePeriod(fillStartDate, period.endDateInclusive)

        return visiblePeriod.eachDate().map { date ->
            val row = rowsByDate[date]
            DailyUsagePoint(
                date = date,
                mobile = UsageBytes(
                    receivedBytes = row?.mobileReceivedBytes ?: 0L,
                    transmittedBytes = row?.mobileTransmittedBytes ?: 0L
                ),
                wifi = UsageBytes(
                    receivedBytes = row?.wifiReceivedBytes ?: 0L,
                    transmittedBytes = row?.wifiTransmittedBytes ?: 0L
                )
            )
        }
    }
}
