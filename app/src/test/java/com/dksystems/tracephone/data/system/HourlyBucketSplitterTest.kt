package com.dksystems.tracephone.data.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HourlyBucketSplitterTest {

    private val hour = 60L * 60L * 1000L
    private val dayStart = 0L

    private fun split(startHour: Double, endHour: Double, bytes: Long) =
        HourlyBucketSplitter.split(
            bucketStartMillis = (startHour * hour).toLong(),
            bucketEndMillis = (endHour * hour).toLong(),
            bytes = bytes,
            dayStartMillis = dayStart
        )

    @Test
    fun `한 시간 안에 들어가는 구간은 그 시간대에 전부 담긴다`() {
        val result = split(startHour = 3.0, endHour = 4.0, bytes = 1_000L)

        assertEquals(mapOf(3 to 1_000L), result)
    }

    @Test
    fun `두 시간에 걸친 구간은 절반씩 나뉜다`() {
        val result = split(startHour = 2.0, endHour = 4.0, bytes = 1_000L)

        assertEquals(mapOf(2 to 500L, 3 to 500L), result)
    }

    @Test
    fun `겹친 시간에 비례해서 나눈다`() {
        // 2시30분 ~ 4시: 2시대에 30분, 3시대에 60분 -> 1:2
        val result = split(startHour = 2.5, endHour = 4.0, bytes = 900L)

        assertEquals(300L, result[2])
        assertEquals(600L, result[3])
    }

    /**
     * 나눈 값의 합이 원래 값과 달라지면 시간별 합계와 하루 합계가 어긋나 보인다.
     * 정수 나눗셈에서 버려지는 나머지를 반드시 되돌려야 한다.
     */
    @Test
    fun `나눈 값의 합은 원래 값과 같다`() {
        val result = split(startHour = 0.0, endHour = 3.0, bytes = 1_000L)

        assertEquals(1_000L, result.values.sum())
    }

    @Test
    fun `하루 밖으로 나간 부분은 담기지 않는다`() {
        // 23시 ~ 다음날 1시. 하루 안에 들어오는 것은 23시대 한 시간뿐이다.
        val result = split(startHour = 23.0, endHour = 25.0, bytes = 1_000L)

        assertEquals(setOf(23), result.keys)
        assertEquals(1_000L, result[23])
    }

    @Test
    fun `하루와 전혀 겹치지 않으면 비어 있다`() {
        val result = split(startHour = 30.0, endHour = 31.0, bytes = 1_000L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `길이가 없는 구간은 비어 있다`() {
        val result = split(startHour = 5.0, endHour = 5.0, bytes = 1_000L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `사용량이 0이면 비어 있다`() {
        val result = split(startHour = 1.0, endHour = 3.0, bytes = 0L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `하루 전체를 덮는 구간은 24개 시간대에 고르게 담긴다`() {
        val result = split(startHour = 0.0, endHour = 24.0, bytes = 2_400L)

        assertEquals(HourlyBucketSplitter.HOURS_PER_DAY, result.size)
        assertEquals(2_400L, result.values.sum())
    }
}
