package com.dksystems.tracephone.ui.summary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.dksystems.tracephone.R
import com.dksystems.tracephone.domain.model.PeriodUnit
import com.dksystems.tracephone.domain.model.UsagePeriod
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 현재 보고 있는 기간을 화면에 쓸 문구로 바꾼다.
 *
 * 날짜 형식은 문자열 리소스에 두어 번역할 때 함께 바꿀 수 있게 했다.
 * 코드에 형식을 박아두면 다른 언어를 추가할 때 손댈 곳이 흩어진다.
 *
 * 예) 일 → `2026년 9월 3일` · 주 → `9월 1일 ~ 9월 3일` · 월 → `2026년 9월`
 */
@Composable
fun rememberPeriodLabel(unit: PeriodUnit, period: UsagePeriod): String {
    val dayPattern = stringResource(R.string.date_pattern_day)
    val monthPattern = stringResource(R.string.date_pattern_month)
    val weekEndpointPattern = stringResource(R.string.date_pattern_week_endpoint)
    val rangeFormat = stringResource(R.string.period_range_format)
    val allTimeLabel = stringResource(R.string.period_all_time)

    return remember(unit, period, dayPattern, monthPattern, weekEndpointPattern) {
        when (unit) {
            PeriodUnit.DAY -> period.startDate.format(formatter(dayPattern))

            PeriodUnit.WEEK -> {
                val endpointFormatter = formatter(weekEndpointPattern)
                rangeFormat.format(
                    period.startDate.format(endpointFormatter),
                    period.endDateInclusive.format(endpointFormatter)
                )
            }

            PeriodUnit.MONTH -> period.startDate.format(formatter(monthPattern))

            PeriodUnit.ALL -> allTimeLabel
        }
    }
}

private fun formatter(pattern: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
