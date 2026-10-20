package com.dksystems.tracephone.ui.component

import com.dksystems.tracephone.core.ChartAxis
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 그래프를 스크린 리더에 읽어 줄 때 쓰는 계산들.
 *
 * 그리는 코드는 기기가 있어야 확인되지만, 무엇을 읽을지 정하는 계산은 여기서 고정할 수 있다.
 */
class UsageBarDescriptionTest {

    private val noUsage = "사용 없음"

    /**
     * 0을 "0 B"로 읽으면 값이 있는 막대와 구분되지 않는다.
     * 24개를 훑는 사람에게는 '없다'는 말이 훨씬 빨리 읽힌다.
     */
    @Test
    fun `사용량이 없는 막대는 없다고 읽는다`() {
        assertEquals(noUsage, describeBytes(0L, noUsage))
        assertEquals(noUsage, describeBytes(-1L, noUsage))
    }

    @Test
    fun `사용량이 있으면 화면과 같은 형식으로 읽는다`() {
        assertEquals("1.50 MB", describeBytes(1_500_000L, noUsage))
    }

    /**
     * 세로축 최댓값은 그래프가 스스로 고르는 값이라 밖에서는 알 수 없다.
     * 요약 문구가 그래프와 다른 숫자를 말하면 안 되므로 같은 계산을 쓰는지 확인한다.
     */
    @Test
    fun `요약이 말하는 세로축 최댓값은 그래프가 그리는 값과 같다`() {
        val bars = listOf(
            UsageBarValue(mobileBytes = 1_200_000L, wifiBytes = 300_000L),
            UsageBarValue(mobileBytes = 400_000L, wifiBytes = 0L)
        )

        val expected = ChartAxis.forMaxValue(bars.maxOf { it.totalBytes }).maxValue
        assertEquals(expected, barChartAxisMaxBytes(bars))
    }

    /** 막대가 없으면 읽어 줄 축도 없다. 예외 대신 0을 돌려준다. */
    @Test
    fun `막대가 없으면 최댓값은 0이다`() {
        assertEquals(0L, barChartAxisMaxBytes(emptyList()))
    }

    /**
     * '전체 누적'은 저장소 첫 기록일부터라 오래 쓰면 막대가 수백 개가 된다.
     * 상한을 두지 않으면 스크린 리더가 그만큼의 항목을 만들어 쓸 수 없게 된다.
     */
    @Test
    fun `막대를 하나씩 읽어 주는 상한은 한 달치다`() {
        assertEquals(31, MAX_INDIVIDUALLY_LABELED_BARS)
    }
}
