package com.dksystems.tracephone.ui.component

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.dksystems.tracephone.ui.theme.DarkUsageColors
import com.dksystems.tracephone.ui.theme.LightUsageColors
import com.dksystems.tracephone.ui.theme.contrastRatio
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 막대 위 눈금이 어디에 놓이든 보이는지 검증한다.
 *
 * 한도 진행 막대와 앱 목록 막대가 이 규칙을 함께 쓴다. 눈금은 **자기가 놓인 바닥의 반대색**을
 * 쓰는데, 바닥이 될 수 있는 색이 넷이라(두 막대의 채움 둘, 초과 채움, 트랙) 하나라도 놓치면
 * 그 조건에서만 눈금이 조용히 사라진다.
 */
class BarMarkerTest {

    /** 도형 채우기 기준. 글자가 아니므로 3:1이다. */
    private val minimumContrast = 3.0

    private val schemes = mapOf(
        "라이트" to lightColorScheme(),
        "다크" to darkColorScheme()
    )

    private val mobileColors = mapOf(
        "라이트" to LightUsageColors.network.mobile,
        "다크" to DarkUsageColors.network.mobile
    )

    @Test
    fun `눈금은 진행률이 기준에 닿으면 채움 안에 놓인다`() {
        assertTrue(markerRestsOnFill(fillFraction = 0.6f, markerFraction = 0.4f))
        assertTrue(markerRestsOnFill(fillFraction = 0.4f, markerFraction = 0.4f))
    }

    @Test
    fun `눈금은 진행률이 기준에 못 미치면 트랙 위에 놓인다`() {
        assertFalse(markerRestsOnFill(fillFraction = 0.3f, markerFraction = 0.4f))
    }

    @Test
    fun `막대가 가득 차면 눈금은 언제나 채움 안이다`() {
        // 한도를 넘기면 progress가 1.0으로 잘린다. 눈금이 트랙 위로 나갈 길이 없다.
        assertTrue(markerRestsOnFill(fillFraction = 1f, markerFraction = 1f))
        assertTrue(markerRestsOnFill(fillFraction = 1f, markerFraction = 0f))
    }

    /** 채움 안에서는 트랙 색을 쓴다. 세 가지 채움 모두에서 읽혀야 한다. */
    @Test
    fun `채움 안의 눈금이 세 가지 채움 위에서 읽힌다`() {
        schemes.forEach { (name, scheme) ->
            assertMeetsContrast("$name 한도 정상", scheme.surfaceVariant, scheme.onSurfaceVariant)
            assertMeetsContrast("$name 한도 초과", scheme.surfaceVariant, scheme.error)
            assertMeetsContrast("$name 목록 모바일", scheme.surfaceVariant, mobileColors.getValue(name))
        }
    }

    /** 트랙 위에서는 글자색을 쓴다. 바닥이 트랙 하나뿐이라 경우도 하나다. */
    @Test
    fun `트랙 위의 눈금이 트랙에서 읽힌다`() {
        schemes.forEach { (name, scheme) ->
            assertMeetsContrast("$name 트랙", scheme.onSurface, scheme.surfaceVariant)
        }
    }

    private fun assertMeetsContrast(label: String, foreground: Color, background: Color) {
        val contrast = contrastRatio(foreground, background)

        assertTrue("$label 대비 $contrast (필요 $minimumContrast)", contrast >= minimumContrast)
    }
}
