package com.dksystems.tracephone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
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
import com.dksystems.tracephone.data.update.AppUpdateChecker
import com.dksystems.tracephone.ui.TracePhoneApp
import com.dksystems.tracephone.ui.theme.ThemeModeViewModel
import com.dksystems.tracephone.ui.theme.TracePhoneTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 단일 액티비티. 화면 전환은 Compose Navigation이 담당한다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appUpdateChecker: AppUpdateChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        registerExitOnBack()

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

    /**
     * 맨 앞 화면에서 뒤로가기를 누르면 앱을 닫고 **작업 목록에서도 치운다.**
     *
     * ### 프로세스를 죽이지는 않는다
     * `killProcess`로 끝내면 [com.dksystems.tracephone.di.ApplicationScope]에서 돌고 있던
     * 일이 중간에 끊긴다. 백업 가져오기는 500행씩 나눠 쓰는 중이라 반쪽만 들어간 상태가
     * 되고, 새 버전 내려받기와 동기화도 같은 자리에서 끊긴다. 복구할 수 없는 기록을 다루는
     * 앱에서 그 위험을 지고 얻을 것이 없다.
     *
     * 대신 [AppUpdateChecker.forgetLastCheck]로 '방금 확인했다'는 기억을 지운다. 사용자가
     * 이렇게 닫았다가 다시 열면 그것은 새로 켠 것이므로, 확인이 간격에 막혀 건너뛰어지면
     * 안 된다. 보이는 결과는 프로세스를 죽인 것과 같고 위험만 없다.
     *
     * 내비게이션이 돌아갈 곳을 들고 있을 때는 그쪽 콜백이 먼저 잡으므로 여기까지 오지 않는다.
     */
    private fun registerExitOnBack() {
        onBackPressedDispatcher.addCallback(this) {
            appUpdateChecker.forgetLastCheck()
            finishAndRemoveTask()
        }
    }
}
