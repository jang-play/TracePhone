package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.FakeMonthlyLimitPreferences
import com.dksystems.tracephone.domain.FakeSystemUsageReader
import com.dksystems.tracephone.domain.FakeUsageRepository
import com.dksystems.tracephone.domain.model.MonthlyLimitSettings
import com.dksystems.tracephone.domain.model.UsagePeriod
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * 주기 이력이 어떤 주기를 세고 어떤 주기를 빼는지 고정한다.
 *
 * 여기가 틀리면 **반쪽만 기록된 주기가 목록에 섞여** 그 달에 갑자기 적게 쓴 것처럼 보인다.
 * 화면은 멀쩡하고 수치만 조용히 거짓이 되므로 눈으로는 잡히지 않는다.
 */
class GetBillingCycleHistoryUseCaseTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 9, 12)
    private val clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone)

    private val megabyte = 1_000_000L

    /** 시작일 1일 기준 주기들. */
    private val september = UsagePeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
    private val august = UsagePeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
    private val july = UsagePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))
    private val june = UsagePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

    private fun useCaseWith(
        earliestRecordedDate: LocalDate?,
        isEnabled: Boolean = true,
        limitGigabytes: Long = 6L,
        cycleStartDay: Int = 1,
        storedByPeriod: Map<UsagePeriod, Long> = emptyMap(),
        todayMobileBytes: Long = 0L
    ): GetBillingCycleHistoryUseCase {
        val repository = FakeUsageRepository(
            earliestRecordedDate = earliestRecordedDate,
            storedMobileBytesByPeriod = storedByPeriod
        )

        return GetBillingCycleHistoryUseCase(
            preferences = FakeMonthlyLimitPreferences(
                MonthlyLimitSettings(
                    isEnabled = isEnabled,
                    limitMegabytes = limitGigabytes * MonthlyLimitSettings.MEGABYTES_PER_GIGABYTE,
                    cycleStartDay = cycleStartDay
                )
            ),
            repository = repository,
            getMobileUsageForPeriod = GetMobileUsageForPeriodUseCase(
                repository = repository,
                systemUsageReader = FakeSystemUsageReader.withTodayMobileBytes(todayMobileBytes),
                clock = clock
            ),
            clock = clock
        )
    }

    @Test
    fun `한도를 쓰지 않으면 이력이 없다`() = runTest {
        val disabled = useCaseWith(earliestRecordedDate = june.startDate, isEnabled = false)
        val noValue = useCaseWith(earliestRecordedDate = june.startDate, limitGigabytes = 0L)

        assertNull(disabled())
        assertNull(noValue())
    }

    @Test
    fun `기록이 없으면 끝난 주기가 비어 있다`() = runTest {
        val history = useCaseWith(earliestRecordedDate = null)()!!

        assertTrue(history.finished.isEmpty())
        assertNull(history.recordsStartDate)
    }

    @Test
    fun `끝난 주기는 최근이 먼저다`() = runTest {
        val history = useCaseWith(earliestRecordedDate = june.startDate)()!!

        assertEquals(listOf(august, july, june), history.finished.map { it.cycle })
    }

    @Test
    fun `이번 주기는 끝난 목록에 들어가지 않는다`() = runTest {
        val history = useCaseWith(earliestRecordedDate = june.startDate)()!!

        assertEquals(september, history.current.cycle)
        assertTrue(history.finished.none { it.cycle == september })
    }

    @Test
    fun `이번 주기는 오늘까지 센 것으로 표시한다`() = runTest {
        val history = useCaseWith(earliestRecordedDate = june.startDate)()!!

        assertEquals(today, history.current.countedThrough)
        assertEquals(12, history.current.countedDayCount)
    }

    /**
     * 최초 백필은 주기 중간에서 시작하므로 가장 오래된 주기는 거의 항상 반쪽이다.
     * 반쪽을 섞으면 그 달에 갑자기 적게 쓴 것처럼 보인다.
     */
    @Test
    fun `기록이 주기 전체를 덮지 못한 주기는 빼놓는다`() = runTest {
        // 6월 10일부터 기록이 있다. 6월 주기는 앞쪽 9일이 비어 있다.
        val history = useCaseWith(earliestRecordedDate = LocalDate.of(2026, 6, 10))()!!

        assertEquals(listOf(august, july), history.finished.map { it.cycle })
        assertTrue(history.hasOlderRecords)
    }

    @Test
    fun `기록 시작일이 주기 시작일과 같으면 그 주기도 센다`() = runTest {
        val history = useCaseWith(earliestRecordedDate = july.startDate)()!!

        assertEquals(listOf(august, july), history.finished.map { it.cycle })
        // 가장 오래된 주기가 기록 시작일에 딱 맞으므로 더 오래된 기록은 없다.
        assertTrue(!history.hasOlderRecords)
    }

    @Test
    fun `끝난 주기 수는 상한을 넘지 않는다`() = runTest {
        val history = useCaseWith(earliestRecordedDate = LocalDate.of(2020, 1, 1))()!!

        assertEquals(12, history.finished.size)
        assertTrue(history.hasOlderRecords)
    }

    /**
     * 주기마다 **그 주기의 범위로** 읽는지 본다.
     *
     * 한 기간을 돌려 쓰면 모든 줄이 같은 값이 되어 비교가 통째로 거짓이 된다.
     * 고정값 하나로는 이 실수를 잡을 수 없어 기간별로 다른 값을 넣었다.
     */
    @Test
    fun `주기마다 그 주기 범위로 사용량을 읽는다`() = runTest {
        val history = useCaseWith(
            earliestRecordedDate = july.startDate,
            storedByPeriod = mapOf(
                august to 5_240 * megabyte,
                july to 3_870 * megabyte
            )
        )()!!

        assertEquals(5_240 * megabyte, history.finished[0].mobileBytes)
        assertEquals(3_870 * megabyte, history.finished[1].mobileBytes)
    }

    /**
     * 이번 주기는 저장된 과거(주기 시작일 ~ 어제)에 시스템의 오늘치를 더한 값이다.
     * 요약 화면 한도 카드와 같은 경로를 쓰므로 두 화면의 숫자가 어긋나지 않는다.
     */
    @Test
    fun `이번 주기는 저장된 과거에 오늘치를 더한다`() = runTest {
        val history = useCaseWith(
            earliestRecordedDate = july.startDate,
            storedByPeriod = mapOf(
                UsagePeriod(september.startDate, today.minusDays(1)) to 1_000 * megabyte
            ),
            todayMobileBytes = 120 * megabyte
        )()!!

        assertEquals(1_120 * megabyte, history.current.mobileBytes)
    }

    /** 시작일 31일은 짧은 달에서 줄어든다. 유스케이스 수준에서도 주기가 겹치지 않아야 한다. */
    @Test
    fun `시작일 31일에서도 주기들이 겹치지 않는다`() = runTest {
        val history = useCaseWith(
            earliestRecordedDate = LocalDate.of(2025, 1, 1),
            cycleStartDay = 31
        )()!!

        val cycles = history.finished
        cycles.zipWithNext { newer, older ->
            assertEquals(
                "주기가 이어지지 않았다: older=${older.cycle}, newer=${newer.cycle}",
                newer.cycle.startDate,
                older.cycle.endDateInclusive.plusDays(1)
            )
        }
        assertEquals(history.current.cycle.startDate, cycles.first().cycle.endDateInclusive.plusDays(1))
    }
}
