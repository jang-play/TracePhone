package com.dksystems.tracephone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageBytesTest {

    @Test
    fun `총량은 수신과 송신의 합이다`() {
        val usage = UsageBytes(receivedBytes = 300L, transmittedBytes = 200L)

        assertEquals(500L, usage.totalBytes)
    }

    @Test
    fun `더하면 수신과 송신이 각각 더해진다`() {
        val first = UsageBytes(receivedBytes = 100L, transmittedBytes = 10L)
        val second = UsageBytes(receivedBytes = 50L, transmittedBytes = 5L)

        val sum = first + second

        assertEquals(150L, sum.receivedBytes)
        assertEquals(15L, sum.transmittedBytes)
    }

    @Test
    fun `ZERO를 더해도 값이 변하지 않는다`() {
        val usage = UsageBytes(receivedBytes = 7L, transmittedBytes = 3L)

        assertEquals(usage, usage + UsageBytes.ZERO)
    }
}
