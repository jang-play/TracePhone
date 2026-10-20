package com.dksystems.tracephone.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * 막대 위에 그은 눈금이 **채워진 부분 위에** 놓이는가.
 *
 * 눈금은 채움 위에 있을 때와 트랙 위에 있을 때 서로 다른 색을 써야 한다.
 * 한 가지 색으로 양쪽에서 보이게 할 수는 없기 때문이다. 그 판단이 이 함수다.
 *
 * 조용히 틀리는 종류의 계산이라 순수 함수로 빼 두었다 - 틀려도 화면은 그려지고,
 * 눈금만 배경에 묻혀 사라진다.
 *
 * 경계에서 눈금이 걸치는 경우는 따지지 않는다. 두 값이 같다는 것은 눈금이 가리키는 자리에
 * 정확히 도달했다는 뜻이고, 그 순간 눈금이 따로 알려 줄 것이 없다.
 *
 * 한도 진행 막대와 앱 목록 막대가 함께 쓴다. 규칙을 따로 두면 두 막대의 눈금이
 * 서로 다른 조건에서 사라진다.
 */
internal fun markerRestsOnFill(fillFraction: Float, markerFraction: Float): Boolean =
    markerFraction <= fillFraction

/**
 * 눈금 색. **자기가 놓인 바닥의 반대색을 쓴다.**
 *
 * 채움 안에서는 트랙 색을 써 막대에 홈을 판 것처럼 보이고,
 * 트랙 위에서는 글자색을 써 또렷한 선이 된다. 두 경우 모두 바닥과 3:1을 넘는다.
 */
@Composable
@ReadOnlyComposable
internal fun barMarkerColor(restsOnFill: Boolean): Color = if (restsOnFill) {
    MaterialTheme.colorScheme.surfaceVariant
} else {
    MaterialTheme.colorScheme.onSurface
}
