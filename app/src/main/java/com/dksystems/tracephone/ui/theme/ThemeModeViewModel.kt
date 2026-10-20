package com.dksystems.tracephone.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dksystems.tracephone.domain.model.ThemeMode
import com.dksystems.tracephone.domain.preferences.ThemePreferences
import com.dksystems.tracephone.ui.SUBSCRIPTION_TIMEOUT_MILLIS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 화면 전체에 적용할 화면 모드를 읽는다. 액티비티가 하나뿐이라 액티비티 범위로 둔다.
 *
 * ### 첫 프레임은 기기 설정을 따른다
 * DataStore를 읽는 데 잠깐 걸리므로 그동안은 [ThemeMode.SYSTEM]으로 그린다.
 * API 31 이상에서는 저장할 때 시스템에도 같은 값을 알려 두었기 때문에
 * `isSystemInDarkTheme()`이 이미 고른 값을 돌려주고, 이 초기값이 어긋날 일이 없다.
 * 그 미만 기기에서만 첫 프레임이 기기 설정을 따랐다가 곧 바뀐다.
 */
@HiltViewModel
class ThemeModeViewModel @Inject constructor(
    themePreferences: ThemePreferences
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = ThemeMode.SYSTEM
        )
}
