package com.dksystems.tracephone.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 기준 눈금의 폭. 6dp짜리 얇은 막대에서도 선으로 읽히는 가장 좁은 값이다. */
private val MARKER_WIDTH = 2.dp

/**
 * 공통의 자로 잰 막대 하나. **여러 줄이 같은 [fraction] 기준을 쓸 때만 의미가 있다.**
 *
 * [SegmentedUsageBar]와 다르다. 저쪽은 한 줄 **안에서** 몫의 비율을 보여주므로
 * 줄마다 가득 찬다. 이쪽은 줄 **사이**를 비교하는 것이 목적이라 줄마다 길이가 다르다.
 *
 * 높이와 모서리를 [SegmentedUsageBar]와 나눠 쓴다. 따로 두면 두 막대가
 * 같은 화면에서 미묘하게 어긋나 보인다.
 *
 * 스크린 리더에는 넣지 않는다. 같은 값이 늘 옆에 글자로 적혀 있어 두 번 읽히게 된다.
 *
 * @param fraction 0.0 ~ 1.0. 호출부가 이미 잘라서 넘긴다.
 * @param markerFraction 기준선 자리. null이면 그리지 않는다. 뜻은 [UsageScaleBar] 호출부가 정한다.
 */
@Composable
fun UsageScaleBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    markerFraction: Float? = null,
    height: Dp = DEFAULT_BAR_HEIGHT
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = barMarkerColor(
        markerRestsOnFill(fillFraction = fraction, markerFraction = markerFraction ?: 0f)
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(BAR_CORNER_RADIUS))
    ) {
        drawRect(color = trackColor, size = size)

        // 0으로 그리면 모서리만 남아 점처럼 보인다.
        if (fraction > 0f) {
            drawRect(color = color, size = Size(size.width * fraction, size.height))
        }

        if (markerFraction != null) {
            val markerWidth = MARKER_WIDTH.toPx()
            val markerLeft = (size.width * markerFraction - markerWidth / 2f)
                .coerceIn(0f, size.width - markerWidth)

            drawRect(
                color = markerColor,
                topLeft = Offset(markerLeft, 0f),
                size = Size(markerWidth, size.height)
            )
        }
    }
}
