package com.dksystems.tracephone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 정렬 기준값과 '목록에 둘 값'을 **나눠 둔 이유**를 고정한다.
 *
 * 두 함수가 거의 같아 보여 합치고 싶어지는데, 합치면 백그라운드순에서
 * 모바일을 분명히 쓴 앱이 조용히 사라진다. 화면은 멀쩡하고 줄만 없어지는 오류다.
 */
class UsageSortOrderTest {

    private fun usage(
        mobileBytes: Long,
        wifiBytes: Long,
        mobileForegroundBytes: Long = 0L,
        isMobileStateKnown: Boolean = true
    ) = AppUsage(
        app = AppIdentity(uid = 1, packageName = "com.example.app", label = "앱"),
        usage = NetworkUsageSplit(
            mobile = if (isMobileStateKnown) {
                UsageStateSplit(
                    totalBytes = mobileBytes,
                    foregroundBytes = mobileForegroundBytes,
                    unknownBytes = 0L
                )
            } else {
                UsageStateSplit.allUnknown(totalBytes = mobileBytes)
            },
            wifi = UsageStateSplit(totalBytes = wifiBytes, foregroundBytes = 0L, unknownBytes = 0L)
        )
    )

    @Test
    fun `정렬마다 세우는 회선이 다르다`() {
        val app = usage(mobileBytes = 300L, wifiBytes = 700L)

        assertEquals(1_000L, UsageSortOrder.TOTAL.visibleBytesOf(app))
        assertEquals(300L, UsageSortOrder.MOBILE.visibleBytesOf(app))
        assertEquals(700L, UsageSortOrder.WIFI.visibleBytesOf(app))
    }

    @Test
    fun `모바일을 쓰지 않은 앱은 모바일순에서 셀 값이 없다`() {
        val wifiOnly = usage(mobileBytes = 0L, wifiBytes = 500L)

        assertEquals(0L, UsageSortOrder.MOBILE.visibleBytesOf(wifiOnly))
        assertEquals(0L, UsageSortOrder.MOBILE_BACKGROUND.visibleBytesOf(wifiOnly))
        // Wi-Fi순에서는 여전히 셀 값이 있다. 거기서 빼면 그 정렬이 비어 버린다.
        assertEquals(500L, UsageSortOrder.WIFI.visibleBytesOf(wifiOnly))
    }

    /**
     * **이 분리의 존재 이유다.**
     *
     * 포그라운드 구분이 기록되지 않은 앱은 백그라운드 몫을 알 수 없어 정렬 키가 0이 된다.
     * 그 앱들은 모바일을 분명히 썼다. 정렬 키로 거르면 통째로 사라진다.
     */
    @Test
    fun `백그라운드순에서 정렬 키와 셀 값이 갈린다`() {
        val unknownPortion = usage(mobileBytes = 5_000L, wifiBytes = 0L, isMobileStateKnown = false)

        assertEquals(0L, UsageSortOrder.MOBILE_BACKGROUND.sortKeyOf(unknownPortion))
        assertEquals(5_000L, UsageSortOrder.MOBILE_BACKGROUND.visibleBytesOf(unknownPortion))
        assertNotEquals(
            UsageSortOrder.MOBILE_BACKGROUND.sortKeyOf(unknownPortion),
            UsageSortOrder.MOBILE_BACKGROUND.visibleBytesOf(unknownPortion)
        )
    }

    @Test
    fun `구분이 기록된 앱에서는 백그라운드 정렬 키가 제 몫을 가리킨다`() {
        val known = usage(mobileBytes = 1_000L, wifiBytes = 0L, mobileForegroundBytes = 400L)

        assertEquals(600L, UsageSortOrder.MOBILE_BACKGROUND.sortKeyOf(known))
        // 셀 값은 백그라운드가 아니라 모바일 전체다. 쓴 적이 있는지만 보기 때문이다.
        assertEquals(1_000L, UsageSortOrder.MOBILE_BACKGROUND.visibleBytesOf(known))
    }

    @Test
    fun `아무것도 쓰지 않은 앱은 어느 정렬에서도 셀 값이 없다`() {
        val unused = usage(mobileBytes = 0L, wifiBytes = 0L)

        UsageSortOrder.entries.forEach { order ->
            assertEquals("$order 에서 0이 아니었다", 0L, order.visibleBytesOf(unused))
        }
    }
}
