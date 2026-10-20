package com.dksystems.tracephone.data.repository

import com.dksystems.tracephone.core.AppLog
import com.dksystems.tracephone.core.SyncConstants
import com.dksystems.tracephone.data.packages.AppIdentityResolver
import com.dksystems.tracephone.data.system.AGGREGATE_UID
import com.dksystems.tracephone.data.system.HourlyBucketSplitter
import com.dksystems.tracephone.data.system.HourlyUsageRecord
import com.dksystems.tracephone.data.system.NetworkStatsDataSource
import com.dksystems.tracephone.data.system.RawUsageRecord
import com.dksystems.tracephone.di.IoDispatcher
import com.dksystems.tracephone.domain.model.AppIdentity
import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.HourlyUsagePoint
import com.dksystems.tracephone.domain.model.NetworkKind
import com.dksystems.tracephone.domain.model.NetworkUsageSplit
import com.dksystems.tracephone.domain.model.UsageBytes
import com.dksystems.tracephone.domain.model.UsageStateSplit
import com.dksystems.tracephone.domain.permission.UsageAccessPermission
import com.dksystems.tracephone.domain.repository.SystemUsageReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시스템에서 직접 읽어 도메인 모델로 바꾼다.
 *
 * 저장소(Room)를 전혀 건드리지 않는다. 반대로 [UsageRepositoryImpl]은 시스템을 건드리지 않는다.
 * 둘을 갈라 둔 덕분에 **어느 쪽 값을 보고 있는지 호출부에서 타입만 봐도 알 수 있다.**
 *
 * 조회 실패를 예외로 올리지 않고 빈 값으로 돌려준다.
 * 사용량 표시는 한 번 못 읽었다고 멈출 일이 아니고, 다음 주기에 다시 시도하면 된다.
 */
@Singleton
class SystemUsageReaderImpl @Inject constructor(
    private val networkStatsDataSource: NetworkStatsDataSource,
    private val appIdentityResolver: AppIdentityResolver,
    private val usageAccessPermission: UsageAccessPermission,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SystemUsageReader {

    /**
     * 오늘치 조회를 여러 경로가 나눠 쓰게 한다.
     *
     * 경고 판정 · 한도 판정 · 위젯 갱신이 잇달아 도는데, 위젯은 Glance 컴포지션 안에서
     * 스스로 값을 읽으므로 바깥에서 건네줄 방법이 없다. 셋이 반드시 지나는 지점이 여기다.
     *
     * 이 클래스가 `@Singleton`이라는 것이 전제다. 인스턴스가 여럿이면 아무것도 공유되지 않는다.
     */
    private val todayUsageMemo = TodayUsageMemo(SyncConstants.TODAY_USAGE_MEMO_TTL_MILLIS)

    override suspend fun readTodayUsage(): List<AppUsage> = withContext(ioDispatcher) {
        val today = LocalDate.now(clock)
        todayUsageMemo.getOrLoad(today, clock.millis()) { queryTodayUsage(today) }
    }

    /**
     * 실제로 시스템을 두드려 오늘치를 읽는다. 기억해 둔 값이 없을 때만 불린다.
     *
     * 읽지 못하면 빈 목록을 돌려준다. [TodayUsageMemo]가 빈 목록을 기억하지 않으므로
     * 권한이 허용되거나 조회가 회복되는 즉시 다시 시도한다.
     */
    private fun queryTodayUsage(today: LocalDate): List<AppUsage> {
        if (!usageAccessPermission.isGranted()) {
            AppLog.info("Usage access not granted; 오늘치 조회를 건너뜁니다.")
            return emptyList()
        }

        val recordsByUid = try {
            networkStatsDataSource.queryDay(today)
                .filter { it.uid != AGGREGATE_UID }
                .groupBy { it.uid }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (queryFailure: Exception) {
            AppLog.error(
                "Reading today's usage failed: date=$today. 다음 평가에서 다시 시도합니다.",
                queryFailure
            )
            return emptyList()
        }

        return recordsByUid.map { (uid, records) ->
            AppUsage(
                app = appIdentityResolver.resolve(uid),
                usage = NetworkUsageSplit(
                    mobile = records.stateSplitOf(NetworkKind.MOBILE),
                    wifi = records.stateSplitOf(NetworkKind.WIFI)
                )
            )
        }
    }

    override suspend fun readHourlyUsage(
        uid: Int,
        date: LocalDate
    ): List<HourlyUsagePoint> = withContext(ioDispatcher) {
        if (!usageAccessPermission.isGranted()) {
            AppLog.info("Usage access not granted; 시간별 조회를 건너뜁니다.")
            return@withContext emptyList()
        }

        val recordsByHour = try {
            networkStatsDataSource.queryHourlyForUid(uid, date).groupBy { it.hour }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (queryFailure: Exception) {
            AppLog.error(
                "Reading hourly usage failed: uid=$uid, date=$date. " +
                    "시스템의 상세 기록 보관 기간을 벗어났을 수 있습니다.",
                queryFailure
            )
            return@withContext emptyList()
        }

        // 기록이 없는 시간대도 0으로 채워 24개를 모두 돌려준다.
        // 빠뜨리면 그래프의 시간 축이 어긋나 언제 썼는지를 잘못 읽게 된다.
        (0 until HourlyBucketSplitter.HOURS_PER_DAY).map { hour ->
            val records = recordsByHour[hour].orEmpty()
            HourlyUsagePoint(
                hour = hour,
                mobile = records.sumHourlyBytesOf(NetworkKind.MOBILE),
                wifi = records.sumHourlyBytesOf(NetworkKind.WIFI)
            )
        }
    }

    override suspend fun findAppIdentity(uid: Int): AppIdentity = withContext(ioDispatcher) {
        appIdentityResolver.resolve(uid)
    }

    private fun List<HourlyUsageRecord>.sumHourlyBytesOf(kind: NetworkKind): UsageBytes =
        filter { it.networkKind == kind }
            .fold(UsageBytes.ZERO) { total, record -> total + record.bytes }

    /**
     * 회선 하나의 총량과 그중 포그라운드 몫을 센다.
     *
     * 포그라운드 구분이 없는 레코드는 포그라운드에 더하지 않고 '모름'으로 따로 센다.
     * 0으로 더하면 백그라운드였던 것처럼 보인다.
     */
    private fun List<RawUsageRecord>.stateSplitOf(kind: NetworkKind): UsageStateSplit {
        val records = filter { it.networkKind == kind }
        return UsageStateSplit(
            totalBytes = records.sumOf { it.bytes.totalBytes },
            foregroundBytes = records.sumOf { it.foregroundBytes?.totalBytes ?: 0L },
            unknownBytes = records.filter { it.foregroundBytes == null }
                .sumOf { it.bytes.totalBytes }
        )
    }
}
