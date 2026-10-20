package com.dksystems.tracephone.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.dksystems.tracephone.ui.theme.UsageTheme

/**
 * 모바일과 Wi-Fi의 비율을 한 줄 막대로 보여준다.
 *
 * 숫자만으로는 "이 앱이 모바일을 주로 쓰는가"가 한눈에 들어오지 않는다.
 * 목록을 훑을 때 비율을 즉시 읽히게 하는 것이 이 막대의 목적이다.
 *
 * @param mobileBytes 모바일 사용량
 * @param wifiBytes Wi-Fi 사용량
 */
@Composable
fun UsageSplitBar(
    mobileBytes: Long,
    wifiBytes: Long,
    modifier: Modifier = Modifier,
    height: Dp = DEFAULT_BAR_HEIGHT
) {
    val colors = UsageTheme.colors.network

    SegmentedUsageBar(
        segments = listOf(
            UsageBarSegment(bytes = mobileBytes, color = colors.mobile),
            UsageBarSegment(bytes = wifiBytes, color = colors.wifi)
        ),
        modifier = modifier,
        height = height
    )
}
