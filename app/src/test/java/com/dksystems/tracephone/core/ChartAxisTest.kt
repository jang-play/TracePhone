package com.dksystems.tracephone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartAxisTest {

    private val oneGigabyte = 1_000_000_000L

    @Test
    fun `축 최댓값은 실제 최댓값 이상이다`() {
        val axis = ChartAxis.forMaxValue(1_873_204_915L)

        assertTrue(
            "축 최댓값(${axis.maxValue})이 데이터 최댓값보다 작으면 막대가 잘린다",
            axis.maxValue >= 1_873_204_915L
        )
    }

    @Test
    fun `눈금 간격은 읽기 좋은 값으로 떨어진다`() {
        val axis = ChartAxis.forMaxValue(1_873_204_915L)

        // 0 / 500 MB / 1 GB / 1.5 GB / 2 GB
        assertEquals(2 * oneGigabyte / 4, axis.tickValues[1] - axis.tickValues[0])
        assertEquals(2 * oneGigabyte, axis.maxValue)
    }

    @Test
    fun `눈금은 0에서 시작해 축 최댓값에서 끝난다`() {
        val axis = ChartAxis.forMaxValue(4_200_000_000L)

        assertEquals(0L, axis.tickValues.first())
        assertEquals(axis.maxValue, axis.tickValues.last())
    }

    @Test
    fun `눈금 간격은 모두 같다`() {
        val axis = ChartAxis.forMaxValue(4_200_000_000L)

        val gaps = axis.tickValues.zipWithNext { current, next -> next - current }.toSet()

        assertEquals(1, gaps.size)
    }

    @Test
    fun `기록이 없으면 0과 1만 있는 축을 돌려준다`() {
        val axis = ChartAxis.forMaxValue(0L)

        assertEquals(1L, axis.maxValue)
        assertEquals(listOf(0L, 1L), axis.tickValues)
    }

    @Test
    fun `아주 작은 값에서도 눈금 간격이 0이 되지 않는다`() {
        val axis = ChartAxis.forMaxValue(1L)

        assertTrue(axis.tickValues[1] - axis.tickValues[0] > 0L)
    }

    @Test
    fun `구간 수는 1 미만일 수 없다`() {
        assertThrows(IllegalArgumentException::class.java) {
            ChartAxis.forMaxValue(oneGigabyte, intervalCount = 0)
        }
    }
}
