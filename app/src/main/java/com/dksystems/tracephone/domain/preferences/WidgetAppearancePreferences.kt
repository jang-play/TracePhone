package com.dksystems.tracephone.domain.preferences

import com.dksystems.tracephone.domain.model.WidgetAppearance
import com.dksystems.tracephone.domain.model.WidgetTextColor
import kotlinx.coroutines.flow.Flow

/**
 * 위젯 겉모습 설정을 저장하고 관찰한다.
 */
interface WidgetAppearancePreferences {

    val appearance: Flow<WidgetAppearance>

    suspend fun setTextColor(textColor: WidgetTextColor)

    suspend fun setBackgroundTransparencyPercent(transparencyPercent: Int)
}
