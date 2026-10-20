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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dksystems.tracephone.R

/**
 * 새 버전이 나왔을 때만 요약 화면 맨 위에 나타나는 줄.
 *
 * ### 색을 쓰지 않는다
 * 새 버전은 **문제가 아니라 사실**이다. 이 앱은 색을 한도 초과처럼 사용자가 조치해야 하는
 * 일에만 쓰므로, 여기서는 보통 카드와 같은 바탕을 쓴다.
 *
 * ### 닫으면 이번 실행 동안만 사라진다
 * 다음에 앱을 켜면 다시 알린다. 저장하지 않는 이유는 '어느 버전까지 닫았나'를 관리할
 * 저장소가 하나 더 생기고, 업데이트 안내는 한 번 닫았다고 영구히 사라져야 하는 종류가
 * 아니기 때문이다.
 *
 * ### 확인을 여기서 시작한다
 * 앱을 켜면 가장 먼저 보이는 화면이라 "시작할 때 확인"의 자리가 된다.
 * [AppUpdateViewModel.checkOnce]가 실행당 한 번만 실제로 묻는다.
 */
@Composable
internal fun AppUpdateBanner(
    modifier: Modifier = Modifier,
    viewModel: AppUpdateViewModel = hiltViewModel()
) {
    if (!viewModel.isSupported) return

    LaunchedEffect(Unit) { viewModel.checkOnce() }

    val release by viewModel.pendingRelease.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()

    val pending = release ?: return
    val statusText = updateDownloadText(download)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.summary_update_banner_available,
                    pending.versionName
                ),
                style = MaterialTheme.typography.bodyLarge
            )

            statusText?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UpdateDownloadButton(
                    download = download,
                    onDownload = { viewModel.downloadAndInstall(pending) },
                    onAllowInstall = viewModel::openInstallPermissionSettings
                )

                TextButton(onClick = { viewModel.dismiss(pending.versionCode) }) {
                    Text(stringResource(R.string.summary_update_banner_dismiss))
                }
            }
        }
    }
}
