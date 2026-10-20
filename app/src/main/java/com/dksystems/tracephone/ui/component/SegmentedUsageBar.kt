package com.dksystems.tracephone.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val DEFAULT_BAR_HEIGHT = 6.dp
/** [UsageScaleBar]와 나눠 쓴다. 두 막대가 같은 화면에 놓이므로 모서리가 같아야 한다. */
internal val BAR_CORNER_RADIUS = 3.dp

/**
 * 칸 사이를 띄우는 폭.
 *
 * 사용량 색은 배경과의 대비를 우선해 고른 탓에 색끼리의 밝기가 서로 비슷하다.
 * 맞붙여 그리면 색상 차이만 남아 색각 이상 사용자에게는 경계가 사라진다.
 * 틈을 두면 트랙 색이 비쳐 경계가 드러난다.
 */
private val SEGMENT_GAP = 1.dp

/**
 * 막대 한 칸. 길이는 [bytes]에 비례한다.
 */
data class UsageBarSegment(val bytes: Long, val color: Color)

/**
 * 여러 몫의 비율을 한 줄 막대로 보여준다.
 *
 * 회선 구분(모바일/Wi-Fi)과 상태 구분(직접 사용/백그라운드)이 같은 모양을 쓰므로
 * 그리는 코드를 하나로 둔다. 두 곳에 따로 두면 높이나 모서리가 조금씩 어긋난다.
 *
 * 스크린 리더에는 넣지 않는다. 이 막대가 나타내는 값은 늘 옆에 글자로 적혀 있어,
 * 노드를 만들면 같은 말을 두 번 듣게 된다.
 * **새 호출처에도 회선 이름과 값을 반드시 함께 두어야 이 규칙이 유지된다.**
 *
 * @param segments 왼쪽부터의 순서대로 그린다. 값이 0인 칸은 그리지 않는다.
 */
@Composable
fun SegmentedUsageBar(
    segments: List<UsageBarSegment>,
    modifier: Modifier = Modifier,
    height: Dp = DEFAULT_BAR_HEIGHT
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(BAR_CORNER_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP)
    ) {
        // weight는 0을 허용하지 않으므로 값이 없는 칸은 아예 그리지 않는다.
        segments.filter { it.bytes > 0L }.forEach { segment ->
            Box(
                modifier = Modifier
                    .weight(segment.bytes.toFloat())
                    .fillMaxHeight()
                    .background(segment.color)
            )
        }
    }
}
