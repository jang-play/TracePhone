package com.dksystems.tracephone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BillingCycleTest {

    @Test
    fun `시작일 이후면 이번 달 시작일부터다`() {
        val cycle = BillingCycle.periodContaining(
            today = LocalDate.of(2026, 9, 20),
            startDayOfMonth = 15
        )

        assertEquals(LocalDate.of(2026, 9, 15), cycle.startDate)
        assertEquals(LocalDate.of(2026, 10, 14), cycle.endDateInclusive)
    }

    @Test
    fun `시작일 전이면 지난 달 시작일부터다`() {
        val cycle = BillingCycle.periodContaining(
            today = LocalDate.of(2026, 9, 3),
            startDayOfMonth = 15
        )

        assertEquals(LocalDate.of(2026, 8, 15), cycle.startDate)
        assertEquals(LocalDate.of(2026, 9, 14), cycle.endDateInclusive)
    }

    @Test
    fun `시작일 당일은 새 주기의 첫날이다`() {
        val cycle = BillingCycle.periodContaining(
            today = LocalDate.of(2026, 9, 15),
            startDayOfMonth = 15
        )

        assertEquals(LocalDate.of(2026, 9, 15), cycle.startDate)
    }

    @Test
    fun `1일 시작이면 달력 월과 같다`() {
        val cycle = BillingCycle.periodContaining(
            today = LocalDate.of(2026, 9, 20),
            startDayOfMonth = 1
        )

        assertEquals(LocalDate.of(2026, 9, 1), cycle.startDate)
        assertEquals(LocalDate.of(2026, 9, 30), cycle.endDateInclusive)
    }

    /**
     * 시작일이 그 달에 없을 때가 이 계산에서 가장 틀리기 쉬운 지점이다.
     * 2월에는 31일이 없으므로 마지막 날로 줄여 잡는다.
     */
    @Test
    fun `31일 시작인데 2월이면 마지막 날로 줄여 잡는다`() {
        val cycle = BillingCycle.periodContaining(
            today = LocalDate.of(2026, 3, 1),
            startDayOfMonth = 31
        )

        assertEquals(LocalDate.of(2026, 2, 28), cycle.startDate)
        assertEquals(LocalDate.of(2026, 3, 30), cycle.endDateInclusive)
    }

    /**
     * 줄여 잡은 뒤에도 주기가 끊기지 않아야 한다.
     * 하루라도 어느 주기에도 속하지 않으면 그 날 사용량이 사라진다.
     */
    @Test
    fun `31일 시작이어도 주기가 빈틈없이 이어진다`() {
        val startDay = 31
        var date = LocalDate.of(2026, 1, 1)
        val lastDate = LocalDate.of(2027, 1, 1)

        var cycle = BillingCycle.periodContaining(date, startDay)
        while (date.isBefore(lastDate)) {
            val currentCycle = BillingCycle.periodContaining(date, startDay)

            if (currentCycle != cycle) {
                // 새 주기는 앞 주기가 끝난 바로 다음 날에 시작해야 한다.
                assertEquals(cycle.endDateInclusive.plusDays(1), currentCycle.startDate)
                cycle = currentCycle
            }

            // 어느 날이든 자기 주기 안에 들어 있어야 한다.
            assertEquals(true, !date.isBefore(currentCycle.startDate))
            assertEquals(true, !date.isAfter(currentCycle.endDateInclusive))

            date = date.plusDays(1)
        }
    }

    @Test
    fun `윤년 2월 29일도 시작일이 될 수 있다`() {
        val cycle = BillingCycle.periodContaining(
            today = LocalDate.of(2028, 3, 1),
            startDayOfMonth = 30
        )

        assertEquals(LocalDate.of(2028, 2, 29), cycle.startDate)
        assertEquals(LocalDate.of(2028, 3, 29), cycle.endDateInclusive)
    }

    @Test
    fun `범위를 벗어난 시작일은 잘라서 쓴다`() {
        val tooSmall = BillingCycle.periodContaining(LocalDate.of(2026, 9, 20), startDayOfMonth = 0)
        val tooLarge = BillingCycle.periodContaining(LocalDate.of(2026, 9, 20), startDayOfMonth = 99)

        assertEquals(LocalDate.of(2026, 9, 1), tooSmall.startDate)
        assertEquals(LocalDate.of(2026, 8, 31), tooLarge.startDate)
    }

    @Test
    fun `직전 주기는 한 달 앞이다`() {
        val previous = BillingCycle.previousPeriod(
            period = UsagePeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
            startDayOfMonth = 1
        )

        assertEquals(LocalDate.of(2026, 8, 1), previous.startDate)
        assertEquals(LocalDate.of(2026, 8, 31), previous.endDateInclusive)
    }

    @Test
    fun `시작일이 1일이 아니어도 직전 주기를 찾는다`() {
        val previous = BillingCycle.previousPeriod(
            period = UsagePeriod(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 14)),
            startDayOfMonth = 15
        )

        assertEquals(LocalDate.of(2026, 8, 15), previous.startDate)
        assertEquals(LocalDate.of(2026, 9, 14), previous.endDateInclusive)
    }

    /**
     * 시작일 31일은 짧은 달에서 줄어든다. 줄어든 주기를 거슬러 올라가는 길이
     * 가장 깨지기 쉽다 — 달을 하나 빼는 방식으로 구현하면 여기서 어긋난다.
     */
    @Test
    fun `시작일 31일 - 3월 주기의 앞은 2월 말에 시작한 주기다`() {
        val previous = BillingCycle.previousPeriod(
            period = UsagePeriod(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 29)),
            startDayOfMonth = 31
        )

        assertEquals(LocalDate.of(2026, 2, 28), previous.startDate)
        assertEquals(LocalDate.of(2026, 3, 30), previous.endDateInclusive)
    }

    @Test
    fun `시작일 31일 - 줄어든 2월 주기의 앞은 1월 31일 주기다`() {
        val previous = BillingCycle.previousPeriod(
            period = UsagePeriod(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 30)),
            startDayOfMonth = 31
        )

        assertEquals(LocalDate.of(2026, 1, 31), previous.startDate)
        assertEquals(LocalDate.of(2026, 2, 27), previous.endDateInclusive)
    }

    @Test
    fun `직전 주기는 해를 넘어간다`() {
        val previous = BillingCycle.previousPeriod(
            period = UsagePeriod(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 27)),
            startDayOfMonth = 31
        )

        assertEquals(LocalDate.of(2025, 12, 31), previous.startDate)
        assertEquals(LocalDate.of(2026, 1, 30), previous.endDateInclusive)
    }

    @Test
    fun `윤년에도 직전 주기를 찾는다`() {
        val previous = BillingCycle.previousPeriod(
            period = UsagePeriod(LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 29)),
            startDayOfMonth = 30
        )

        assertEquals(LocalDate.of(2024, 1, 30), previous.startDate)
        assertEquals(LocalDate.of(2024, 2, 28), previous.endDateInclusive)
    }

    /**
     * 이 앱이 주기를 거슬러 올라갈 수 있는 근거 자체를 고정한다.
     *
     * 하루라도 비면 그날 사용량이 어느 주기에도 들어가지 않고, 겹치면 두 번 세어진다.
     * 주기 이력 화면이 이 성질에 기대어 달 빼기 계산을 따로 두지 않는다.
     */
    @Test
    fun `어떤 시작일에서도 직전 주기 종료일 다음날이 현재 주기 시작일이다`() {
        listOf(1, 15, 28, 29, 30, 31).forEach { startDay ->
            (1..12).forEach { month ->
                val current = BillingCycle.periodContaining(
                    today = LocalDate.of(2026, month, 20),
                    startDayOfMonth = startDay
                )
                val previous = BillingCycle.previousPeriod(current, startDay)

                assertEquals(
                    "시작일 $startDay, ${month}월에서 주기가 이어지지 않았다",
                    current.startDate,
                    previous.endDateInclusive.plusDays(1)
                )
            }
        }
    }

    /** 한 번은 맞고 여러 번은 틀리는 구현을 막는다. 윤년과 짧은 달을 모두 지나게 잡았다. */
    @Test
    fun `스물네 번 거슬러 올라가도 빈틈이나 겹침이 없다`() {
        val startDay = 31
        var cycle = BillingCycle.periodContaining(LocalDate.of(2026, 3, 15), startDay)

        repeat(24) {
            val previous = BillingCycle.previousPeriod(cycle, startDay)

            assertEquals(
                "주기가 이어지지 않았다: previous=$previous, cycle=$cycle",
                cycle.startDate,
                previous.endDateInclusive.plusDays(1)
            )
            cycle = previous
        }
    }
}
