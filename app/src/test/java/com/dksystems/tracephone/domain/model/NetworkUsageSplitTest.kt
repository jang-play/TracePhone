package com.dksystems.tracephone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkUsageSplitTest {

    private fun split(total: Long, foreground: Long, unknown: Long = 0L) =
        UsageStateSplit(totalBytes = total, foregroundBytes = foreground, unknownBytes = unknown)

    @Test
    fun `회선별 백그라운드를 섞지 않는다`() {
        val usage = NetworkUsageSplit(
            mobile = split(total = 100L, foreground = 10L),
            wifi = split(total = 1_000L, foreground = 0L)
        )

        assertEquals(90L, usage.mobile.backgroundBytes)
        assertEquals(1_000L, usage.wifi.backgroundBytes)
    }

    @Test
    fun `합계는 두 회선을 더한 값이다`() {
        val usage = NetworkUsageSplit(
            mobile = split(total = 100L, foreground = 10L),
            wifi = split(total = 1_000L, foreground = 400L)
        )

        assertEquals(1_100L, usage.totalBytes)
        assertEquals(90L + 600L, usage.backgroundBytes)
    }

    @Test
    fun `회선을 지정해 꺼낼 수 있다`() {
        val usage = NetworkUsageSplit(
            mobile = split(total = 100L, foreground = 10L),
            wifi = split(total = 1_000L, foreground = 400L)
        )

        assertEquals(usage.mobile, usage.of(NetworkKind.MOBILE))
        assertEquals(usage.wifi, usage.of(NetworkKind.WIFI))
    }

    @Test
    fun `한쪽 회선에만 구분값이 있어도 표시 대상이다`() {
        val usage = NetworkUsageSplit(
            mobile = split(total = 100L, foreground = 40L),
            wifi = UsageStateSplit.allUnknown(totalBytes = 1_000L)
        )

        assertTrue(usage.hasKnownPortion)
        assertTrue(usage.wifi.hasUnknownPortion)
        assertFalse(usage.wifi.hasKnownPortion)
    }

    @Test
    fun `양쪽 모두 구분값이 없으면 표시하지 않는다`() {
        val usage = NetworkUsageSplit(
            mobile = UsageStateSplit.allUnknown(totalBytes = 100L),
            wifi = UsageStateSplit.allUnknown(totalBytes = 1_000L)
        )

        assertFalse(usage.hasKnownPortion)
    }

    @Test
    fun `기록이 없으면 모든 값이 0이다`() {
        assertEquals(0L, NetworkUsageSplit.ZERO.totalBytes)
        assertEquals(0L, NetworkUsageSplit.ZERO.backgroundBytes)
        assertFalse(NetworkUsageSplit.ZERO.hasKnownPortion)
    }

    /**
     * 목록에서 막대와 회선별 숫자를 접을지 판단하는 규칙이다.
     * 실기기에서 본 값(111.44MB 대 7KB)을 그대로 쓴다.
     */
    @Test
    fun `한 회선이 1퍼센트에 못 미치면 그 회선은 보여 줄 값이 없다고 본다`() {
        val usage = NetworkUsageSplit(
            mobile = split(total = 111_440_000L, foreground = 111_410_000L),
            wifi = split(total = 7_000L, foreground = 7_000L)
        )

        assertTrue(usage.isDominatedBySingleKind)
    }

    @Test
    fun `두 회선을 고루 쓰면 나눠 보여준다`() {
        val usage = NetworkUsageSplit(
            mobile = split(total = 60_000_000L, foreground = 0L),
            wifi = split(total = 40_000_000L, foreground = 0L)
        )

        assertFalse(usage.isDominatedBySingleKind)
    }

    /** 1%를 넘어서면 접지 않는다. 100MB의 2%면 2MB이고, 적어 둘 만한 값이다. */
    @Test
    fun `1퍼센트를 넘으면 나눠 보여준다`() {
        val usage = NetworkUsageSplit(
            mobile = split(total = 100_000_000L, foreground = 0L),
            wifi = split(total = 2_000_000L, foreground = 0L)
        )

        assertFalse(usage.isDominatedBySingleKind)
    }

    @Test
    fun `쓴 양이 없으면 보여 줄 구성이 없다`() {
        assertTrue(NetworkUsageSplit.ZERO.isDominatedBySingleKind)
    }
}
