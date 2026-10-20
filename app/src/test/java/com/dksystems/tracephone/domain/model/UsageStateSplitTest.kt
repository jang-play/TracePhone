package com.dksystems.tracephone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStateSplitTest {

    private val ratioTolerance = 0.0001f

    @Test
    fun `백그라운드는 총량에서 포그라운드와 구분없음을 뺀 값이다`() {
        val split = UsageStateSplit(totalBytes = 100L, foregroundBytes = 70L, unknownBytes = 0L)

        assertEquals(30L, split.backgroundBytes)
    }

    @Test
    fun `구분 없는 몫은 백그라운드로 세지 않는다`() {
        val split = UsageStateSplit(totalBytes = 100L, foregroundBytes = 20L, unknownBytes = 60L)

        // 60바이트는 '모르는 것'이지 백그라운드가 아니다.
        assertEquals(20L, split.backgroundBytes)
        assertTrue(split.hasUnknownPortion)
    }

    @Test
    fun `값이 어긋나도 백그라운드가 음수가 되지 않는다`() {
        val split = UsageStateSplit(totalBytes = 10L, foregroundBytes = 30L, unknownBytes = 0L)

        assertEquals(0L, split.backgroundBytes)
    }

    @Test
    fun `전부 구분이 없으면 분리 표시를 감출 수 있어야 한다`() {
        val split = UsageStateSplit.allUnknown(totalBytes = 500L)

        assertFalse(split.hasKnownPortion)
        assertEquals(0L, split.measuredBytes)
        assertEquals(500L, split.unknownBytes)
    }

    @Test
    fun `구분되지 않은 몫이 있으면 측정된 몫을 따로 알 수 있다`() {
        // 총 100 가운데 50은 구분 정보가 없다. 나머지 50 중 절반이 백그라운드다.
        val split = UsageStateSplit(totalBytes = 100L, foregroundBytes = 25L, unknownBytes = 50L)

        assertEquals(25L, split.backgroundBytes)
        assertEquals(50L, split.measuredBytes)
        assertTrue(split.hasUnknownPortion)
        // 비율은 언제나 전체 대비다. 화면이 세 몫을 함께 보여줄 때만 이 값을 쓴다.
        assertEquals(0.25f, split.backgroundRatio, ratioTolerance)
    }

    @Test
    fun `막대에 쓰는 세 비율의 합은 1이다`() {
        val split = UsageStateSplit(totalBytes = 200L, foregroundBytes = 80L, unknownBytes = 40L)

        val sum = split.foregroundRatio + split.backgroundRatio + split.unknownRatio
        assertEquals(1f, sum, ratioTolerance)
    }

    @Test
    fun `사용량이 없으면 비율은 모두 0이다`() {
        val split = UsageStateSplit.ZERO

        assertEquals(0f, split.foregroundRatio, ratioTolerance)
        assertEquals(0f, split.backgroundRatio, ratioTolerance)
        assertEquals(0f, split.unknownRatio, ratioTolerance)
    }

    @Test
    fun `여러 기간을 더하면 각 몫이 함께 더해진다`() {
        val first = UsageStateSplit(totalBytes = 100L, foregroundBytes = 60L, unknownBytes = 0L)
        val second = UsageStateSplit(totalBytes = 50L, foregroundBytes = 0L, unknownBytes = 50L)

        val merged = first + second

        assertEquals(150L, merged.totalBytes)
        assertEquals(60L, merged.foregroundBytes)
        assertEquals(50L, merged.unknownBytes)
        assertEquals(40L, merged.backgroundBytes)
    }
}
