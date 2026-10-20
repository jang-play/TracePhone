package com.dksystems.tracephone.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dksystems.tracephone.R
import com.dksystems.tracephone.ui.theme.UsageTheme

/**
 * 모바일/Wi-Fi 색 범례.
 *
 * 막대와 그래프가 같은 색 규칙을 쓰므로 범례는 한 번만 보여주면 된다.
 */
@Composable
fun NetworkKindLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(
            color = UsageTheme.colors.network.mobile,
            label = stringResource(R.string.summary_mobile)
        )
        LegendItem(
            color = UsageTheme.colors.network.wifi,
            label = stringResource(R.string.summary_wifi)
        )
    }
}
