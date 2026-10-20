package com.dksystems.tracephone.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dksystems.tracephone.domain.model.ThemeMode
import com.dksystems.tracephone.domain.preferences.ThemePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFERENCES_NAME = "theme_preferences"

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCES_NAME
)

/**
 * 화면 모드를 DataStore에 저장한다.
 *
 * **여기는 값을 남기기만 한다.** 고른 값을 시스템에 알려 시작 화면까지 맞추는 일은
 * [com.dksystems.tracephone.domain.appearance.ThemeModeApplier]가 맡고,
 * 둘을 잇는 순서는 설정 화면이 정한다. 이 앱의 다른 설정들도 같은 모양이다.
 *
 * 저장 형식과 알 수 없는 값의 처리는 [toEnumOrDefault]에 적어 두었다.
 */
@Singleton
class ThemePreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) : ThemePreferences {

    private val storedPreferences: Flow<Preferences> = context.themeDataStore.data
        .recoveringFromReadFailure(PREFERENCES_NAME, "기본 화면 모드로 시작합니다.")

    override val themeMode: Flow<ThemeMode> = storedPreferences.map { preferences ->
        preferences[THEME_MODE_KEY].toEnumOrDefault(DEFAULT_THEME_MODE)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    private companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

        val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
    }
}
