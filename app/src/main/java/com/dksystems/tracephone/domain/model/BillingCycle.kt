package com.dksystems.tracephone.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * 요금제 청구 주기를 계산한다.
 *
 * ### 시작일이 그 달에 없을 때
 * 시작일을 31일로 두면 2월에는 그런 날짜가 없다. 그 달의 마지막 날로 줄여 잡는다.
 * 그러면 주기가 `1/31 ~ 2/27`, `2/28 ~ 3/30`처럼 이어져 빈틈도 겹침도 생기지 않는다.
 * 건너뛰거나 다음 달로 미루면 하루가 어느 주기에도 속하지 않거나 두 주기에 겹쳐,
 * 그 하루의 사용량이 사라지거나 두 번 세어진다.
 */
object BillingCycle {

    const val MIN_START_DAY = 1
    const val MAX_START_DAY = 31
    const val DEFAULT_START_DAY = 1

    /**
     * [today]가 속한 청구 주기를 돌려준다.
     *
     * @param startDayOfMonth 매월 며칠에 초기화되는지. 범위를 벗어나면 잘라서 쓴다.
     */
    fun periodContaining(today: LocalDate, startDayOfMonth: Int): UsagePeriod {
        val startDay = startDayOfMonth.coerceIn(MIN_START_DAY, MAX_START_DAY)

        val thisMonthStart = startDateIn(YearMonth.from(today), startDay)
        val currentStart = if (today.isBefore(thisMonthStart)) {
            startDateIn(YearMonth.from(today).minusMonths(1), startDay)
        } else {
            thisMonthStart
        }

        val nextStart = startDateIn(YearMonth.from(currentStart).plusMonths(1), startDay)
        return UsagePeriod(
            startDate = currentStart,
            endDateInclusive = nextStart.minusDays(1)
        )
    }

    /**
     * [period] 바로 앞의 주기.
     *
     * 시작일 하루 전은 반드시 앞 주기에 속한다 — [periodContaining]이 빈틈도 겹침도 만들지
     * 않기 때문이다. 시작일을 31일로 둔 2월처럼 달마다 길이가 달라지는 경우까지 이 성질이
     * 지켜지므로, 달을 빼는 계산을 따로 두지 않는다.
     *
     * 예: `3/31 ~ 4/29`의 앞은 `2/28 ~ 3/30`이고, 그 앞은 `1/31 ~ 2/27`이다.
     */
    fun previousPeriod(period: UsagePeriod, startDayOfMonth: Int): UsagePeriod =
        periodContaining(period.startDate.minusDays(1), startDayOfMonth)

    private fun startDateIn(yearMonth: YearMonth, startDay: Int): LocalDate =
        yearMonth.atDay(startDay.coerceAtMost(yearMonth.lengthOfMonth()))
}
