package com.dksystems.tracephone.ui.summary

import com.dksystems.tracephone.domain.model.AppIdentity
import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.NetworkUsageSplit
import com.dksystems.tracephone.domain.model.PeriodSelection
import com.dksystems.tracephone.domain.model.PeriodUnit
import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.domain.model.UsageSortOrder
import com.dksystems.tracephone.domain.model.UsageStateSplit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 목록에 무엇을 그릴지와, 합계가 그 판단에 끌려가지 않는지를 고정한다.
 *
 * **합계는 기간의 사실이고 목록은 보는 방식이다.** 둘을 같은 값에서 뽑으면
 * 정렬 칩을 눌렀을 뿐인데 그 달의 Wi-Fi 총량이 달라진다.
 * 실기기에서 같은 10월이 모바일순 5.49 GB, Wi-Fi순 6.38 GB로 보였다.
 */
class UsageSummaryUiStateTest {

    private val today = LocalDate.of(2026, 10, 2)
    private val period = UsagePeriod.of(PeriodSelection(PeriodUnit.MONTH), today)

    private val mobileApp = appUsage(uid = 1, mobileBytes = 900L, wifiBytes = 100L)

    /** Wi-Fi만 쓴 앱. 모바일순 목록에서 큰 숫자가 `0 B`로 찍히던 종류다. */
    private val wifiOnlyApp = appUsage(uid = 2, mobileBytes = 0L, wifiBytes = 5_000L)

    private fun stateOf(sortOrder: UsageSortOrder, vararg usages: AppUsage) = UsageSummaryUiState(
        selection = PeriodSelection(PeriodUnit.MONTH),
        period = period,
        sortOrder = sortOrder,
        appUsages = usages.toList(),
        isLoading = false
    )

    /**
     * 실기기에서 최근 30일 192개 앱 중 61개(32%)가 모바일 0이었다.
     * 모바일순 목록의 3분의 1이 아무것도 말하지 않는 줄이었다.
     */
    @Test
    fun `모바일순 목록에서 Wi-Fi만 쓴 앱을 뺀다`() {
        val state = stateOf(UsageSortOrder.MOBILE, mobileApp, wifiOnlyApp)

        assertEquals(listOf(mobileApp), state.visibleAppUsages)
    }

    /** Wi-Fi순에서 Wi-Fi만 쓴 앱을 빼면 그 정렬이 비어 버린다. */
    @Test
    fun `Wi-Fi순 목록에는 Wi-Fi만 쓴 앱이 남는다`() {
        val state = stateOf(UsageSortOrder.WIFI, mobileApp, wifiOnlyApp)

        assertEquals(listOf(mobileApp, wifiOnlyApp), state.visibleAppUsages)
    }

    @Test
    fun `합계순 목록에는 둘 다 남는다`() {
        val state = stateOf(UsageSortOrder.TOTAL, mobileApp, wifiOnlyApp)

        assertEquals(listOf(mobileApp, wifiOnlyApp), state.visibleAppUsages)
    }

    /**
     * **이 분리의 존재 이유다.**
     *
     * 포그라운드 구분이 기록되지 않은 앱은 백그라운드 몫을 알 수 없어 정렬 키가 0이다.
     * 정렬 키로 거르면 모바일을 분명히 쓴 앱이 통째로 사라진다.
     */
    @Test
    fun `백그라운드순에서 구분이 없는 앱도 모바일을 썼으면 남는다`() {
        val unknownPortion = AppUsage(
            app = AppIdentity(uid = 7, packageName = "com.example.app7", label = "앱 7"),
            usage = NetworkUsageSplit(
                mobile = UsageStateSplit.allUnknown(totalBytes = 5_000L),
                wifi = UsageStateSplit.ZERO
            )
        )

        val state = stateOf(UsageSortOrder.MOBILE_BACKGROUND, unknownPortion)

        assertEquals(listOf(unknownPortion), state.visibleAppUsages)
    }

    /**
     * 합계 카드가 목록을 더해 만들던 때, 정렬을 바꾸면 그 기간의 Wi-Fi 총량이 달라졌다.
     * 합계는 거르기 **전**에서 나와야 한다.
     */
    @Test
    fun `합계는 정렬을 바꿔도 달라지지 않는다`() {
        val totals = UsageSortOrder.entries.map { order ->
            val state = stateOf(order, mobileApp, wifiOnlyApp)
            Triple(state.mobileTotalBytes, state.wifiTotalBytes, state.totalBytes)
        }

        assertEquals(
            List(UsageSortOrder.entries.size) { Triple(900L, 5_100L, 6_000L) },
            totals
        )
    }

    /** 모바일순에서 Wi-Fi만 쓴 앱을 뺐다고 그 앱의 Wi-Fi가 합계에서 빠지면 안 된다. */
    @Test
    fun `목록에서 뺀 앱의 사용량도 합계에는 들어간다`() {
        val state = stateOf(UsageSortOrder.MOBILE, mobileApp, wifiOnlyApp)

        assertEquals(listOf(mobileApp), state.visibleAppUsages)
        assertEquals(5_100L, state.wifiTotalBytes)
    }

    private fun appUsage(uid: Int, mobileBytes: Long, wifiBytes: Long) = AppUsage(
        app = AppIdentity(uid = uid, packageName = "com.example.app$uid", label = "앱 $uid"),
        usage = NetworkUsageSplit(
            mobile = UsageStateSplit(mobileBytes, foregroundBytes = 0L, unknownBytes = 0L),
            wifi = UsageStateSplit(wifiBytes, foregroundBytes = 0L, unknownBytes = 0L)
        )
    )
}
