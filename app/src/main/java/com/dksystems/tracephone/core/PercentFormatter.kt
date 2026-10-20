package com.dksystems.tracephone.core

import kotlin.math.roundToInt

/**
 * 비율(0.0 ~ 1.0)을 화면에 적을 백분율 정수로 바꾼다.
 *
 * 소수점 없는 정수만 쓴다. 사용량 비율은 대략의 크기를 읽는 값이라
 * 소수점을 붙이면 읽는 시간만 늘고 판단은 달라지지 않는다.
 */
object PercentFormatter {

    private const val PERCENT_MAX = 100

    /** 0 이상 100 이하의 정수로 자른다. 반올림으로 범위를 벗어나는 일을 막는다. */
    fun toPercent(ratio: Float): Int =
        (ratio * PERCENT_MAX).roundToInt().coerceIn(0, PERCENT_MAX)
}
