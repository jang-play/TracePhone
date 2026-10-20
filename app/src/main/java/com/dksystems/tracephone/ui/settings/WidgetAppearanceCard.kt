package com.dksystems.tracephone.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dksystems.tracephone.R
import com.dksystems.tracephone.domain.model.WidgetAppearance
import com.dksystems.tracephone.domain.model.WidgetTextColor
import kotlin.math.roundToInt

/**
 * 위젯 겉모습 설정 카드.
 *
 * 위젯은 배경화면 위에 놓이므로 기기 테마만으로는 대비를 보장할 수 없다.
 * 배경화면을 아는 것은 사용자뿐이라 글자색과 투명도를 직접 고르게 한다.
 */
@Composable
internal fun WidgetAppearanceCard(
    appearance: WidgetAppearance,
    onTextColorSelected: (WidgetTextColor) -> Unit,
    onTransparencySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.widget_appearance_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.widget_appearance_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextColorSelector(
                selected = appearance.textColor,
                onSelect = onTextColorSelected
            )

            TransparencySlider(
                transparencyPercent = appearance.backgroundTransparencyPercent,
                onSelect = onTransparencySelected
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextColorSelector(
    selected: WidgetTextColor,
    onSelect: (WidgetTextColor) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.widget_text_color_label),
            style = MaterialTheme.typography.bodyMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetTextColor.entries.forEach { textColor ->
                FilterChip(
                    selected = textColor == selected,
                    onClick = { onSelect(textColor) },
                    label = { Text(stringResource(textColor.labelResId())) }
                )
            }
        }
    }
}

/**
 * 배경 투명도 슬라이더.
 *
 * 끄는 동안에는 화면 안에서만 값을 들고 있다가 손을 뗄 때 저장한다.
 * 움직이는 내내 저장하면 한 번 끌 때마다 수십 번을 쓰고, 그때마다 위젯을 다시 그리게 된다.
 */
@Composable
private fun TransparencySlider(transparencyPercent: Int, onSelect: (Int) -> Unit) {
    var draggedPercent by remember(transparencyPercent) {
        mutableFloatStateOf(transparencyPercent.toFloat())
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.widget_transparency_label),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    R.string.widget_transparency_value,
                    draggedPercent.roundToInt()
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Slider(
            value = draggedPercent,
            onValueChange = { newValue -> draggedPercent = newValue },
            onValueChangeFinished = { onSelect(draggedPercent.roundToInt()) },
            valueRange = WidgetAppearance.MIN_TRANSPARENCY_PERCENT.toFloat()..
                WidgetAppearance.MAX_TRANSPARENCY_PERCENT.toFloat(),
            steps = TRANSPARENCY_STEP_COUNT
        )

        Text(
            text = stringResource(R.string.widget_transparency_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@StringRes
private fun WidgetTextColor.labelResId(): Int = when (this) {
    WidgetTextColor.AUTO -> R.string.widget_text_color_auto
    WidgetTextColor.WHITE -> R.string.widget_text_color_white
    WidgetTextColor.BLACK -> R.string.widget_text_color_black
}

/**
 * 슬라이더가 멈추는 지점의 수.
 *
 * `steps`는 양 끝을 뺀 중간 지점의 개수다. 0~100을 5씩 나누면 멈추는 곳이 21군데이고
 * 그중 양 끝 둘을 뺀 19가 여기에 들어갈 값이다.
 */
private const val TRANSPARENCY_STEP_COUNT =
    (WidgetAppearance.MAX_TRANSPARENCY_PERCENT - WidgetAppearance.MIN_TRANSPARENCY_PERCENT) /
        WidgetAppearance.TRANSPARENCY_STEP_PERCENT - 1
