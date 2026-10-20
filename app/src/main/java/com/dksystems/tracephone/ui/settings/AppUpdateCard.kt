package com.dksystems.tracephone.ui.settings

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dksystems.tracephone.R
import com.dksystems.tracephone.domain.model.ApkDownload
import com.dksystems.tracephone.domain.model.AppRelease
import com.dksystems.tracephone.domain.model.UpdateCheck
import com.dksystems.tracephone.domain.model.UpdateCheckFailure
import com.dksystems.tracephone.ui.update.AppUpdateViewModel
import com.dksystems.tracephone.ui.update.UpdateDownloadButton
import com.dksystems.tracephone.ui.update.updateDownloadText

/**
 * 설치된 버전과 새 버전 안내.
 *
 * 확인 주소가 빌드에 들어 있지 않으면 **카드 자체를 내보이지 않는다.** 누를 수 없는
 * 버튼이나 "지원하지 않습니다" 같은 문구를 두면, 사용자는 자기가 뭘 잘못했는지 찾게 된다.
 *
 * 요약 화면의 배너와 같은 상태를 본다([AppUpdateViewModel]이 앱 수명의 한 곳에 위임한다).
 * 배너에서 받기를 시작하고 설정으로 넘어와도 진행률이 이어진다.
 */
@Composable
internal fun AppUpdateCard(
    modifier: Modifier = Modifier,
    viewModel: AppUpdateViewModel = hiltViewModel()
) {
    if (!viewModel.isSupported) return

    val updateCheck by viewModel.updateCheck.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.checkOnce() }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_update_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    R.string.settings_update_installed,
                    viewModel.installedVersionName
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = updateCheckText(updateCheck),
                style = MaterialTheme.typography.bodyMedium
            )

            updateDownloadText(download)?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            UpdateActions(
                updateCheck = updateCheck,
                download = download,
                viewModel = viewModel
            )

            // 받아 놓고도 설치 화면이 뜨지 않는 경우가 있다. 실기기에서 겪은 일이라
            // 그때 할 일을 미리 적어 둔다. 받기를 시작한 뒤에만 보여 카드를 늘리지 않는다.
            if (download !is ApkDownload.Idle) {
                Text(
                    text = stringResource(R.string.update_install_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 버튼 줄.
 *
 * 새 버전이 있을 때만 받기 버튼이 나타난다. 그 옆의 브라우저 링크는 **대체 경로**다 —
 * 앱 안에서 받는 길이 막히는 기기가 있을 수 있어 손으로 받는 방법을 남겨 둔다.
 */
@Composable
private fun UpdateActions(
    updateCheck: UpdateCheck,
    download: ApkDownload,
    viewModel: AppUpdateViewModel
) {
    val uriHandler = LocalUriHandler.current
    val available = (updateCheck as? UpdateCheck.Available)?.release

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = viewModel::recheck,
            enabled = updateCheck !is UpdateCheck.Checking
        ) {
            Text(stringResource(recheckLabelOf(updateCheck)))
        }

        if (available != null) {
            UpdateDownloadButton(
                download = download,
                onDownload = { viewModel.downloadAndInstall(available) },
                onAllowInstall = viewModel::openInstallPermissionSettings
            )

            TextButton(onClick = { uriHandler.openUri(available.downloadUrl) }) {
                Text(stringResource(R.string.update_action_open_in_browser))
            }
        }
    }
}

private fun recheckLabelOf(updateCheck: UpdateCheck): Int =
    if (updateCheck is UpdateCheck.NotChecked) {
        R.string.settings_update_action_check
    } else {
        R.string.settings_update_action_recheck
    }

@Composable
private fun updateCheckText(updateCheck: UpdateCheck): String = when (updateCheck) {
    // 카드가 그려지지 않는 상태지만, when을 빠짐없이 두어 상태가 늘어날 때 컴파일이 알려 주게 한다.
    UpdateCheck.Unavailable -> ""
    UpdateCheck.NotChecked -> stringResource(R.string.settings_update_not_checked)
    UpdateCheck.Checking -> stringResource(R.string.settings_update_checking)
    is UpdateCheck.UpToDate ->
        stringResource(R.string.settings_update_up_to_date, updateCheck.installedVersionName)
    is UpdateCheck.Available -> availableText(updateCheck.release)
    is UpdateCheck.Failed -> stringResource(failureTextOf(updateCheck.reason))
}

@Composable
private fun availableText(release: AppRelease): String =
    stringResource(R.string.settings_update_available, release.versionName)

private fun failureTextOf(reason: UpdateCheckFailure): Int = when (reason) {
    UpdateCheckFailure.NETWORK -> R.string.settings_update_failed_network
    UpdateCheckFailure.MALFORMED -> R.string.settings_update_failed_malformed
    UpdateCheckFailure.UNTRUSTED_DOWNLOAD_URL -> R.string.settings_update_failed_untrusted
    UpdateCheckFailure.UNKNOWN -> R.string.settings_update_failed_unknown
}
