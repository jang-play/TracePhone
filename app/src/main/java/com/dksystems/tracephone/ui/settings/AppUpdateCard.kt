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
import com.dksystems.tracephone.domain.model.AppRelease
import com.dksystems.tracephone.domain.model.UpdateCheck
import com.dksystems.tracephone.domain.model.UpdateCheckFailure

/**
 * 설치된 버전과 새 버전 안내.
 *
 * 확인 주소가 빌드에 들어 있지 않으면 **카드 자체를 내보이지 않는다.** 누를 수 없는
 * 버튼이나 "지원하지 않습니다" 같은 문구를 두면, 사용자는 자기가 뭘 잘못했는지 찾게 된다.
 */
@Composable
internal fun AppUpdateCard(
    modifier: Modifier = Modifier,
    viewModel: AppUpdateViewModel = hiltViewModel()
) {
    if (!viewModel.isSupported) return

    val updateCheck by viewModel.updateCheck.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.checkIfNeeded() }

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

            UpdateActions(
                updateCheck = updateCheck,
                onRecheck = viewModel::recheck
            )
        }
    }
}

/**
 * 버튼 줄.
 *
 * 확인 중에는 다시 확인을 막는다. 연달아 누르면 같은 요청이 쌓이고, 사용자는
 * 어느 응답이 자기 것인지 알 수 없다.
 */
@Composable
private fun UpdateActions(updateCheck: UpdateCheck, onRecheck: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = onRecheck,
            enabled = updateCheck !is UpdateCheck.Checking
        ) {
            Text(stringResource(recheckLabelOf(updateCheck)))
        }

        if (updateCheck is UpdateCheck.Available) {
            TextButton(onClick = { uriHandler.openUri(updateCheck.release.downloadUrl) }) {
                Text(stringResource(R.string.settings_update_action_download))
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
}
