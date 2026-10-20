package com.dksystems.tracephone.widget

import android.content.Context
import com.dksystems.tracephone.domain.model.WidgetAppearance
import com.dksystems.tracephone.domain.model.WidgetUsage
import com.dksystems.tracephone.domain.preferences.WidgetAppearancePreferences
import com.dksystems.tracephone.domain.usecase.GetWidgetUsageUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * 위젯이 한 번에 그릴 값과 겉모습.
 *
 * 위젯은 그려진 뒤에 스스로 값을 더 가져오지 못한다.
 * 둘을 따로 읽으면 색만 바뀌고 숫자는 옛것인 화면이 생길 수 있다.
 */
internal data class WidgetSnapshot(
    val usage: WidgetUsage,
    val appearance: WidgetAppearance
)

/**
 * 위젯이 의존성을 얻는 창구.
 *
 * `GlanceAppWidget`은 Hilt가 직접 주입해 줄 수 있는 대상이 아니다.
 * 위젯을 만드는 주체가 시스템이라 생성 시점에 개입할 수 없기 때문이다.
 * 그래서 애플리케이션 컴포넌트에서 필요한 것만 꺼내 쓴다.
 */
internal object WidgetDataLoader {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun getWidgetUsage(): GetWidgetUsageUseCase

        fun widgetAppearancePreferences(): WidgetAppearancePreferences
    }

    suspend fun load(context: Context, topAppCount: Int): WidgetSnapshot {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )

        return WidgetSnapshot(
            usage = entryPoint.getWidgetUsage().invoke(topAppCount),
            appearance = entryPoint.widgetAppearancePreferences().appearance.first()
        )
    }
}
