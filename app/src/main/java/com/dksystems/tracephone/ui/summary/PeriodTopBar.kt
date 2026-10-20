package com.dksystems.tracephone.ui.summary

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.dksystems.tracephone.R

/**
 * 앱 바가 기간 이동을 맡는다.
 *
 * 앱 이름을 제목으로 걸어 두면 **화면이 하나뿐인 앱에서 아무것도 알려주지 않는 줄**이 된다.
 * 그 자리를 실제로 바뀌는 값(보고 있는 기간)에 내주고, 목록 위에 있던 화살표 줄 하나를 없앴다.
 *
 * 갈 수 없는 방향의 화살표는 숨기지 않고 **비활성화**한다.
 * 숨기면 제목의 위치가 흔들려 어느 쪽으로 갈 수 있는지 오히려 알기 어려워진다.
 *
 * ### '오늘'은 현재 칸이 아닐 때만 나온다
 * 과거로 여러 칸 들어간 사용자가 같은 횟수만큼 `>`를 눌러 돌아오지 않아도 되게 한다.
 * 현재 칸에서는 할 일이 없으므로 **자리를 비워** 제목이 넓게 쓰도록 둔다.
 * 나타나는 조건이 `>` 화살표가 켜지는 조건과 같아, 따로 판단할 값을 두지 않았다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodTopBar(
    label: String,
    navigation: PeriodNavigation,
    onSettingsSelected: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = navigation.onPrevious, enabled = navigation.canGoPrevious) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.period_previous)
                )
            }
        },
        actions = {
            if (navigation.canGoNext) {
                TextButton(onClick = navigation.onReturnToCurrent) {
                    Text(stringResource(R.string.period_today))
                }
            }

            IconButton(onClick = navigation.onNext, enabled = navigation.canGoNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.period_next)
                )
            }
            IconButton(onClick = onSettingsSelected) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.action_settings)
                )
            }
        }
    )
}

/** 기간을 앞뒤로 옮기는 데 필요한 것들. 앱 바가 받는 값이 넷으로 늘어나는 것을 막는다. */
internal data class PeriodNavigation(
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onReturnToCurrent: () -> Unit
)
