package com.dksystems.tracephone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetAppearanceTest {

    private val alphaTolerance = 0.0001f

    private fun appearance(transparencyPercent: Int) = WidgetAppearance(
        textColor = WidgetTextColor.AUTO,
        backgroundTransparencyPercent = transparencyPercent
    )

    @Test
    fun `투명도 0이면 배경이 불투명하다`() {
        assertEquals(1f, appearance(0).backgroundAlpha, alphaTolerance)
    }

    @Test
    fun `투명도 100이면 배경이 보이지 않는다`() {
        assertEquals(0f, appearance(100).backgroundAlpha, alphaTolerance)
    }

    /** 화면에서 말하는 '투명도'와 그리기에 쓰는 '불투명도'는 서로 뒤집힌 값이다. */
    @Test
    fun `투명도 40이면 불투명도는 60이다`() {
        assertEquals(0.6f, appearance(40).backgroundAlpha, alphaTolerance)
    }

    @Test
    fun `범위를 벗어난 값은 잘라서 계산한다`() {
        assertEquals(1f, appearance(-20).backgroundAlpha, alphaTolerance)
        assertEquals(0f, appearance(140).backgroundAlpha, alphaTolerance)
    }

    /** 기본값이 바뀌면 설정을 건드린 적 없는 사람의 위젯 모습이 달라진다. */
    @Test
    fun `기본값은 자동 글자색에 불투명한 배경이다`() {
        assertEquals(WidgetTextColor.AUTO, WidgetAppearance.DEFAULT.textColor)
        assertEquals(1f, WidgetAppearance.DEFAULT.backgroundAlpha, alphaTolerance)
    }

    @Test
    fun `투명도 간격은 0부터 100까지를 정확히 나눈다`() {
        val range = WidgetAppearance.MAX_TRANSPARENCY_PERCENT -
            WidgetAppearance.MIN_TRANSPARENCY_PERCENT

        assertEquals(0, range % WidgetAppearance.TRANSPARENCY_STEP_PERCENT)
    }
}
