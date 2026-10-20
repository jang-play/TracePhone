package com.dksystems.tracephone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 앱별 목록 막대가 쓰는 자와 하루 기준 눈금을 고정한다.
 *
 * 이 값이 틀려도 화면은 멀쩡히 그려진다. 막대 길이만 조용히 어긋나
 * 사용자가 앱끼리 잘못 견주게 된다. 눈으로는 확인되지 않는 종류의 오류다.
 */
class AppListScaleTest {

    private val megabyte = 1_000_000L

    /** 9월 15일 ~ 10월 14일, 30일짜리 주기. */
    private val cycle = UsagePeriod(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 14))

    private fun limitStatus(limitGigabytes: Long) = MonthlyLimitStatus(
        settings = MonthlyLimitSettings(
            isEnabled = true,
            limitMegabytes = limitGigabytes * MonthlyLimitSettings.MEGABYTES_PER_GIGABYTE,
            cycleStartDay = 15
        ),
        cycle = cycle,
        usedBytes = 0L,
        today = LocalDate.of(2026, 9, 20)
    )

    /**
     * 하루 기준을 자로 삼던 때 실기기에서 무너졌다 - 기준 200 MB에 그날 쓴 모바일이 13.58 MB여서
     * 모든 막대가 4% 이하가 됐다. 자는 데이터에서 나와야 비교가 성립한다.
     */
    @Test
    fun `자는 하루 기준에 흔들리지 않는다`() {
        val lightDay = AppListScale(maxBytes = 13 * megabyte, referenceBytes = 200 * megabyte)

        // 그날 모바일 전부를 쓴 앱이 있다면 막대가 가득 찬다. 하루 기준은 이 값에 끼어들지 않는다.
        assertEquals(1f, lightDay.fractionOf(13 * megabyte), 0f)
        assertEquals(0.5f, lightDay.fractionOf(6_500_000L), 0.001f)
    }

    /**
     * 막대 길이가 곧 그 앱의 몫이다.
     *
     * 자가 합계이므로 `fractionOf`와 줄에 적히는 백분율이 같은 분모를 쓴다.
     * 예전에는 막대가 '가장 큰 앱' 대비, 몫이 '전체' 대비여서 1등 줄에 `54%`와
     * 가득 찬 막대가 나란히 놓였다.
     */
    @Test
    fun `막대 길이가 곧 몫이다`() {
        val scale = AppListScale(maxBytes = 1_000 * megabyte)

        assertEquals(0.54f, scale.fractionOf(540 * megabyte), 0.001f)
        assertEquals(0.17f, scale.fractionOf(170 * megabyte), 0.001f)
    }

    @Test
    fun `눈금은 하루 기준 자리에 선다`() {
        val scale = AppListScale(maxBytes = 800 * megabyte, referenceBytes = 200 * megabyte)

        assertEquals(0.25f, scale.referenceFraction!!, 0.001f)
    }

    /**
     * 기준이 자를 넘으면 눈금이 없다. 오른쪽 끝에 붙이면 '오늘 쓴 만큼이 곧 하루치'로 읽히는데,
     * 실제로는 다 합쳐도 못 미쳤다는 뜻이라 정반대다.
     */
    @Test
    fun `하루 기준에 못 미친 날은 눈금을 그리지 않는다`() {
        val scale = AppListScale(maxBytes = 13 * megabyte, referenceBytes = 200 * megabyte)

        assertNull(scale.referenceFraction)
    }

    /** 그날 쓴 모바일이 정확히 하루치일 때. 눈금이 오른쪽 끝에 선다. */
    @Test
    fun `기준이 자와 같으면 눈금은 오른쪽 끝이다`() {
        val scale = AppListScale(maxBytes = 200 * megabyte, referenceBytes = 200 * megabyte)

        assertEquals(1f, scale.referenceFraction!!, 0f)
    }

    @Test
    fun `기준이 없으면 눈금도 없다`() {
        assertNull(AppListScale(maxBytes = 800 * megabyte, referenceBytes = 0L).referenceFraction)
    }

    @Test
    fun `잴 것이 아무것도 없으면 막대는 비어 있고 눈금도 없다`() {
        // 목록이 비었거나 아무도 모바일을 쓰지 않은 날. 0으로 나누면 안 된다.
        val scale = AppListScale(maxBytes = 0L, referenceBytes = 200 * megabyte)

        assertEquals(0f, scale.fractionOf(100 * megabyte), 0f)
        assertNull(scale.referenceFraction)
    }

    @Test
    fun `막대는 자를 넘지 않는다`() {
        val scale = AppListScale(maxBytes = 100 * megabyte)

        assertEquals(0.5f, scale.fractionOf(50 * megabyte), 0.001f)
        assertEquals(1f, scale.fractionOf(500 * megabyte), 0f)
        assertEquals(0f, scale.fractionOf(-1L), 0f)
    }

    @Test
    fun `하루 기준은 한도를 주기 일수로 나눈 값이다`() {
        // 20 GB / 30일 = 666.6 MB. 남은 양이 아니라 한도를 나눠 주기 내내 고정된다.
        val reference = dailyReferenceBytesOf(PeriodUnit.DAY, limitStatus(limitGigabytes = 20L))

        assertEquals(20_000 * megabyte / 30, reference)
    }

    @Test
    fun `일 보기가 아니면 하루 기준을 쓰지 않는다`() {
        // 주나 월에 하루치 눈금을 세우면 맨 앞에 붙은 채 아무것도 말하지 않는다.
        listOf(PeriodUnit.WEEK, PeriodUnit.MONTH, PeriodUnit.ALL).forEach { unit ->
            assertEquals(
                "$unit 에서 하루 기준이 쓰였다",
                0L,
                dailyReferenceBytesOf(unit, limitStatus(limitGigabytes = 20L))
            )
        }
    }

    @Test
    fun `한도를 쓰지 않으면 하루 기준이 없다`() {
        assertEquals(0L, dailyReferenceBytesOf(PeriodUnit.DAY, null))
    }
}
