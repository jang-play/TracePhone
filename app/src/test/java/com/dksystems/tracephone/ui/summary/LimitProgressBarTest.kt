package com.dksystems.tracephone.ui.summary

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.dksystems.tracephone.ui.theme.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 한도 진행 막대의 색 규칙을 고정한다.
 *
 * 눈금이 어디서든 보이는지는 [com.dksystems.tracephone.ui.component.BarMarkerTest]가 맡는다.
 * 두 막대가 그 규칙을 함께 쓰므로 한곳에 모아 두었다.
 *
 * ### 이 테스트가 막지 못하는 것
 * Composable이 실제로 어느 토큰을 고르는지는 여기서 볼 수 없다. 계측 테스트와 Robolectric을
 * 쓰지 않기로 했기 때문이다. 즉 누군가 `limitStatusColor`를 다시 `primary`로 되돌리는 것은
 * 이 테스트로 잡히지 않는다. 여기서 고정하는 것은 **그 선택이 기대는 전제**다 -
 * 고른 토큰끼리 대비가 나오는가, 그리고 중립 계열이 정말 중립인가.
 */
class LimitProgressBarTest {

    /** 도형 채우기 기준. 글자가 아니므로 3:1이다. */
    private val minimumContrast = 3.0

    /**
     * 무채색으로 볼 채널 편차의 한계(0~255).
     *
     * 회선 색은 편차가 160을 넘는다. 32면 둘을 가르고도 남는다.
     */
    private val neutralChannelSpread = 32

    private val schemes = mapOf(
        "라이트" to lightColorScheme(),
        "다크" to darkColorScheme()
    )

    /** 정상 구간의 채움이 트랙 위에서 읽힌다. */
    @Test
    fun `정상 구간의 채움이 트랙 위에서 읽힌다`() {
        assertMeetsContrast { it.onSurfaceVariant to it.surfaceVariant }
    }

    /** 초과 구간의 채움이 트랙 위에서 읽힌다. */
    @Test
    fun `초과 구간의 채움이 트랙 위에서 읽힌다`() {
        assertMeetsContrast { it.error to it.surfaceVariant }
    }

    /**
     * 막대에 쓰는 중립색이 실제로 무채색인가.
     *
     * 이 전제가 깨지면 배경화면에 따라 막대가 '모바일 주황'과 겹칠 수 있다.
     * M3가 중립 계열의 채도를 낮게 묶어 두는 덕에 성립하는 것이라, 우리가 믿는 쪽에 못을 박는다.
     */
    @Test
    fun `막대의 중립색은 무채색에 가깝다`() {
        schemes.forEach { (name, scheme) ->
            val spread = channelSpread(scheme.onSurfaceVariant)
            assertTrue(
                "$name onSurfaceVariant 채널 편차 $spread (한계 $neutralChannelSpread)",
                spread <= neutralChannelSpread
            )
        }
    }

    private fun assertMeetsContrast(pick: (ColorScheme) -> Pair<Color, Color>) {
        schemes.forEach { (name, scheme) ->
            val (foreground, background) = pick(scheme)
            val contrast = contrastRatio(foreground, background)
            assertTrue(
                "$name 대비 $contrast (필요 $minimumContrast)",
                contrast >= minimumContrast
            )
        }
    }

    /** 가장 밝은 채널과 가장 어두운 채널의 차이. 무채색이면 0에 가깝다. */
    private fun channelSpread(color: Color): Int {
        val channels = listOf(color.red, color.green, color.blue).map { (it * 255f).toInt() }

        return channels.max() - channels.min()
    }
}
