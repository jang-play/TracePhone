package com.dksystems.tracephone.data.update

import com.dksystems.tracephone.core.AppLog
import com.dksystems.tracephone.di.ApplicationScope
import com.dksystems.tracephone.domain.model.ApkDownload
import com.dksystems.tracephone.domain.model.ApkDownloadFailure
import com.dksystems.tracephone.domain.model.ApkStorageException
import com.dksystems.tracephone.domain.model.ApkTooLargeException
import com.dksystems.tracephone.domain.model.AppRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 받은 APK를 둘 파일 이름.
 *
 * **버전을 이름에 넣지 않는다.** 넣으려면 기록의 `versionName`을 써야 하는데, 그 값은
 * Firebase 콘솔에서 **사람이 손으로 적은** 글자다. 슬래시나 `..`이 섞이면 그대로 파일 경로가
 * 되어 엉뚱한 자리를 가리킨다. `2026/10/21`처럼 적은 오타 하나가 "원인을 알 수 없는 네트워크
 * 오류"로 둔갑해 사용자가 영원히 다시 누르게 된다.
 *
 * 이름으로 구별할 이유도 없다. `ApkDownloader`가 받기 전에 캐시 폴더를 비우므로
 * 그 안에는 언제나 이 파일 하나뿐이다.
 */
private const val DOWNLOADED_APK_FILE_NAME = "update.apk"

/**
 * 새 버전을 받아 설치 화면까지 데려간다.
 *
 * ### [AppUpdateChecker]와 합치지 않는다
 * "새 버전이 있는가"와 "받아서 설치하는가"는 다른 책임이다. 한 클래스에 두면 상태가
 * 섞여, 확인만 하고 싶을 때도 받기 상태를 지나치게 된다.
 *
 * ### Singleton이고 앱 스코프에서 도는 이유
 * 받는 데 몇 초가 걸린다. 화면 스코프에서 돌리면 받는 중에 설정으로 넘어가거나 화면이
 * 회전할 때 끊긴다. 진행률도 화면보다 오래 살아야 이어진다.
 */
@Singleton
class AppUpdateDownloader @Inject constructor(
    private val apkDownloader: ApkDownloader,
    private val apkInstaller: ApkInstaller,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    private val mutableDownload = MutableStateFlow<ApkDownload>(ApkDownload.Idle)
    val download: StateFlow<ApkDownload> = mutableDownload.asStateFlow()

    /**
     * 받아서 설치 화면을 띄운다. 이미 받고 있으면 아무 일도 하지 않는다.
     *
     * 허용이 꺼져 있으면 **받기 전에** 멈춘다. 다 받아 놓고 설치 화면이 뜨지 않으면
     * 사용자는 통신량만 쓰고 아무것도 얻지 못한다.
     */
    fun downloadAndInstall(release: AppRelease) {
        if (mutableDownload.value is ApkDownload.Downloading) return

        if (!apkInstaller.canInstallPackages) {
            mutableDownload.value = ApkDownload.InstallPermissionRequired
            return
        }

        mutableDownload.value = ApkDownload.Downloading(downloadedBytes = 0L, totalBytes = null)
        applicationScope.launch { runDownload(release) }
    }

    /**
     * 허용 화면을 연다. 돌아온 뒤 사용자가 다시 누르면 [downloadAndInstall]이 통과한다.
     *
     * **열면서 상태를 거둔다.** 허용 여부는 앱 밖에서 바뀌는데 [ApkDownload.InstallPermissionRequired]는
     * 앱 수명 동안 남는다. 그대로 두면 사용자가 허용을 켜고 돌아와도 "허용이 필요합니다"가
     * 화면에 남아 이미 해결한 일을 안 했다고 적는다. 거두면 받기 버튼만 남고, 아직 허용되지
     * 않았다면 누르는 순간 [downloadAndInstall]이 다시 같은 상태로 되돌린다.
     */
    fun openInstallPermissionSettings() {
        mutableDownload.value = ApkDownload.Idle
        apkInstaller.openInstallPermissionSettings()
    }

    private suspend fun runDownload(release: AppRelease) {
        mutableDownload.value = try {
            val apk = apkDownloader.download(
                url = release.downloadUrl,
                fileName = DOWNLOADED_APK_FILE_NAME,
                onProgress = { downloaded, total ->
                    mutableDownload.value = ApkDownload.Downloading(downloaded, total)
                }
            )

            apkInstaller.launchInstaller(apk)
            ApkDownload.Ready(release.versionCode)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            ApkDownload.Failed(failureReasonOf(failure))
        }
    }
}

/**
 * 예외를 사용자가 할 수 있는 일로 번역한다.
 *
 * 마지막 갈래가 [ApkDownloadFailure.NETWORK]인 이유는, 예상하지 못한 실패에서 사용자가
 * 시도해 볼 수 있는 유일한 일이 '다시 누르기'이기 때문이다. 로그에는 실제 원인을 남긴다.
 */
private fun failureReasonOf(failure: Exception): ApkDownloadFailure = when (failure) {
    is ApkTooLargeException -> {
        AppLog.error("APK download exceeded the size limit", failure)
        ApkDownloadFailure.TOO_LARGE
    }

    is ApkStorageException -> {
        AppLog.error("APK download could not prepare storage", failure)
        ApkDownloadFailure.STORAGE
    }

    is IOException -> {
        AppLog.error("APK download failed", failure)
        ApkDownloadFailure.NETWORK
    }

    else -> {
        AppLog.error("APK download failed unexpectedly", failure)
        ApkDownloadFailure.NETWORK
    }
}
