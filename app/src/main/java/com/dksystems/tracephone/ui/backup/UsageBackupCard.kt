package com.dksystems.tracephone.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.dksystems.tracephone.domain.model.UsageBackupFailure
import com.dksystems.tracephone.domain.model.UsageBackupState

/**
 * 모든 파일 형식을 고를 수 있게 둔다.
 *
 * CSV의 MIME 종류는 제공자마다 다르다(`text/csv`, `text/comma-separated-values`,
 * Drive에서는 `application/vnd.ms-excel`로 오기도 한다). 종류로 거르면 정작 자기 백업
 * 파일이 목록에서 사라진다. **내용을 검사하는 쪽이 옳다** — 첫 줄이 열 이름과 다르면
 * 거부하므로, 엉뚱한 파일을 골라도 DB에 닿지 않는다.
 */
private val ANY_FILE_TYPE = arrayOf("*/*")

private const val EXPORT_MIME_TYPE = "text/csv"

/**
 * 누적 기록을 파일로 꺼내고 되돌린다.
 *
 * ### 이 카드가 있어야 하는 이유
 * 이 앱의 값은 **시스템이 지운 뒤에도 남는 기록**이다. 그런데 그 기록을 꺼내는 길이
 * PC 연결과 디버그 빌드뿐이어서, 폰을 바꾸거나 앱을 지우면 영구히 사라졌다.
 * 이 카드가 그 길을 앱 안으로 들여온다.
 *
 * ### 되돌리기가 기존 기록을 지우지 않는다
 * 파일에 있는 (날짜·앱·회선) 행만 덮어쓰고 나머지는 그대로 둔다. 옛 백업을 넣어도
 * 지금 기록이 날아가지 않는다. 그래서 '되돌리기'가 위험한 버튼이 아니다.
 */
@Composable
internal fun UsageBackupCard(
    modifier: Modifier = Modifier,
    viewModel: UsageBackupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE)
    ) { target ->
        // 사용자가 선택을 취소하면 null이 온다. 그때는 아무 일도 일어나지 않아야 한다.
        target?.let(viewModel::exportTo)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { source ->
        source?.let(viewModel::importFrom)
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_backup_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_backup_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            backupStatusText(state)?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
                    enabled = !state.isBusy
                ) {
                    Text(stringResource(R.string.settings_backup_action_export))
                }

                TextButton(
                    onClick = { importLauncher.launch(ANY_FILE_TYPE) },
                    enabled = !state.isBusy
                ) {
                    Text(stringResource(R.string.settings_backup_action_import))
                }
            }
        }
    }
}

/** 파일을 읽거나 쓰는 중에는 버튼을 잠근다. 두 번 누르면 같은 일이 겹친다. */
private val UsageBackupState.isBusy: Boolean
    get() = this is UsageBackupState.Exporting || this is UsageBackupState.Importing

/**
 * 지금 상태를 한 줄로. 아직 아무 일도 없으면 null이라 화면이 줄을 만들지 않는다.
 *
 * 성공을 **행 수와 함께** 적는다. "완료"만으로는 빈 파일을 내보냈는지 알 수 없고,
 * 이 기능은 복구할 수 없는 기록을 다루므로 숫자로 확인할 수 있어야 한다.
 */
@Composable
private fun backupStatusText(state: UsageBackupState): String? = when (state) {
    UsageBackupState.Idle -> null

    UsageBackupState.Exporting -> stringResource(R.string.settings_backup_exporting)

    UsageBackupState.Importing -> stringResource(R.string.settings_backup_importing)

    is UsageBackupState.Exported ->
        stringResource(R.string.settings_backup_exported, state.rowCount)

    is UsageBackupState.Imported ->
        stringResource(R.string.settings_backup_imported, state.rowCount)

    is UsageBackupState.Failed -> stringResource(backupFailureTextOf(state.reason))
}

private fun backupFailureTextOf(reason: UsageBackupFailure): Int = when (reason) {
    UsageBackupFailure.MALFORMED -> R.string.settings_backup_failed_malformed
    UsageBackupFailure.STORAGE -> R.string.settings_backup_failed_storage
    UsageBackupFailure.UNKNOWN -> R.string.settings_backup_failed_unknown
}
