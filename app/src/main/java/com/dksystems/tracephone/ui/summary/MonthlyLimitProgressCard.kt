package com.dksystems.tracephone.ui.summary

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.domain.model.MonthlyLimitStatus
import com.dksystems.tracephone.domain.model.UsagePace
import com.dksystems.tracephone.ui.component.barMarkerColor
import com.dksystems.tracephone.ui.component.markerRestsOnFill
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 진행 막대 높이. 오늘 눈금이 묻히지 않을 만큼 두껍게 둔다. */
private val PROGRESS_BAR_HEIGHT = 10.dp

/** 오늘 자리를 알리는 눈금의 폭. */
private val PACE_MARKER_WIDTH = 3.dp

/**
 * 카드 안쪽 여백과 줄 간격.
 *
 * 이 카드는 목록 맨 위에 있어 여기서 쓴 세로 공간이 그대로 앱 줄 수에서 빠진다.
 * 기본값(16dp·8dp)보다 조금씩 좁혀 목록에 한 줄을 더 내준다.
 */
private val CARD_PADDING = 14.dp
private val CARD_LINE_SPACING = 6.dp

/**
 * 이번 청구 주기의 한도 사용 현황. **네 줄**로 적는다.
 *
 * ```
 * 요금제 한도            9월 1일 ~ 9월 30일 · 40% 지남
 * 12.4 / 20.0 GB                        62% · 빠름
 * ================|-----
 * 남음 7.6 GB · 12일 · 하루 630 MB
 * ```
 *
 * 주기 날짜를 함께 적는다. 요약 화면의 '월'은 달력 기준이지만 청구 주기는 그렇지 않아,
 * 날짜를 밝히지 않으면 두 숫자가 왜 다른지 알 수 없다.
 *
 * '하루 얼마씩'을 함께 보여주는 이유는 남은 양만으로는 행동을 정하기 어렵기 때문이다.
 * 3.8 GB가 넉넉한지는 남은 날수를 알아야 판단할 수 있다.
 *
 * ### 왜 짧은 문장들을 한 줄로 붙였나
 * 남은 양·하루치·속도가 각각 한 줄을 쓰던 때 이 카드 혼자 화면의 4분의 1을 먹었다.
 * 셋 다 한 줄에 들어갈 만큼 짧은데도 줄을 나눠 쓰고 있었고, 그만큼 앱 목록이 밀려났다.
 *
 * ### 줄마다 한 가지만 말한다
 * 첫 줄은 **주기**(언제부터 언제까지, 얼마나 왔나), 둘째 줄은 **사용**(얼마나 썼나, 속도는 어떤가),
 * 넷째 줄은 **남은 것**이다. 경과율을 주기 줄로 올린 덕에 남은 것 줄이 가로폭을 통째로 쓴다.
 * 넷을 한 줄에 몰아넣었을 때는 `하루 178.26 MB`의 단위가 잘렸다.
 */
@Composable
fun MonthlyLimitProgressCard(
    status: MonthlyLimitStatus,
    onHistorySelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cycleLabel = rememberCycleLabel(status)
    val statusColor = limitStatusColor(status.isExceeded)

    // 누르면 무엇이 되는지 스크린 리더에 알린다. `Card(onClick=)`에는 라벨을 넘길 자리가
    // 없어 semantics로 따로 붙인다. 라벨만 주고 동작을 비우면 활성화가 되지 않으므로
    // 같은 동작을 함께 넘긴다. 손가락 탭은 Card가 처리하므로 두 번 실행되지 않는다.
    val historyLabel = stringResource(R.string.cycle_history_action)

    // `semantics(mergeDescendants = true)`는 쓰지 않는다. 그러면 막대의 진행률 설명이
    // 카드 전체 설명에 묻혀, 스크린 리더 사용자가 "주기의 40%가 지났습니다"를 잃는다.
    Card(
        onClick = onHistorySelected,
        modifier = modifier
            .fillMaxWidth()
            .semantics { onClick(label = historyLabel) { onHistorySelected(); true } }
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_LINE_SPACING)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.monthly_limit_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = stringResource(
                        R.string.monthly_limit_cycle_elapsed,
                        cycleLabel,
                        status.elapsedPercent
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = stringResource(
                        R.string.monthly_limit_progress,
                        ByteFormatter.formatCompact(status.usedBytes),
                        ByteFormatter.formatCompact(status.limitBytes)
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                UsedPercentText(status = status, statusColor = statusColor)
            }

            LimitProgressBar(status = status, statusColor = statusColor)

            RemainingText(status = status, statusColor = statusColor)

            ProjectionText(status = status)
        }
    }
}

/**
 * 한도 상태를 나타내는 색. 막대와 숫자가 같은 색을 쓴다.
 *
 * ### 정상 구간에 동적 색상을 쓰지 않는 이유
 * 예전에는 `colorScheme.primary`였다. 그런데 [com.dksystems.tracephone.ui.theme.TracePhoneTheme]이
 * API 31+에서 동적 색상을 타므로 **이 막대 색이 배경화면에서 나왔다.**
 * 배경화면이 따뜻한 색이면 막대가 주황이 되는데, 바로 아래 [UsageTotalsCard]에
 * '모바일'이라는 이름표가 붙은 주황 막대가 같은 화면에 놓인다. 뜻이 겹친다.
 *
 * 한도 진행은 회선이나 상태 같은 **분류가 아니라 진행**이다. 분류 색의 언어를 쓰면 안 된다.
 * 중립 계열은 M3에서 채도가 낮게 제약되어(neutral variant) 배경화면이 무엇이든
 * 회색 언저리에 머문다. 그래서 분류 색과 절대 겹치지 않는다.
 *
 * ### 초과 구간은 그대로 둔다
 * `colorScheme.error`는 동적 색상을 타지 않는다. `dynamicLightColorScheme`이
 * error 계열 네 값을 기본값으로 넘기기 때문이다. 즉 벽지와 무관하게 늘 빨강이다.
 */
@Composable
private fun limitStatusColor(isExceeded: Boolean): Color =
    if (isExceeded) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

/**
 * 진행 막대와 '오늘' 눈금.
 *
 * 눈금은 주기가 지난 만큼의 자리다.
 * 막대가 눈금을 넘어섰으면 예상보다 빨리 쓰고 있는 것이고 못 미치면 여유가 있는 것이다.
 * 날짜를 세어 나눠 보지 않아도 한눈에 읽힌다.
 *
 * `LinearProgressIndicator`로는 눈금을 그을 수 없어 직접 그린다.
 */
@Composable
private fun LimitProgressBar(status: MonthlyLimitStatus, statusColor: Color) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = barMarkerColor(
        markerRestsOnFill(fillFraction = status.progress, markerFraction = status.elapsedRatio)
    )

    val barLabel = stringResource(R.string.monthly_limit_bar_label)
    val barState = stringResource(
        R.string.monthly_limit_bar_state,
        status.usedPercent,
        status.elapsedPercent
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(PROGRESS_BAR_HEIGHT)
            .progressSemantics(status.progress)
            // 눈금이 가리키는 '주기가 지난 비율'은 색으로만 드러난다. 그것을 말로 옮긴다.
            .semantics {
                contentDescription = barLabel
                stateDescription = barState
            }
    ) {
        drawLimitBar(
            status = status,
            trackColor = trackColor,
            fillColor = statusColor,
            markerColor = markerColor
        )
    }
}

/**
 * 트랙 · 채움 · 눈금을 차례로 얹는다.
 *
 * 눈금은 마지막에 그려야 채움 위에 놓였을 때 덮이지 않는다.
 * 색을 고르는 일은 [LimitProgressBar]가 이미 끝냈고, 여기서는 그리기만 한다.
 */
private fun DrawScope.drawLimitBar(
    status: MonthlyLimitStatus,
    trackColor: Color,
    fillColor: Color,
    markerColor: Color
) {
    val cornerRadius = CornerRadius(size.height / 2f)

    drawRoundRect(color = trackColor, size = size, cornerRadius = cornerRadius)
    drawRoundRect(
        color = fillColor,
        size = Size(size.width * status.progress, size.height),
        cornerRadius = cornerRadius
    )

    val markerWidth = PACE_MARKER_WIDTH.toPx()
    val markerLeft = (size.width * status.elapsedRatio - markerWidth / 2f)
        .coerceIn(0f, size.width - markerWidth)

    drawRect(
        color = markerColor,
        topLeft = Offset(markerLeft, 0f),
        size = Size(markerWidth, size.height)
    )
}

/**
 * 사용 비율과 속도 판정.
 *
 * 둘을 붙여 두는 이유는 `62%`만으로는 그게 이른지 늦은지 알 수 없기 때문이다.
 * 바로 위 주기 줄의 경과율(`40% 지남`)과 견주라고 요구하는 대신 결론을 적어 준다.
 *
 * 예상보다 빠르면 이 줄 전체가 경고색이 된다. **막대는 그대로 둔다** —
 * 막대의 빨강은 '한도를 넘겼다'는 뜻으로 아껴 두어야 두 상태가 구분된다.
 *
 * 한도를 넘긴 뒤에는 속도를 적지 않는다. 이미 넘긴 상태에서 '빠름/여유'는 판단에 보탬이 되지 않고,
 * 아래 줄의 초과량이 할 말을 다 한다.
 */
@Composable
private fun UsedPercentText(status: MonthlyLimitStatus, statusColor: Color) {
    val pace = status.pace()

    val text = if (status.isExceeded) {
        stringResource(R.string.monthly_limit_used, status.usedPercent)
    } else {
        stringResource(
            R.string.monthly_limit_used_pace,
            status.usedPercent,
            stringResource(pace.wordResId())
        )
    }

    val color = if (!status.isExceeded && pace == UsagePace.FASTER) {
        MaterialTheme.colorScheme.error
    } else {
        statusColor
    }

    // 고정폭 글꼴을 쓰지 않는다. 이 값은 세로로 나열되지 않아 자릿수를 맞출 이유가 없고,
    // 고정폭에서는 구분점과 공백까지 숫자 한 자리 폭을 차지해 `19%  ·  빠름`처럼 벌어진다.
    Text(text = text, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1)
}

/**
 * 남은 양·날수·하루치. 가로폭을 통째로 쓴다.
 *
 * 이 줄만 짝이 없는 이유는 셋을 이어 붙인 문장이 카드 폭을 거의 다 쓰기 때문이다.
 * 오른쪽에 무엇이든 함께 두면 좁은 기기에서 단위가 잘려 `하루 178.26 …`이 된다.
 */
/**
 * 지금 속도로 주기 끝까지 갔을 때의 예상 사용량. **넘길 때만 적는다.**
 *
 * 넘지 않으면 적을 이유가 없다 — 바로 위 '적정/여유'가 이미 그 말을 하고 있고,
 * 숫자를 하나 더 얹으면 카드가 빽빽해진다. **줄의 등장 자체가 정보가 된다.**
 *
 * 주기 초에는 [MonthlyLimitStatus.projectedBytes]가 null이라 아무것도 그리지 않는다.
 * 분모가 작아 하루치 출렁임이 몇 배로 부풀려지기 때문이다.
 *
 * '넘긴다'고 단정하지 않는다. 예측이지 사실이 아니다.
 * 같은 이유로 색을 쓰지 않는다 — 아직 일어나지 않은 일이다.
 */
@Composable
private fun ProjectionText(status: MonthlyLimitStatus) {
    val projected = status.projectedBytes ?: return
    if (projected <= status.limitBytes) {
        return
    }

    Text(
        text = stringResource(
            R.string.monthly_limit_projection,
            ByteFormatter.formatCompact(projected)
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RemainingText(status: MonthlyLimitStatus, statusColor: Color) {
    val text = if (status.isExceeded) {
        stringResource(
            R.string.monthly_limit_exceeded,
            ByteFormatter.formatCompact(status.usedBytes - status.limitBytes)
        )
    } else {
        stringResource(
            R.string.monthly_limit_remaining_compact,
            ByteFormatter.formatCompact(status.remainingBytes),
            status.remainingDays,
            ByteFormatter.formatCompact(status.dailyAllowanceBytes)
        )
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (status.isExceeded) statusColor else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

@StringRes
private fun UsagePace.wordResId(): Int = when (this) {
    UsagePace.FASTER -> R.string.monthly_limit_pace_faster
    UsagePace.ON_TRACK -> R.string.monthly_limit_pace_on_track
    UsagePace.SLOWER -> R.string.monthly_limit_pace_slower
}

/** 주기의 시작과 끝 날짜. 기간 이동 라벨과 같은 형식을 써서 화면 전체가 같은 말투를 쓴다. */
@Composable
private fun rememberCycleLabel(status: MonthlyLimitStatus): String {
    val endpointPattern = stringResource(R.string.date_pattern_week_endpoint)
    val rangeFormat = stringResource(R.string.monthly_limit_cycle_range)

    return remember(status.cycle, endpointPattern, rangeFormat) {
        val formatter = DateTimeFormatter.ofPattern(endpointPattern, Locale.getDefault())
        rangeFormat.format(
            status.cycle.startDate.format(formatter),
            status.cycle.endDateInclusive.format(formatter)
        )
    }
}
