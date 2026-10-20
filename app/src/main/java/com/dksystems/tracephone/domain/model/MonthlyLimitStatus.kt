package com.dksystems.tracephone.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 이번 청구 주기의 한도 사용 현황. 화면과 위젯이 함께 쓴다.
 *
 * @property usedBytes 주기 시작일부터 오늘까지 모바일로 쓴 양
 * @property today 남은 날수를 세는 기준일
 */
data class MonthlyLimitStatus(
    val settings: MonthlyLimitSettings,
    val cycle: UsagePeriod,
    val usedBytes: Long,
    val today: LocalDate
) {

    val limitBytes: Long
        get() = settings.limitBytes

    val remainingBytes: Long
        get() = (limitBytes - usedBytes).coerceAtLeast(0L)

    val isExceeded: Boolean
        get() = usedBytes >= limitBytes

    /**
     * 진행 막대에 쓸 비율. 0.0 ~ 1.0으로 자른다.
     *
     * 넘긴 정도는 [usedPercent]가 100을 넘는 값으로 알려준다.
     * 막대까지 넘치게 그리면 화면이 깨진다.
     */
    val progress: Float
        get() = if (limitBytes <= 0L) 0f else (usedBytes.toFloat() / limitBytes).coerceIn(0f, 1f)

    /** 사용 비율(%). 한도를 넘기면 100보다 큰 값이 된다. */
    val usedPercent: Int
        get() = if (limitBytes <= 0L) 0 else ((usedBytes * PERCENT_SCALE) / limitBytes).toInt()

    /** 오늘을 포함해 주기가 끝날 때까지 남은 날수. 최소 1. */
    val remainingDays: Int
        get() = (ChronoUnit.DAYS.between(today, cycle.endDateInclusive).toInt() + 1)
            .coerceAtLeast(1)

    /**
     * 남은 날수로 남은 양을 고르게 나눈 하루치.
     *
     * "얼마 남았나"보다 "하루에 얼마씩 써도 되나"가 행동으로 옮기기 쉽다.
     */
    val dailyAllowanceBytes: Long
        get() = remainingBytes / remainingDays

    /**
     * 주기가 얼마나 지났는가. 0.0 ~ 1.0.
     *
     * 오늘을 포함해 센다. 오늘 쓴 양이 이미 [usedBytes]에 들어 있으므로
     * 견주는 두 값의 기준일이 같아야 한다.
     */
    val elapsedRatio: Float
        get() = elapsedDays.toFloat() / cycle.dayCount

    /** [elapsedRatio]를 사람이 읽는 백분율로. */
    val elapsedPercent: Int
        get() = ((elapsedDays * PERCENT_SCALE) / cycle.dayCount).toInt()

    /**
     * 지금까지 쓴 양이 주기 진행에 견줘 어떤가.
     *
     * "4.81 GB 남음"만으로는 이 속도가 괜찮은지 알 수 없다.
     * 날짜를 세어 나눠 봐야 알 수 있던 것을 대신 판단해 준다.
     */
    fun pace(): UsagePace {
        val difference = progress - elapsedRatio

        return when {
            difference > PACE_TOLERANCE_RATIO -> UsagePace.FASTER
            difference < -PACE_TOLERANCE_RATIO -> UsagePace.SLOWER
            else -> UsagePace.ON_TRACK
        }
    }

    /**
     * 지금 속도로 주기 끝까지 갔을 때의 사용량. 아직 판단할 수 없으면 null.
     *
     * ### 주기 초에는 내놓지 않는다
     * 분모가 작아 하루치 출렁임이 그대로 몇 배로 부풀려진다. 실측에서 1~5일째 예상치가
     * 실제의 1.5~2.5배로 튀었다 — 한 주기는 5일째에 9.5 GB를 예상했으나 실제는 5.55 GB였다.
     * [MIN_DAYS_FOR_PROJECTION]은 그 관측에서 나온 값이다.
     *
     * ### 이것으로 알림을 만들지 않는다
     * [pace]를 그대로 알림 조건으로 쓰면 **네 주기 중 세 주기가 1일째에 울린다.**
     * 예측을 조건으로 바꿔도 네 번 중 한 번은 틀렸다. 알림은 틀리면 대가가 크고
     * 화면에 숫자로 적으면 대가가 없어, 화면에만 적기로 했다.
     */
    val projectedBytes: Long?
        get() = if (elapsedDays < MIN_DAYS_FOR_PROJECTION) {
            null
        } else {
            usedBytes / elapsedDays * cycle.dayCount
        }

    /** 오늘을 포함해 주기 시작일부터 지난 날수. 주기 길이를 넘지 않는다. */
    private val elapsedDays: Int
        get() = (ChronoUnit.DAYS.between(cycle.startDate, today).toInt() + 1)
            .coerceIn(1, cycle.dayCount)

    /** 지금 상태에서 알려야 할 지점. 없으면 null. 더 높은 지점을 우선한다. */
    fun reachedThreshold(): MonthlyLimitThreshold? =
        MonthlyLimitThreshold.entries
            .sortedByDescending { it.percent }
            .firstOrNull { it.isReachedBy(usedBytes, limitBytes) }

    private companion object {
        const val PERCENT_SCALE = 100L

        /**
         * 예상 사용량을 내놓기 전에 지나야 할 날수.
         *
         * 이보다 짧으면 분모가 작아 하루 몰아 쓴 것이 한 달치로 부풀려진다.
         * 실측에서 1~5일째 예상치가 실제의 1.5~2.5배로 튀었고, 7일부터 쓸 만해졌다.
         */
        const val MIN_DAYS_FOR_PROJECTION = 7

        /**
         * 이만큼의 차이는 앞서거나 뒤처졌다고 하지 않는다.
         *
         * 하루 이틀 치의 출렁임에도 문구가 바뀌면 그 말을 믿지 않게 된다.
         */
        const val PACE_TOLERANCE_RATIO = 0.05f
    }
}

/** 지금까지 쓴 양이 주기 진행에 견줘 어떤가. */
enum class UsagePace {

    /** 주기가 지난 만큼보다 많이 썼다. 이 속도면 주기가 끝나기 전에 한도를 넘긴다. */
    FASTER,

    /** 주기 진행과 비슷하다. */
    ON_TRACK,

    /** 주기가 지난 만큼보다 적게 썼다. */
    SLOWER
}
