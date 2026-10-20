package com.dksystems.tracephone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 주기 이력의 집계 규칙을 고정한다.
 *
 * 이 값들이 틀려도 화면은 멀쩡히 그려진다. 주기끼리의 비교만 조용히 어긋나
 * 사용자가 한도를 잘못 잡는다. 눈으로는 확인되지 않는 종류의 오류다.
 */
class BillingCycleHistoryTest {

    private val megabyte = 1_000_000L

    /** 9월 한 달. 30일짜리 주기. */
    private val september = UsagePeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
    private val august = UsagePeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
    private val july = UsagePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))

    private fun finishedCycle(cycle: UsagePeriod, megabytes: Long) = BillingCycleUsage(
        cycle = cycle,
        countedThrough = cycle.endDateInclusive,
        mobileBytes = megabytes * megabyte
    )

    /** 9월 12일까지 쓴 진행 중인 주기. */
    private fun currentCycle(megabytes: Long) = BillingCycleUsage(
        cycle = september,
        countedThrough = LocalDate.of(2026, 9, 12),
        mobileBytes = megabytes * megabyte
    )

    private fun historyOf(
        current: BillingCycleUsage = currentCycle(megabytes = 1_200L),
        finished: List<BillingCycleUsage> = emptyList(),
        limitMegabytes: Long = 6_000L,
        recordsStartDate: LocalDate? = null
    ) = BillingCycleHistory(
        current = current,
        finished = finished,
        currentLimitBytes = limitMegabytes * megabyte,
        recordsStartDate = recordsStartDate
    )

    /**
     * 주기가 28~31일로 길이가 달라 총량만으로는 견줄 수 없다.
     * 진행 중인 주기를 주기 길이로 나누면 실제보다 작게 나와 '적게 쓰는 달'로 보인다.
     */
    @Test
    fun `하루 평균은 주기 길이가 아니라 센 날수로 나눈다`() {
        val current = currentCycle(megabytes = 1_200L)

        assertEquals(12, current.countedDayCount)
        // 1,200 MB / 12일 = 100 MB. 주기 길이 30일로 나누면 40 MB가 되어 실제보다 작다.
        assertEquals(100 * megabyte, current.dailyAverageBytes)
    }

    @Test
    fun `끝난 주기는 센 날수가 주기 길이와 같다`() {
        val finished = finishedCycle(august, megabytes = 3_100L)

        assertEquals(31, finished.countedDayCount)
        assertTrue(finished.isComplete)
        assertEquals(100 * megabyte, finished.dailyAverageBytes)
    }

    @Test
    fun `진행 중인 주기는 끝까지 센 것이 아니다`() {
        assertFalse(currentCycle(megabytes = 1_200L).isComplete)
    }

    @Test
    fun `센 마지막 날이 주기 밖이면 만들 수 없다`() {
        listOf(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 10, 1)).forEach { outside ->
            val failure = runCatching {
                BillingCycleUsage(cycle = september, countedThrough = outside, mobileBytes = 0L)
            }.exceptionOrNull()

            assertTrue(
                "$outside 를 받아들였다",
                failure is IllegalArgumentException
            )
        }
    }

    @Test
    fun `가장 많이 쓴 주기는 끝난 주기 중에서 고른다`() {
        val history = historyOf(
            // 진행 중인 주기가 가장 큰 값을 갖더라도 '최악의 달'이 되지 않는다.
            current = currentCycle(megabytes = 9_000L),
            finished = listOf(
                finishedCycle(august, megabytes = 5_240L),
                finishedCycle(july, megabytes = 3_870L)
            )
        )

        assertEquals(5_240 * megabyte, history.peakBytes)
    }

    /** 진행 중인 주기는 아직 덜 쓴 값이라 평균을 끌어내린다. */
    @Test
    fun `평균에 이번 주기를 넣지 않는다`() {
        val history = historyOf(
            current = currentCycle(megabytes = 100L),
            finished = listOf(
                finishedCycle(august, megabytes = 5_000L),
                finishedCycle(july, megabytes = 3_000L)
            )
        )

        assertEquals(4_000 * megabyte, history.averageBytes)
    }

    @Test
    fun `끝난 주기가 없으면 평균과 최대가 0이다`() {
        val history = historyOf(finished = emptyList())

        assertEquals(0L, history.peakBytes)
        assertEquals(0L, history.averageBytes)
    }

    /**
     * 한도가 넉넉하면 자에서 빠질 수 있다. 그러면 기준선을 그릴 자리가 없어
     * 정작 알고 싶은 '얼마나 여유인가'가 사라진다.
     */
    @Test
    fun `한도가 가장 많이 쓴 주기보다 커도 기준선이 자 안에 들어온다`() {
        val history = historyOf(
            finished = listOf(finishedCycle(august, megabytes = 3_000L)),
            limitMegabytes = 6_000L
        )

        assertEquals(6_000 * megabyte, history.scaleMaxBytes)
        assertEquals(1f, history.limitFraction!!, 0f)
        assertEquals(0.5f, history.fractionOf(3_000 * megabyte), 0.001f)
    }

    @Test
    fun `한도가 가장 많이 쓴 주기보다 작으면 자는 그 주기에 맞춘다`() {
        val history = historyOf(
            finished = listOf(finishedCycle(august, megabytes = 8_000L)),
            limitMegabytes = 6_000L
        )

        assertEquals(8_000 * megabyte, history.scaleMaxBytes)
        assertEquals(0.75f, history.limitFraction!!, 0.001f)
    }

    @Test
    fun `한도가 없으면 기준선도 없다`() {
        val history = historyOf(
            finished = listOf(finishedCycle(august, megabytes = 3_000L)),
            limitMegabytes = 0L
        )

        assertNull(history.limitFraction)
    }

    @Test
    fun `잴 것이 아무것도 없으면 0으로 나누지 않는다`() {
        val history = historyOf(
            current = currentCycle(megabytes = 0L),
            finished = emptyList(),
            limitMegabytes = 0L
        )

        assertEquals(0L, history.scaleMaxBytes)
        assertEquals(0f, history.fractionOf(100 * megabyte), 0f)
        assertNull(history.limitFraction)
    }

    @Test
    fun `막대는 자를 넘지 않는다`() {
        val history = historyOf(
            finished = listOf(finishedCycle(august, megabytes = 3_000L)),
            limitMegabytes = 0L
        )

        assertEquals(1f, history.fractionOf(9_000 * megabyte), 0f)
        assertEquals(0f, history.fractionOf(-1L), 0f)
    }

    @Test
    fun `보이는 것보다 오래된 기록이 있으면 알린다`() {
        val history = historyOf(
            finished = listOf(finishedCycle(august, megabytes = 3_000L)),
            recordsStartDate = LocalDate.of(2026, 6, 6)
        )

        assertTrue(history.hasOlderRecords)
    }

    @Test
    fun `가장 오래된 주기가 기록 시작일과 같으면 더 오래된 기록이 없다`() {
        val history = historyOf(
            finished = listOf(finishedCycle(august, megabytes = 3_000L)),
            recordsStartDate = august.startDate
        )

        assertFalse(history.hasOlderRecords)
    }

    @Test
    fun `끝난 주기가 없으면 이번 주기 시작일을 기준으로 본다`() {
        val beforeCurrent = historyOf(
            finished = emptyList(),
            recordsStartDate = LocalDate.of(2026, 8, 20)
        )
        val sameAsCurrent = historyOf(
            finished = emptyList(),
            recordsStartDate = september.startDate
        )

        assertTrue(beforeCurrent.hasOlderRecords)
        assertFalse(sameAsCurrent.hasOlderRecords)
    }

    @Test
    fun `기록이 아예 없으면 더 오래된 기록도 없다`() {
        assertFalse(historyOf(recordsStartDate = null).hasOlderRecords)
    }
}
