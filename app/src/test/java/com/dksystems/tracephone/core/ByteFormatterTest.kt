package com.dksystems.tracephone.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatterTest {

    @Test
    fun `0 이하는 0 B로 표시한다`() {
        assertEquals("0 B", ByteFormatter.format(0L))
        assertEquals("0 B", ByteFormatter.format(-1L))
    }

    @Test
    fun `바이트와 킬로바이트는 소수점 없이 표시한다`() {
        assertEquals("512 B", ByteFormatter.format(512L))
        assertEquals("2 KB", ByteFormatter.format(2_000L))
    }

    @Test
    fun `메가바이트부터는 소수점 두 자리로 표시한다`() {
        assertEquals("1.00 MB", ByteFormatter.format(1_000_000L))
        assertEquals("1.50 GB", ByteFormatter.format(1_500_000_000L))
    }

    @Test
    fun `단위는 1000에서 다음 단계로 넘어간다`() {
        assertEquals("999 B", ByteFormatter.format(999L))
        assertEquals("1 KB", ByteFormatter.format(1_000L))
    }

    /**
     * 실기기에서 확인한 값이다.
     * 시스템의 `Formatter.formatFileSize`는 이 바이트 수를 `0.99GB`로 표시한다.
     * 1024 기준이었다면 940.69 MB가 되어 설정 화면과 45.7 MB나 어긋났다.
     */
    @Test
    fun `시스템 설정 화면과 같은 1000 기준으로 계산한다`() {
        assertEquals("986.39 MB", ByteFormatter.format(986_389_338L))
    }

    // ---------- 그래프 축 눈금용 표기 ----------

    @Test
    fun `의미 없는 소수점 이하 0은 떼어낸다`() {
        assertEquals("2 GB", ByteFormatter.formatCompact(2_000_000_000L))
        assertEquals("1 MB", ByteFormatter.formatCompact(1_000_000L))
    }

    /**
     * 소수점을 전부 없애면 1.5 GB 지점의 격자선이 "2 GB"로 적혀
     * 선의 위치와 라벨이 어긋난다. 필요한 자리는 남겨야 한다.
     */
    @Test
    fun `필요한 소수점 자리는 남긴다`() {
        assertEquals("1.5 GB", ByteFormatter.formatCompact(1_500_000_000L))
        assertEquals("986.39 MB", ByteFormatter.formatCompact(986_389_338L))
    }

    @Test
    fun `소수점이 없던 단위는 그대로 둔다`() {
        assertEquals("0 B", ByteFormatter.formatCompact(0L))
        assertEquals("512 B", ByteFormatter.formatCompact(512L))
        assertEquals("2 KB", ByteFormatter.formatCompact(2_000L))
    }
}
