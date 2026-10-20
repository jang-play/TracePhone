package com.dksystems.tracephone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.domain.model.MonthlyLimitSettings
import java.util.Locale
import kotlin.math.roundToLong

/**
 * 한도 카드에서 일어나는 일들. 카드가 받는 값이 여섯으로 늘어나는 것을 막는다.
 *
 * `PeriodNavigation`·`AppUsageRowContext`와 같은 방식이다.
 */
internal data class MonthlyLimitCardActions(
    val onEnabledChange: (Boolean) -> Unit,
    val onLimitApplied: (Long) -> Unit,
    val onCycleStartDayApplied: (Int) -> Unit,
    val onHistorySelected: () -> Unit
)

/**
 * 요금제 한도 설정 카드.
 *
 * 한도는 **GB로 입력받는다.** 요금제가 GB 단위로 표기되므로 사용자가 그대로 옮겨 적을 수 있다.
 * MB로 받으면 10 GB 요금제에 10000을 넣어야 해서 자릿수를 세다 틀리기 쉽다.
 * 저장은 MB로 하고, 화면에 보일 때만 GB로 되돌린다.
 *
 * ### '주기 이력 보기'가 여기 있는 이유
 * 한도를 얼마로 적을지 정하는 자리가 바로 여기다. 지난 주기들의 실적이 그 판단의 근거이므로
 * 입력칸 바로 아래에 둔다. 한도를 켠 뒤에만 나타난다 — 꺼져 있으면 셀 주기가 없다.
 */
@Composable
internal fun MonthlyLimitCard(
    settings: MonthlyLimitSettings,
    actions: MonthlyLimitCardActions,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.monthly_limit_enable),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.monthly_limit_enable_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(checked = settings.isEnabled, onCheckedChange = actions.onEnabledChange)
            }

            if (!settings.isEnabled) {
                return@Column
            }

            HorizontalDivider()

            AppliedTextInput(
                label = stringResource(R.string.monthly_limit_amount_label),
                hint = stringResource(R.string.monthly_limit_amount_hint),
                storedText = toGigabyteText(settings.limitMegabytes),
                isTypingAllowed = ::isGigabyteInputAllowed,
                onApply = { text -> toMegabytesOrNull(text)?.let(actions.onLimitApplied) }
            )

            AppliedTextInput(
                label = stringResource(R.string.monthly_limit_cycle_label),
                hint = stringResource(R.string.monthly_limit_cycle_hint),
                storedText = settings.cycleStartDay.toString(),
                isTypingAllowed = ::isCycleStartDayInputAllowed,
                onApply = { text -> text.toIntOrNull()?.let(actions.onCycleStartDayApplied) }
            )

            Text(
                text = if (settings.isConfigured) {
                    stringResource(
                        R.string.monthly_limit_effective,
                        ByteFormatter.formatCompact(settings.limitBytes),
                        settings.cycleStartDay
                    )
                } else {
                    stringResource(R.string.monthly_limit_not_configured)
                },
                style = MaterialTheme.typography.bodyMedium
            )

            TextButton(
                onClick = actions.onHistorySelected,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(stringResource(R.string.cycle_history_action))
            }
        }
    }
}

/** 숫자와 소수점 하나만 받는다. `5.5 GB` 같은 요금제가 있어 소수점을 허용한다. */
private fun isGigabyteInputAllowed(text: String): Boolean =
    text.length <= MAX_GIGABYTE_INPUT_LENGTH &&
        text.count { it == DECIMAL_SEPARATOR } <= 1 &&
        text.all { it.isDigit() || it == DECIMAL_SEPARATOR }

private fun isCycleStartDayInputAllowed(text: String): Boolean =
    text.length <= MAX_CYCLE_START_DAY_DIGITS && text.all { it.isDigit() }

/** GB 입력을 MB로 바꾼다. 해석할 수 없으면 null. */
private fun toMegabytesOrNull(text: String): Long? =
    text.toDoubleOrNull()
        ?.let { gigabytes -> (gigabytes * MonthlyLimitSettings.MEGABYTES_PER_GIGABYTE).roundToLong() }
        ?.coerceIn(MonthlyLimitSettings.NO_LIMIT, MonthlyLimitSettings.MAX_LIMIT_MEGABYTES)

/**
 * 저장된 MB를 입력칸에 보일 GB 문자열로 바꾼다.
 *
 * 소수점 이하가 0이면 떼어낸다. `10.0 GB`보다 `10 GB`가 요금제 표기와 같다.
 */
private fun toGigabyteText(limitMegabytes: Long): String {
    if (limitMegabytes <= MonthlyLimitSettings.NO_LIMIT) {
        return ""
    }

    val gigabytes = limitMegabytes.toDouble() / MonthlyLimitSettings.MEGABYTES_PER_GIGABYTE
    return String.format(Locale.US, "%.1f", gigabytes).removeSuffix(TRAILING_ZERO_DECIMAL)
}

private const val MAX_GIGABYTE_INPUT_LENGTH = 7
private const val MAX_CYCLE_START_DAY_DIGITS = 2
private const val DECIMAL_SEPARATOR = '.'
private const val TRAILING_ZERO_DECIMAL = ".0"
