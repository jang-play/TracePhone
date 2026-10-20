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

    /** 허용 화면을 연다. 돌아온 뒤 사용자가 다시 누르면 [downloadAndInstall]이 통과한다. */
    fun openInstallPermissionSettings() = apkInstaller.openInstallPermissionSettings()

    private suspend fun runDownload(release: AppRelease) {
        mutableDownload.value = try {
            val apk = apkDownloader.download(
                url = release.downloadUrl,
                fileName = "TracePhone-${release.versionName}.apk",
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
