package com.dksystems.tracephone.ui.summary

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dksystems.tracephone.R
import com.dksystems.tracephone.domain.model.PeriodUnit
import com.dksystems.tracephone.domain.model.UsageSortOrder

/**
 * 목록 위에 놓이는 선택 칩 두 줄 — 무엇을 볼지(기간)와 어떻게 세울지(정렬).
 *
 * 둘 다 열거형 전체를 칩으로 펼치는 같은 모양이라 한곳에 모아 둔다.
 * 열거형에 항목을 더하면 화면은 손대지 않아도 칩이 함께 늘어난다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodUnitSelector(
    selected: PeriodUnit,
    onSelect: (PeriodUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        // 칩이 좁은 화면에서 잘리지 않도록 가로로 넘길 수 있게 한다.
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PeriodUnit.entries.forEach { unit ->
            FilterChip(
                selected = unit == selected,
                onClick = { onSelect(unit) },
                label = { Text(stringResource(unit.labelResId())) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SortOrderSelector(
    selected: UsageSortOrder,
    onSelect: (UsageSortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UsageSortOrder.entries.forEach { order ->
            FilterChip(
                selected = order == selected,
                onClick = { onSelect(order) },
                label = { Text(stringResource(order.labelResId())) }
            )
        }
    }
}

@StringRes
private fun PeriodUnit.labelResId(): Int = when (this) {
    PeriodUnit.DAY -> R.string.period_unit_day
    PeriodUnit.WEEK -> R.string.period_unit_week
    PeriodUnit.MONTH -> R.string.period_unit_month
    PeriodUnit.ALL -> R.string.period_all_time
}

@StringRes
private fun UsageSortOrder.labelResId(): Int = when (this) {
    UsageSortOrder.TOTAL -> R.string.sort_by_total
    UsageSortOrder.MOBILE -> R.string.sort_by_mobile
    UsageSortOrder.WIFI -> R.string.sort_by_wifi
    UsageSortOrder.MOBILE_BACKGROUND -> R.string.sort_by_mobile_background
}
