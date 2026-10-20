package com.dksystems.tracephone.data.system

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.dksystems.tracephone.core.AppLog
import com.dksystems.tracephone.domain.appearance.ThemeModeApplier
import com.dksystems.tracephone.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `UiModeManager`로 앱별 야간 모드를 지정한다.
 *
 * **API 31 미만에는 이 방법이 없다.** 그 기기에서는 아무 일도 하지 않고, 화면은 Compose가
 * 저장된 값으로 그리므로 시작 화면 한 번의 어긋남만 남는다.
 *
 * 이 호출은 액티비티를 다시 만들게 하지만 사용자에게는 테마가 바뀌는 것으로만 보인다.
 */
@Singleton
class SystemThemeModeApplier @Inject constructor(
    @ApplicationContext private val context: Context
) : ThemeModeApplier {

    override fun apply(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }

        val uiModeManager = context.getSystemService<UiModeManager>()
        if (uiModeManager == null) {
            AppLog.warn("UiModeManager를 얻지 못했습니다; 시작 화면은 기기 설정을 따릅니다.")
            return
        }

        uiModeManager.setApplicationNightMode(mode.toSystemNightMode())
    }

    private fun ThemeMode.toSystemNightMode(): Int = when (this) {
        // AUTO는 앱별 지정을 거두고 기기 설정으로 되돌린다. 실기기에서 확인했다.
        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
    }
}
