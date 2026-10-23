package com.dksystems.tracephone.ui.navigation

import com.dksystems.tracephone.domain.model.PeriodUnit
import com.dksystems.tracephone.domain.model.UsagePeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 경로를 **만드는 쪽과 선언하는 쪽이 어긋나지 않는지** 고정한다.
 *
 * 이 어긋남은 컴파일로 잡히지 않는다. 경로 틀(`.../{startDate}/{endDate}/{periodUnit}`)에
 * 칸을 하나 더 늘리고 만드는 함수를 고치지 않으면, 화면을 열려는 순간에야 아무 일도
 * 일어나지 않는다. 자리 수를 함께 세어 그 실수를 여기서 잡는다.
 */
class TracePhoneDestinationsTest {

    private val period = UsagePeriod(
        startDate = LocalDate.of(2026, 10, 1),
        endDateInclusive = LocalDate.of(2026, 10, 31)
    )

    @Test
    fun `모든 앱 경로는 날짜 범위와 기간 단위를 순서대로 담는다`() {
        val route = TracePhoneDestinations.allApps(period, PeriodUnit.MONTH)

        assertEquals("all_apps/2026-10-01/2026-10-31/MONTH", route)
    }

    /**
     * 시작일과 종료일이 뒤바뀌어도 글자 수는 같아서 눈으로는 지나친다.
     * 두 날짜를 다르게 두고 자리를 확인한다.
     */
    @Test
    fun `시작일이 먼저고 종료일이 나중이다`() {
        val segments = TracePhoneDestinations.allApps(period, PeriodUnit.DAY).split("/")

        assertEquals("2026-10-01", segments[1])
        assertEquals("2026-10-31", segments[2])
    }

    /** 만든 경로의 칸 수가 선언한 틀의 칸 수와 같아야 내비게이션이 그 화면을 찾는다. */
    @Test
    fun `만든 경로의 칸 수가 선언한 틀과 같다`() {
        val built = TracePhoneDestinations.allApps(period, PeriodUnit.ALL)

        assertEquals(
            TracePhoneDestinations.ALL_APPS_ROUTE.count { it == '/' },
            built.count { it == '/' }
        )
    }

    /**
     * '전체 누적'은 1970년부터다. 경로에 담겨도 ISO 날짜라 탈출 문자가 필요 없다.
     * 그 사실이 깨지면 경로가 갈라져 화면을 찾지 못한다.
     */
    @Test
    fun `전체 누적의 1970년 시작일도 그대로 담긴다`() {
        val allTime = UsagePeriod(
            startDate = UsagePeriod.EARLIEST_POSSIBLE_DATE,
            endDateInclusive = LocalDate.of(2026, 10, 23)
        )

        assertEquals(
            "all_apps/1970-01-01/2026-10-23/ALL",
            TracePhoneDestinations.allApps(allTime, PeriodUnit.ALL)
        )
    }
}
