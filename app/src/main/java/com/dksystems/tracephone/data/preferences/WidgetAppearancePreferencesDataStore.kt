package com.dksystems.tracephone.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dksystems.tracephone.domain.model.WidgetAppearance
import com.dksystems.tracephone.domain.model.WidgetTextColor
import com.dksystems.tracephone.domain.preferences.WidgetAppearancePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFERENCES_NAME = "widget_appearance_preferences"

private val Context.widgetAppearanceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCES_NAME
)

/**
 * 위젯 겉모습 설정을 기기에 저장한다.
 *
 * 열거형은 순서가 아니라 **이름**으로 저장한다.
 * 순서로 저장하면 나중에 항목을 끼워 넣을 때 저장된 값이 다른 색을 가리키게 된다.
 */
@Singleton
class WidgetAppearancePreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) : WidgetAppearancePreferences {

    private val storedPreferences: Flow<Preferences> = context.widgetAppearanceDataStore.data
        .recoveringFromReadFailure(PREFERENCES_NAME, "기본 모양을 사용합니다.")

    override val appearance: Flow<WidgetAppearance> = storedPreferences.map { preferences ->
        WidgetAppearance(
            textColor = toTextColorOrDefault(preferences[TEXT_COLOR_KEY]),
            backgroundTransparencyPercent = preferences[TRANSPARENCY_PERCENT_KEY]
                ?: WidgetAppearance.DEFAULT.backgroundTransparencyPercent
        )
    }

    override suspend fun setTextColor(textColor: WidgetTextColor) {
        context.widgetAppearanceDataStore.edit { it[TEXT_COLOR_KEY] = textColor.name }
    }

    override suspend fun setBackgroundTransparencyPercent(transparencyPercent: Int) {
        val safeValue = transparencyPercent.coerceIn(
            WidgetAppearance.MIN_TRANSPARENCY_PERCENT,
            WidgetAppearance.MAX_TRANSPARENCY_PERCENT
        )
        context.widgetAppearanceDataStore.edit { it[TRANSPARENCY_PERCENT_KEY] = safeValue }
    }

    /** 저장된 이름이 없거나 모르는 값이면 기본값으로 돌아간다. */
    private fun toTextColorOrDefault(storedName: String?): WidgetTextColor =
        WidgetTextColor.entries.firstOrNull { it.name == storedName }
            ?: WidgetAppearance.DEFAULT.textColor

    private companion object {
        val TEXT_COLOR_KEY = stringPreferencesKey("widget_text_color")
        val TRANSPARENCY_PERCENT_KEY = intPreferencesKey("widget_background_transparency")
    }
}
