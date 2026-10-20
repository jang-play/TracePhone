package com.dksystems.tracephone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 진행률 계산.
 *
 * 이 함수가 하는 일은 나눗셈 하나지만, **나눌 수 없는 경우를 0으로 돌려주면 화면이
 * "멈췄다"고 거짓을 말한다.** 그 경계를 고정한다.
 */
class ApkDownloadTest {

    @Test
    fun `총량을 모르면 진행률이 없다`() {
        // 서버가 Content-Length를 주지 않는 경우. 0%를 그리면 멈춘 것으로 읽힌다.
        assertNull(downloadProgressOf(downloadedBytes = 1_000L, totalBytes = null))
    }

    @Test
    fun `총량이 0이면 진행률이 없다`() {
        assertNull(downloadProgressOf(downloadedBytes = 0L, totalBytes = 0L))
    }

    @Test
    fun `총량이 음수여도 진행률이 없다`() {
        // contentLengthLong은 모를 때 -1을 돌려준다. 그 값이 그대로 흘러들 수 있다.
        assertNull(downloadProgressOf(downloadedBytes = 0L, totalBytes = -1L))
    }

    @Test
    fun `절반 받으면 0_5다`() {
        assertEquals(0.5f, downloadProgressOf(500L, 1_000L)!!, 0.0001f)
    }

    @Test
    fun `시작 직후는 0이다`() {
        assertEquals(0f, downloadProgressOf(0L, 1_000L)!!, 0.0001f)
    }

    @Test
    fun `다 받으면 1이다`() {
        assertEquals(1f, downloadProgressOf(1_000L, 1_000L)!!, 0.0001f)
    }

    @Test
    fun `총량보다 많이 받아도 1을 넘지 않는다`() {
        // 전송 인코딩에 따라 알려준 길이보다 많이 내려올 수 있다. 막대가 삐져나가면 안 된다.
        assertEquals(1f, downloadProgressOf(1_500L, 1_000L)!!, 0.0001f)
    }

    @Test
    fun `받은 양이 음수여도 0 아래로 내려가지 않는다`() {
        assertEquals(0f, downloadProgressOf(-10L, 1_000L)!!, 0.0001f)
    }
}
