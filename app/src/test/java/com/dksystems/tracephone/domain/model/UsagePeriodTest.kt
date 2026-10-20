package com.dksystems.tracephone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class UsagePeriodTest {

    /** 2026-09-03은 목요일이다. 주 시작 요일 계산을 검증하기 좋은 날이다. */
    private val thursday = LocalDate.of(2026, 9, 3)

    private fun periodOf(
        unit: PeriodUnit,
        offset: Int = 0,
        today: LocalDate = thursday,
        firstDayOfWeek: DayOfWeek = UsagePeriod.DEFAULT_FIRST_DAY_OF_WEEK
    ) = UsagePeriod.of(PeriodSelection(unit, offset), today, firstDayOfWeek)

    // ---------- 현재 칸 ----------

    @Test
    fun `일 단위 현재 칸은 오늘 하루다`() {
        val period = periodOf(PeriodUnit.DAY)

        assertEquals(thursday, period.startDate)
        assertEquals(thursday, period.endDateInclusive)
        assertEquals(1, period.dayCount)
    }

    @Test
    fun `한 주는 월요일에 시작한다`() {
        assertEquals(DayOfWeek.MONDAY, UsagePeriod.DEFAULT_FIRST_DAY_OF_WEEK)
    }

    @Test
    fun `주 단위 현재 칸은 그 주 월요일부터 오늘까지다`() {
        val period = periodOf(PeriodUnit.WEEK)

        assertEquals(LocalDate.of(2026, 8, 31), period.startDate)
        assertEquals(thursday, period.endDateInclusive)
    }

    @Test
    fun `주 시작 요일을 일요일로 주면 시작일도 함께 바뀐다`() {
        val period = periodOf(PeriodUnit.WEEK, firstDayOfWeek = DayOfWeek.SUNDAY)

        assertEquals(LocalDate.of(2026, 8, 30), period.startDate)
    }

    @Test
    fun `오늘이 월요일이면 시작일은 오늘이다`() {
        val monday = LocalDate.of(2026, 8, 31)

        val period = periodOf(PeriodUnit.WEEK, today = monday)

        assertEquals(monday, period.startDate)
        assertEquals(monday, period.endDateInclusive)
    }

    @Test
    fun `월 단위 현재 칸은 1일부터 오늘까지다`() {
        val period = periodOf(PeriodUnit.MONTH)

        assertEquals(LocalDate.of(2026, 9, 1), period.startDate)
        assertEquals(thursday, period.endDateInclusive)
    }

    @Test
    fun `전체 누적은 가장 이른 날짜부터 오늘까지다`() {
        val period = periodOf(PeriodUnit.ALL)

        assertEquals(UsagePeriod.EARLIEST_POSSIBLE_DATE, period.startDate)
        assertEquals(thursday, period.endDateInclusive)
    }

    // ---------- 과거 칸으로 이동 ----------

    @Test
    fun `일 단위를 한 칸 되돌리면 어제가 된다`() {
        val period = periodOf(PeriodUnit.DAY, offset = -1)

        assertEquals(LocalDate.of(2026, 9, 2), period.startDate)
        assertEquals(LocalDate.of(2026, 9, 2), period.endDateInclusive)
    }

    @Test
    fun `지난 주는 오늘까지가 아니라 그 주 전체를 포함한다`() {
        val period = periodOf(PeriodUnit.WEEK, offset = -1)

        assertEquals(LocalDate.of(2026, 8, 24), period.startDate)
        assertEquals(LocalDate.of(2026, 8, 30), period.endDateInclusive)
        assertEquals(7, period.dayCount)
    }

    @Test
    fun `지난 달은 1일부터 말일까지를 포함한다`() {
        val period = periodOf(PeriodUnit.MONTH, offset = -1)

        assertEquals(LocalDate.of(2026, 8, 1), period.startDate)
        assertEquals(LocalDate.of(2026, 8, 31), period.endDateInclusive)
        assertEquals(31, period.dayCount)
    }

    @Test
    fun `월 이동은 각 달의 실제 길이를 따른다`() {
        val period = periodOf(PeriodUnit.MONTH, offset = -7)

        assertEquals(LocalDate.of(2026, 2, 1), period.startDate)
        assertEquals(LocalDate.of(2026, 2, 28), period.endDateInclusive)
    }

    @Test
    fun `월 이동은 해를 넘어간다`() {
        val period = periodOf(PeriodUnit.MONTH, offset = -9)

        assertEquals(LocalDate.of(2025, 12, 1), period.startDate)
        assertEquals(LocalDate.of(2025, 12, 31), period.endDateInclusive)
    }

    @Test
    fun `전체 누적은 이동해도 범위가 그대로다`() {
        val period = periodOf(PeriodUnit.ALL, offset = -5)

        assertEquals(UsagePeriod.EARLIEST_POSSIBLE_DATE, period.startDate)
        assertEquals(thursday, period.endDateInclusive)
    }

    // ---------- 선택 이동 규칙 ----------

    @Test
    fun `미래로는 갈 수 없다`() {
        val current = PeriodSelection(PeriodUnit.DAY)

        assertEquals(current, current.next())
    }

    @Test
    fun `과거로 갔다가 돌아오면 현재 칸이 된다`() {
        val selection = PeriodSelection(PeriodUnit.DAY).previous().previous()

        assertFalse(selection.isCurrent)
        assertTrue(selection.next().next().isCurrent)
    }

    @Test
    fun `몇 칸을 들어갔든 한 번에 현재로 돌아온다`() {
        val deep = PeriodSelection(PeriodUnit.DAY, offset = -30)

        val returned = deep.current()

        assertTrue(returned.isCurrent)
        // 단위는 그대로 둔다. 돌아오면서 보는 단위까지 바뀌면 사용자가 자리를 다시 잡아야 한다.
        assertEquals(PeriodUnit.DAY, returned.unit)
    }

    @Test
    fun `현재 칸에서 돌아오기를 해도 그대로다`() {
        val current = PeriodSelection(PeriodUnit.WEEK)

        assertEquals(current, current.current())
    }

    @Test
    fun `단위를 바꾸면 현재 칸에서 다시 시작한다`() {
        val movedBack = PeriodSelection(PeriodUnit.DAY, offset = -10)

        val switched = movedBack.withUnit(PeriodUnit.MONTH)

        assertEquals(PeriodUnit.MONTH, switched.unit)
        assertTrue(switched.isCurrent)
    }

    @Test
    fun `전체 누적은 이동 대상이 아니다`() {
        assertFalse(PeriodSelection(PeriodUnit.ALL).isNavigable)
        assertTrue(PeriodSelection(PeriodUnit.MONTH).isNavigable)
    }

    // ---------- 그 밖 ----------

    @Test
    fun `최근 N일은 오늘을 포함한다`() {
        val period = UsagePeriod.lastDays(dayCount = 7, today = thursday)

        assertEquals(LocalDate.of(2026, 8, 28), period.startDate)
        assertEquals(thursday, period.endDateInclusive)
        assertEquals(7, period.dayCount)
    }

    @Test
    fun `기간에 포함된 날짜를 오래된 순으로 돌려준다`() {
        val period = UsagePeriod.lastDays(dayCount = 3, today = thursday)

        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 3)
            ),
            period.eachDate()
        )
    }

    @Test
    fun `시작일이 종료일보다 뒤이면 만들 수 없다`() {
        assertThrows(IllegalArgumentException::class.java) {
            UsagePeriod(startDate = thursday, endDateInclusive = thursday.minusDays(1))
        }
    }

    @Test
    fun `최근 0일은 만들 수 없다`() {
        assertThrows(IllegalArgumentException::class.java) {
            UsagePeriod.lastDays(dayCount = 0, today = thursday)
        }
    }
}
