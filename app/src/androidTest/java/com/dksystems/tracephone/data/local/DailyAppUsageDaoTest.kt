package com.dksystems.tracephone.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dksystems.tracephone.domain.model.NetworkKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DailyAppUsageDaoTest {

    private lateinit var database: TracePhoneDatabase
    private lateinit var dao: DailyAppUsageDao

    private val testUid = 10234
    private val firstDate = LocalDate.of(2026, 9, 1)
    private val secondDate = LocalDate.of(2026, 9, 2)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TracePhoneDatabase::class.java
        ).build()
        dao = database.dailyAppUsageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun hasAnyRecord_isFalseOnEmptyDatabase() = runTest {
        assertFalse(dao.hasAnyRecord())

        dao.upsertAll(listOf(usageEntity(firstDate, NetworkKind.MOBILE, receivedBytes = 100L)))

        assertTrue(dao.hasAnyRecord())
    }

    /**
     * 이 프로젝트에서 가장 중요한 회귀 테스트.
     *
     * 시스템이 주는 값은 그 날의 누적치다. 같은 날을 여러 번 동기화해도
     * 수치가 배로 늘어나면 안 된다. 저장이 덮어쓰기인지 확인한다.
     */
    @Test
    fun repeatedSyncOfSameDayDoesNotAccumulate() = runTest {
        val record = usageEntity(firstDate, NetworkKind.MOBILE, receivedBytes = 1_000L)

        repeat(3) { dao.upsertAll(listOf(record)) }

        val totals = dao.observeAppTotals(firstDate, firstDate).first()
        assertEquals(1, totals.size)
        assertEquals(1_000L, totals.first().mobileReceivedBytes)
    }

    @Test
    fun laterSyncReplacesEarlierValueForSameDay() = runTest {
        dao.upsertAll(listOf(usageEntity(firstDate, NetworkKind.MOBILE, receivedBytes = 1_000L)))
        dao.upsertAll(listOf(usageEntity(firstDate, NetworkKind.MOBILE, receivedBytes = 1_500L)))

        val totals = dao.observeAppTotals(firstDate, firstDate).first()

        assertEquals(1_500L, totals.first().mobileReceivedBytes)
    }

    @Test
    fun mobileAndWifiAreStoredSeparatelyForSameDay() = runTest {
        dao.upsertAll(
            listOf(
                usageEntity(firstDate, NetworkKind.MOBILE, receivedBytes = 100L),
                usageEntity(firstDate, NetworkKind.WIFI, receivedBytes = 900L)
            )
        )

        val totals = dao.observeAppTotals(firstDate, firstDate).first().first()

        assertEquals(100L, totals.mobileReceivedBytes)
        assertEquals(900L, totals.wifiReceivedBytes)
    }

    @Test
    fun appTotalsSumAcrossDaysInRange() = runTest {
        dao.upsertAll(
            listOf(
                usageEntity(firstDate, NetworkKind.MOBILE, receivedBytes = 100L),
                usageEntity(secondDate, NetworkKind.MOBILE, receivedBytes = 250L)
            )
        )

        val totals = dao.observeAppTotals(firstDate, secondDate).first().first()

        assertEquals(350L, totals.mobileReceivedBytes)
    }

    @Test
    fun appTotalsExcludeDaysOutsideRange() = runTest {
        dao.upsertAll(
            listOf(
                usageEntity(firstDate, NetworkKind.MOBILE, receivedBytes = 100L),
                usageEntity(secondDate, NetworkKind.MOBILE, receivedBytes = 250L)
            )
        )

        val totals = dao.observeAppTotals(firstDate, firstDate).first().first()

        assertEquals(100L, totals.mobileReceivedBytes)
    }

    @Test
    fun dailyTotalsAreOrderedByDateAscending() = runTest {
        dao.upsertAll(
            listOf(
                usageEntity(secondDate, NetworkKind.WIFI, receivedBytes = 20L),
                usageEntity(firstDate, NetworkKind.WIFI, receivedBytes = 10L)
            )
        )

        val dailyTotals = dao.observeDailyTotals(testUid, firstDate, secondDate).first()

        assertEquals(listOf(firstDate, secondDate), dailyTotals.map { it.usageDate })
    }

    @Test
    fun hasAnyStateRecord_isFalseWhenOnlyLegacyRowsExist() = runTest {
        dao.upsertAll(
            listOf(usageEntity(firstDate, NetworkKind.MOBILE, receivedBytes = 100L))
        )

        assertFalse("구분값이 없는 행만 있으면 false여야 한다", dao.hasAnyStateRecord())

        dao.upsertAll(
            listOf(
                usageEntity(
                    secondDate,
                    NetworkKind.MOBILE,
                    receivedBytes = 100L,
                    foregroundReceivedBytes = 40L
                )
            )
        )

        assertTrue(dao.hasAnyStateRecord())
    }

    /**
     * 구분값이 없는 행을 포그라운드 0으로 세면 화면에 '전부 백그라운드'가 찍힌다.
     * 그 몫이 '구분 없음'으로 따로 집계되는지 확인한다.
     */
    @Test
    fun appTotalsCountLegacyRowsAsUnknownInsteadOfBackground() = runTest {
        dao.upsertAll(
            listOf(
                // 예전 기록: 구분 없음 100
                usageEntity(firstDate, NetworkKind.MOBILE, receivedBytes = 100L),
                // 새 기록: 총 200 중 포그라운드 60
                usageEntity(
                    secondDate,
                    NetworkKind.MOBILE,
                    receivedBytes = 200L,
                    foregroundReceivedBytes = 60L
                )
            )
        )

        val row = dao.observeAppTotals(firstDate, secondDate).first().single()

        assertEquals(300L, row.mobileReceivedBytes)
        assertEquals(60L, row.mobileForegroundBytes)
        assertEquals(100L, row.mobileUnknownStateBytes)
    }

    /**
     * 회선을 합쳐 세면 Wi-Fi로 크게 받는 앱에 가려 모바일 백그라운드가 묻힌다.
     * 요금이 붙는 쪽은 모바일뿐이므로 두 회선이 섞이지 않아야 한다.
     */
    @Test
    fun appTotalsKeepForegroundSeparateForEachNetworkKind() = runTest {
        dao.upsertAll(
            listOf(
                usageEntity(
                    firstDate,
                    NetworkKind.MOBILE,
                    receivedBytes = 100L,
                    foregroundReceivedBytes = 30L
                ),
                usageEntity(
                    firstDate,
                    NetworkKind.WIFI,
                    receivedBytes = 900L,
                    foregroundReceivedBytes = 100L
                )
            )
        )

        val row = dao.observeAppTotals(firstDate, firstDate).first().single()

        assertEquals(30L, row.mobileForegroundBytes)
        assertEquals(100L, row.wifiForegroundBytes)
        assertEquals(0L, row.mobileUnknownStateBytes)
        assertEquals(0L, row.wifiUnknownStateBytes)
    }

    @Test
    fun stateSplitSumsTotalForegroundAndUnknownSeparately() = runTest {
        dao.upsertAll(
            listOf(
                usageEntity(firstDate, NetworkKind.MOBILE, receivedBytes = 100L),
                usageEntity(
                    secondDate,
                    NetworkKind.MOBILE,
                    receivedBytes = 200L,
                    foregroundReceivedBytes = 60L
                )
            )
        )

        val split = dao.observeStateSplit(testUid, firstDate, secondDate).first()

        assertEquals(300L, split.mobileTotalBytes)
        assertEquals(60L, split.mobileForegroundBytes)
        assertEquals(100L, split.mobileUnknownStateBytes)
    }

    @Test
    fun stateSplitIsZeroWhenNoRecordsExist() = runTest {
        val split = dao.observeStateSplit(testUid, firstDate, secondDate).first()

        assertEquals(0L, split.mobileTotalBytes)
        assertEquals(0L, split.mobileForegroundBytes)
        assertEquals(0L, split.mobileUnknownStateBytes)
        assertEquals(0L, split.wifiTotalBytes)
    }

    /**
     * @param foregroundReceivedBytes null이면 포그라운드 구분이 없는 예전 기록을 뜻한다.
     */
    private fun usageEntity(
        date: LocalDate,
        networkKind: NetworkKind,
        receivedBytes: Long,
        foregroundReceivedBytes: Long? = null
    ) = DailyAppUsageEntity(
        usageDate = date,
        uid = testUid,
        networkKind = networkKind,
        packageName = "com.example.app",
        receivedBytes = receivedBytes,
        transmittedBytes = 0L,
        foregroundReceivedBytes = foregroundReceivedBytes,
        foregroundTransmittedBytes = foregroundReceivedBytes?.let { 0L },
        recordedAtEpochMillis = 0L
    )
}
