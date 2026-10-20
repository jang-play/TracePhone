package com.dksystems.tracephone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAlertSettingsTest {

    private val presetBytes = 300 * UsageAlertSettings.BYTES_PER_MEGABYTE

    private fun settings(customMegabytes: Long) = UsageAlertSettings(
        isEnabled = true,
        presetLimitBytes = presetBytes,
        customLimitMegabytes = customMegabytes
    )

    @Test
    fun `직접 입력이 0이면 목록에서 고른 값을 쓴다`() {
        val alertSettings = settings(customMegabytes = UsageAlertSettings.CUSTOM_LIMIT_DISABLED)

        assertFalse(alertSettings.usesCustomLimit)
        assertEquals(presetBytes, alertSettings.effectiveLimitBytes)
    }

    @Test
    fun `직접 입력이 0보다 크면 그 값을 쓴다`() {
        val alertSettings = settings(customMegabytes = 250L)

        assertTrue(alertSettings.usesCustomLimit)
        assertEquals(250 * UsageAlertSettings.BYTES_PER_MEGABYTE, alertSettings.effectiveLimitBytes)
    }

    @Test
    fun `직접 입력은 목록에서 고른 값보다 우선한다`() {
        val alertSettings = settings(customMegabytes = 50L)

        // 목록 값(300MB)보다 작아도 직접 입력이 이긴다.
        assertEquals(50 * UsageAlertSettings.BYTES_PER_MEGABYTE, alertSettings.effectiveLimitBytes)
    }

    @Test
    fun `초과 판정은 실제 적용되는 한도를 기준으로 한다`() {
        val alertSettings = settings(customMegabytes = 50L)
        val fiftyMegabytes = 50 * UsageAlertSettings.BYTES_PER_MEGABYTE

        assertEquals(1, alertSettings.reachedMultipleBy(fiftyMegabytes))
        assertEquals(
            UsageAlertSettings.NOT_REACHED,
            alertSettings.reachedMultipleBy(fiftyMegabytes - 1)
        )
    }

    /** 한도가 50MB면 50에서 1번째, 100에서 2번째, 150에서 3번째 알림이 나가야 한다. */
    @Test
    fun `한도의 배수를 넘길 때마다 배수가 하나씩 올라간다`() {
        val alertSettings = settings(customMegabytes = 50L)
        val megabyte = UsageAlertSettings.BYTES_PER_MEGABYTE

        assertEquals(0, alertSettings.reachedMultipleBy(49 * megabyte))
        assertEquals(1, alertSettings.reachedMultipleBy(50 * megabyte))
        assertEquals(1, alertSettings.reachedMultipleBy(99 * megabyte))
        assertEquals(2, alertSettings.reachedMultipleBy(100 * megabyte))
        assertEquals(3, alertSettings.reachedMultipleBy(150 * megabyte))
        assertEquals(4, alertSettings.reachedMultipleBy(200 * megabyte))
    }

    @Test
    fun `한도가 0이면 몇 배인지 따지지 않는다`() {
        val noLimit = UsageAlertSettings(
            isEnabled = true,
            presetLimitBytes = 0L,
            customLimitMegabytes = UsageAlertSettings.CUSTOM_LIMIT_DISABLED
        )

        assertEquals(UsageAlertSettings.NOT_REACHED, noLimit.reachedMultipleBy(Long.MAX_VALUE))
    }

    @Test
    fun `기본값은 경고가 꺼져 있고 직접 입력도 쓰지 않는다`() {
        assertFalse(UsageAlertSettings.DEFAULT.isEnabled)
        assertFalse(UsageAlertSettings.DEFAULT.usesCustomLimit)
        assertTrue(
            UsageAlertSettings.DEFAULT.presetLimitBytes in UsageAlertSettings.PRESET_CHOICES_BYTES
        )
    }

    @Test
    fun `목록은 100 200 300 MB 세 가지다`() {
        assertEquals(
            listOf(
                100 * UsageAlertSettings.BYTES_PER_MEGABYTE,
                200 * UsageAlertSettings.BYTES_PER_MEGABYTE,
                300 * UsageAlertSettings.BYTES_PER_MEGABYTE
            ),
            UsageAlertSettings.PRESET_CHOICES_BYTES
        )
    }
}
