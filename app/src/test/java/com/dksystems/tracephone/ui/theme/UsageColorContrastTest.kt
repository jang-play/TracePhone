package com.dksystems.tracephone.ui.theme

import androidx.compose.ui.graphics.Color
import com.dksystems.tracephone.widget.WidgetColors
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 사용량 색이 배경 위에서 읽히는지 검증한다.
 *
 * 눈으로 보고 넘어가면 다음에 색을 만질 때 조용히 무너진다.
 * 이 테스트가 이번 변경의 실질적인 산출물이다.
 *
 * ### 배경 색을 하드코딩하는 이유
 * 막대와 범례는 대부분 `Card` 위에 놓인다. Material3 카드의 바탕은 `surfaceContainerHighest`인데
 * [TracePhoneTheme]이 그 토큰을 지정하지 않아 **M3 기본값이 그대로 쓰인다.**
 * 즉 아래 값이 실기기에서 실제로 카드가 띠는 색이다.
 */
class UsageColorContrastTest {

    /** 도형 채우기에 요구되는 최소 대비. 글자가 아니므로 3:1이 기준이다. */
    private val minimumContrast = 3.0

    /** 카드·화면 배경처럼 주로 놓이는 자리에는 여유를 두고 4.5:1을 목표로 잡았다. */
    private val targetContrast = 4.5

    private val lightBackgrounds = mapOf(
        "background" to LightBackground,
        "surface" to LightSurface,
        "card(surfaceContainerHighest)" to Color(0xFFE6E0E9),
        "surfaceVariant" to Color(0xFFE7E0EC)
    )

    private val darkBackgrounds = mapOf(
        "background" to DarkBackground,
        "surface" to DarkSurface,
        "card(surfaceContainerHighest)" to Color(0xFF36343B),
        "surfaceVariant" to Color(0xFF49454F)
    )

    @Test
    fun `라이트 색은 라이트 배경에서 목표 대비를 넘는다`() {
        assertMeetsContrast(LightUsageColors, lightBackgrounds, targetContrast)
    }

    @Test
    fun `다크 색은 다크 배경에서 최소 대비를 넘는다`() {
        assertMeetsContrast(DarkUsageColors, darkBackgrounds, minimumContrast)
    }

    /**
     * 색을 옮기더라도 '따뜻한 색은 모바일, 차가운 색은 Wi-Fi'가 뒤집히면 안 된다.
     *
     * 대비만 맞추다 보면 색상이 밀려나기 쉬운데, 그러면 사용자가 두 회선을 반대로 읽는다.
     * 채널 순서로 고정해 두면 그 실수가 테스트에서 잡힌다.
     */
    @Test
    fun `모바일은 따뜻한 색이고 Wi-Fi는 차가운 색이다`() {
        listOf(LightUsageColors, DarkUsageColors).forEach { colors ->
            val mobile = colors.network.mobile
            assertTrue("모바일이 따뜻한 색이 아니다: $mobile", mobile.red > mobile.green)
            assertTrue("모바일이 따뜻한 색이 아니다: $mobile", mobile.green > mobile.blue)

            val wifi = colors.network.wifi
            assertTrue("Wi-Fi가 차가운 색이 아니다: $wifi", wifi.blue > wifi.green)
            assertTrue("Wi-Fi가 차가운 색이 아니다: $wifi", wifi.green > wifi.red)
        }
    }

    /**
     * 위젯 진행 막대는 **배경화면 위에** 놓인다.
     *
     * 기기 테마는 배경화면이 밝은지 어두운지 알지 못하므로 흑백 양쪽에서 읽혀야 한다.
     * 앱 팔레트를 끌어다 쓰면 한쪽 끝에서 반드시 실패한다는 사실을 여기서 못 박는다.
     */
    @Test
    fun `위젯 진행 막대는 흰 배경화면과 검은 배경화면 양쪽에서 읽힌다`() {
        listOf(Color.White, Color.Black).forEach { wallpaper ->
            val contrast = contrastRatio(WidgetColors.LIMIT_PROGRESS_COLOR, wallpaper)
            assertTrue(
                "위젯 진행 막대가 $wallpaper 위에서 대비 $contrast",
                contrast >= minimumContrast
            )
        }
    }

    private fun assertMeetsContrast(
        colors: UsageColors,
        backgrounds: Map<String, Color>,
        required: Double
    ) {
        val named = mapOf(
            "모바일" to colors.network.mobile,
            "Wi-Fi" to colors.network.wifi,
            "직접 사용" to colors.state.foreground,
            "백그라운드" to colors.state.background,
            "구분 없음" to colors.state.unknown
        )

        named.forEach { (colorName, color) ->
            backgrounds.forEach { (backgroundName, background) ->
                val contrast = contrastRatio(color, background)
                assertTrue(
                    "$colorName 이 $backgroundName 위에서 대비 $contrast (필요 $required)",
                    contrast >= required
                )
            }
        }
    }
}
