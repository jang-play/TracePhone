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
 * 뜻이 상태에 따라 바뀌지 않는다. 누르면 늘 '받아서 설치'를 시도하고, 허용이 없으면
 * 그 사실이 상태로 돌아온다. 판단은 누르는 시점에 다시 한다.
 */
@Composable
internal fun UpdateDownloadButton(
    download: ApkDownload,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onDownload,
        enabled = download !is ApkDownload.Downloading,
        modifier = modifier
    ) {
        Text(stringResource(R.string.update_action_download_and_install))
    }
}

/**
 * 허용이 꺼져 있을 때만 함께 나타나는 버튼.
 *
 * ### 받기 버튼과 하나로 묶지 않는다 — 실기기에서 겪은 막다른 길
 * 처음에는 버튼 하나가 상태에 따라 '받아서 설치'와 '허용 화면 열기' 사이를 오갔다.
 * 그랬더니 허용을 켜고 돌아온 뒤에도 그 버튼이 **설정 화면만 다시 열어**, 받기로 넘어갈
 * 길이 없었다. 앱을 재시작하기 전까지 갇힌다.
 *
 * 원인은 **상태는 앱 수명 동안 남는데 허용 여부는 앱 밖에서 바뀐다**는 것이다.
 * 밖에서 바뀌는 사실을 안에 쌓아 둔 상태로 판단하면 어긋난다. 그래서 둘을 따로 두고,
 * 받기 버튼은 누를 때마다 허용 여부를 다시 묻게 했다.
 */
@Composable
internal fun InstallPermissionButton(
    onAllowInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(onClick = onAllowInstall, modifier = modifier) {
        Text(stringResource(R.string.update_action_allow_install))
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
