package com.dksystems.tracephone.ui.settings

import androidx.annotation.StringRes
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dksystems.tracephone.R
import com.dksystems.tracephone.domain.model.ThemeMode

/**
 * 화면 모드 선택 카드.
 *
 * 기기 전체 설정과 따로 둘 수 있게 한다. 이 앱은 숫자와 색으로 읽는 화면이라
 * 보기 편한 밝기가 기기 전체의 선택과 다를 수 있다.
 */
@Composable
internal fun ThemeModeCard(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.settings_theme_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = stringResource(R.string.settings_theme_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    ThemeModeRow(
                        mode = mode,
                        isSelected = mode == selected,
                        onSelect = { onSelect(mode) }
                    )
                }
            }
        }
    }
}

/**
 * 항목 한 줄.
 *
 * `selectable`을 줄 전체에 걸어 라디오 버튼뿐 아니라 글자를 눌러도 골라지게 한다.
 * `Role.RadioButton`을 함께 넘겨 스크린 리더가 '선택 안 됨/선택됨'으로 읽게 한다.
 * 라디오 버튼 자체는 `onClick = null`로 두어 같은 동작이 두 번 등록되지 않게 한다.
 */
@Composable
private fun ThemeModeRow(mode: ThemeMode, isSelected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Text(
            text = stringResource(mode.labelResId()),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@StringRes
private fun ThemeMode.labelResId(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}
