package com.dksystems.tracephone.ui.permission

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.AppLog

/**
 * 사용 정보 접근 권한을 안내하는 화면.
 *
 * 앱이 권한을 직접 요청할 수 없으므로 설정 화면으로 보내는 것이 할 수 있는 전부다.
 * 사용자가 무엇을 해야 하는지 구체적으로 알려주는 것이 이 화면의 유일한 임무다.
 */
@Composable
fun PermissionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            // 이 화면은 Scaffold 안에 있지 않으므로 시스템 바 여백을 직접 확보한다.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = stringResource(R.string.permission_description),
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = stringResource(R.string.permission_steps),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // 일부 기기는 이 설정 화면을 제공하지 않는다. 크래시 대신 전체 설정으로 보낸다.
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    AppLog.warn(
                        "ACTION_USAGE_ACCESS_SETTINGS unavailable; 전체 설정 화면으로 대체합니다."
                    )
                    context.startActivity(
                        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        ) {
            Text(text = stringResource(R.string.permission_open_settings))
        }
    }
}
