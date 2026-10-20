package com.dksystems.tracephone.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 모드가 실제로 어떤 테마로 그려지는가.
 *
 * 기기 설정을 **무시해야 하는 경우**가 이 계산의 전부다.
 * 강제 설정이 기기 설정에 밀리면 사용자가 고른 값이 조용히 무시된다.
 */
class ThemeModeTest {

    @Test
    fun `시스템 설정 따름은 기기를 그대로 따라간다`() {
        assertTrue(ThemeMode.SYSTEM.resolvesToDark(isSystemDark = true))
        assertFalse(ThemeMode.SYSTEM.resolvesToDark(isSystemDark = false))
    }

    @Test
    fun `밝게는 기기가 야간 모드여도 밝게 그린다`() {
        assertFalse(ThemeMode.LIGHT.resolvesToDark(isSystemDark = true))
        assertFalse(ThemeMode.LIGHT.resolvesToDark(isSystemDark = false))
    }

    @Test
    fun `어둡게는 기기가 주간 모드여도 어둡게 그린다`() {
        assertTrue(ThemeMode.DARK.resolvesToDark(isSystemDark = true))
        assertTrue(ThemeMode.DARK.resolvesToDark(isSystemDark = false))
    }

    /**
     * 저장 값은 열거형의 **이름**이라 이름을 바꾸면 저장해 둔 설정이 기본값으로 되돌아간다.
     * 순서도 마찬가지로 화면의 라디오 순서가 된다.
     */
    @Test
    fun `항목의 이름과 순서를 고정한다`() {
        assertTrue(
            ThemeMode.entries.map { it.name } == listOf("SYSTEM", "LIGHT", "DARK")
        )
    }
}
