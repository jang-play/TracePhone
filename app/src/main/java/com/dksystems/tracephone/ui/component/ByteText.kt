package com.dksystems.tracephone.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import com.dksystems.tracephone.core.ByteFormatter

/** 단위를 값보다 얼마나 작게 적을지. */
private const val UNIT_SIZE_RATIO = 0.85f

private const val UNIT_SEPARATOR = " "

/**
 * 바이트 값을 숫자와 단위로 나눠 그린다.
 *
 * 숫자는 고정폭 그대로 두어 세로로 늘어놓았을 때 자릿수가 맞고,
 * 단위는 한 단계 작은 일반 글꼴로 적어 숫자가 먼저 읽히게 한다.
 *
 * 전부 고정폭으로 적으면 단위 앞 공백까지 숫자 한 자리만큼 벌어져
 * `146.96  MB`처럼 값과 단위가 갈라져 보인다.
 *
 * @param style 숫자에 쓸 글꼴. 단위는 이 크기의 [UNIT_SIZE_RATIO]배로 그린다.
 */
@Composable
fun ByteText(
    bytes: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val formatted = remember(bytes) { ByteFormatter.split(bytes) }

    val text = remember(formatted, style.fontSize) {
        buildAnnotatedString {
            append(formatted.value)
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = style.fontSize * UNIT_SIZE_RATIO
                )
            ) {
                append(UNIT_SEPARATOR)
                append(formatted.unit)
            }
        }
    }

    Text(text = text, style = style, color = color, modifier = modifier, maxLines = 1)
}
