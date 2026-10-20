package com.dksystems.tracephone.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.dksystems.tracephone.core.ChartAxis
import com.dksystems.tracephone.ui.theme.NetworkUsageColors

/**
 * 막대 그래프를 실제로 캔버스에 그리는 부분.
 *
 * 여기 있는 함수들은 **무엇을 그릴지 정하지 않는다.** 어디에 그릴지([PlotArea])와
 * 무슨 색으로 그릴지를 모두 [UsageBarChart]에서 받아 칠하기만 한다.
 * 그래서 순서를 바꾸거나 한 겹을 빼도 그래프의 판단 로직은 건드리지 않는다.
 *
 * 그리는 순서가 곧 위아래 겹침이다 — 고른 자리 칠하기 → 격자선 → 막대 → 밑줄 → 가로축 라벨.
 */

private const val BAR_WIDTH_RATIO = 0.7f

/** 고른 칸 밑줄의 두께. 막대가 축 꼭대기까지 닿아도 어느 칸인지 보이게 한다. */
private val SELECTION_UNDERLINE_HEIGHT = 3.dp

/**
 * 쌓은 두 칸 사이를 띄우는 높이.
 *
 * 사용량 색은 배경과의 대비를 우선해 고른 탓에 색끼리의 밝기가 서로 비슷하다.
 * 맞붙여 그리면 색상 차이만 남아 색각 이상 사용자에게는 경계가 사라진다.
 */
private val STACK_GAP = 1.dp

private const val GRID_LINE_WIDTH_PX = 1f
private val GRID_LINE_DASH = floatArrayOf(6f, 6f)

/**
 * 눈금 값을 왼쪽에 적고 그 높이에 격자선을 긋는다.
 *
 * 라벨은 격자선 높이의 가운데에 두되, 맨 위와 맨 아래에서는 화면 밖으로 넘치지 않게 잘라 넣는다.
 */
internal fun DrawScope.drawValueAxis(
    axis: ChartAxis,
    labels: List<TextLayoutResult>,
    plot: PlotArea,
    gridLineColor: Color,
    labelColor: Color
) {
    val dashEffect = PathEffect.dashPathEffect(GRID_LINE_DASH)

    axis.tickValues.forEachIndexed { index, tickValue ->
        val heightRatio = tickValue.toFloat() / axis.maxValue
        val lineY = plot.height - plot.height * heightRatio

        drawLine(
            color = gridLineColor,
            start = Offset(plot.left, lineY),
            end = Offset(size.width, lineY),
            strokeWidth = GRID_LINE_WIDTH_PX,
            pathEffect = dashEffect
        )

        val label = labels[index]
        val labelTop = (lineY - label.size.height / 2f)
            .coerceIn(0f, plot.height - label.size.height)

        drawText(
            textLayoutResult = label,
            color = labelColor,
            topLeft = Offset(plot.valueLabelRight - label.size.width, labelTop)
        )
    }
}

/**
 * 가로축에서 짚어 둔 자리에 세로 격자선을 긋는다.
 *
 * 가로축에 라벨만 있고 선이 없으면 그 라벨이 어느 막대를 가리키는지 여전히 세어야 한다.
 */
internal fun DrawScope.drawPositionGridLines(
    labels: List<MeasuredAxisLabel>,
    barCount: Int,
    plot: PlotArea,
    color: Color
) {
    val dashEffect = PathEffect.dashPathEffect(GRID_LINE_DASH)

    labels.forEach { label ->
        val lineX = plot.slotCenter(label.index, barCount)

        drawLine(
            color = color,
            start = Offset(lineX, 0f),
            end = Offset(lineX, plot.height),
            strokeWidth = GRID_LINE_WIDTH_PX,
            pathEffect = dashEffect
        )
    }
}

/** 가로축 라벨을 칸 가운데에 맞춰 적는다. 양 끝에서는 화면 밖으로 넘치지 않게 잘라 넣는다. */
internal fun DrawScope.drawPositionLabels(
    labels: List<MeasuredAxisLabel>,
    barCount: Int,
    plot: PlotArea,
    gapPx: Float,
    color: Color
) {
    labels.forEach { label ->
        val labelLeft = (plot.slotCenter(label.index, barCount) - label.layout.size.width / 2f)
            .coerceIn(0f, size.width - label.layout.size.width)

        drawText(
            textLayoutResult = label.layout,
            color = color,
            topLeft = Offset(labelLeft, plot.height + gapPx)
        )
    }
}

/**
 * 고른 막대의 자리를 위아래로 길게 칠한다.
 *
 * 막대 자체에 테두리를 두르면 사용량이 적은 시간대는 테두리가 실선 하나로 뭉쳐 보이지 않는다.
 * 칸 전체를 칠하면 값이 0이어도 어디를 골랐는지 드러난다.
 */
internal fun DrawScope.drawSelectionBand(
    selectedIndex: Int,
    barCount: Int,
    plot: PlotArea,
    color: Color
) {
    drawRect(
        color = color,
        topLeft = Offset(plot.slotLeft(selectedIndex, barCount), 0f),
        size = Size(plot.slotWidth(barCount), plot.height)
    )
}

/**
 * 고른 칸 아래에 밑줄을 긋는다.
 *
 * 칠한 자리는 막대가 높을수록 막대에 가려 위쪽 자투리만 남고,
 * 막대가 축 꼭대기까지 닿으면 아예 보이지 않는다.
 * 밑줄은 막대 위에 그으므로 높이와 상관없이 늘 보인다.
 */
internal fun DrawScope.drawSelectionUnderline(
    selectedIndex: Int,
    barCount: Int,
    plot: PlotArea,
    color: Color
) {
    val underlineHeight = SELECTION_UNDERLINE_HEIGHT.toPx()

    drawRect(
        color = color,
        topLeft = Offset(
            plot.slotLeft(selectedIndex, barCount),
            plot.height - underlineHeight
        ),
        size = Size(plot.slotWidth(barCount), underlineHeight)
    )
}

/**
 * 막대 하나씩 그린다. 아래에서 위로 모바일 다음 Wi-Fi 순으로 쌓는다.
 *
 * 두 회선을 모두 쓴 막대만 칸 사이를 띄운다.
 * 한쪽만 쓴 막대까지 띄우면 값이 작을 때 막대가 통째로 사라진다.
 */
internal fun DrawScope.drawBars(
    bars: List<UsageBarValue>,
    axisMaxValue: Long,
    plot: PlotArea,
    colors: NetworkUsageColors
) {
    val slotWidth = plot.slotWidth(bars.size)
    val barWidth = slotWidth * BAR_WIDTH_RATIO
    val barLeftOffset = (slotWidth - barWidth) / 2f
    val stackGap = STACK_GAP.toPx()

    bars.forEachIndexed { index, bar ->
        val barLeft = plot.slotLeft(index, bars.size) + barLeftOffset

        val mobileHeight = plot.height * bar.mobileBytes / axisMaxValue
        val wifiHeight = plot.height * bar.wifiBytes / axisMaxValue
        val gap = if (bar.mobileBytes > 0L && bar.wifiBytes > 0L) stackGap else 0f

        drawRect(
            color = colors.mobile,
            topLeft = Offset(barLeft, plot.height - mobileHeight),
            size = Size(barWidth, mobileHeight)
        )
        drawRect(
            color = colors.wifi,
            topLeft = Offset(barLeft, plot.height - mobileHeight - gap - wifiHeight),
            size = Size(barWidth, wifiHeight)
        )
    }
}
