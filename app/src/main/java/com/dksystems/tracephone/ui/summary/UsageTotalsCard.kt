package com.dksystems.tracephone.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.SyncAge
import com.dksystems.tracephone.ui.component.ByteText
import com.dksystems.tracephone.ui.component.LegendItem
import com.dksystems.tracephone.ui.component.UsageSplitBar
import com.dksystems.tracephone.ui.theme.UsageTheme
import com.dksystems.tracephone.ui.theme.UsageValueTextStyle
import java.time.format.DateTimeFormatter

/**
 * 이 기간의 사용량과 회선 구성. **세 줄**로 적는다.
 *
 * ```
 * 13.59 MB                    전체 2.57 GB
 * ████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
 * ● 모바일 13.59 MB        ● Wi-Fi 2.55 GB
 * ```
 *
 * ### 큰 숫자는 모바일이다
 * 요금이 붙는 쪽이 모바일이고 목록의 큰 숫자도 모바일이다. 여기만 합계를 크게 적으면
 * **화면에서 가장 큰 숫자가 Wi-Fi로 부풀려진 값**이 되어, 아래로 눈을 옮기는 순간
 * 기준이 바뀐다. 합계는 오른쪽에 작게 붙여 '참고로 이만큼'이라는 자리를 준다.
 *
 * ### 합계를 Wi-Fi와 같은 크기로 두는 이유
 * 합계는 두 회선을 더한 값이라 어느 쪽 편도 아니다. 회선 값들보다 크게 적으면
 * 그것이 이 카드의 결론처럼 보이는데, 이 앱의 결론은 모바일이다.
 *
 * ### 누르면 '모든 앱'이 열린다
 * 이 카드가 `모바일 822 MB`라고 말하므로 **"그 822 MB를 누가 썼나"는 바로 여기서 생기는
 * 질문이다.** 한도 카드가 같은 논리로 주기 이력을 여는 것과 짝을 이룬다.
 *
 * 아래 목록도 같은 질문에 답하지만 한 번에 다섯 줄 남짓만 보여준다 - 줄을 눌러 상세로
 * 들어가므로 **터치 대상 최소 48dp**가 바닥이기 때문이다. 누르지 않는 화면에서는 그 바닥이
 * 없어 예닷곱 배가 들어온다. 문을 여기에 둔 덕에 화면 높이는 1dp도 더 쓰지 않는다.
 */
@Composable
internal fun UsageTotalsCard(
    mobileBytes: Long,
    wifiBytes: Long,
    totalBytes: Long,
    syncAge: SyncAge?,
    onAllAppsSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 누르면 무엇이 되는지 스크린 리더에 알린다. `Card(onClick=)`에는 라벨을 넘길 자리가
    // 없어 semantics로 따로 붙인다. 라벨만 주고 동작을 비우면 활성화가 되지 않으므로
    // 같은 동작을 함께 넘긴다. 손가락 탭은 Card가 처리하므로 두 번 실행되지 않는다.
    val allAppsLabel = stringResource(R.string.all_apps_action)

    Card(
        onClick = onAllAppsSelected,
        modifier = modifier
            .fillMaxWidth()
            .semantics { onClick(label = allAppsLabel) { onAllAppsSelected(); true } }
    ) {
        Column(
            modifier = Modifier.padding(TOTALS_CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                ByteText(
                    bytes = mobileBytes,
                    style = MaterialTheme.typography.headlineSmall,
                    // 같은 값이 아래 범례 줄에 이름과 함께 적혀 있다.
                    // 노드를 만들면 스크린 리더가 같은 말을 두 번 듣게 된다.
                    modifier = Modifier.clearAndSetSemantics { }
                )

                TotalValue(bytes = totalBytes)
            }

            UsageSplitBar(mobileBytes = mobileBytes, wifiBytes = wifiBytes)

            // 이름과 값을 한 줄에 둔다. 이름 아래에 값을 두던 때보다 카드가 한 줄만큼 낮아진다.
            //
            // 막대와 같은 좌우 순서(왼쪽 모바일, 오른쪽 Wi-Fi)를 지켜
            // 색을 다시 확인하지 않아도 어느 쪽 값인지 읽히게 한다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NetworkTotal(
                    color = UsageTheme.colors.network.mobile,
                    label = stringResource(R.string.summary_mobile),
                    bytes = mobileBytes
                )
                NetworkTotal(
                    color = UsageTheme.colors.network.wifi,
                    label = stringResource(R.string.summary_wifi),
                    bytes = wifiBytes
                )
            }

            if (syncAge != null) {
                Text(
                    text = syncAgeText(syncAge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 마지막으로 확인한 시각을 글자로.
 *
 * **'동기화'나 '갱신'이라는 말을 쓰지 않는다.** 그것은 값이 바뀌었다는 뜻으로 읽히는데,
 * 확인은 바꿀 것이 없어도 일어난다. '확인함'은 실제로 일어난 사건만 말하므로
 * 어느 경우에도 거짓이 되지 않는다.
 *
 * 하루가 넘으면 날짜로 바꾼다. 색으로 경고하지 않고 **형식이 바뀌는 것**으로 알린다 —
 * 동기화 주기가 6시간이고 Doze로 몇 시간 밀리는 것이 정상이라, 임계값을 잡으면
 * 멀쩡한 상태를 빨갛게 칠하게 되고 사용자는 그 색을 무시하는 법을 배운다.
 */
@Composable
private fun syncAgeText(age: SyncAge): String = when (age) {
    SyncAge.NeverChecked -> stringResource(R.string.summary_never_checked)
    SyncAge.JustNow -> stringResource(R.string.summary_checked_just_now)
    is SyncAge.Minutes -> stringResource(R.string.summary_checked_minutes, age.count)
    is SyncAge.Hours -> stringResource(R.string.summary_checked_hours, age.count)
    is SyncAge.Older -> stringResource(
        R.string.summary_checked_on,
        age.checkedDate.format(
            DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern_week_endpoint))
        )
    )
}

private val TOTALS_CARD_PADDING = 14.dp

/**
 * 두 회선을 더한 값. 색 점을 붙이지 않는다.
 *
 * 점은 막대의 어느 칸을 가리키는지 알리는 표시인데, 합계에 해당하는 칸이 없다.
 * 점을 붙이면 막대에서 그 색을 찾게 된다.
 */
@Composable
private fun TotalValue(bytes: Long) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.summary_total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ByteText(bytes = bytes, style = UsageValueTextStyle)
    }
}

@Composable
private fun NetworkTotal(color: Color, label: String, bytes: Long) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(color = color, label = label)
        ByteText(bytes = bytes, style = UsageValueTextStyle)
    }
}
