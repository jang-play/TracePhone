package com.dksystems.tracephone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MonthlyLimitStatusTest {

    private val gigabyte = 1_000L * MonthlyLimitSettings.BYTES_PER_MEGABYTE
    private val today = LocalDate.of(2026, 9, 20)

    private fun status(usedBytes: Long, limitGigabytes: Long = 10L) = MonthlyLimitStatus(
        settings = MonthlyLimitSettings(
            isEnabled = true,
            limitMegabytes = limitGigabytes * MonthlyLimitSettings.MEGABYTES_PER_GIGABYTE,
            cycleStartDay = 15
        ),
        // 9월 15일 ~ 10월 14일. 오늘이 20일이므로 25일 남았다.
        cycle = UsagePeriod(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 14)),
        usedBytes = usedBytes,
        today = today
    )

    @Test
    fun `남은 양은 한도에서 쓴 양을 뺀 값이다`() {
        val current = status(usedBytes = 6 * gigabyte)

        assertEquals(4 * gigabyte, current.remainingBytes)
        assertEquals(60, current.usedPercent)
        assertFalse(current.isExceeded)
    }

    @Test
    fun `한도를 넘겨도 남은 양은 음수가 되지 않는다`() {
        val current = status(usedBytes = 12 * gigabyte)

        assertEquals(0L, current.remainingBytes)
        assertTrue(current.isExceeded)
    }

    /**
     * 넘긴 정도는 백분율로 알리고 막대는 가득 찬 상태로 둔다.
     * 막대까지 1을 넘기면 화면이 깨진다.
     */
    @Test
    fun `막대 비율은 1을 넘지 않지만 백분율은 넘는다`() {
        val current = status(usedBytes = 12 * gigabyte)

        assertEquals(1f, current.progress, 0.0001f)
        assertEquals(120, current.usedPercent)
    }

    @Test
    fun `남은 날수는 오늘을 포함한다`() {
        // 9월 20일부터 10월 14일까지, 오늘을 포함해 25일.
        assertEquals(25, status(usedBytes = 0L).remainingDays)
    }

    @Test
    fun `하루 권장량은 남은 양을 남은 날수로 나눈 값이다`() {
        val current = status(usedBytes = 5 * gigabyte)

        assertEquals(5 * gigabyte / 25, current.dailyAllowanceBytes)
    }

    @Test
    fun `가장 낮은 지점에도 못 미치면 알릴 것이 없다`() {
        assertNull(status(usedBytes = 4 * gigabyte).reachedThreshold())
    }

    @Test
    fun `50퍼센트를 넘기면 절반 지점을 알린다`() {
        assertEquals(
            MonthlyLimitThreshold.HALF,
            status(usedBytes = 5 * gigabyte).reachedThreshold()
        )
    }

    @Test
    fun `80퍼센트를 넘기면 경고 지점을 알린다`() {
        assertEquals(
            MonthlyLimitThreshold.WARNING,
            status(usedBytes = 8 * gigabyte).reachedThreshold()
        )
    }

    @Test
    fun `90퍼센트를 넘기면 임박 지점을 알린다`() {
        assertEquals(
            MonthlyLimitThreshold.ALMOST,
            status(usedBytes = 9 * gigabyte).reachedThreshold()
        )
    }

    /**
     * 저장된 이름으로 '이미 알렸는지'를 판단하므로 **이름을 바꾸면 안 된다.**
     * 바꾸면 저장된 값이 어느 지점도 가리키지 못해 같은 지점을 다시 알린다.
     */
    @Test
    fun `지점의 이름과 퍼센트가 짝을 유지한다`() {
        assertEquals(
            listOf("HALF" to 50, "WARNING" to 80, "ALMOST" to 90, "REACHED" to 100),
            MonthlyLimitThreshold.entries.map { it.name to it.percent }
        )
    }

    /** 80%와 100%를 모두 넘긴 상태라면 더 급한 쪽을 알려야 한다. */
    @Test
    fun `한도를 다 쓰면 도달 지점을 알린다`() {
        assertEquals(
            MonthlyLimitThreshold.REACHED,
            status(usedBytes = 10 * gigabyte).reachedThreshold()
        )
    }

    @Test
    fun `한도가 0이면 어떤 지점도 알리지 않는다`() {
        val current = status(usedBytes = 5 * gigabyte, limitGigabytes = 0L)

        assertNull(current.reachedThreshold())
        assertEquals(0f, current.progress, 0.0001f)
        assertEquals(0, current.usedPercent)
    }

    /**
     * 주기는 9월 15일부터 30일간이고 오늘은 9월 20일이다.
     * 오늘을 포함해 6일이 지났으므로 20%다.
     *
     * 오늘을 포함해 세는 이유는 오늘 쓴 양이 이미 usedBytes에 들어 있기 때문이다.
     * 견주는 두 값의 기준일이 어긋나면 매일 아침 '빠름'으로 읽힌다.
     */
    @Test
    fun `주기가 지난 비율은 오늘을 포함해 센다`() {
        assertEquals(20, status(usedBytes = 0L).elapsedPercent)
    }

    @Test
    fun `주기 첫날에도 하루가 지난 것으로 센다`() {
        val firstDay = MonthlyLimitStatus(
            settings = MonthlyLimitSettings(
                isEnabled = true,
                limitMegabytes = 10L * MonthlyLimitSettings.MEGABYTES_PER_GIGABYTE,
                cycleStartDay = 15
            ),
            cycle = UsagePeriod(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 14)),
            usedBytes = 0L,
            today = LocalDate.of(2026, 9, 15)
        )

        assertEquals(3, firstDay.elapsedPercent)
    }

    /** 20%가 지났는데 50%를 썼다. 이 속도면 주기가 끝나기 전에 한도를 넘긴다. */
    @Test
    fun `주기 진행보다 많이 썼으면 빠르다고 본다`() {
        assertEquals(UsagePace.FASTER, status(usedBytes = 5 * gigabyte).pace())
    }

    @Test
    fun `주기 진행보다 적게 썼으면 여유 있다고 본다`() {
        assertEquals(UsagePace.SLOWER, status(usedBytes = 1 * gigabyte).pace())
    }

    /**
     * 22%와 20%는 사실상 같은 속도다.
     * 하루 이틀 치의 출렁임에 문구가 바뀌면 그 말을 믿지 않게 된다.
     */
    @Test
    fun `조금 앞선 정도로는 빠르다고 하지 않는다`() {
        assertEquals(UsagePace.ON_TRACK, status(usedBytes = 22 * gigabyte / 10).pace())
    }

    /** 주기 시작일과 오늘을 직접 정해 경과 날수를 조절한다. */
    private fun statusAfter(elapsedDays: Int, usedBytes: Long): MonthlyLimitStatus {
        val cycleStart = LocalDate.of(2026, 9, 1)
        return MonthlyLimitStatus(
            settings = MonthlyLimitSettings(
                isEnabled = true,
                limitMegabytes = 10L * MonthlyLimitSettings.MEGABYTES_PER_GIGABYTE,
                cycleStartDay = 1
            ),
            // 9월 1일 ~ 9월 30일, 30일짜리 주기.
            cycle = UsagePeriod(cycleStart, LocalDate.of(2026, 9, 30)),
            usedBytes = usedBytes,
            today = cycleStart.plusDays(elapsedDays - 1L)
        )
    }

    /**
     * 주기 초에는 분모가 작아 하루 몰아 쓴 것이 한 달치로 부풀려진다.
     * 실측에서 5일째 예상치가 9.5 GB였는데 실제는 5.55 GB였다.
     */
    @Test
    fun `주기 초에는 예상 사용량을 내놓지 않는다`() {
        (1..6).forEach { day ->
            assertNull(
                "$day 일째에 예상값이 나왔다",
                statusAfter(elapsedDays = day, usedBytes = 2 * gigabyte).projectedBytes
            )
        }
    }

    @Test
    fun `7일째부터 예상 사용량을 내놓는다`() {
        // 7일 동안 7 GB를 썼다. 하루 1 GB이므로 30일이면 30 GB.
        val current = statusAfter(elapsedDays = 7, usedBytes = 7 * gigabyte)

        assertEquals(30 * gigabyte, current.projectedBytes)
    }

    /** 주기 끝까지 왔으면 예상은 더 이상 예상이 아니라 실제와 같아야 한다. */
    @Test
    fun `주기 마지막 날의 예상은 실제 사용량과 같다`() {
        val current = statusAfter(elapsedDays = 30, usedBytes = 6 * gigabyte)

        assertEquals(6 * gigabyte, current.projectedBytes)
    }

    @Test
    fun `쓴 것이 없으면 예상도 0이다`() {
        assertEquals(0L, statusAfter(elapsedDays = 10, usedBytes = 0L).projectedBytes)
    }
}
