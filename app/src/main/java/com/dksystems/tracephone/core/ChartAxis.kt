package com.dksystems.tracephone.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * 그래프 세로축의 눈금.
 *
 * ### 왜 실제 최댓값을 그대로 쓰지 않는가
 * 최댓값이 `1,873,204,915`바이트라면 축 맨 위에 `1.87 GB`가 찍힌다.
 * 그러면 중간 높이의 막대가 얼마인지 암산해야 한다.
 * 눈금 간격을 1·2·5 × 10ⁿ 꼴로 고르면 `0 / 1 GB / 2 GB`처럼 읽히는 값만 남아
 * 막대 높이를 눈으로 가늠할 수 있다.
 *
 * @property maxValue 축 맨 위 값. 실제 최댓값 이상이다.
 * @property tickValues 0부터 [maxValue]까지 같은 간격으로 놓인 눈금 값
 */
data class ChartAxis(
    val maxValue: Long,
    val tickValues: List<Long>
) {

    companion object {

        /** 눈금 사이 구간의 목표 개수. 너무 촘촘하면 라벨이 겹치고 성기면 값을 가늠하기 어렵다. */
        const val DEFAULT_INTERVAL_COUNT = 4

        /** 눈금 간격으로 허용하는 가수(mantissa). 이 값들만 써야 라벨이 깔끔하게 읽힌다. */
        private val NICE_MANTISSAS = listOf(1.0, 2.0, 5.0, 10.0)

        /**
         * @param maxValue 데이터의 실제 최댓값. 0 이하이면 눈금 0과 1만 있는 축을 돌려준다.
         * @param intervalCount 목표 구간 수. 1 이상이어야 한다.
         */
        fun forMaxValue(
            maxValue: Long,
            intervalCount: Int = DEFAULT_INTERVAL_COUNT
        ): ChartAxis {
            require(intervalCount >= 1) { "구간 수는 1 이상이어야 합니다: $intervalCount" }

            if (maxValue <= 0L) {
                return ChartAxis(maxValue = 1L, tickValues = listOf(0L, 1L))
            }

            val step = niceStep(maxValue.toDouble() / intervalCount)
            val axisMaxValue = ceil(maxValue.toDouble() / step).toLong() * step

            val ticks = generateSequence(0L) { previous -> previous + step }
                .takeWhile { tick -> tick <= axisMaxValue }
                .toList()

            return ChartAxis(maxValue = axisMaxValue, tickValues = ticks)
        }

        /** 대략적인 간격을 받아 1·2·5 × 10ⁿ 중 그보다 크거나 같은 가장 작은 값으로 올린다. */
        private fun niceStep(roughStep: Double): Long {
            val magnitude = 10.0.pow(floor(log10(roughStep)))
            val normalized = roughStep / magnitude
            val niceMantissa = NICE_MANTISSAS.first { candidate -> normalized <= candidate }

            // 바이트 단위에서 간격이 0이 되면 눈금을 만들 수 없다.
            return (niceMantissa * magnitude).toLong().coerceAtLeast(1L)
        }
    }
}
