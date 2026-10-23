package com.dksystems.tracephone.ui.allapps

import com.dksystems.tracephone.domain.model.AppIdentity
import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.NetworkUsageSplit
import com.dksystems.tracephone.domain.model.PeriodSelection
import com.dksystems.tracephone.domain.model.PeriodUnit
import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.domain.model.UsageStateSplit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * '모든 앱' 화면이 무엇을 그리고 무엇을 분모로 쓰는지 고정한다.
 *
 * 이 화면은 **막대의 자와 적어 둔 분모가 같아야** 뜻이 통한다. 화면에 `모바일 822 MB`라고
 * 적으면서 막대를 다른 값으로 재면, 사용자는 길이를 값으로 환산할 수 없다.
 */
class AllAppsUiStateTest {

    private val today = LocalDate.of(2026, 10, 23)
    private val period = UsagePeriod.of(PeriodSelection(PeriodUnit.MONTH), today)

    private val mobileApp = appUsage(uid = 1, mobileBytes = 900L, wifiBytes = 100L)

    /** Wi-Fi만 쓴 앱. 이 화면은 모바일만 재므로 그릴 것이 없는 줄이다. */
    private val wifiOnlyApp = appUsage(uid = 2, mobileBytes = 0L, wifiBytes = 5_000L)

    private fun stateOf(vararg usages: AppUsage) = AllAppsUiState(
        period = period,
        periodUnit = PeriodUnit.MONTH,
        appUsages = usages.toList(),
        isLoading = false
    )

    /**
     * 한 화면에 많이 보여주려고 만든 화면이다. 빈 막대와 `0 B`가 깔리면
     * 그 줄들이 정작 보여줄 앱의 자리를 차지한다.
     */
    @Test
    fun `모바일을 쓰지 않은 앱은 그리지 않는다`() {
        val state = stateOf(mobileApp, wifiOnlyApp)

        assertEquals(listOf(mobileApp), state.visibleAppUsages)
    }

    /** 분모는 모바일만 더한다. Wi-Fi가 섞이면 모든 막대가 짧아지고 몫이 거짓이 된다. */
    @Test
    fun `분모는 모바일만 더한 값이다`() {
        val state = stateOf(mobileApp, appUsage(uid = 3, mobileBytes = 100L, wifiBytes = 9_000L))

        assertEquals(1_000L, state.mobileTotalBytes)
    }

    /**
     * 거른 뒤에 더해도 같은 값이 나와야 한다. 지금은 거르는 조건이 '모바일이 0'이라
     * 저절로 맞지만, 조건이 바뀌면 **화면에 적힌 분모와 막대의 자가 갈린다.**
     * 요약 화면에서 정렬을 바꿀 때 합계가 흔들린 전례가 이것이다.
     */
    @Test
    fun `적어 둔 분모와 그린 줄들의 합이 같다`() {
        val state = stateOf(mobileApp, wifiOnlyApp, appUsage(uid = 3, 100L, wifiBytes = 0L))

        assertEquals(
            state.mobileTotalBytes,
            state.visibleAppUsages.sumOf { it.mobile.totalBytes }
        )
    }

    /** 기록이 없는 기간에서도 상태를 만들 수 있어야 한다. 0으로 나누는 자리가 생긴다. */
    @Test
    fun `기록이 없으면 분모가 0이고 그릴 줄이 없다`() {
        val state = stateOf()

        assertEquals(0L, state.mobileTotalBytes)
        assertEquals(emptyList<AppUsage>(), state.visibleAppUsages)
    }

    private fun appUsage(uid: Int, mobileBytes: Long, wifiBytes: Long) = AppUsage(
        app = AppIdentity(uid = uid, packageName = "com.example.app$uid", label = "앱 $uid"),
        usage = NetworkUsageSplit(
            mobile = UsageStateSplit(mobileBytes, foregroundBytes = 0L, unknownBytes = 0L),
            wifi = UsageStateSplit(wifiBytes, foregroundBytes = 0L, unknownBytes = 0L)
        )
    )
}
