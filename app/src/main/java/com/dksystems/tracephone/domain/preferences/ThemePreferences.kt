package com.dksystems.tracephone.domain.preferences

import com.dksystems.tracephone.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** 사용자가 고른 화면 모드. */
interface ThemePreferences {

    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}
