package com.dksystems.tracephone.ui.detail

import androidx.annotation.StringRes
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.core.PercentFormatter
import com.dksystems.tracephone.domain.model.NetworkKind
import com.dksystems.tracephone.domain.model.NetworkUsageSplit
import com.dksystems.tracephone.domain.model.UsageStateSplit
import com.dksystems.tracephone.ui.component.ByteText
import com.dksystems.tracephone.ui.component.DailyTrendChart
import com.dksystems.tracephone.ui.component.rememberDailyTrendSummary
import com.dksystems.tracephone.ui.component.LegendItem
import com.dksystems.tracephone.ui.component.NetworkKindLegend
import com.dksystems.tracephone.ui.component.UsageSplitBar
import com.dksystems.tracephone.ui.component.UsageStateBar
import com.dksystems.tracephone.ui.theme.UsageTheme
import com.dksystems.tracephone.ui.theme.UsageValueTextStyle
import java.time.LocalDate

/**
 * 앱 하나의 일자별 사용량 추이 화면.
 *
 * 요약 화면에서 고른 기간을 그대로 이어받으므로 두 화면의 합계가 일치한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    onNavigateBack: () -> Unit,
    onDateSelected: (uid: Int, date: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.appLabel.ifEmpty {
                                stringResource(R.string.detail_title)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // 지워진 앱은 저장된 패키지명이 그대로 제목이 된다.
                        // 그것이 무엇인지 알아보기 어려우므로 지워졌다는 사실을 함께 적는다.
                        if (!uiState.isAppInstalled) {
                            Text(
                                text = stringResource(R.string.summary_app_not_installed),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
            if (!uiState.hasUsage) {
                Text(
                    text = stringResource(R.string.detail_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            TotalsCard(uiState = uiState)

            StateSplitCard(split = uiState.stateSplit)

            SectionHeading(
                text = stringResource(R.string.detail_title),
                accessibilityText = rememberDailyTrendSummary(uiState.dailyPoints)
            )

            DailyTrendChart(
                dailyPoints = uiState.dailyPoints,
                onDateSelected = { date -> onDateSelected(uiState.uid, date) }
            )

            Text(
                text = stringResource(R.string.detail_tap_bar_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            NetworkKindLegend()

            DirectionBreakdown(uiState = uiState)
        }
    }
}

@Composable
private fun TotalsCard(uiState: AppDetailUiState) {
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
 * 앱을 직접 보고 있을 때 쓴 양과 그렇지 않을 때 쓴 양. 회선별로 나눠 보여준다.
 *
 * 이 앱에서 가장 실용적인 정보이므로 합계 바로 아래에 둔다.
 * 회선을 합치지 않는 이유는 [NetworkUsageSplit]에 적어 두었다.
 */
@Composable
private fun StateSplitCard(split: NetworkUsageSplit) {
    if (split.totalBytes <= 0L) {
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeading(text = stringResource(R.string.state_split_title))

            // 쓰지 않은 회선은 통째로 건너뛴다. 0만 적힌 칸이 늘면 실제 값이 묻힌다.
            NetworkKind.entries
                .filter { split.of(it).totalBytes > 0L }
                .forEach { kind ->
                    StateSplitSection(
                        label = stringResource(kind.labelResId()),
                        split = split.of(kind)
                    )
                }

            if (split.mobile.hasUnknownPortion || split.wifi.hasUnknownPortion) {
                Text(
                    text = stringResource(R.string.state_split_unknown_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stringResource(R.string.state_split_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StateSplitSection(label: String, split: UsageStateSplit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            ByteText(bytes = split.totalBytes, style = UsageValueTextStyle)
        }

        if (!split.hasKnownPortion) {
            Text(
                text = stringResource(R.string.state_split_all_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        UsageStateBar(split = split)

        StateValueRow(
            color = UsageTheme.colors.state.foreground,
            label = stringResource(R.string.state_foreground),
            bytes = split.foregroundBytes,
            ratio = split.foregroundRatio
        )
        StateValueRow(
            color = UsageTheme.colors.state.background,
            label = stringResource(R.string.state_background),
            bytes = split.backgroundBytes,
            ratio = split.backgroundRatio
        )

        if (split.hasUnknownPortion) {
            StateValueRow(
                color = UsageTheme.colors.state.unknown,
                label = stringResource(R.string.state_unknown),
                bytes = split.unknownBytes,
                ratio = split.unknownRatio
            )
        }
    }
}

@Composable
private fun StateValueRow(color: Color, label: String, bytes: Long, ratio: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        LegendItem(color = color, label = label)
        Text(
            text = stringResource(
                R.string.state_split_value_format,
                ByteFormatter.format(bytes),
                PercentFormatter.toPercent(ratio)
            ),
            style = UsageValueTextStyle
        )
    }
}

@StringRes
private fun NetworkKind.labelResId(): Int = when (this) {
    NetworkKind.MOBILE -> R.string.summary_mobile
    NetworkKind.WIFI -> R.string.summary_wifi
}

/**
 * 수신/송신 합계.
 *
 * 업로드가 유독 많은 앱을 찾아내려면 방향별 수치가 필요하다.
 */
@Composable
private fun DirectionBreakdown(uiState: AppDetailUiState) {
    val receivedBytes = uiState.dailyPoints.sumOf { it.mobile.receivedBytes + it.wifi.receivedBytes }
    val transmittedBytes =
        uiState.dailyPoints.sumOf { it.mobile.transmittedBytes + it.wifi.transmittedBytes }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LabeledValue(
                label = stringResource(R.string.detail_received),
                bytes = receivedBytes
            )
            LabeledValue(
                label = stringResource(R.string.detail_transmitted),
                bytes = transmittedBytes
            )
        }
    }
}

/**
 * 화면 구역의 제목.
 *
 * 제목으로 표시해 두면 스크린 리더가 제목 단위로 건너뛸 수 있다.
 * 그래프처럼 항목이 많은 구역을 지나가려는 사람에게는 이것이 유일한 지름길이다.
 *
 * @param accessibilityText 스크린 리더가 제목 대신 읽을 문장. 그래프의 문맥을 여기에 담는다.
 */
@Composable
private fun SectionHeading(text: String, accessibilityText: String? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics {
            heading()
            accessibilityText?.let { contentDescription = it }
        }
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
