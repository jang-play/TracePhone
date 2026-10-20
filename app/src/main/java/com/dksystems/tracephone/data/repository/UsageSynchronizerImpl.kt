package com.dksystems.tracephone.data.repository

import com.dksystems.tracephone.core.AppLog
import com.dksystems.tracephone.data.local.DailyAppUsageDao
import com.dksystems.tracephone.data.local.DailyAppUsageEntity
import com.dksystems.tracephone.data.packages.AppIdentityResolver
import com.dksystems.tracephone.data.system.AGGREGATE_UID
import com.dksystems.tracephone.data.system.NetworkStatsDataSource
import com.dksystems.tracephone.di.IoDispatcher
import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.domain.model.UsageSyncResult
import com.dksystems.tracephone.domain.permission.UsageAccessPermission
import com.dksystems.tracephone.domain.repository.UsageSynchronizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시스템 통계를 읽어 저장소에 옮긴다.
 *
 * 읽는 쪽과 쓰는 쪽을 모두 알아야 하는 유일한 클래스라, 생성자 인자가 다른 곳보다 하나 많다.
 * 묶어서 줄일 수는 있지만 그러면 무엇에 기대는지가 오히려 가려진다.
 */
@Singleton
class UsageSynchronizerImpl @Inject constructor(
    private val networkStatsDataSource: NetworkStatsDataSource,
    private val appIdentityResolver: AppIdentityResolver,
    private val dailyAppUsageDao: DailyAppUsageDao,
    private val usageAccessPermission: UsageAccessPermission,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : UsageSynchronizer {

    override suspend fun syncRecentDays(dayCount: Long): UsageSyncResult =
        withContext(ioDispatcher) {
            if (!usageAccessPermission.isGranted()) {
                AppLog.info("Usage access not granted; 동기화를 건너뜁니다.")
                return@withContext UsageSyncResult.PermissionRequired
            }

            val period = UsagePeriod.lastDays(dayCount, LocalDate.now(clock))

            // 동기화 한 번 동안 UID -> 패키지명 조회 결과를 재사용한다.
            // 90일 × 수백 개 앱이면 같은 UID를 수만 번 조회하게 된다.
            val packageNameByUid = mutableMapOf<Int, String?>()

            var syncedDayCount = 0
            var recordCount = 0

            for (date in period.eachDate()) {
                val entities = try {
                    readDay(date, packageNameByUid)
                } catch (permissionRevoked: SecurityException) {
                    AppLog.error(
                        "NetworkStats query denied: date=$date. " +
                            "설정에서 사용 정보 접근을 다시 허용해야 합니다.",
                        permissionRevoked
                    )
                    return@withContext UsageSyncResult.PermissionRequired
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (queryFailure: Exception) {
                    AppLog.error(
                        "NetworkStats query failed: date=$date. " +
                            "이 날짜만 건너뛰고 다음 동기화에서 다시 시도합니다.",
                        queryFailure
                    )
                    continue
                }

                dailyAppUsageDao.upsertAll(entities)
                syncedDayCount++
                recordCount += entities.size
            }

            AppLog.info("Daily sync completed: days=$syncedDayCount, records=$recordCount")
            UsageSyncResult.Success(syncedDayCount = syncedDayCount, recordCount = recordCount)
        }

    /**
     * 하루치를 읽어 저장 가능한 형태로 바꾼다.
     *
     * @param packageNameByUid 이번 동기화 동안 공유하는 UID -> 패키지명 캐시
     */
    private fun readDay(
        date: LocalDate,
        packageNameByUid: MutableMap<Int, String?>
    ): List<DailyAppUsageEntity> {
        val recordedAt = clock.millis()

        return networkStatsDataSource.queryDay(date)
            .filter { it.uid != AGGREGATE_UID }
            .map { record ->
                val packageName = packageNameByUid.getOrPut(record.uid) {
                    appIdentityResolver.findPrimaryPackageName(record.uid)
                }

                DailyAppUsageEntity(
                    usageDate = date,
                    uid = record.uid,
                    networkKind = record.networkKind,
                    packageName = packageName,
                    receivedBytes = record.bytes.receivedBytes,
                    transmittedBytes = record.bytes.transmittedBytes,
                    // null을 그대로 저장한다. 시스템이 구분을 주지 않은 경우와 0바이트를 구별해야 한다.
                    foregroundReceivedBytes = record.foregroundBytes?.receivedBytes,
                    foregroundTransmittedBytes = record.foregroundBytes?.transmittedBytes,
                    recordedAtEpochMillis = recordedAt
                )
            }
    }
}
