package com.dksystems.tracephone.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.x 대비비. 밝은 쪽을 분자에 둔다.
 *
 * 색 대비를 재는 테스트가 둘 이상이라 여기 모아 둔다.
 * 계산식을 각자 들고 있으면 한쪽만 고쳐져 서로 다른 답을 내놓게 된다.
 */
internal fun contrastRatio(foreground: Color, background: Color): Double {
    val first = relativeLuminance(foreground)
    val second = relativeLuminance(background)

    return (max(first, second) + LUMINANCE_OFFSET) / (min(first, second) + LUMINANCE_OFFSET)
}

private fun relativeLuminance(color: Color): Double =
    RED_WEIGHT * toLinear(color.red) +
        GREEN_WEIGHT * toLinear(color.green) +
        BLUE_WEIGHT * toLinear(color.blue)

private fun toLinear(channel: Float): Double {
    val value = channel.toDouble()

    return if (value <= LINEAR_THRESHOLD) {
        value / LINEAR_DIVISOR
    } else {
        ((value + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA_EXPONENT)
    }
}

private const val LUMINANCE_OFFSET = 0.05

private const val RED_WEIGHT = 0.2126
private const val GREEN_WEIGHT = 0.7152
private const val BLUE_WEIGHT = 0.0722

private const val LINEAR_THRESHOLD = 0.03928
private const val LINEAR_DIVISOR = 12.92
private const val GAMMA_OFFSET = 0.055
private const val GAMMA_DIVISOR = 1.055
private const val GAMMA_EXPONENT = 2.4
