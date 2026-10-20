package com.dksystems.tracephone.ui.summary

import com.dksystems.tracephone.domain.model.AppIdentity
import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.NetworkUsageSplit
import com.dksystems.tracephone.domain.model.UsageSortOrder
import com.dksystems.tracephone.domain.model.UsageStateSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 목록 한 줄을 스크린 리더가 읽을 문구를 검증한다.
 *
 * **큰 숫자는 언제나 모바일이다.** 낭독이 합계를 읽으면 화면과 다른 값을 말하게 된다.
 *
 * 보조 값을 무엇으로 할지는 [showsBackgroundValue]가 정하고 화면과 낭독이 그 결과를 함께 쓴다.
 * 조건이 한 벌뿐이라 둘이 어긋날 자리가 없다.
 */
class AppUsageRowDescriptionTest {

    private val labels = AppUsageRowLabels(
        mobile = "%1\$s, 모바일 %2\$s",
        share = "전체의 %1\$s",
        separator = ", ",
        notInstalled = "설치되지 않음"
    )

    @Test
    fun `이름과 모바일 사용량과 몫과 보조 값을 이 순서로 읽는다`() {
        val description = describeAppUsageRow(
            usage = appUsage(mobileBytes = 60_000_000L, wifiBytes = 40_000_000L),
            texts = AppUsageRowTexts(share = "31%", secondary = "Wi-Fi 40.00 MB"),
            labels = labels
        )

        assertEquals("다음, 모바일 60.00 MB, 전체의 31%, Wi-Fi 40.00 MB", description)
    }

    /** 합계가 아니라 모바일을 읽는다. 화면의 큰 숫자와 같아야 한다. */
    @Test
    fun `합계가 아니라 모바일을 읽는다`() {
        val description = describeAppUsageRow(
            usage = appUsage(mobileBytes = 7_000L, wifiBytes = 111_440_000L),
            texts = AppUsageRowTexts(share = "<1%", secondary = "Wi-Fi 111.44 MB"),
            labels = labels
        )

        assertEquals("다음, 모바일 7 KB, 전체의 <1%, Wi-Fi 111.44 MB", description)
    }

    /**
     * 화면이 `<1%`로 다듬어 적은 몫을 낭독이 `0%`로 되돌리면 안 된다.
     * 그래서 문구를 잇는 쪽은 백분율을 다시 계산하지 않고 화면에 적힌 글자를 그대로 받는다.
     */
    @Test
    fun `몫과 보조 값은 화면에 적힌 글자를 그대로 읽는다`() {
        val description = describeAppUsageRow(
            usage = appUsage(mobileBytes = 1_000L, wifiBytes = 0L),
            texts = AppUsageRowTexts(share = "<1%", secondary = "모바일 백그라운드 600 B"),
            labels = labels
        )

        assertEquals("다음, 모바일 1 KB, 전체의 <1%, 모바일 백그라운드 600 B", description)
    }

    /** 백그라운드순으로 세워 놓고 그 값을 감추면 순서를 읽을 방법이 없다. */
    @Test
    fun `백그라운드순으로 정렬했을 때만 백그라운드를 적는다`() {
        assertTrue(showsBackgroundValue(UsageSortOrder.MOBILE_BACKGROUND, hasKnownPortion = true))
        assertFalse(showsBackgroundValue(UsageSortOrder.MOBILE, hasKnownPortion = true))
        assertFalse(showsBackgroundValue(UsageSortOrder.TOTAL, hasKnownPortion = true))
        assertFalse(showsBackgroundValue(UsageSortOrder.WIFI, hasKnownPortion = true))
    }

    /**
     * 포그라운드 구분이 기록되기 전의 값은 백그라운드 몫을 알 수 없다.
     * 0으로 꾸며 적으면 '뒤에서 아무것도 안 썼다'는 거짓말이 된다.
     */
    @Test
    fun `구분된 기록이 없으면 백그라운드를 적지 않는다`() {
        assertFalse(showsBackgroundValue(UsageSortOrder.MOBILE_BACKGROUND, hasKnownPortion = false))
    }

    /**
     * 버림으로 계산한다. 0.9%짜리 앱이 수십 개일 때 반올림하면 합이 100%를 훌쩍 넘어
     * 사용자가 숫자를 믿지 않게 된다.
     */
    @Test
    fun `몫은 버림으로 센다`() {
        assertEquals(18, usageSharePercent(usageBytes = 189L, listTotalBytes = 1_000L))
        assertEquals(0, usageSharePercent(usageBytes = 9L, listTotalBytes = 1_000L))
        assertEquals(100, usageSharePercent(usageBytes = 1_000L, listTotalBytes = 1_000L))
    }

    /** 목록이 비었거나 아무도 모바일을 쓰지 않았을 때 0으로 나누지 않는다. */
    @Test
    fun `전체가 0이면 몫도 0이다`() {
        assertEquals(0, usageSharePercent(usageBytes = 0L, listTotalBytes = 0L))
    }

    /**
     * 화면에 보이는 '설치되지 않음'을 낭독만 빠뜨리면 어긋난다.
     *
     * 이름과 사용량 다음, 몫·보조값보다 **앞**에 둔다. 맨 뒤로 보내면 보조값과 나란히 놓여
     * 어느 값에 걸리는 말인지 흐려진다.
     */
    @Test
    fun `지워진 앱은 사용량 다음에 그 사실을 읽는다`() {
        val description = describeAppUsageRow(
            usage = appUsage(mobileBytes = 60_000_000L, wifiBytes = 0L, isInstalled = false),
            texts = AppUsageRowTexts(share = "31%", secondary = "Wi-Fi 0 B"),
            labels = labels
        )

        assertEquals("다음, 모바일 60.00 MB, 설치되지 않음, 전체의 31%, Wi-Fi 0 B", description)
    }

    @Test
    fun `설치돼 있는 앱에는 그 말을 덧붙이지 않는다`() {
        val description = describeAppUsageRow(
            usage = appUsage(mobileBytes = 60_000_000L, wifiBytes = 0L),
            texts = AppUsageRowTexts(share = "31%", secondary = "Wi-Fi 0 B"),
            labels = labels
        )

        assertFalse(description.contains("설치되지 않음"))
    }

    private fun appUsage(
        mobileBytes: Long,
        wifiBytes: Long,
        mobileForegroundBytes: Long = 0L,
        isInstalled: Boolean = true
    ) = AppUsage(
        app = AppIdentity(
            uid = 1,
            packageName = "net.daum.android.daum",
            label = "다음",
            isInstalled = isInstalled
        ),
        usage = NetworkUsageSplit(
            mobile = UsageStateSplit(
                totalBytes = mobileBytes,
                foregroundBytes = mobileForegroundBytes,
                unknownBytes = 0L
            ),
            wifi = UsageStateSplit(
                totalBytes = wifiBytes,
                foregroundBytes = 0L,
                unknownBytes = 0L
            )
        )
    )
}
