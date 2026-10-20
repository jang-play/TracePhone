package com.dksystems.tracephone.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dksystems.tracephone.MainActivity
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.domain.model.MonthlyLimitStatus
import com.dksystems.tracephone.domain.model.WidgetUsage

/**
 * 홈 화면에서 이번 청구 주기의 모바일 사용량을 보여주는 위젯.
 *
 * ### 크기에 따라 담는 양을 달리한다
 * 좁은 위젯에 앱 목록까지 넣으면 글자가 잘려 아무것도 읽히지 않는다.
 * 좁을 때는 진행률만, 넓을 때만 많이 쓴 앱을 덧붙인다.
 *
 * ### 값은 앱과 같은 경로로 구한다
 * 위젯이 따로 계산하면 홈 화면과 앱 안의 숫자가 달라진다.
 * 어느 쪽이 맞는지 사용자가 판단할 방법이 없으므로 둘 다 믿지 못하게 된다.
 */
class TracePhoneWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT_SIZE, WIDE_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetDataLoader.load(context, TOP_APP_COUNT)
        val colors = WidgetColors(
            appearance = snapshot.appearance,
            isNightMode = WidgetColors.isNightMode(context)
        )

        provideContent {
            WidgetContent(usage = snapshot.usage, colors = colors)
        }
    }

    companion object {
        /** 함께 보여줄 앱 개수. 이보다 늘리면 좁은 위젯에서 글자가 잘린다. */
        const val TOP_APP_COUNT = 2

        val COMPACT_SIZE = DpSize(180.dp, 60.dp)
        val WIDE_SIZE = DpSize(250.dp, 130.dp)
    }
}

@Composable
private fun WidgetContent(usage: WidgetUsage, colors: WidgetColors) {
    val isWide = LocalSize.current.height >= TracePhoneWidget.WIDE_SIZE.height

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.background)
            .cornerRadius(WIDGET_CORNER_RADIUS)
            .padding(WIDGET_PADDING)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        HeaderRow(usage = usage, colors = colors)

        Spacer(modifier = GlanceModifier.height(ROW_SPACING))

        if (usage.limitStatus != null) {
            LimitSummary(status = usage.limitStatus, colors = colors)
        } else {
            NoLimitSummary(usage = usage, colors = colors)
        }

        if (isWide && usage.topApps.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(ROW_SPACING))
            usage.topApps.forEach { app ->
                TopAppRow(
                    label = app.app.label,
                    bytes = app.mobile.totalBytes,
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun HeaderRow(usage: WidgetUsage, colors: WidgetColors) {
    val context = LocalContext.current

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = context.getString(R.string.widget_title),
            style = TextStyle(color = colors.secondaryText, fontSize = LABEL_FONT_SIZE),
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            text = WidgetTextFormatter.cycleLabel(context, usage.cycle),
            style = TextStyle(color = colors.secondaryText, fontSize = LABEL_FONT_SIZE)
        )
    }
}

@Composable
private fun LimitSummary(status: MonthlyLimitStatus, colors: WidgetColors) {
    val context = LocalContext.current
    val accentColor = if (status.isExceeded) colors.progressExceeded else colors.progress

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = context.getString(
                R.string.monthly_limit_progress,
                ByteFormatter.formatCompact(status.usedBytes),
                ByteFormatter.formatCompact(status.limitBytes)
            ),
            style = TextStyle(
                color = colors.text,
                fontSize = VALUE_FONT_SIZE,
                fontWeight = FontWeight.Bold
            ),
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            text = "${status.usedPercent}%",
            style = TextStyle(color = accentColor, fontSize = VALUE_FONT_SIZE)
        )
    }

    Spacer(modifier = GlanceModifier.height(BAR_SPACING))

    LinearProgressIndicator(
        progress = status.progress,
        modifier = GlanceModifier.fillMaxWidth(),
        color = accentColor,
        backgroundColor = colors.progressTrack
    )

    Spacer(modifier = GlanceModifier.height(BAR_SPACING))

    Text(
        text = WidgetTextFormatter.remainingLabel(context, status),
        style = TextStyle(color = colors.secondaryText, fontSize = LABEL_FONT_SIZE)
    )
}

/** 한도를 정하지 않았어도 사용량은 보여준다. 설정 안내만 있는 위젯은 쓸모가 없다. */
@Composable
private fun NoLimitSummary(usage: WidgetUsage, colors: WidgetColors) {
    val context = LocalContext.current

    Text(
        text = ByteFormatter.formatCompact(usage.mobileBytes),
        style = TextStyle(
            color = colors.text,
            fontSize = VALUE_FONT_SIZE,
            fontWeight = FontWeight.Bold
        )
    )
    Text(
        text = context.getString(R.string.widget_no_limit_hint),
        style = TextStyle(color = colors.secondaryText, fontSize = LABEL_FONT_SIZE)
    )
}

@Composable
private fun TopAppRow(label: String, bytes: Long, colors: WidgetColors) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(color = colors.text, fontSize = LABEL_FONT_SIZE),
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            text = ByteFormatter.formatCompact(bytes),
            style = TextStyle(color = colors.secondaryText, fontSize = LABEL_FONT_SIZE)
        )
    }
}

private val WIDGET_CORNER_RADIUS = 16.dp
private val WIDGET_PADDING = 12.dp
private val ROW_SPACING = 6.dp
private val BAR_SPACING = 4.dp
private val LABEL_FONT_SIZE = 12.sp
private val VALUE_FONT_SIZE = 18.sp
