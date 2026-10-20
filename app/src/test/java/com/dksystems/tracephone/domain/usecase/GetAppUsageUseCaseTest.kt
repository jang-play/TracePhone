package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.FakeUsageRepository
import com.dksystems.tracephone.domain.model.AppIdentity
import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.domain.model.PeriodSelection
import com.dksystems.tracephone.domain.model.PeriodUnit
import com.dksystems.tracephone.domain.model.NetworkUsageSplit
import com.dksystems.tracephone.domain.model.UsageStateSplit
import com.dksystems.tracephone.domain.model.UsagePeriod
import com.dksystems.tracephone.domain.model.UsageSortOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GetAppUsageUseCaseTest {

    private val today = LocalDate.of(2026, 9, 3)
    private val period = UsagePeriod.of(PeriodSelection(PeriodUnit.MONTH), today)

    private val mobileHeavyApp = appUsage(uid = 1, mobileBytes = 900L, wifiBytes = 100L)
    private val wifiHeavyApp = appUsage(uid = 2, mobileBytes = 100L, wifiBytes = 2_000L)
    private val unusedApp = appUsage(uid = 3, mobileBytes = 0L, wifiBytes = 0L)

    @Test
    fun `사용량이 0인 앱은 목록에서 제외한다`() = runTest {
        val useCase = useCaseWith(mobileHeavyApp, unusedApp)

        val result = useCase(period, UsageSortOrder.TOTAL).first()

        assertEquals(listOf(mobileHeavyApp), result)
    }

    @Test
    fun `합계순 정렬은 총량이 많은 앱을 앞에 둔다`() = runTest {
        val useCase = useCaseWith(mobileHeavyApp, wifiHeavyApp)

        val result = useCase(period, UsageSortOrder.TOTAL).first()

        assertEquals(listOf(wifiHeavyApp, mobileHeavyApp), result)
    }

    @Test
    fun `모바일순 정렬은 모바일 사용량만 기준으로 삼는다`() = runTest {
        val useCase = useCaseWith(mobileHeavyApp, wifiHeavyApp)

        val result = useCase(period, UsageSortOrder.MOBILE).first()

        assertEquals(listOf(mobileHeavyApp, wifiHeavyApp), result)
    }

    @Test
    fun `Wi-Fi순 정렬은 Wi-Fi 사용량만 기준으로 삼는다`() = runTest {
        val useCase = useCaseWith(mobileHeavyApp, wifiHeavyApp)

        val result = useCase(period, UsageSortOrder.WIFI).first()

        assertEquals(listOf(wifiHeavyApp, mobileHeavyApp), result)
    }

    @Test
    fun `모바일 백그라운드순은 화면을 보지 않을 때 모바일로 쓴 양이 많은 앱을 앞에 둔다`() = runTest {
        // 총량은 적지만 모바일 대부분을 백그라운드에서 쓴 앱이 앞에 와야 한다.
        val quietBackgroundApp = appUsage(
            uid = 1,
            mobileBytes = 100L,
            wifiBytes = 0L,
            mobileForegroundBytes = 10L
        )
        val busyForegroundApp = appUsage(
            uid = 2,
            mobileBytes = 1_000L,
            wifiBytes = 0L,
            mobileForegroundBytes = 960L
        )

        val useCase = useCaseWith(busyForegroundApp, quietBackgroundApp)

        val result = useCase(period, UsageSortOrder.MOBILE_BACKGROUND).first()

        assertEquals(listOf(quietBackgroundApp, busyForegroundApp), result)
    }

    @Test
    fun `모바일 백그라운드순은 Wi-Fi 백그라운드가 큰 앱에 밀리지 않는다`() = runTest {
        // Wi-Fi로 크게 받는 앱에 가려 요금이 새는 앱이 밀리면 이 정렬의 의미가 없다.
        val mobileLeakApp = appUsage(
            uid = 1,
            mobileBytes = 100L,
            wifiBytes = 0L,
            mobileForegroundBytes = 0L
        )
        // Wi-Fi로 100만 바이트를 전부 백그라운드에서 썼지만 모바일 백그라운드는 0이다.
        val wifiHeavyApp = appUsage(
            uid = 2,
            mobileBytes = 0L,
            wifiBytes = 1_000_000L,
            wifiForegroundBytes = 0L
        )

        val useCase = useCaseWith(wifiHeavyApp, mobileLeakApp)

        val result = useCase(period, UsageSortOrder.MOBILE_BACKGROUND).first()

        // 여기서는 거르지 않는다. 합계 카드가 이 목록을 더하므로, 정렬별로 거르면
        // 정렬을 바꿀 때마다 그 기간의 Wi-Fi 총량이 달라진다.
        assertEquals(listOf(mobileLeakApp, wifiHeavyApp), result)
    }

    private fun useCaseWith(vararg usages: AppUsage) =
        GetAppUsageUseCase(FakeUsageRepository(appUsages = usages.toList()))

    private fun appUsage(
        uid: Int,
        mobileBytes: Long,
        wifiBytes: Long,
        mobileForegroundBytes: Long = 0L,
        wifiForegroundBytes: Long = 0L
    ) = AppUsage(
        app = AppIdentity(uid = uid, packageName = "com.example.app$uid", label = "앱 $uid"),
        usage = NetworkUsageSplit(
            mobile = UsageStateSplit(
                totalBytes = mobileBytes,
                foregroundBytes = mobileForegroundBytes,
                unknownBytes = 0L
            ),
            wifi = UsageStateSplit(
                totalBytes = wifiBytes,
                foregroundBytes = wifiForegroundBytes,
                unknownBytes = 0L
            )
        )
    )
}
