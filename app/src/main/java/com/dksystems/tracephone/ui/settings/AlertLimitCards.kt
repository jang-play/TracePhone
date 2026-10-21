package com.dksystems.tracephone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.domain.model.UsageAlertSettings

/** 앱별 하루 한도 경고를 켜고 끈다. 끄면 아래 한도 카드가 사라진다. */
@Composable
internal fun AlertToggleCard(isEnabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_alert_switch_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.settings_alert_switch_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isEnabled, onCheckedChange = onEnabledChange)
        }
    }
}

/**
 * 한도 선택 카드.
 *
 * 목록에서 고른 값이 기본이고, 직접 입력한 값이 0보다 크면 그쪽이 우선한다.
 * 지금 무엇이 적용되는지 헷갈리기 쉬우므로 맨 아래에 실제 적용 값을 적어준다.
 */
@Composable
internal fun DailyLimitCard(
    settings: UsageAlertSettings,
    onPresetSelected: (Long) -> Unit,
    onCustomLimitApplied: (Long) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.settings_limit_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = stringResource(R.string.settings_limit_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Column(modifier = Modifier.selectableGroup()) {
                UsageAlertSettings.PRESET_CHOICES_BYTES.forEach { limitBytes ->
                    PresetLimitRow(
                        limitBytes = limitBytes,
                        isSelected = limitBytes == settings.presetLimitBytes,
                        isEnabled = !settings.usesCustomLimit,
                        onSelect = { onPresetSelected(limitBytes) }
                    )
                }
            }

            CustomLimitInput(
                storedLimitMegabytes = settings.customLimitMegabytes,
                onApply = onCustomLimitApplied,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Text(
                text = stringResource(
                    R.string.settings_limit_effective,
                    ByteFormatter.formatCompact(settings.effectiveLimitBytes)
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * 미리 정해 둔 한도 한 줄.
 *
 * `selectable`을 줄 전체에 걸어 라디오 버튼뿐 아니라 글자를 눌러도 골라지게 하고,
 * `Role.RadioButton`을 함께 넘겨 스크린 리더가 '선택됨/선택 안 됨'으로 읽게 한다.
 * [ThemeModeCard]의 줄과 같은 모양이다.
 *
 * 직접 입력값을 쓰는 동안에는 **정말로 누를 수 없게** 한다. 예전에는 회색으로 흐려 놓고도
 * 누르면 값이 조용히 바뀌었다. 보이는 것과 실제가 어긋나면 눌러 놓고도 바뀐 줄 모른다.
 * 되돌리는 방법은 바로 아래 입력 칸이 이미 안내하고 있다 — 0을 넣으면 여기서 고른 값을 쓴다.
 */
@Composable
private fun PresetLimitRow(
    limitBytes: Long,
    isSelected: Boolean,
    isEnabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                enabled = isEnabled,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = isSelected, onClick = null, enabled = isEnabled)
        Text(
            text = ByteFormatter.formatCompact(limitBytes),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isEnabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * 직접 입력 칸(MB).
 *
 * 입력 도중이 아니라 적용을 눌렀을 때만 반영한다. 그 이유는 [AppliedTextInput]에 적어 두었다.
 */
@Composable
private fun CustomLimitInput(
    storedLimitMegabytes: Long,
    onApply: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    AppliedTextInput(
        label = stringResource(R.string.settings_custom_limit_label),
        hint = stringResource(R.string.settings_custom_limit_hint),
        storedText = storedLimitMegabytes.toString(),
        isTypingAllowed = { text ->
            // 숫자만 받는다. 부호나 소수점이 섞이면 해석이 갈린다.
            text.length <= MAX_CUSTOM_LIMIT_DIGITS && text.all { it.isDigit() }
        },
        onApply = { text -> text.toLongOrNull()?.let(onApply) },
        modifier = modifier
    )
}

/** 입력 자릿수 상한. 지나치게 큰 값이 들어와 계산이 넘치는 것을 막는다. */
private const val MAX_CUSTOM_LIMIT_DIGITS = 7
