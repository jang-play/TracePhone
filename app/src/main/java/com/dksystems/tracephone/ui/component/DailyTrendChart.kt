package com.dksystems.tracephone.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.domain.model.DailyUsagePoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 날짜 축 라벨 형식. 좁은 화면에서도 읽히도록 월/일만 쓴다. */
private val AXIS_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

/**
 * 가로축에 적을 날짜 수.
 *
 * 한 달치를 그리면 막대가 서른 개다. 넷쯤 짚어 두면 글자가 겹치지 않으면서
 * 가운데 막대가 며칠인지도 그 선을 기준으로 읽힌다.
 */
private const val DATE_LABEL_COUNT = 4

/**
 * 일자별 사용량 막대 그래프.
 *
 * 그리는 일은 [UsageBarChart]가 맡고 여기서는 날짜를 글자로 바꾸는 일만 한다.
 *
 * @param onDateSelected 막대를 눌렀을 때 그 날짜를 알려준다. 시간대별 화면으로 넘어갈 때 쓴다.
 */
@Composable
fun DailyTrendChart(
    dailyPoints: List<DailyUsagePoint>,
    modifier: Modifier = Modifier,
    onDateSelected: ((LocalDate) -> Unit)? = null
) {
    if (dailyPoints.isEmpty()) {
        return
    }

    val bars = remember(dailyPoints) {
        dailyPoints.map { point ->
            UsageBarValue(
                mobileBytes = point.mobile.totalBytes,
                wifiBytes = point.wifi.totalBytes
            )
        }
    }

    val actionLabel = stringResource(R.string.chart_daily_bar_action)

    UsageBarChart(
        bars = bars,
        labels = rememberDailyBarLabels(dailyPoints),
        modifier = modifier,
        // 날짜를 누르면 곧바로 다른 화면으로 넘어간다.
        // 이 화면에 고른 상태로 남지 않으므로 도드라지게 칠할 막대도 없다.
        interaction = onDateSelected?.let { callback ->
            BarInteraction.Navigate(actionLabel) { index -> callback(dailyPoints[index].date) }
        }
    )
}

/**
 * 그래프가 무엇을 덮고 있는지 한 줄로 요약한다.
 *
 * 축 눈금을 픽셀에 직접 그리는 탓에 그 글자는 스크린 리더에 없다.
 * 눈금을 하나씩 노드로 만드는 대신, 축이 정하는 **문맥**만 제목이 대신 읽게 한다.
 * 막대마다 정확한 값을 따로 읽어 주므로 눈금은 환산할 대상이 없는 숫자일 뿐이다.
 */
@Composable
fun rememberDailyTrendSummary(dailyPoints: List<DailyUsagePoint>): String {
    val datePattern = stringResource(R.string.chart_bar_date_pattern)
    val summaryFormat = stringResource(R.string.chart_daily_summary)
    val denseFormat = stringResource(R.string.chart_daily_summary_dense)

    return remember(dailyPoints, datePattern, summaryFormat, denseFormat) {
        val formatter = DateTimeFormatter.ofPattern(datePattern)
        val firstDate = dailyPoints.first().date.format(formatter)
        val lastDate = dailyPoints.last().date.format(formatter)

        if (dailyPoints.size <= MAX_INDIVIDUALLY_LABELED_BARS) {
            // 축 라벨과 같은 형식을 쓴다. 눈으로 보는 글자와 귀로 듣는 말이 달라선 안 된다.
            val axisMax = ByteFormatter.formatCompact(barChartAxisMaxBytes(dailyPoints.toBars()))
            summaryFormat.format(firstDate, lastDate, dailyPoints.size, axisMax)
        } else {
            val peak = dailyPoints.maxBy { it.totalBytes }
            denseFormat.format(
                firstDate,
                lastDate,
                dailyPoints.size,
                peak.date.format(formatter),
                ByteFormatter.format(peak.totalBytes)
            )
        }
    }
}

@Composable
private fun rememberDailyBarLabels(dailyPoints: List<DailyUsagePoint>): BarChartLabels {
    val barFormat = stringResource(R.string.chart_bar_label)
    val noUsage = stringResource(R.string.chart_bar_no_usage)
    val datePattern = stringResource(R.string.chart_bar_date_pattern)

    return remember(dailyPoints, barFormat, noUsage, datePattern) {
        val formatter = DateTimeFormatter.ofPattern(datePattern)

        BarChartLabels(
            xAxis = dateAxisLabels(dailyPoints),
            describeBar = { index, bar ->
                barFormat.format(
                    dailyPoints[index].date.format(formatter),
                    describeBytes(bar.totalBytes, noUsage)
                )
            }
        )
    }
}

/** 0을 "0 B"로 읽으면 값이 있는 막대와 헷갈린다. 없다는 말로 바꾼다. */
internal fun describeBytes(byteCount: Long, noUsageText: String): String =
    if (byteCount <= 0L) noUsageText else ByteFormatter.format(byteCount)

private fun List<DailyUsagePoint>.toBars(): List<UsageBarValue> = map { point ->
    UsageBarValue(mobileBytes = point.mobile.totalBytes, wifiBytes = point.wifi.totalBytes)
}

private fun dateAxisLabels(dailyPoints: List<DailyUsagePoint>): List<BarAxisLabel> =
    axisLabelIndices(dailyPoints.size, DATE_LABEL_COUNT).map { index ->
        BarAxisLabel(index = index, text = dailyPoints[index].date.format(AXIS_DATE_FORMATTER))
    }

/**
 * 처음과 끝을 포함해 고르게 벌어진 자리를 고른다.
 *
 * 막대가 적어 라벨 수보다 모자라면 전부 적는다. 겹칠 일이 없다.
 */
private fun axisLabelIndices(barCount: Int, labelCount: Int): List<Int> {
    if (barCount <= labelCount) {
        return List(barCount) { index -> index }
    }

    val lastIndex = barCount - 1
    return List(labelCount) { position -> position * lastIndex / (labelCount - 1) }
}
