package com.dksystems.tracephone.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 조회 기간의 단위. 실제 날짜 범위로 바꾸는 일은 [UsagePeriod.of]가 맡는다. */
enum class PeriodUnit {
    DAY,
    WEEK,
    MONTH,

    /** 저장된 기록 전체. 이동 개념이 없다. */
    ALL
}

/**
 * 사용자가 보고 있는 기간. 단위와 '지금으로부터 몇 칸 전인지'로 표현한다.
 *
 * 날짜를 직접 들고 다니지 않는 이유는, 자정을 넘겨도 선택이 자동으로 따라오게 하기 위해서다.
 * `offset = 0`인 '오늘'은 날짜가 바뀌면 새 날짜를 가리켜야 한다.
 *
 * @property offset 0이 현재, -1이 직전 칸이다. 미래는 없으므로 양수가 되지 않는다.
 */
data class PeriodSelection(
    val unit: PeriodUnit,
    val offset: Int = CURRENT_OFFSET
) {

    val isCurrent: Boolean
        get() = offset == CURRENT_OFFSET

    /** 이동 개념이 없는 단위인지. 화살표를 감출지 판단하는 데 쓴다. */
    val isNavigable: Boolean
        get() = unit != PeriodUnit.ALL

    fun previous(): PeriodSelection = copy(offset = offset - 1)

    /** 미래로는 갈 수 없으므로 현재를 넘지 않도록 자른다. */
    fun next(): PeriodSelection = copy(offset = (offset + 1).coerceAtMost(CURRENT_OFFSET))

    /**
     * 현재 칸으로 되돌린다. 단위는 그대로 둔다.
     *
     * 한 칸씩 여러 번 눌러 과거로 간 사용자가 같은 횟수만큼 되돌아오지 않아도 되게 한다.
     */
    fun current(): PeriodSelection = copy(offset = CURRENT_OFFSET)

    /** 단위를 바꾸면 항상 현재 칸에서 다시 시작한다. 다른 단위의 offset은 의미가 다르다. */
    fun withUnit(newUnit: PeriodUnit): PeriodSelection = PeriodSelection(newUnit)

    companion object {
        const val CURRENT_OFFSET = 0

        val DEFAULT = PeriodSelection(PeriodUnit.MONTH)
    }
}

/**
 * 조회할 날짜 범위. 양 끝을 모두 포함한다.
 *
 * @property startDate 시작일 (포함)
 * @property endDateInclusive 종료일 (포함)
 */
data class UsagePeriod(
    val startDate: LocalDate,
    val endDateInclusive: LocalDate
) {

    init {
        require(!startDate.isAfter(endDateInclusive)) {
            "시작일이 종료일보다 뒤일 수 없습니다: start=$startDate, end=$endDateInclusive"
        }
    }

    /** 범위에 포함된 날짜 수. 하루짜리 기간이면 1이다. */
    val dayCount: Int
        get() = (ChronoUnit.DAYS.between(startDate, endDateInclusive) + 1).toInt()

    /** 범위에 포함된 모든 날짜를 오래된 순으로 돌려준다. 하루 단위 조회에 사용한다. */
    fun eachDate(): List<LocalDate> =
        (0 until dayCount).map { offset -> startDate.plusDays(offset.toLong()) }

    companion object {

        /**
         * 전체 누적 조회의 시작점.
         *
         * 저장소에는 앱 설치 이후의 기록만 존재하므로, 충분히 과거의 고정 날짜를
         * 하한으로 두면 별도 분기 없이 "전체"를 표현할 수 있다.
         */
        val EARLIEST_POSSIBLE_DATE: LocalDate = LocalDate.of(1970, 1, 1)

        private const val DAYS_IN_WEEK = 7

        /**
         * 한 주의 시작 요일.
         *
         * 기기 로케일을 따르지 않고 월요일로 고정한다.
         * 로케일에 맡기면 같은 데이터를 보고도 기기마다 '이번 주'의 범위가 달라져
         * 수치를 비교할 수 없게 된다.
         */
        val DEFAULT_FIRST_DAY_OF_WEEK: DayOfWeek = DayOfWeek.MONDAY

        /**
         * 선택을 실제 날짜 범위로 바꾼다.
         *
         * 현재 칸(offset 0)의 주와 달은 **오늘까지**만 포함한다. 아직 오지 않은 날은 기록도 없다.
         * 지난 칸은 그 주/달 전체를 포함한다.
         *
         * @param today 오늘 날짜. 테스트에서 고정값을 넣을 수 있도록 주입받는다.
         * @param firstDayOfWeek 주의 시작 요일. 이 앱은 월요일을 한 주의 시작으로 본다.
         */
        fun of(
            selection: PeriodSelection,
            today: LocalDate,
            firstDayOfWeek: DayOfWeek = DEFAULT_FIRST_DAY_OF_WEEK
        ): UsagePeriod {
            val offset = selection.offset.toLong()

            return when (selection.unit) {
                PeriodUnit.DAY -> today.plusDays(offset).let { date -> UsagePeriod(date, date) }

                PeriodUnit.WEEK -> {
                    val weekStart = startOfWeek(today, firstDayOfWeek).plusWeeks(offset)
                    val weekEnd = weekStart.plusDays(DAYS_IN_WEEK - 1L)
                    UsagePeriod(weekStart, minOf(weekEnd, today))
                }

                PeriodUnit.MONTH -> {
                    val monthStart = today.withDayOfMonth(1).plusMonths(offset)
                    val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
                    UsagePeriod(monthStart, minOf(monthEnd, today))
                }

                PeriodUnit.ALL -> UsagePeriod(EARLIEST_POSSIBLE_DATE, today)
            }
        }

        /**
         * 오늘을 포함한 최근 [dayCount]일 범위를 만든다. 동기화 대상 날짜를 정할 때 쓴다.
         *
         * @param dayCount 1 이상이어야 한다.
         */
        fun lastDays(dayCount: Long, today: LocalDate): UsagePeriod {
            require(dayCount >= 1) { "기간은 최소 1일이어야 합니다: dayCount=$dayCount" }
            return UsagePeriod(
                startDate = today.minusDays(dayCount - 1),
                endDateInclusive = today
            )
        }

        /**
         * 주의 시작일을 구한다.
         *
         * `today`가 이미 주 시작 요일이면 그대로 돌려준다.
         * 그렇지 않으면 직전 시작 요일까지 되돌아간다.
         */
        private fun startOfWeek(today: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate {
            val daysSinceWeekStart =
                (today.dayOfWeek.value - firstDayOfWeek.value + DAYS_IN_WEEK) % DAYS_IN_WEEK
            return today.minusDays(daysSinceWeekStart.toLong())
        }
    }
}
