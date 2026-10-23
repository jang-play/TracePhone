package com.dksystems.tracephone.ui.allapps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dksystems.tracephone.R
import com.dksystems.tracephone.core.ByteFormatter
import com.dksystems.tracephone.domain.model.AppListScale
import com.dksystems.tracephone.domain.model.AppUsage
import com.dksystems.tracephone.ui.component.ByteText
import com.dksystems.tracephone.ui.component.UsageScaleBar
import com.dksystems.tracephone.ui.summary.AppUsageRowLabels
import com.dksystems.tracephone.ui.summary.AppUsageRowTexts
import com.dksystems.tracephone.ui.summary.describeAppUsageRow
import com.dksystems.tracephone.ui.summary.rememberPeriodLabel
import com.dksystems.tracephone.ui.summary.shareTextOf
import com.dksystems.tracephone.ui.theme.UsageTheme

/**
 * 한 기간의 **모든 앱**을 한 화면에 늘어놓는 화면.
 *
 * ```
 * 모든 앱
 * 2026년 10월
 * ─────────────────────────────────
 * 모바일 822 MB · 앱 47개
 * ─────────────────────────────────
 * 해리포터   ████████████████  507 MB
 * 다음       ███████████▏      254 MB
 * 카카오맵   █                  21 MB
 * ```
 *
 * ### 왜 별도 화면인가 - 벽을 만든 것은 '누를 수 있음'이었다
 *
 * 요약 화면의 목록은 앱을 한 번에 다섯 줄 남짓밖에 보여주지 못한다. 그 한계는 우연이 아니다.
 * 줄을 눌러 상세로 들어가므로 **터치 대상 최소 48dp**가 바닥이고
 * (`AppUsageRow`의 아이콘 32dp + 여백 8dp가 정확히 그 값이다), 머리말이 고정이라
 * 목록은 남은 높이만 받는다. 요약 화면 안에서는 늘릴 자리가 없다.
 *
 * **그 바닥을 만든 것이 '누를 수 있음'이므로, 누르지 않는 화면에서는 바닥이 없다.**
 * 여기서는 한 줄이 글자 높이까지 내려가 **예닷곱 배가 들어온다.**
 *
 * ### 한 장의 그림을 그리지 않은 이유
 *
 * 트리맵·파이·100% 누적 띠를 모두 검토하고 버렸다. 이 기기의 실제 분포
 * (가장 큰 앱 61%, 둘째 30%, **나머지 수십 개가 각 0.1% 이하**)를 대입하면
 * 한 장의 그림은 큰 둘만 그리고 나머지는 전부 실가닥이 된다.
 * 그런데 '모든 앱'의 대부분은 그 꼬리다. 꼬리에 **이름과 값**을 붙일 수 있는 형태는 줄 목록뿐이다.
 *
 * 색을 하나만 쓰는 것도 같은 결정의 일부다. 앱마다 색을 주려면 구분 색 팔레트가 필요한데,
 * 이 앱의 색은 전부 '회선 2종 + 상태 3종'에 의미가 묶여 있고 새 색은 대비 기준을 통과해야 한다.
 * 막대 길이가 이미 크기를 말하므로 색이 할 일이 없다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllAppsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AllAppsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 모든 줄이 함께 쓰는 자. 자가 같아야 앱끼리 비교된다.
    // 기준은 **이 기간의 모바일 합계**라서 요약 화면 목록의 막대와 뜻이 글자 그대로 같다.
    val scale = remember(uiState.mobileTotalBytes) {
        AppListScale(maxBytes = uiState.mobileTotalBytes)
    }

    Scaffold(
        modifier = modifier,
        topBar = { AllAppsTopBar(uiState = uiState, onNavigateBack = onNavigateBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // 분모를 화면에 적어 둔다. 막대가 '전체 중 얼마'를 말하는데
            // 그 '전체'가 보이지 않으면 막대 길이를 값으로 환산할 방법이 없다.
            Text(
                text = stringResource(
                    R.string.all_apps_totals,
                    ByteFormatter.format(uiState.mobileTotalBytes),
                    uiState.visibleAppUsages.size
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = SCREEN_PADDING, vertical = 8.dp)
            )

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                allAppsItems(uiState = uiState, scale = scale)
            }
        }
    }
}

/**
 * 제목 두 줄: 무슨 화면인지와 **어느 기간인지**.
 *
 * 기간을 적지 않으면 요약 화면에서 무엇을 고르고 들어왔는지 되짚을 수 없다.
 * 앱 상세 화면이 같은 자리에 '설치되지 않음'을 적는 것과 같은 구조다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllAppsTopBar(uiState: AllAppsUiState, onNavigateBack: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.all_apps_title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = rememberPeriodLabel(uiState.periodUnit, uiState.period),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back)
                )
            }
        }
    )
}

/**
 * 목록 본문. 비어 있거나 불러오는 중이면 그 사실을 정해진 높이의 한 칸으로 알린다.
 *
 * `LazyColumn` 안에서 높이가 정해지지 않은 칸은 배치를 무너뜨린다 - 요약 화면이 같은 이유로
 * 같은 방식을 쓴다.
 */
private fun LazyListScope.allAppsItems(uiState: AllAppsUiState, scale: AppListScale) {
    when {
        uiState.isLoading -> item(key = MESSAGE_KEY) {
            MessageBox { CircularProgressIndicator() }
        }

        uiState.visibleAppUsages.isEmpty() -> item(key = MESSAGE_KEY) {
            MessageBox {
                Text(
                    text = stringResource(R.string.summary_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        else -> items(items = uiState.visibleAppUsages, key = { it.app.uid }) { usage ->
            AllAppsRow(
                usage = usage,
                scale = scale,
                modifier = Modifier.padding(horizontal = SCREEN_PADDING)
            )
        }
    }
}

/**
 * 한 줄: 이름 · 막대 · 값.
 *
 * ### 높이를 고정하지 않는다
 * 줄을 20dp로 못 박으면 큰 글씨 설정에서 글자가 잘린다. 여백만 주고 글자 높이에 맡겨
 * **글씨가 커지면 줄도 함께 커지게** 한다. 그만큼 한 화면에 들어오는 줄이 줄어드는데,
 * 그것이 잘린 글자보다 낫다.
 *
 * ### 퍼센트를 적지 않는다
 * 막대가 곧 그 비율이다. 같은 값을 두 번 그리면 정보는 늘지 않고 폭만 먹는다.
 * 스크린 리더에는 막대가 보이지 않으므로 **낭독에는 퍼센트를 넣는다** - 거기서는
 * 그것이 유일한 전달 수단이다.
 *
 * ### 누를 수 없다
 * 상세로 가려면 뒤로 가서 요약 목록에서 누른다. 누를 수 있게 하면 48dp 바닥이
 * 돌아와 이 화면의 존재 이유가 사라진다.
 */
@Composable
private fun AllAppsRow(usage: AppUsage, scale: AppListScale, modifier: Modifier = Modifier) {
    val mobileBytes = usage.mobile.totalBytes
    val shareText = shareTextOf(usageBytes = mobileBytes, listTotalBytes = scale.maxBytes)
    val description = rememberAllAppsRowDescription(usage = usage, shareText = shareText)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = ROW_VERTICAL_PADDING)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = usage.app.label,
            modifier = Modifier.weight(NAME_WEIGHT),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        UsageScaleBar(
            fraction = scale.fractionOf(mobileBytes),
            color = UsageTheme.colors.network.mobile,
            modifier = Modifier.weight(BAR_WEIGHT)
        )

        // 값의 오른쪽 끝을 줄마다 맞춘다. 고정폭 숫자를 쓰는 이유가 거기에 있다.
        Box(modifier = Modifier.weight(VALUE_WEIGHT), contentAlignment = Alignment.CenterEnd) {
            ByteText(bytes = mobileBytes, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * 한 줄을 한 문장으로.
 *
 * 요약 화면 목록과 **같은 함수를 쓴다.** 두 화면이 같은 값을 보여주는데 낭독만 따로
 * 만들면 한쪽을 고칠 때 다른 쪽이 조용히 어긋난다. `<1%`를 '없음'과 구분하는 규칙도
 * 거기 한 벌만 있다.
 */
@Composable
private fun rememberAllAppsRowDescription(usage: AppUsage, shareText: String): String {
    val labels = AppUsageRowLabels(
        mobile = stringResource(R.string.summary_app_row_mobile),
        share = stringResource(R.string.summary_app_row_share),
        separator = stringResource(R.string.accessibility_separator),
        notInstalled = stringResource(R.string.summary_app_not_installed)
    )

    // 보조값(Wi-Fi·백그라운드)은 이 화면에 없다. 빈 글자를 넘기면 그 칸이 빠진다.
    val texts = AppUsageRowTexts(share = shareText, secondary = "")

    return remember(usage, texts, labels) {
        describeAppUsageRow(usage, texts, labels)
    }
}

@Composable
private fun MessageBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MESSAGE_BOX_HEIGHT),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}

private val SCREEN_PADDING = 16.dp

/** 줄 사이 숨 쉴 틈. 이것과 글자 높이가 줄 높이를 정한다. */
private val ROW_VERTICAL_PADDING = 3.dp

private val MESSAGE_BOX_HEIGHT = 160.dp

/**
 * 한 줄의 가로 배분.
 *
 * 이름에 가장 많이 준다 - 한글 앱 이름이 잘리면 어느 앱인지 알 수 없고, 막대가 조금 짧아지는
 * 것은 비교에 거의 영향이 없다(모든 줄이 같은 폭으로 짧아진다).
 */
private const val NAME_WEIGHT = 0.42f
private const val BAR_WEIGHT = 0.33f
private const val VALUE_WEIGHT = 0.25f

/** 안내 칸의 키. 불러오는 중과 비어 있음이 같은 자리를 쓴다. */
private const val MESSAGE_KEY = "list_message"
