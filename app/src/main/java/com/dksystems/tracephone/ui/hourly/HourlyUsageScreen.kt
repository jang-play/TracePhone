package com.dksystems.tracephone.ui.hourly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dksystems.tracephone.R
import com.dksystems.tracephone.domain.model.HourlyUsagePoint
import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.ui.component.BarAxisLabel
import com.dksystems.tracephone.ui.component.BarChartLabels
import com.dksystems.tracephone.ui.component.BarInteraction
import com.dksystems.tracephone.ui.component.ByteText
import com.dksystems.tracephone.ui.component.barChartAxisMaxBytes
import com.dksystems.tracephone.ui.component.describeBytes
import com.dksystems.tracephone.ui.component.NetworkKindLegend
import com.dksystems.tracephone.ui.component.UsageBarChart
import com.dksystems.tracephone.ui.component.UsageBarValue
import com.dksystems.tracephone.ui.component.UsageSplitBar
import com.dksystems.tracephone.ui.theme.UsageValueTextStyle
import java.time.format.DateTimeFormatter

/**
 * 앱 하나의 하루를 시간대별로 보여주는 화면.
 *
 * 시스템이 사용량을 시간 단위로 기록하지 않으므로 값은 근사치다.
 * 그 사실을 화면에 밝혀 두지 않으면 사용자가 정확한 시각으로 오해한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HourlyUsageScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HourlyUsageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val titleDateFormatter = remember(Unit) { DateTimeFormatter.ofPattern("M월 d일") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.appLabel.ifEmpty {
                                stringResource(R.string.hourly_title)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.date.format(titleDateFormatter),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator()

                !uiState.hasUsage -> Text(
                    text = stringResource(R.string.hourly_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> HourlyContent(uiState = uiState)
            }
        }
    }
}

@Composable
private fun HourlyContent(uiState: HourlyUsageUiState) {
    HourlyTotalsCard(uiState = uiState)
    HourlyChartSection(hourlyPoints = uiState.hourlyPoints)
}

/** 하루 전체 합계. 시간대별 막대를 보기 전에 크기의 기준을 잡아 준다. */
@Composable
private fun HourlyTotalsCard(uiState: HourlyUsageUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ByteText(
                bytes = uiState.totalBytes,
                style = MaterialTheme.typography.headlineMedium
            )
            UsageSplitBar(
                mobileBytes = uiState.mobileTotalBytes,
                wifiBytes = uiState.wifiTotalBytes
            )
            LabeledValue(
                label = stringResource(R.string.summary_mobile),
                bytes = uiState.mobileTotalBytes
            )
            LabeledValue(
                label = stringResource(R.string.summary_wifi),
                bytes = uiState.wifiTotalBytes
            )
        }
    }
}

/**
 * 시간대별 막대 그래프와 고른 시간대의 값.
 *
 * 막대 높이만으로는 몇 MB인지 읽을 수 없다. 눌러서 정확한 값을 보게 한다.
 *
 * 누르기 전에는 **가장 많이 쓴 시간대**를 골라 둔다.
 * 24개 막대에서 가장 높은 것을 눈으로 찾는 수고를 덜어 주고,
 * 막대를 고를 수 있다는 사실도 함께 드러난다.
 */
@Composable
private fun HourlyChartSection(hourlyPoints: List<HourlyUsagePoint>) {
    val bars = remember(hourlyPoints) { toBarValues(hourlyPoints) }
    val peakIndex = remember(hourlyPoints) {
        hourlyPoints.indices.maxByOrNull { hourlyPoints[it].totalBytes }
    }

    // 화면을 돌려도 고른 시간대를 잃지 않도록 저장한다.
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val shownIndex = selectedIndex ?: peakIndex

    val chartSummary = stringResource(
        R.string.chart_hourly_summary,
        // 축 라벨과 같은 형식을 쓴다. 눈으로 보는 글자와 귀로 듣는 말이 달라선 안 된다.
        ByteFormatter.formatCompact(barChartAxisMaxBytes(bars))
    )

    // 축 눈금을 픽셀에 그려 스크린 리더에는 없다. 그 문맥을 제목이 대신 읽는다.
    Text(
        text = stringResource(R.string.hourly_chart_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics {
            heading()
            contentDescription = chartSummary
        }
    )

    UsageBarChart(
        bars = bars,
        labels = rememberHourlyBarLabels(hourlyPoints),
        interaction = BarInteraction.Select(
            selectedIndex = shownIndex,
            actionLabel = stringResource(R.string.chart_hourly_bar_action),
            onSelect = { index -> selectedIndex = index }
        )
    )

    NetworkKindLegend()

    Text(
        text = stringResource(R.string.hourly_approximation_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    shownIndex?.let { index ->
        hourlyPoints.getOrNull(index)?.let { point ->
            HourDetailCard(point = point, isPeak = selectedIndex == null)
        }
    }
}

/**
 * 가로축에 짚을 시각.
 *
 * 여섯 시간마다 하나씩 두고 마지막 칸에도 하나 둔다.
 * 하루의 끝이 어디인지 보이지 않으면 그래프가 무엇을 덮고 있는지 알 수 없다.
 */
private val AXIS_LABEL_HOURS = listOf(0, 6, 12, 18, 23)

/**
 * 눈에 보이는 축 라벨과 스크린 리더가 읽을 막대 설명.
 *
 * 축 글자와 음성이 같은 문구(`hourly_axis_hour`)를 쓴다.
 * 둘이 다르면 눈으로 보는 사람과 귀로 듣는 사람이 서로 다른 화면을 말하게 된다.
 */
@Composable
private fun rememberHourlyBarLabels(hourlyPoints: List<HourlyUsagePoint>): BarChartLabels {
    val hourFormat = stringResource(R.string.hourly_axis_hour)
    val barFormat = stringResource(R.string.chart_bar_label)
    val noUsage = stringResource(R.string.chart_bar_no_usage)

    return remember(hourlyPoints, hourFormat, barFormat, noUsage) {
        BarChartLabels(
            xAxis = AXIS_LABEL_HOURS.map { hour ->
                BarAxisLabel(index = hour, text = hourFormat.format(hour))
            },
            describeBar = { index, bar ->
                val hour = hourlyPoints.getOrNull(index)?.hour ?: index
                barFormat.format(hourFormat.format(hour), describeBytes(bar.totalBytes, noUsage))
            }
        )
    }
}

private fun toBarValues(hourlyPoints: List<HourlyUsagePoint>): List<UsageBarValue> =
    hourlyPoints.map { point ->
        UsageBarValue(
            mobileBytes = point.mobile.totalBytes,
            wifiBytes = point.wifi.totalBytes
        )
    }

/**
 * 고른 시간대 하나를 자세히 적는다.
 *
 * 화면 맨 아래에 두어 안내 문구가 사라져도 그 위의 내용이 밀리지 않는다.
 *
 * @param isPeak 아직 아무 막대도 누르지 않아 가장 많이 쓴 시간대를 대신 보여주는 중인지
 */
@Composable
private fun HourDetailCard(point: HourlyUsagePoint, isPeak: Boolean) {
    // 막대를 골라도 포커스는 막대에 남는다. 알리지 않으면 무엇이 달라졌는지 알 수 없다.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = hourRangeTitle(hour = point.hour, isPeak = isPeak),
                style = MaterialTheme.typography.titleSmall
            )

            HourUsageRows(point = point)

            if (isPeak) {
                Text(
                    text = stringResource(R.string.hourly_tap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 시간대 이름. 한 시간짜리 구간임이 드러나도록 끝 시각까지 적는다. */
@Composable
private fun hourRangeTitle(hour: Int, isPeak: Boolean): String {
    val endHour = hour + 1
    val titleResId = if (isPeak) R.string.hourly_peak_label else R.string.hourly_selected_label

    return stringResource(titleResId, hour, endHour)
}

@Composable
private fun HourUsageRows(point: HourlyUsagePoint) {
    LabeledValue(
        label = stringResource(R.string.hourly_hour_total),
        bytes = point.totalBytes
    )
    LabeledValue(
        label = stringResource(R.string.summary_mobile),
        bytes = point.mobile.totalBytes
    )
    LabeledValue(
        label = stringResource(R.string.summary_wifi),
        bytes = point.wifi.totalBytes
    )
}

@Composable
private fun LabeledValue(label: String, bytes: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        ByteText(bytes = bytes, style = UsageValueTextStyle)
    }
}
