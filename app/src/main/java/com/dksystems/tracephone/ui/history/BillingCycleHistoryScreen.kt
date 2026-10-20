package com.dksystems.tracephone.ui.history

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.domain.model.BillingCycleHistory
import com.dksystems.tracephone.domain.model.BillingCycleUsage
import com.dksystems.tracephone.ui.component.ByteText
import com.dksystems.tracephone.ui.theme.UsageValueTextStyle
import java.time.format.DateTimeFormatter

/**
 * 지난 청구 주기들의 모바일 실적. **한도를 얼마로 잡을지 판단할 근거를 만드는 화면이다.**
 *
 * ### 이번 주기를 목록에 넣지 않는 이유
 * 진행 중인 주기(예: 12일치)를 끝난 주기(31일치)와 같은 자 위에 나란히 놓으면
 * **"이번 달에 갑자기 줄었다"**로 읽힌다. 맨 위 카드로 빼 성격이 다름을 자리로 알린다.
 *
 * ### 줄에 한도 대비 비율을 적지 않는 이유
 * 주기별 한도 이력을 저장하지 않으므로 지난 주기에 한도가 얼마였는지 알 수 없다.
 * `5.24 / 6.0 GB · 87%`는 **근거 없는 주장**이다. 한도는 목록 전체를 가로지르는
 * 기준선 하나로만 긋고, 그것이 '지금' 값이라는 사실을 글자로 밝힌다.
 *
 * 기준선은 지금의 사실(한도는 6 GB다), 막대는 과거의 사실(8월에 5.24 GB 썼다)이다.
 * 둘 다 참인 값을 같은 자에 올린 것이라, 사용자가 읽어 내는 "지금 한도로 치면
 * 8월엔 빠듯했겠다"는 맞는 추론이 된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingCycleHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BillingCycleHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cycle_history_title)) },
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
        val history = uiState.history

        when {
            uiState.isLoading -> LoadingContent(Modifier.padding(innerPadding))

            // 정상 경로로는 닿지 않는다 - 두 진입점 모두 한도가 켜져 있을 때만 나타난다.
            // 프로세스가 정리된 뒤 화면이 복원되는 경로가 남아 있어 빈 상태를 둔다.
            history == null -> NoticeContent(
                text = stringResource(R.string.cycle_history_no_limit),
                modifier = Modifier.padding(innerPadding)
            )

            else -> HistoryContent(
                history = history,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoticeContent(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(SCREEN_PADDING)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HistoryContent(history: BillingCycleHistory, modifier: Modifier = Modifier) {
    val monthFormatter = rememberFormatter(R.string.date_pattern_month)
    val dayFormatter = rememberFormatter(R.string.date_pattern_week_endpoint)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING)
    ) {
        CurrentCycleCard(
            usage = history.current,
            dateRange = stringResource(
                R.string.cycle_history_current_range,
                history.current.cycle.startDate.format(dayFormatter),
                history.current.cycle.endDateInclusive.format(dayFormatter),
                history.current.countedThrough.format(dayFormatter)
            )
        )

        if (history.finished.isEmpty()) {
            Text(
                text = stringResource(R.string.cycle_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FinishedSummaryCard(history = history)
            LimitLineNotice(history = history)

            history.finished.forEach { usage ->
                BillingCycleRow(
                    usage = usage,
                    history = history,
                    cycleName = stringResource(
                        R.string.cycle_history_cycle_name,
                        usage.cycle.startDate.format(monthFormatter)
                    ),
                    dateRange = stringResource(
                        R.string.monthly_limit_cycle_range,
                        usage.cycle.startDate.format(dayFormatter),
                        usage.cycle.endDateInclusive.format(dayFormatter)
                    )
                )
            }

            // 개수를 적지 않는다. 빠진 이유가 둘(반쪽만 기록된 주기, 개수 상한)인데
            // 목록 길이를 적으면 그 길이가 상한인 것처럼 읽힌다.
            if (history.hasOlderRecords) {
                Text(
                    text = stringResource(R.string.cycle_history_truncated),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = stringResource(R.string.cycle_history_mobile_only),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CurrentCycleCard(usage: BillingCycleUsage, dateRange: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_LINE_SPACING)
        ) {
            Text(
                text = stringResource(R.string.cycle_history_current_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = dateRange,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                ByteText(bytes = usage.mobileBytes, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        R.string.cycle_history_daily_average,
                        ByteFormatter.format(usage.dailyAverageBytes)
                    ),
                    style = UsageValueTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stringResource(R.string.cycle_history_current_notice),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FinishedSummaryCard(history: BillingCycleHistory) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_LINE_SPACING)
        ) {
            Text(
                text = stringResource(
                    R.string.cycle_history_finished_count,
                    history.finished.size
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }
            )

            SummaryLine(
                label = stringResource(R.string.cycle_history_peak),
                bytes = history.peakBytes
            )
            SummaryLine(
                label = stringResource(R.string.cycle_history_average),
                bytes = history.averageBytes
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, bytes: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ByteText(bytes = bytes, style = UsageValueTextStyle)
    }
}

/**
 * 기준선이 '지금' 한도라는 사실과, 그때 한도는 알 수 없다는 사실을 함께 밝힌다.
 *
 * 한도가 꺼진 상태로는 이 화면에 닿지 않지만, 값이 0인 경우에는 기준선이 없으므로
 * 기준선 설명도 적지 않는다. 없는 선을 설명하면 사용자가 그 선을 찾게 된다.
 */
@Composable
private fun LimitLineNotice(history: BillingCycleHistory) {
    if (history.limitFraction == null) {
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(CARD_LINE_SPACING)) {
        Text(
            text = stringResource(
                R.string.cycle_history_limit_line,
                ByteFormatter.format(history.currentLimitBytes)
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.cycle_history_limit_unknown),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 날짜 패턴을 리소스에서 꺼내 포맷터를 만든다. 표시 규칙을 화면마다 따로 적지 않기 위해서다. */
@Composable
private fun rememberFormatter(patternResId: Int): DateTimeFormatter {
    val pattern = stringResource(patternResId)
    return remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
}

private val SCREEN_PADDING = 16.dp
private val SECTION_SPACING = 12.dp
private val CARD_PADDING = 14.dp
private val CARD_LINE_SPACING = 4.dp
