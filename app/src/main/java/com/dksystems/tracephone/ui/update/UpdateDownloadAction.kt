package com.dksystems.tracephone.ui.update

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dksystems.tracephone.R
import com.dksystems.tracephone.domain.model.ApkDownload
import com.dksystems.tracephone.domain.model.ApkDownloadFailure
import com.dksystems.tracephone.domain.model.downloadProgressOf

private const val PERCENT_SCALE = 100

/**
 * 받기·설치 버튼. 배너와 설정 카드가 같은 것을 쓴다.
 *
 * 허용이 꺼져 있을 때 버튼의 뜻이 바뀐다 — 그 상태에서 '내려받아 설치'를 누르면 받기만 하고
 * 설치 화면이 뜨지 않으므로, 먼저 해야 할 일을 버튼이 직접 말한다.
 */
@Composable
internal fun UpdateDownloadButton(
    download: ApkDownload,
    onDownload: () -> Unit,
    onAllowInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val needsPermission = download is ApkDownload.InstallPermissionRequired

    TextButton(
        onClick = if (needsPermission) onAllowInstall else onDownload,
        enabled = download !is ApkDownload.Downloading,
        modifier = modifier
    ) {
        Text(
            stringResource(
                if (needsPermission) {
                    R.string.update_action_allow_install
                } else {
                    R.string.update_action_download_and_install
                }
            )
        )
    }
}

/**
 * 받기 상태를 한 줄로. 아직 아무 일도 없으면 null이라 화면이 줄을 만들지 않는다.
 *
 * 진행률을 그릴 수 없는 경우(서버가 길이를 알려주지 않음)에는 비율 없이 "받는 중"만 적는다.
 * 0%를 적으면 멈춘 것으로 읽힌다.
 */
@Composable
internal fun updateDownloadText(download: ApkDownload): String? = when (download) {
    ApkDownload.Idle -> null

    is ApkDownload.Downloading -> downloadingText(download)

    is ApkDownload.Ready -> stringResource(R.string.update_ready)

    ApkDownload.InstallPermissionRequired ->
        stringResource(R.string.update_install_permission_required)

    is ApkDownload.Failed -> stringResource(downloadFailureTextOf(download.reason))
}

@Composable
private fun downloadingText(download: ApkDownload.Downloading): String {
    val progress = downloadProgressOf(download.downloadedBytes, download.totalBytes)
        ?: return stringResource(R.string.update_downloading)

    return stringResource(
        R.string.update_downloading_with_progress,
        (progress * PERCENT_SCALE).toInt()
    )
}

private fun downloadFailureTextOf(reason: ApkDownloadFailure): Int = when (reason) {
    ApkDownloadFailure.NETWORK -> R.string.update_failed_download
    ApkDownloadFailure.TOO_LARGE -> R.string.update_failed_too_large
    ApkDownloadFailure.STORAGE -> R.string.update_failed_storage
}
