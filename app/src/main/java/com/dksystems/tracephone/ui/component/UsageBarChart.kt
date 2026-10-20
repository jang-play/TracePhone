package com.dksystems.tracephone.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.core.ChartAxis
import com.dksystems.tracephone.ui.theme.UsageTheme

private val DEFAULT_CHART_HEIGHT = 180.dp
private val AXIS_LABEL_GAP = 6.dp

/** 고른 막대 자리를 칠할 때의 진하기. 막대와 격자선을 가리지 않을 만큼만 옅게 둔다. */
private const val SELECTION_ALPHA = 0.12f

/**
 * 모바일 위에 Wi-Fi를 쌓은 막대 그래프.
 *
 * 일자별 추이와 시간대별 추이가 같은 모양이므로 하나로 둔다.
 * 가로축 라벨만 다르게 넣으면 된다.
 *
 * 세로축에는 [ChartAxis]가 고른 눈금 용량을 적고 옅은 격자선을 긋는다.
 * 막대 높이를 격자선에 대어 보면 대략적인 용량을 읽을 수 있다.
 *
 * 밖과 주고받는 타입은 `UsageBarChartModel.kt`에, 실제로 칠하는 일은
 * `UsageBarChartDrawing.kt`에 있다. 여기에는 **재고 맞추고 순서를 정하는 일**만 남긴다.
 *
 * @param labels 눈에 보이는 축 라벨과 스크린 리더가 읽을 막대 설명
 * @param height 막대가 그려지는 높이. 가로축 라벨은 이 아래에 따로 자리를 잡는다.
 * @param interaction 막대를 눌렀을 때 할 일. null이면 누를 수 없다.
 */
@Composable
fun UsageBarChart(
    bars: List<UsageBarValue>,
    labels: BarChartLabels,
    modifier: Modifier = Modifier,
    height: Dp = DEFAULT_CHART_HEIGHT,
    interaction: BarInteraction? = null
) {
    if (bars.isEmpty()) {
        return
    }

    val axis = remember(bars) { ChartAxis.forMaxValue(bars.maxOf { it.totalBytes }) }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant

    val valueLabels: List<TextLayoutResult> = remember(axis, labelStyle, textMeasurer) {
        axis.tickValues.map { tickValue ->
            textMeasurer.measure(ByteFormatter.formatCompact(tickValue), labelStyle)
        }
    }
    val positionLabels = remember(labels.xAxis, bars.size, labelStyle, textMeasurer) {
        labels.xAxis
            .filter { label -> label.index in bars.indices }
            .map { label ->
                MeasuredAxisLabel(label.index, textMeasurer.measure(label.text, labelStyle))
            }
    }

    val density = LocalDensity.current
    val valueLabelWidthPx = valueLabels.maxOf { it.size.width }.toFloat()
    val axisGapPx = with(density) { AXIS_LABEL_GAP.toPx() }
    val bottomStripPx = positionLabels.maxOfOrNull { it.layout.size.height + axisGapPx } ?: 0f
    val canvasHeight = height + with(density) { bottomStripPx.toDp() }
    val insets = with(density) {
        PlotInsets(left = (valueLabelWidthPx + axisGapPx).toDp(), bottom = bottomStripPx.toDp())
    }

    // 탭 처리는 캔버스 크기를 알아야 하므로, 최신 동작과 막대 수를 그때그때 읽는다.
    val currentInteraction by rememberUpdatedState(interaction)
    val currentBarCount by rememberUpdatedState(bars.size)

    // 두 표시 모두 글자색을 쓴다.
    // 강조색을 쓰면 Wi-Fi 계열색과 닮아 막대 아래쪽에 Wi-Fi를 조금 쓴 것처럼 보인다.
    // 칠하는 자리는 막대보다 뒤에 깔리므로 옅게, 밑줄은 막대 위에 그으므로 그대로 쓴다.
    val selectionBandColor = MaterialTheme.colorScheme.onSurface.copy(alpha = SELECTION_ALPHA)
    val selectionUnderlineColor = MaterialTheme.colorScheme.onSurface
    val networkColors = UsageTheme.colors.network

    val highlightedIndex = (interaction as? BarInteraction.Select)
        ?.selectedIndex
        ?.takeIf { it in bars.indices }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(canvasHeight)
            .semantics { isTraversalGroup = true }
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(valueLabelWidthPx, axisGapPx, bottomStripPx) {
                    detectTapGestures { tapOffset ->
                        val onSelect = currentInteraction?.onSelect ?: return@detectTapGestures
                        val plot =
                            plotAreaOf(size.toSize(), valueLabelWidthPx, axisGapPx, bottomStripPx)
                        val slotWidth = plot.slotWidth(currentBarCount)
                        if (slotWidth <= 0f || tapOffset.x < plot.left) {
                            return@detectTapGestures
                        }

                        val index = ((tapOffset.x - plot.left) / slotWidth)
                            .toInt()
                            .coerceIn(0, currentBarCount - 1)
                        onSelect(index)
                    }
                }
        ) {
            val plot = plotAreaOf(size, valueLabelWidthPx, axisGapPx, bottomStripPx)
            if (plot.width <= 0f || plot.height <= 0f) {
                return@Canvas
            }

            // 격자선과 막대보다 먼저 칠해 뒤에 깔리게 한다.
            highlightedIndex?.let { index ->
                drawSelectionBand(index, bars.size, plot, selectionBandColor)
            }

            drawValueAxis(axis, valueLabels, plot, gridLineColor, labelColor)
            drawPositionGridLines(positionLabels, bars.size, plot, gridLineColor)
            drawBars(bars, axis.maxValue, plot, networkColors)

            // 밑줄은 막대 위에 그어야 가려지지 않는다.
            highlightedIndex?.let { index ->
                drawSelectionUnderline(index, bars.size, plot, selectionUnderlineColor)
            }

            drawPositionLabels(positionLabels, bars.size, plot, axisGapPx, labelColor)
        }

        BarSemanticsOverlay(bars, labels, interaction, insets)
    }
}

/**
 * 막대마다 스크린 리더가 읽을 자리를 만든다.
 *
 * 픽셀에 그린 막대는 접근성 트리에 들어가지 않으므로,
 * 같은 자리에 **아무것도 그리지 않는 칸**을 겹쳐 트리만 만든다.
 * 배경도 테두리도 없으니 화면에 보이는 모습은 달라지지 않는다.
 *
 * ### 왜 `clickable`을 쓰지 않는가
 * `clickable`은 최소 터치 크기(48dp)를 강제한다. 칸 하나가 12dp쯤인 그래프에서는
 * 이웃 칸의 터치 영역이 서로 겹쳐 **지금 잘 도는 탭 판정이 깨진다.**
 * `semantics`만 두면 히트테스트에 참여하지 않아 탭은 그대로 아래 캔버스로 가고,
 * 스크린 리더는 접근성 액션으로 정확히 원하는 칸을 고른다.
 *
 * 이 때문에 접근성 검사기가 '터치 대상이 작다'고 경고한다.
 * 화면 모습을 바꾸지 않으려고 **일부러 남긴 것**이니 `clickable`로 되돌리지 말 것.
 */
@Composable
private fun BoxScope.BarSemanticsOverlay(
    bars: List<UsageBarValue>,
    labels: BarChartLabels,
    interaction: BarInteraction?,
    insets: PlotInsets
) {
    if (bars.size > MAX_INDIVIDUALLY_LABELED_BARS) {
        return
    }

    val selection = interaction as? BarInteraction.Select

    Row(
        // 그리는 쪽이 왼쪽 기준 좌표를 쓰므로 겹칩도 같은 기준을 써야 어긋나지 않는다.
        modifier = Modifier
            .matchParentSize()
            .absolutePadding(left = insets.left, bottom = insets.bottom)
    ) {
        bars.forEachIndexed { index, bar ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics {
                        contentDescription = labels.describeBar(index, bar)
                        selection?.let { selected = index == it.selectedIndex }
                        interaction?.let { action ->
                            onClick(label = action.actionLabel) {
                                action.onSelect(index)
                                true
                            }
                        }
                    }
            )
        }
    }
}

/**
 * 그래프가 실제로 그려지는 영역. 양쪽 축 라벨이 차지하는 만큼을 뺀 나머지다.
 *
 * 탭 판정과 그리기가 이 값을 함께 써야 누른 자리와 그려진 자리가 어긋나지 않는다.
 * 그래서 이 화면이 정하고 `UsageBarChartDrawing.kt`가 받아 쓴다.
 *
 * @property valueLabelRight 세로축 라벨을 오른쪽 맞춤으로 놓을 기준선
 */
internal data class PlotArea(
    val valueLabelRight: Float,
    val left: Float,
    val width: Float,
    val height: Float
) {

    fun slotWidth(barCount: Int): Float = width / barCount

    fun slotLeft(index: Int, barCount: Int): Float = left + index * slotWidth(barCount)

    fun slotCenter(index: Int, barCount: Int): Float =
        slotLeft(index, barCount) + slotWidth(barCount) / 2f
}

/** 겹칩이 캔버스와 같은 자리를 덮도록 맞추는 여백. 축 라벨이 차지한 만큼이다. */
private data class PlotInsets(val left: Dp, val bottom: Dp)

/** 위치가 정해진 가로축 라벨. 글자 크기를 재 두어야 칸 가운데에 맞춰 놓을 수 있다. */
internal data class MeasuredAxisLabel(val index: Int, val layout: TextLayoutResult)

private fun plotAreaOf(
    canvasSize: Size,
    valueLabelWidthPx: Float,
    axisGapPx: Float,
    bottomStripPx: Float
): PlotArea {
    val left = valueLabelWidthPx + axisGapPx

    return PlotArea(
        valueLabelRight = valueLabelWidthPx,
        left = left,
        width = canvasSize.width - left,
        height = canvasSize.height - bottomStripPx
    )
}
