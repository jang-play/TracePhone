package com.dksystems.tracephone.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 시스템이 위젯을 만들고 갱신할 때 거치는 지점.
 *
 * 매니페스트에 등록되는 것은 이 리시버이고, 실제 화면은 [TracePhoneWidget]이 그린다.
 */
class TracePhoneWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TracePhoneWidget()
}
