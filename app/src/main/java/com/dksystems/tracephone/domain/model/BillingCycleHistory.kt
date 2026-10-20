package com.dksystems.tracephone.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 청구 주기 하나의 모바일 실적.
 *
 * ### [MonthlyLimitStatus]를 쓰지 않는 이유
 * 그 모델의 파생값은 모두 '오늘'을 기준으로 잘려 있다. 끝난 주기를 넣으면
 * 남은 날수가 늘 1, 경과율이 늘 100%가 되어 속도 판정이 **언제나 '여유'**로 나온다.
 * 거짓을 말하는 필드를 물려받는 대신, 끝난 주기에서 뜻이 있는 것만 새로 적는다.
 *
 * @property countedThrough 합계에 들어간 마지막 날(포함). 끝난 주기면 주기의 끝,
 *   진행 중이면 오늘이다. 날수가 아니라 날짜로 받는 이유는 화면이 그대로 적을 수 있기 때문이다.
 */
data class BillingCycleUsage(
    val cycle: UsagePeriod,
    val countedThrough: LocalDate,
    val mobileBytes: Long
) {

    init {
        require(
            !countedThrough.isBefore(cycle.startDate) &&
                !countedThrough.isAfter(cycle.endDateInclusive)
        ) {
            "센 마지막 날이 주기 밖입니다: cycle=$cycle, countedThrough=$countedThrough"
        }
    }

    /** 합계에 들어간 날수. 하루짜리면 1이다. */
    val countedDayCount: Int
        get() = (ChronoUnit.DAYS.between(cycle.startDate, countedThrough) + 1).toInt()

    /** 주기 끝까지 셌는지. 거짓이면 진행 중인 주기다. */
    val isComplete: Boolean
        get() = countedThrough == cycle.endDateInclusive

    /**
     * 하루 평균. **주기 길이가 아니라 센 날수로 나눈다.**
     *
     * 주기는 28~31일로 길이가 달라 총량만으로는 주기끼리 견줄 수 없다.
     * 진행 중인 주기를 주기 길이로 나누면 실제보다 작게 나와 '적게 쓰는 달'로 보인다.
     */
    val dailyAverageBytes: Long
        get() = mobileBytes / countedDayCount
}

/**
 * 주기 이력 화면 한 장의 재료.
 *
 * ### 한도에서 파생된 비율이 한 곳에도 없다
 * 주기별 한도 이력을 저장하지 않으므로 **지난 주기에 한도가 얼마였는지 알 방법이 없다.**
 * 그래서 이 모델은 어떤 주기와도 한도를 나눠 비율을 만들지 않는다.
 * 한도는 [limitFraction] 하나로 '지금 설정된 값'의 자리만 알려 주고,
 * 그것이 현재 값이라는 사실은 화면이 글자로 밝힌다.
 *
 * 비율을 만들 수단을 타입에서 빼 두면, 나중에 누가 줄에 `87%`를 적는 일 자체가 어려워진다.
 * 정직성을 주석이 아니라 타입으로 지킨다.
 *
 * @property finished 끝났고 **기록이 주기 전체를 덮는** 주기만. 최근이 먼저.
 * @property currentLimitBytes 지금 설정된 한도. **지난 주기에 적용되던 값이 아니다.**
 * @property recordsStartDate 저장소에 기록이 있는 첫 날. 없으면 null.
 */
data class BillingCycleHistory(
    val current: BillingCycleUsage,
    val finished: List<BillingCycleUsage>,
    val currentLimitBytes: Long,
    val recordsStartDate: LocalDate?
) {

    /** 끝난 주기 중 가장 많이 쓴 양. 한도를 정할 때의 '최악의 달'이다. */
    val peakBytes: Long
        get() = finished.maxOfOrNull { it.mobileBytes } ?: 0L

    /** 끝난 주기들의 평균. 진행 중인 주기는 넣지 않는다 — 아직 덜 쓴 값이 평균을 끌어내린다. */
    val averageBytes: Long
        get() = if (finished.isEmpty()) 0L else finished.sumOf { it.mobileBytes } / finished.size

    /**
     * 모든 줄이 함께 쓰는 자의 최댓값. **한도도 함께 넣는다.**
     *
     * 한도가 가장 많이 쓴 주기보다 크면(= 넉넉한 경우) 자에서 빠지는데, 그러면 기준선을
     * 그릴 자리가 없어 정작 알고 싶은 '얼마나 여유인가'가 사라진다.
     *
     * [AppListScale]과 규칙이 다른 지점이라 그 타입을 쓰지 않는다 —
     * 저쪽은 기준이 자를 넘으면 눈금을 일부러 지운다.
     */
    val scaleMaxBytes: Long
        get() = maxOf(peakBytes, currentLimitBytes)

    /** 같은 자 위에서 [bytes]가 차지하는 비율. 0.0 ~ 1.0. */
    fun fractionOf(bytes: Long): Float {
        if (scaleMaxBytes <= 0L) return 0f
        return (bytes.toFloat() / scaleMaxBytes).coerceIn(0f, 1f)
    }

    /** 현재 한도 기준선 자리. 한도가 없으면 null. 자가 한도를 담고 있어 1.0을 넘지 않는다. */
    val limitFraction: Float?
        get() = if (currentLimitBytes > 0L) fractionOf(currentLimitBytes) else null

    /**
     * 여기 보이는 것보다 오래된 기록이 더 있는지.
     *
     * 반쪽만 기록된 주기를 뺀 경우와 개수 상한에 걸린 경우를 함께 가리킨다.
     * 둘을 나누지 않는 이유는, 사용자가 할 행동("더 오래된 건 못 본다")이 같기 때문이다.
     */
    val hasOlderRecords: Boolean
        get() {
            val start = recordsStartDate ?: return false
            val oldestShown = finished.lastOrNull()?.cycle?.startDate ?: current.cycle.startDate
            return start.isBefore(oldestShown)
        }
}
