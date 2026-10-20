package com.dksystems.tracephone.data.system

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import com.dksystems.tracephone.domain.model.NetworkKind
import com.dksystems.tracephone.domain.model.UsageBytes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시스템의 네트워크 통계를 읽는 유일한 창구.
 *
 * `NetworkStatsManager`를 직접 만지는 코드는 이 클래스 밖에 존재하지 않는다.
 * 나머지 레이어는 [RawUsageRecord] 목록만 다루므로 시스템 API 변화의 영향을 받지 않는다.
 *
 * ### 하루씩 나눠 조회하는 이유
 * `querySummary`는 기간 전체의 합계를 돌려줄 뿐 일자별로 쪼개주지 않는다.
 * 일자별 추이가 필요하므로 하루 단위로 반복 호출한다.
 */
@Singleton
class NetworkStatsDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * 가입자 식별자.
     *
     * Android 10(API 29)부터 앱은 `getSubscriberId()`를 얻을 수 없다.
     * null을 넘기면 모든 가입자의 합계가 반환되며, 이것이 우리가 원하는 값이다.
     */
    private val subscriberIdForAllCarriers: String? = null

    private val zoneId: ZoneId
        get() = ZoneId.systemDefault()

    /**
     * 하루치 사용량을 UID · 회선 종류별로 읽는다.
     *
     * @param date 조회할 날짜 (기기의 로컬 시간대 기준)
     * @return 사용량이 0보다 큰 레코드만. 시스템에 기록이 없으면 빈 목록.
     * @throws SecurityException 사용 정보 접근 권한이 없을 때. 호출부가 권한 안내로 전환해야 한다.
     */
    fun queryDay(date: LocalDate): List<RawUsageRecord> {
        val networkStatsManager = requireNetworkStatsManager()

        val startMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()

        // 다음 날 0시에서 1밀리초를 빼 해당 날짜 안에 정확히 머무르게 한다.
        val endMillis = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

        return NetworkKind.entries.flatMap { kind ->
            queryKind(networkStatsManager, kind, startMillis, endMillis)
        }
    }

    private fun requireNetworkStatsManager(): NetworkStatsManager =
        context.getSystemService(NetworkStatsManager::class.java)
            ?: throw IllegalStateException(
                "NetworkStatsManager unavailable; 이 기기에서는 사용량 조회를 지원하지 않습니다."
            )

    private fun queryKind(
        networkStatsManager: NetworkStatsManager,
        kind: NetworkKind,
        startMillis: Long,
        endMillis: Long
    ): List<RawUsageRecord> {
        val totalsByUid = mutableMapOf<Int, StateAwareTotals>()

        // 같은 UID라도 포그라운드 여부·로밍·과금 여부에 따라 버킷이 나뉘어 오므로 UID 단위로 합산한다.
        // 이때 포그라운드 몫을 따로 세어 둔다. 추가 조회 없이 같은 결과에서 얻을 수 있다.
        val stats = networkStatsManager.querySummary(
            toConnectivityType(kind),
            subscriberIdForAllCarriers,
            startMillis,
            endMillis
        )

        try {
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val previous = totalsByUid[bucket.uid] ?: StateAwareTotals.ZERO
                totalsByUid[bucket.uid] = previous.accumulate(bucket)
            }
        } finally {
            stats.close()
        }

        return totalsByUid
            .filterValues { it.total.totalBytes > 0L }
            .map { (uid, totals) ->
                RawUsageRecord(
                    uid = uid,
                    networkKind = kind,
                    bytes = totals.total,
                    foregroundBytes = totals.foregroundOrNull()
                )
            }
    }

    /**
     * UID 하나에 대한 누적 상태.
     *
     * @property isStateResolved 이 UID의 모든 버킷이 포그라운드 여부를 알려주었는지.
     *   하나라도 [NetworkStats.Bucket.STATE_ALL]처럼 구분되지 않은 값으로 오면 false가 되고,
     *   그때는 포그라운드 몫을 0이 아니라 "모름"으로 남긴다.
     *   0으로 두면 화면에 "전부 백그라운드"라는 사실이 아닌 값이 나온다.
     */
    private data class StateAwareTotals(
        val total: UsageBytes,
        val foreground: UsageBytes,
        val isStateResolved: Boolean
    ) {

        fun accumulate(bucket: NetworkStats.Bucket): StateAwareTotals {
            val bucketBytes = UsageBytes(
                receivedBytes = bucket.rxBytes,
                transmittedBytes = bucket.txBytes
            )
            val isForeground = bucket.state == NetworkStats.Bucket.STATE_FOREGROUND
            val isBackground = bucket.state == NetworkStats.Bucket.STATE_DEFAULT

            return StateAwareTotals(
                total = total + bucketBytes,
                foreground = if (isForeground) foreground + bucketBytes else foreground,
                isStateResolved = isStateResolved && (isForeground || isBackground)
            )
        }

        fun foregroundOrNull(): UsageBytes? = if (isStateResolved) foreground else null

        companion object {
            val ZERO = StateAwareTotals(
                total = UsageBytes.ZERO,
                foreground = UsageBytes.ZERO,
                isStateResolved = true
            )
        }
    }

    /**
     * 앱 하나의 하루치를 시간대별로 읽는다.
     *
     * `queryDetailsForUid`는 시간이 나뉜 구간들을 돌려주므로 한 번의 호출로 하루를 훑을 수 있다.
     * 시간마다 따로 조회하면 48번을 호출해야 하고, 그렇게 해도 시스템이 내부적으로
     * 같은 구간을 비례 배분할 뿐이라 더 정확해지지 않는다.
     *
     * @return 시간대(0~23)별 레코드. 기록이 없는 시간대는 담기지 않는다.
     * @throws SecurityException 사용 정보 접근 권한이 없을 때
     */
    fun queryHourlyForUid(uid: Int, date: LocalDate): List<HourlyUsageRecord> {
        val networkStatsManager = requireNetworkStatsManager()

        val dayStartMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEndMillis =
            date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

        return NetworkKind.entries.flatMap { kind ->
            queryHourlyKind(networkStatsManager, kind, uid, dayStartMillis, dayEndMillis)
        }
    }

    private fun queryHourlyKind(
        networkStatsManager: NetworkStatsManager,
        kind: NetworkKind,
        uid: Int,
        dayStartMillis: Long,
        dayEndMillis: Long
    ): List<HourlyUsageRecord> {
        val bytesByHour = mutableMapOf<Int, UsageBytes>()

        val stats = networkStatsManager.queryDetailsForUid(
            toConnectivityType(kind),
            subscriberIdForAllCarriers,
            dayStartMillis,
            dayEndMillis,
            uid
        )

        try {
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                accumulateBucketIntoHours(bucket, dayStartMillis, bytesByHour)
            }
        } finally {
            stats.close()
        }

        return bytesByHour
            .filterValues { it.totalBytes > 0L }
            .map { (hour, bytes) -> HourlyUsageRecord(hour = hour, networkKind = kind, bytes = bytes) }
    }

    /**
     * 구간 하나를 시간대별로 쪼개 누적한다.
     *
     * 수신과 송신을 각각 나눈다. 합쳐서 나눈 뒤 다시 갈라내면 비율이 어긋난다.
     */
    private fun accumulateBucketIntoHours(
        bucket: NetworkStats.Bucket,
        dayStartMillis: Long,
        bytesByHour: MutableMap<Int, UsageBytes>
    ) {
        val receivedByHour = HourlyBucketSplitter.split(
            bucketStartMillis = bucket.startTimeStamp,
            bucketEndMillis = bucket.endTimeStamp,
            bytes = bucket.rxBytes,
            dayStartMillis = dayStartMillis
        )
        val transmittedByHour = HourlyBucketSplitter.split(
            bucketStartMillis = bucket.startTimeStamp,
            bucketEndMillis = bucket.endTimeStamp,
            bytes = bucket.txBytes,
            dayStartMillis = dayStartMillis
        )

        (receivedByHour.keys + transmittedByHour.keys).forEach { hour ->
            val previous = bytesByHour[hour] ?: UsageBytes.ZERO
            bytesByHour[hour] = previous + UsageBytes(
                receivedBytes = receivedByHour[hour] ?: 0L,
                transmittedBytes = transmittedByHour[hour] ?: 0L
            )
        }
    }

    // TYPE_MOBILE / TYPE_WIFI 상수에는 deprecated 표시가 붙었지만
    // querySummary가 여전히 이 값을 받도록 정의되어 있어 대체 상수가 없다.
    @Suppress("DEPRECATION")
    private fun toConnectivityType(kind: NetworkKind): Int = when (kind) {
        NetworkKind.MOBILE -> ConnectivityManager.TYPE_MOBILE
        NetworkKind.WIFI -> ConnectivityManager.TYPE_WIFI
    }
}
