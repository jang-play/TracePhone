package com.dksystems.tracephone.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 누적 사용량 테이블 접근.
 *
 * 집계는 모두 SQL에서 끝낸다. 90일치 × 수백 개 앱 × 두 회선이면 수만 행이 되는데,
 * 이를 메모리로 끌어올려 코틀린에서 합치면 목록을 넘길 때마다 눈에 띄게 버벅인다.
 *
 * ### 포그라운드 몫을 세는 규칙
 * `foregroundReceivedBytes`와 `foregroundTransmittedBytes`는 언제나 함께 쓰이므로
 * '구분값이 있는가'는 앞의 한 열만 확인하면 된다.
 * null인 행은 포그라운드 합계에 넣지 않고 '구분 없음' 합계로 따로 센다.
 * 0으로 더해 넣으면 과거 기록이 전부 백그라운드였던 것처럼 보인다.
 */
@Dao
interface DailyAppUsageDao {

    /**
     * 사용량을 저장한다. 같은 (날짜, uid, 회선) 조합은 **덮어쓴다**.
     *
     * 시스템 값이 그 날의 누적치이므로 더하지 않고 대체하는 것이 맞다.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<DailyAppUsageEntity>)

    /** 기록이 하나라도 있는지 확인한다. 최초 백필 여부 판단에 쓴다. */
    @Query("SELECT EXISTS(SELECT 1 FROM daily_app_usage)")
    suspend fun hasAnyRecord(): Boolean

    /**
     * 포그라운드 구분이 기록된 행이 하나라도 있는지 확인한다.
     *
     * 이 구분을 도입하기 전에 쌓인 기록만 있는 기기를 알아내, 시스템이 아직 보관 중인 기간을
     * 한 번 다시 읽어 채우는 데 쓴다. 별도의 '완료 표시'를 저장하지 않고 자료 자체로 판단한다.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM daily_app_usage WHERE foregroundReceivedBytes IS NOT NULL)")
    suspend fun hasAnyStateRecord(): Boolean

    /**
     * 저장된 가장 오래된 날짜. 기록이 없으면 null.
     *
     * 기간을 과거로 이동할 때 어디서 멈춰야 하는지 판단하는 데 쓴다.
     * 동기화로 기록이 늘면 자동으로 갱신되도록 Flow로 노출한다.
     */
    @Query("SELECT MIN(usageDate) FROM daily_app_usage")
    fun observeEarliestDate(): Flow<LocalDate?>

    /**
     * 기간 내 앱별 합계를 관찰한다.
     *
     * `packageName`은 같은 uid 안에서도 행마다 다를 수 있어(설치/제거 이력) `MAX`로 하나를 고른다.
     * 표시 이름은 어차피 `AppIdentityResolver`가 현재 설치 상태를 우선해 다시 판단한다.
     */
    @Query(
        """
        SELECT
            uid AS uid,
            MAX(packageName) AS packageName,
            SUM(CASE WHEN networkKind = 'MOBILE' THEN receivedBytes ELSE 0 END) AS mobileReceivedBytes,
            SUM(CASE WHEN networkKind = 'MOBILE' THEN transmittedBytes ELSE 0 END) AS mobileTransmittedBytes,
            SUM(
                CASE WHEN networkKind = 'MOBILE'
                    THEN COALESCE(foregroundReceivedBytes, 0) + COALESCE(foregroundTransmittedBytes, 0)
                    ELSE 0
                END
            ) AS mobileForegroundBytes,
            SUM(
                CASE WHEN networkKind = 'MOBILE' AND foregroundReceivedBytes IS NULL
                    THEN receivedBytes + transmittedBytes
                    ELSE 0
                END
            ) AS mobileUnknownStateBytes,
            SUM(CASE WHEN networkKind = 'WIFI' THEN receivedBytes ELSE 0 END) AS wifiReceivedBytes,
            SUM(CASE WHEN networkKind = 'WIFI' THEN transmittedBytes ELSE 0 END) AS wifiTransmittedBytes,
            SUM(
                CASE WHEN networkKind = 'WIFI'
                    THEN COALESCE(foregroundReceivedBytes, 0) + COALESCE(foregroundTransmittedBytes, 0)
                    ELSE 0
                END
            ) AS wifiForegroundBytes,
            SUM(
                CASE WHEN networkKind = 'WIFI' AND foregroundReceivedBytes IS NULL
                    THEN receivedBytes + transmittedBytes
                    ELSE 0
                END
            ) AS wifiUnknownStateBytes
        FROM daily_app_usage
        WHERE usageDate BETWEEN :startDate AND :endDateInclusive
        GROUP BY uid
        """
    )
    fun observeAppTotals(
        startDate: LocalDate,
        endDateInclusive: LocalDate
    ): Flow<List<AppUsageTotalsRow>>

    /**
     * 앱 하나를 기간 전체에 걸쳐 회선별·상태별로 나눈 합계를 관찰한다.
     *
     * 일자별 추이와 따로 두는 이유는, 비어 있는 날을 0으로 채우는 추이 쪽 처리에
     * 상태 구분까지 끌고 들어가면 두 관심사가 뒤엉키기 때문이다.
     *
     * `IFNULL`로 감싸 기록이 없어도 0으로 채운 한 줄이 나오게 한다.
     */
    @Query(
        """
        SELECT
            IFNULL(SUM(CASE WHEN networkKind = 'MOBILE'
                THEN receivedBytes + transmittedBytes ELSE 0 END), 0) AS mobileTotalBytes,
            IFNULL(SUM(CASE WHEN networkKind = 'MOBILE'
                THEN COALESCE(foregroundReceivedBytes, 0) + COALESCE(foregroundTransmittedBytes, 0)
                ELSE 0 END), 0) AS mobileForegroundBytes,
            IFNULL(SUM(CASE WHEN networkKind = 'MOBILE' AND foregroundReceivedBytes IS NULL
                THEN receivedBytes + transmittedBytes ELSE 0 END), 0) AS mobileUnknownStateBytes,
            IFNULL(SUM(CASE WHEN networkKind = 'WIFI'
                THEN receivedBytes + transmittedBytes ELSE 0 END), 0) AS wifiTotalBytes,
            IFNULL(SUM(CASE WHEN networkKind = 'WIFI'
                THEN COALESCE(foregroundReceivedBytes, 0) + COALESCE(foregroundTransmittedBytes, 0)
                ELSE 0 END), 0) AS wifiForegroundBytes,
            IFNULL(SUM(CASE WHEN networkKind = 'WIFI' AND foregroundReceivedBytes IS NULL
                THEN receivedBytes + transmittedBytes ELSE 0 END), 0) AS wifiUnknownStateBytes
        FROM daily_app_usage
        WHERE uid = :uid AND usageDate BETWEEN :startDate AND :endDateInclusive
        """
    )
    fun observeStateSplit(
        uid: Int,
        startDate: LocalDate,
        endDateInclusive: LocalDate
    ): Flow<UsageStateSplitRow>

    /**
     * 기간 내 모바일 총 사용량. 요금제 한도 계산에 쓴다.
     *
     * 기록이 없으면 0을 돌려준다. 한도 화면은 어떤 경우에도 값을 그려야 하므로 null을 내지 않는다.
     */
    @Query(
        """
        SELECT IFNULL(SUM(receivedBytes + transmittedBytes), 0)
        FROM daily_app_usage
        WHERE networkKind = 'MOBILE' AND usageDate BETWEEN :startDate AND :endDateInclusive
        """
    )
    suspend fun mobileTotalBetween(startDate: LocalDate, endDateInclusive: LocalDate): Long

    /**
     * 기간 내 모바일 사용량이 많은 앱을 위에서부터 [limit]개 돌려준다.
     *
     * 위젯은 두세 개만 보여주므로 전체를 읽어 와 정렬하지 않고 SQL에서 잘라 온다.
     */
    @Query(
        """
        SELECT
            uid AS uid,
            MAX(packageName) AS packageName,
            SUM(receivedBytes + transmittedBytes) AS mobileBytes
        FROM daily_app_usage
        WHERE networkKind = 'MOBILE' AND usageDate BETWEEN :startDate AND :endDateInclusive
        GROUP BY uid
        HAVING mobileBytes > 0
        ORDER BY mobileBytes DESC
        LIMIT :limit
        """
    )
    suspend fun topMobileApps(
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        limit: Int
    ): List<TopMobileAppRow>

    /**
     * 앱 하나의 일자별 합계를 오래된 순으로 관찰한다.
     *
     * 기록이 없는 날짜는 결과에 포함되지 않는다. 빈 날을 0으로 채우는 일은 저장소가 담당한다.
     */
    @Query(
        """
        SELECT
            usageDate AS usageDate,
            SUM(CASE WHEN networkKind = 'MOBILE' THEN receivedBytes ELSE 0 END) AS mobileReceivedBytes,
            SUM(CASE WHEN networkKind = 'MOBILE' THEN transmittedBytes ELSE 0 END) AS mobileTransmittedBytes,
            SUM(CASE WHEN networkKind = 'WIFI' THEN receivedBytes ELSE 0 END) AS wifiReceivedBytes,
            SUM(CASE WHEN networkKind = 'WIFI' THEN transmittedBytes ELSE 0 END) AS wifiTransmittedBytes
        FROM daily_app_usage
        WHERE uid = :uid AND usageDate BETWEEN :startDate AND :endDateInclusive
        GROUP BY usageDate
        ORDER BY usageDate ASC
        """
    )
    fun observeDailyTotals(
        uid: Int,
        startDate: LocalDate,
        endDateInclusive: LocalDate
    ): Flow<List<DailyUsageTotalsRow>>
}
