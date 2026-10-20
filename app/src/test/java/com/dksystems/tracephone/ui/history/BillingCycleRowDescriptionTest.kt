package com.dksystems.tracephone.ui.history

import com.dksystems.tracephone.domain.model.BillingCycleUsage
import com.dksystems.tracephone.domain.model.UsagePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

/**
 * 주기 한 줄이 스크린 리더에 어떻게 읽히는지 고정한다.
 *
 * 라벨 없는 숫자들이 따로 읽히면 어느 값이 무엇인지 알 수 없다.
 * 눈으로는 확인할 수 없는 종류의 어긋남이다.
 */
class BillingCycleRowDescriptionTest {

    private val megabyte = 1_000_000L

    private val labels = BillingCycleRowLabels(
        cycleName = "2026년 8월 주기",
        dailyAverage = "하루 평균 %1\$s",
        noUsage = "사용 없음",
        separator = ", "
    )

    private fun cycle(megabytes: Long) = BillingCycleUsage(
        cycle = UsagePeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
        countedThrough = LocalDate.of(2026, 8, 31),
        mobileBytes = megabytes * megabyte
    )

    @Test
    fun `주기 이름과 총량과 하루 평균을 모두 읽는다`() {
        val description = describeBillingCycleRow(cycle(megabytes = 3_100L), labels)

        assertEquals("2026년 8월 주기, 3.10 GB, 하루 평균 100.00 MB", description)
    }

    /** 0을 `0 B`로 읽으면 '기록이 없는 것'과 '쓰지 않은 것'이 같은 말이 된다. */
    @Test
    fun `쓰지 않은 주기는 사용 없음으로 읽는다`() {
        val description = describeBillingCycleRow(cycle(megabytes = 0L), labels)

        assertEquals("2026년 8월 주기, 사용 없음, 하루 평균 사용 없음", description)
    }

    /**
     * 한도 대비 비율을 읽지 않는다. 그 주기에 한도가 얼마였는지 알 수 없으므로,
     * 화면이 적지 않는 것을 낭독만 적으면 거짓이 된다.
     */
    @Test
    fun `한도 이야기는 읽지 않는다`() {
        val description = describeBillingCycleRow(cycle(megabytes = 3_100L), labels)

        listOf("%", "한도", "남음", "초과").forEach { forbidden ->
            assertFalse(
                "낭독에 '$forbidden' 이 들어 있다: $description",
                description.contains(forbidden)
            )
        }
    }
}
