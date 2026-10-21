package com.dksystems.tracephone.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dksystems.tracephone.data.update.AppUpdateChecker
import com.dksystems.tracephone.data.update.AppUpdateDownloader
import com.dksystems.tracephone.domain.model.ApkDownload
import com.dksystems.tracephone.domain.model.AppRelease
import com.dksystems.tracephone.domain.model.UpdateCheck
import com.dksystems.tracephone.domain.update.InstalledAppVersion
import com.dksystems.tracephone.ui.SUBSCRIPTION_TIMEOUT_MILLIS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 업데이트 안내의 화면 상태.
 *
 * ### 상태를 스스로 들고 있지 않는다
 * 요약 화면의 배너와 설정 화면의 카드가 **같은 사실을 말해야** 한다. 두 화면은 서로 다른
 * ViewModel 인스턴스를 받으므로, 상태를 여기에 두면 화면마다 따로 확인하고 서로 다른 답을
 * 보일 수 있다. 그래서 앱 수명의 [AppUpdateChecker]·[AppUpdateDownloader]에 위임하고
 * 이 클래스는 **화면이 쓰기 좋은 모양으로 바꾸는 일만** 한다.
 *
 * `ui.settings`가 아니라 중립 패키지에 둔 이유도 같다 — 쓰는 화면이 둘이다.
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val appUpdateChecker: AppUpdateChecker,
    private val appUpdateDownloader: AppUpdateDownloader,
    installedAppVersion: InstalledAppVersion
) : ViewModel() {

    val isSupported: Boolean = appUpdateChecker.isSupported

    val installedVersionName: String = installedAppVersion.versionName

    val updateCheck: StateFlow<UpdateCheck> = appUpdateChecker.updateCheck

    val download: StateFlow<ApkDownload> = appUpdateDownloader.download

    /**
     * 배너로 알릴 새 버전. 없거나 사용자가 닫았으면 null.
     *
     * 닫기를 '없음'과 같은 자리에서 다루는 이유는, 화면이 "띄울까 말까"를 한 값으로 묻게
     * 하려는 것이다. 둘을 따로 주면 화면마다 조합하는 규칙을 다시 쓰게 된다.
     */
    val pendingRelease: StateFlow<AppRelease?> = combine(
        appUpdateChecker.updateCheck,
        appUpdateChecker.dismissedVersionCode
    ) { check, dismissedVersionCode ->
        (check as? UpdateCheck.Available)
            ?.release
            ?.takeIf { it.versionCode != dismissedVersionCode }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = null
    )

    /** 앱이 앞으로 올 때 부른다. 확인한 지 오래됐을 때만 실제로 묻는다. */
    fun checkIfStale() = appUpdateChecker.checkIfStale()

    fun recheck() = appUpdateChecker.recheck()

    fun dismiss(versionCode: Int) = appUpdateChecker.dismiss(versionCode)

    fun downloadAndInstall(release: AppRelease) =
        appUpdateDownloader.downloadAndInstall(release)

    fun openInstallPermissionSettings() = appUpdateDownloader.openInstallPermissionSettings()
}
