package com.dksystems.tracephone.domain.usecase

import com.dksystems.tracephone.domain.FakeSystemUsageReader
import com.dksystems.tracephone.domain.FakeUsageRepository
import com.dksystems.tracephone.domain.model.UsagePeriod
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * "과거는 저장소, 오늘은 시스템" 규칙을 고정한다.
 *
 * **저장소 구현 안에 있던 동안에는 테스트할 수 없던 로직이다.**
 * 도메인으로 올라오면서 비로소 규칙으로 확인할 수 있게 됐다.
 *
 * 여기가 틀리면 한도 화면과 위젯의 숫자가 조용히 어긋난다.
 * 두 배로 세거나(오늘을 양쪽에서 세거나) 오늘치가 통째로 빠진다.
 */
class GetMobileUsageForPeriodUseCaseTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 9, 11)
    private val clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone)

    private val megabyte = 1_000_000L

    private fun useCaseWith(
        storedMobileBytes: Long,
        todayMobileBytes: Long
    ) = GetMobileUsageForPeriodUseCase(
        repository = FakeUsageRepository(storedMobileBytes = storedMobileBytes),
        systemUsageReader = FakeSystemUsageReader.withTodayMobileBytes(todayMobileBytes),
        clock = clock
    )

    @Test
    fun `오늘이 든 기간은 저장된 과거에 시스템의 오늘치를 더한다`() = runTest {
        val useCase = useCaseWith(
            storedMobileBytes = 100 * megabyte,
            todayMobileBytes = 30 * megabyte
        )

        val total = useCase(UsagePeriod(today.minusDays(9), today))

        assertEquals(130 * megabyte, total)
    }

    @Test
    fun `오늘이 없는 기간은 저장된 값만 센다`() = runTest {
        // 시스템 값이 있어도 섞이면 안 된다. 지난달 합계에 오늘치가 붙으면 숫자가 틀린다.
        val useCase = useCaseWith(
            storedMobileBytes = 100 * megabyte,
            todayMobileBytes = 30 * megabyte
        )

        val total = useCase(UsagePeriod(today.minusDays(9), today.minusDays(1)))

        assertEquals(100 * megabyte, total)
    }

    @Test
    fun `오늘 하루만 보는 기간은 시스템 값만 센다`() = runTest {
        // 저장소 쪽 범위가 비어 있으므로(어제까지가 시작일보다 앞선다) 0으로 친다.
        val useCase = useCaseWith(
            storedMobileBytes = 100 * megabyte,
            todayMobileBytes = 30 * megabyte
        )

        val total = useCase(UsagePeriod(today, today))

        assertEquals(30 * megabyte, total)
    }

    @Test
    fun `시스템이 0을 주면 저장된 오늘치로 물러선다`() = runTest {
        // 권한이 없을 때 0을 그대로 쓰면 한도가 갑자기 줄어든 것처럼 보인다.
        val useCase = useCaseWith(
            storedMobileBytes = 40 * megabyte,
            todayMobileBytes = 0L
        )

        val total = useCase(UsagePeriod(today, today))

        assertEquals(40 * megabyte, total)
    }
}
