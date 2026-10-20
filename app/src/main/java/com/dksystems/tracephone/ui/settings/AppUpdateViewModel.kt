package com.dksystems.tracephone.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dksystems.tracephone.domain.model.UpdateCheck
import com.dksystems.tracephone.domain.update.InstalledAppVersion
import com.dksystems.tracephone.domain.usecase.CheckForUpdateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 앱 버전 카드의 상태.
 *
 * `SettingsViewModel`에 얹지 않고 따로 둔다. 그쪽은 이미 주입 대상이 열 개여서
 * 더 얹으면 읽기 어려워지고, 업데이트 확인은 다른 설정들과 공유하는 상태가 없다.
 *
 * ### 확인은 화면이 열려 있는 동안에만 일어난다
 * 배경 작업으로 주기 확인을 걸지 않았다. 이 앱은 사용 기록을 다루므로 네트워크가
 * 언제 쓰이는지 사용자가 볼 수 있는 자리에만 두는 쪽을 골랐다.
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val checkForUpdate: CheckForUpdateUseCase,
    installedAppVersion: InstalledAppVersion
) : ViewModel() {

    val isSupported: Boolean = checkForUpdate.isSupported

    val installedVersionName: String = installedAppVersion.versionName

    private val mutableUpdateCheck = MutableStateFlow<UpdateCheck>(UpdateCheck.NotChecked)
    val updateCheck: StateFlow<UpdateCheck> = mutableUpdateCheck.asStateFlow()

    /**
     * 화면에 들어왔을 때 한 번.
     *
     * 화면 회전처럼 구성이 다시 만들어질 때도 이 함수가 다시 불린다. 그때 또 요청하면
     * 사용자가 누르지 않은 통신이 생기므로, 아직 확인한 적이 없을 때만 요청한다.
     */
    fun checkIfNeeded() {
        if (mutableUpdateCheck.value is UpdateCheck.NotChecked) {
            check()
        }
    }

    /** 사용자가 다시 확인을 눌렀을 때. */
    fun recheck() = check()

    private fun check() {
        if (mutableUpdateCheck.value is UpdateCheck.Checking) return

        viewModelScope.launch {
            mutableUpdateCheck.value = UpdateCheck.Checking
            mutableUpdateCheck.value = checkForUpdate()
        }
    }
}
