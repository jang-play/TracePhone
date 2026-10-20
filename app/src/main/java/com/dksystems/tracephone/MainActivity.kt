package com.dksystems.tracephone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dksystems.tracephone.ui.TracePhoneApp
import com.dksystems.tracephone.ui.theme.ThemeModeViewModel
import com.dksystems.tracephone.ui.theme.TracePhoneTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 단일 액티비티. 화면 전환은 Compose Navigation이 담당한다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 35부터 시스템이 화면을 시스템 바 밑까지 확장한다.
        // 이 호출이 없으면 상태바 아이콘 색이 배경 밝기를 따라가지 않아
        // 밝은 화면에서 시계와 배터리 표시가 읽히지 않는다.
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        setContent {
            // 설정에서 고른 화면 모드가 기기 설정보다 앞선다.
            val themeModeViewModel: ThemeModeViewModel = hiltViewModel()
            val themeMode by themeModeViewModel.themeMode.collectAsStateWithLifecycle()

            TracePhoneTheme(useDarkTheme = themeMode.resolvesToDark(isSystemInDarkTheme())) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TracePhoneApp()
                }
            }
        }
    }
}
