package com.dksystems.tracephone.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.dksystems.tracephone.domain.model.UsageStateSplit
import com.dksystems.tracephone.ui.theme.UsageTheme

/**
 * 직접 사용 / 백그라운드 / 구분 없음의 비율을 한 줄 막대로 보여준다.
 *
 * '구분 없음'을 감추지 않고 함께 그린다. 감추면 나머지 두 칸이 100%처럼 보여
 * 실제보다 많이 측정된 것으로 오해하게 된다.
 */
@Composable
fun UsageStateBar(
    split: UsageStateSplit,
    modifier: Modifier = Modifier,
    height: Dp = DEFAULT_BAR_HEIGHT
) {
    val colors = UsageTheme.colors.state

    SegmentedUsageBar(
        segments = listOf(
            UsageBarSegment(bytes = split.foregroundBytes, color = colors.foreground),
            UsageBarSegment(bytes = split.backgroundBytes, color = colors.background),
            UsageBarSegment(bytes = split.unknownBytes, color = colors.unknown)
        ),
        modifier = modifier,
        height = height
    )
}
