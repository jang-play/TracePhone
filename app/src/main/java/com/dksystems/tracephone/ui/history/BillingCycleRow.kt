package com.dksystems.tracephone.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.domain.model.BillingCycleHistory
import com.dksystems.tracephone.domain.model.BillingCycleUsage
import com.dksystems.tracephone.ui.component.ByteText
import com.dksystems.tracephone.ui.component.UsageScaleBar
import com.dksystems.tracephone.ui.component.describeBytes
import com.dksystems.tracephone.ui.theme.UsageTheme
import com.dksystems.tracephone.ui.theme.UsageValueTextStyle

/**
 * 주기 한 줄을 스크린 리더가 읽을 문구로 잇는 데 필요한 글자들.
 *
 * 리소스에서 이미 꺼낸 문자열만 받는다. 안드로이드에 기대지 않아야 JVM 테스트로 고정할 수 있다.
 */
internal data class BillingCycleRowLabels(
    val cycleName: String,
    val dailyAverage: String,
    val noUsage: String,
    val separator: String
)

/**
 * 주기 한 줄을 한 문장으로 잇는다.
 *
 * 예) `"2026년 8월 주기, 5.24 GB, 하루 평균 169 MB"`
 *
 * 막대는 낭독하지 않는다. 같은 값이 바로 옆에 글자로 적혀 있어 두 번 읽히게 된다.
 * **한도 대비 비율도 읽지 않는다** — 그 주기에 한도가 얼마였는지 알 수 없으므로,
 * 화면이 적지 않는 것을 낭독만 적으면 거짓이 된다.
 */
internal fun describeBillingCycleRow(
    usage: BillingCycleUsage,
    labels: BillingCycleRowLabels
): String = listOf(
    labels.cycleName,
    describeBytes(usage.mobileBytes, labels.noUsage),
    labels.dailyAverage.format(describeBytes(usage.dailyAverageBytes, labels.noUsage))
).joinToString(labels.separator)

/**
 * 끝난 주기 한 줄. 이름·총량·날짜 범위·하루 평균·막대.
 *
 * 막대는 목록 전체가 같은 자를 쓰므로 줄 사이를 바로 견줄 수 있다.
 * 기준선은 **지금 설정된 한도** 자리이고, 그 사실은 목록 위 안내 문구가 밝힌다.
 * 줄에는 한도 대비 비율을 적지 않는다 — 그 주기의 한도를 모르기 때문이다.
 *
 * 줄 전체를 한 문장으로 읽어 주고 안쪽 글자는 낭독에서 뺀다.
 * 라벨 없는 숫자들이 따로 읽히면 어느 값이 무엇인지 알 수 없다.
 */
@Composable
internal fun BillingCycleRow(
    usage: BillingCycleUsage,
    history: BillingCycleHistory,
    cycleName: String,
    dateRange: String,
    modifier: Modifier = Modifier
) {
    val dailyAverageFormat = stringResource(R.string.cycle_history_daily_average)
    val labels = BillingCycleRowLabels(
        cycleName = cycleName,
        dailyAverage = dailyAverageFormat,
        noUsage = stringResource(R.string.chart_bar_no_usage),
        separator = stringResource(R.string.accessibility_separator)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = describeBillingCycleRow(usage, labels) },
        verticalArrangement = Arrangement.spacedBy(ROW_LINE_SPACING)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = cycleName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { }
            )
            ByteText(
                bytes = usage.mobileBytes,
                style = UsageValueTextStyle,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }

        Text(
            text = dateRange + SECONDARY_SEPARATOR +
                dailyAverageFormat.format(ByteFormatter.format(usage.dailyAverageBytes)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics { }
        )

        UsageScaleBar(
            fraction = history.fractionOf(usage.mobileBytes),
            color = UsageTheme.colors.network.mobile,
            markerFraction = history.limitFraction
        )
    }
}

private val ROW_LINE_SPACING = 4.dp

/** 날짜 범위와 하루 평균을 한 줄에 둘 때 쓰는 구분자. */
private const val SECONDARY_SEPARATOR = " · "
