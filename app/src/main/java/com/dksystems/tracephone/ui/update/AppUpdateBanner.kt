package com.dksystems.tracephone.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dksystems.tracephone.R
import com.dksystems.tracephone.domain.model.ApkDownload
import com.dksystems.tracephone.domain.model.AppRelease
import com.dksystems.tracephone.domain.model.UpdateCheck

/**
 * 업데이트에 관해 **요약 화면에서 말할 것이 있을 때만** 나타나는 줄.
 *
 * ### 색을 쓰지 않는다
 * 새 버전은 **문제가 아니라 사실**이다. 이 앱은 색을 한도 초과처럼 사용자가 조치해야 하는
 * 일에만 쓰므로, 여기서는 보통 카드와 같은 바탕을 쓴다.
 *
 * ### 확인하지 못한 것도 알린다
 * 예전에는 새 버전이 있을 때만 나타났다. 그래서 **"새 버전 없음"과 "확인 실패"가 요약
 * 화면에서 똑같이 아무것도 안 보였다.** 실기기에서 네트워크를 끊고 확인해 보니 화면에
 * 흔적이 한 줄도 없었다. 잘 되고 있을 때와 실패했을 때가 구별되지 않으면 사용자는
 * 기능이 고장 났다고 여긴다. 그래서 실패만 따로 알린다 —
 * '최신입니다'는 알리지 않는다. 그것은 아무 일도 하지 않아도 되는 상태이고,
 * 앱을 열 때마다 줄을 하나 더 그리는 값을 치를 이유가 없다.
 *
 * ### 확인을 여기서 시작하지 않는다
 * 묻는 일은 [com.dksystems.tracephone.ui.TracePhoneApp]이 앱이 앞으로 올 때마다 한다.
 * 이 배너는 결과를 보여주는 일만 한다.
 */
@Composable
internal fun AppUpdateBanner(
    modifier: Modifier = Modifier,
    viewModel: AppUpdateViewModel = hiltViewModel()
) {
    if (!viewModel.isSupported) return

    val release by viewModel.pendingRelease.collectAsStateWithLifecycle()
    val updateCheck by viewModel.updateCheck.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()

    val pending = release
    when {
        pending != null -> AvailableBanner(pending, download, viewModel, modifier)

        updateCheck is UpdateCheck.Failed ->
            CheckFailedBanner(onRecheck = viewModel::recheck, modifier = modifier)
    }
}

/**
 * 새 버전이 나왔다.
 *
 * `닫기`는 다음 확인까지만 감춘다. 영구히 숨기면 "앱을 열 때 알려준다"가 거짓이 된다 —
 * 그 이유는 [com.dksystems.tracephone.data.update.AppUpdateChecker.dismissedVersionCode]에 있다.
 */
@Composable
private fun AvailableBanner(
    release: AppRelease,
    download: ApkDownload,
    viewModel: AppUpdateViewModel,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.summary_update_banner_available,
                    release.versionName
                ),
                style = MaterialTheme.typography.bodyLarge
            )

            updateDownloadText(download)?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UpdateDownloadButton(
                    download = download,
                    onDownload = { viewModel.downloadAndInstall(release) }
                )

                if (download is ApkDownload.InstallPermissionRequired) {
                    InstallPermissionButton(
                        onAllowInstall = viewModel::openInstallPermissionSettings
                    )
                }

                TextButton(onClick = { viewModel.dismiss(release.versionCode) }) {
                    Text(stringResource(R.string.summary_update_banner_dismiss))
                }
            }
        }
    }
}

/**
 * 확인 자체가 되지 않았다.
 *
 * **닫기를 두지 않는다.** 닫을 수 있으면 "확인하지 못한 상태"를 숨긴 채로 앱을 쓰게 되고,
 * 그것은 예전의 침묵과 같아진다. 다음 확인이 성공하면 스스로 사라지고, 실패한 뒤에는
 * 간격이 짧아져 금방 다시 시도한다.
 */
@Composable
private fun CheckFailedBanner(onRecheck: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.summary_update_banner_failed),
                style = MaterialTheme.typography.bodyLarge
            )

            TextButton(onClick = onRecheck) {
                Text(stringResource(R.string.settings_update_action_recheck))
            }
        }
    }
}
