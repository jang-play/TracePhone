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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dksystems.tracephone.domain.model.AppListLayout
import com.dksystems.tracephone.R

/**
 * 앱 목록 모양 선택 카드.
 *
 * 두 모양이 보여주는 값은 같다. 막대를 또렷이 볼지, 한 화면에 더 많이 볼지가 맞바꿈 관계라
 * 어느 쪽이 나은지는 쓰는 사람의 목록 길이와 눈에 달렸다.
 *
 * 줄마다 한 문장씩 설명을 붙인다. '자세히'와 '간단히'라는 이름만으로는 무엇이 달라지는지
 * 알 수 없어, 고르기 전에 화면을 오가며 확인하게 된다.
 */
@Composable
internal fun AppListLayoutCard(
    selected: AppListLayout,
    onSelect: (AppListLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.settings_app_list_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = stringResource(R.string.settings_app_list_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Column(modifier = Modifier.selectableGroup()) {
                AppListLayout.entries.forEach { layout ->
                    AppListLayoutRow(
                        layout = layout,
                        isSelected = layout == selected,
                        onSelect = { onSelect(layout) }
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
 * [ThemeModeCard]의 줄과 같은 모양이다.
 *
 * 설명까지 한 덩어리로 묶는다. 설명만 따로 두면 스크린 리더가 이름과 설명을 다른 항목으로 읽어,
 * 무엇에 딸린 설명인지 알 수 없다.
 */
@Composable
private fun AppListLayoutRow(
    layout: AppListLayout,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = isSelected, onClick = null)

        Column {
            Text(
                text = stringResource(layout.labelResId()),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(layout.hintResId()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@StringRes
private fun AppListLayout.labelResId(): Int = when (this) {
    AppListLayout.DETAILED -> R.string.settings_app_list_detailed
    AppListLayout.COMPACT -> R.string.settings_app_list_compact
}

@StringRes
private fun AppListLayout.hintResId(): Int = when (this) {
    AppListLayout.DETAILED -> R.string.settings_app_list_detailed_hint
    AppListLayout.COMPACT -> R.string.settings_app_list_compact_hint
}
